package com.wrth.mediaplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.util.Base64        // For Base64 decoding
import android.graphics.BitmapFactory
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.bumptech.glide.Glide
import okhttp3.ResponseBody
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import kotlin.math.abs

// Retrofit API service for media controls
interface MediaControlService {
    @POST("media")
    fun controlMedia(@Body action: Map<String, String>): Call<ResponseBody>
}

// Retrofit API service for getting the current track cover image
interface MediaService {
    @GET("currentTrack")
    fun getCurrentTrack(): Call<TrackResponse>
}
interface SeekService {
    @POST("seek")
    fun seekTrack(@Body position: Map<String, Int>): Call<ResponseBody>
}
// Data class for the response containing the album art URL
data class TrackResponse(val playing: Boolean?, val songName: String?, val artistName: String?, val coverImg: String?, val currentPos: Int?, val duration: Int?)

class MainActivity : AppCompatActivity() {
    private lateinit var baseURL: String // Adjust IP as necessary
    private lateinit var coverImageView: ImageView
    private lateinit var backgroundImageView: ImageView
    private lateinit var songNameTextView: TextView
    private lateinit var artistNameTextView: TextView
    private lateinit var songProgressBar: SeekBar
    private lateinit var seekService: SeekService
    private lateinit var elapsedTimeText: TextView
    private lateinit var remainingTimeText: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var gestureDetector: GestureDetector
    private lateinit var hiddenMenu: View
    private lateinit var editText: EditText
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    private val handler = Handler(Looper.getMainLooper())
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(baseURL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val mediaControlService: MediaControlService by lazy { retrofit.create(MediaControlService::class.java) }
    private val mediaService: MediaService by lazy { retrofit.create(MediaService::class.java) }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.decorView.windowInsetsController!!.hide(
            android.view.WindowInsets.Type.statusBars()
        )
        setContentView(R.layout.activity_main)
        coverImageView = findViewById(R.id.coverImageView)
        coverImageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)  // Prevent hardware blur
        backgroundImageView = findViewById(R.id.backgroundImageView)
        songNameTextView = findViewById(R.id.songName)
        artistNameTextView = findViewById(R.id.artistName)
        songProgressBar = findViewById(R.id.songProgressBar)
        playPauseButton= findViewById(R.id.playPauseButton)
        hiddenMenu = findViewById(R.id.hiddenMenu)
        editText = findViewById(R.id.hiddenInput)
        saveButton = findViewById(R.id.saveButton)
        elapsedTimeText = findViewById(R.id.elapsedTimeText)
        remainingTimeText = findViewById(R.id.remainingTimeText)

        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        baseURL = getHiddenText().ifEmpty { "http://100.74.79.10" }

        editText.setText(sharedPreferences.getString("hidden_text", ""))



        // Initialize buttons

        val nextButton: ImageButton = findViewById(R.id.nextButton)
        val prevButton: ImageButton = findViewById(R.id.prevButton)
        // Set button listeners
        playPauseButton.setOnClickListener {
            sendCommand("play_pause")
            animateButton(playPauseButton)
        }
        nextButton.setOnClickListener {
            sendCommand("next")
            animateButton(nextButton)
        }
        prevButton.setOnClickListener {
            sendCommand("previous")
            animateButton(prevButton)
        }

        saveButton.setOnClickListener {
            val text = editText.text.toString()
            sharedPreferences.edit().putString("hidden_text", text).apply() // Save text persistently
            hiddenMenu.visibility = View.GONE // Hide the menu after saving
        }

        hiddenMenu.setOnClickListener {
            hiddenMenu.visibility = View.GONE
        }

        seekService = retrofit.create(SeekService::class.java)
        setupSeekBar()

        // Volume control
        gestureDetector = GestureDetector(this, GestureListener())


        // Start periodic cover image updates
        handler.post(updateDataTask)
    }

    private var backPressedTime: Long = 0

    override fun onBackPressed() {
        if (hiddenMenu.visibility == View.GONE) {
            hiddenMenu.visibility = View.VISIBLE // Show the hidden menu
        } else {
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed() // Exit if pressed twice within 2 seconds
            } else {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
            backPressedTime = System.currentTimeMillis()
        }
    }

    private fun getHiddenText(): String {
        return sharedPreferences.getString("hidden_text", "") ?: ""
    }

    // Periodically update the album cover every second
    private val updateDataTask = object : Runnable {
        override fun run() {
            loadCurrentTrack()
            handler.postDelayed(this, 1000) // Refresh every second
        }
    }

    // Send media control commands
    private fun sendCommand(action: String) {
        val command = mapOf("action" to action)
        mediaControlService.controlMedia(command).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    println("Command sent: $action")
                } else {
                    println("Failed to send command: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("Error sending command: ${t.message}")
            }
        })
    }

    // Load and display the album cover from the server
    private fun loadCurrentTrack() {
        val call = mediaService.getCurrentTrack()
        call.enqueue(object : Callback<TrackResponse> {
            @SuppressLint("SetTextI18n")
            override fun onResponse(call: Call<TrackResponse>, response: Response<TrackResponse>) {
                val playing = response.body()?.playing
                val coverIMG = response.body()?.coverImg
                val songName = response.body()?.songName
                val artistName = response.body()?.artistName
                val currentPosition = response.body()?.currentPos
                val duration = response.body()?.duration

                if (songName != null) {
                    songNameTextView.text = songName
                }
                if (artistName != null) {
                    artistNameTextView.text = artistName
                }
                if (!coverIMG.isNullOrEmpty()) {
                    try {
                        val decodedString = Base64.decode(coverIMG, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                        coverImageView.setImageDrawable(null)  // Ensure it resets
                        coverImageView.setImageBitmap(bitmap)  // Set original unblurred image

                        // Apply blurred album art as background
                        val blurredBitmap = blurBitmap(bitmap)
                        backgroundImageView.setImageBitmap(blurredBitmap)

                    } catch (e: Exception) {
                        e.printStackTrace() // Log the error
                    }
                }
                if (currentPosition != null && duration != null) {
                    songProgressBar.max = duration
                    songProgressBar.progress = currentPosition
                    val elapsedTime = formatTime(currentPosition)
                    val remainingTime = formatTime(duration - currentPosition)
                    elapsedTimeText.text = elapsedTime
                    remainingTimeText.text = "-$remainingTime"
                }
                if (playing != null)
                {
                    if(playing == true)
                    {
                        playPauseButton.setImageResource(R.drawable.ic_pause)  // Replace with your pause icon
                    }
                    else
                    {
                        playPauseButton.setImageResource(R.drawable.ic_play)   // Replace with your play icon
                    }

                }
            }

            override fun onFailure(call: Call<TrackResponse>, t: Throwable) {
                Glide.with(this@MainActivity)
                    .load(R.drawable.placeholder_image)  // Load placeholder on error
                    .into(coverImageView)
            }
        })
    }
    private fun setupSeekBar() {
        var lastProgress = 0
        songProgressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                lastProgress = progress
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Optional: Handle touch start event
                sendCommand("play_pause")

            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Optional: Handle touch stop event

                val positionMs = lastProgress * 1000 // Convert to milliseconds
                seekTrack(positionMs) // Seek to the position

                sendCommand("play_pause")
            }
        })
    }
    private fun seekTrack(positionMs: Int) {
        val seekCommand = mapOf("position_ms" to positionMs)

        seekService.seekTrack(seekCommand).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                if (response.isSuccessful) {
                    println("Seek successful!")
                } else {
                    println("Failed to seek: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("Error seeking track: ${t.message}")
            }
        })
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            return detectSwipeGesture(e1, e2)
        }
    }
    private fun detectSwipeGesture(e1: MotionEvent, e2: MotionEvent): Boolean {
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        if (abs(diffY) > abs(diffX)) {
            if (abs(diffY) > 5) {
                if (diffY > 0) {
                    // Swipe down
                    sendCommand("volumeDown")
                    Toast.makeText(this, "Volume Down", Toast.LENGTH_SHORT).show()
                    return true
                } else {
                    // Swipe up
                    sendCommand("volumeUp")
                    Toast.makeText(this, "Volume Up", Toast.LENGTH_SHORT).show()
                    return true
                }
            }
        }
        return false
    }

    // Function to blur the image
    private fun blurBitmap(bitmap: Bitmap): Bitmap {
        val renderScript = RenderScript.create(this)

        // Create a mutable copy of the bitmap
        val outputBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        val input = Allocation.createFromBitmap(renderScript, outputBitmap)
        val output = Allocation.createTyped(renderScript, input.type)
        val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))

        script.setRadius(25f) // Blur intensity (max is 25)
        script.setInput(input)
        script.forEach(output)
        output.copyTo(outputBitmap)  // Copy to a separate bitmap

        renderScript.destroy()
        return outputBitmap // Return the new blurred bitmap
    }

    private fun animateButton(view: View) {
        view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(100)
        }.start()
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(seconds: Int): String {
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%01d:%02d", minutes, remainingSeconds)
    }
}

<h1>Meedi</h1>
A remote control app built for Android.
The app displays a minimal, Apple-inspired interface reminiscent of Apple Carplay, and allows you to see and control the currently playing media on your Windows computer all from your phone.
While originally designed for Spotify, this works for any media playing on your computer which is recognised by Windows, including music, videos, movies, etc.

<h3>Features</h3>
<li>Displays cover art, and a blurred background using the cover</li>
<li>Displays track name and creator</li>
<li>Displays track progress bar</li>
<li>Play/pause track</li>
<li>Next or previous track</li>
<li>Seek track</li>
<li>Swipe up or down to change volume</li>
<li>Change media source computer</li>

<br>
<br>


![Screenshot_2025-03-20-02-08-15-12_404e6f93c9a5c8d4eb6a9e4d1cf5a58d](https://github.com/user-attachments/assets/13033d34-1d7b-4454-949c-018c7ec0d934)
![Screenshot_2025-03-20-02-09-16-32_404e6f93c9a5c8d4eb6a9e4d1cf5a58d](https://github.com/user-attachments/assets/dfbbf2d8-763f-4f2c-999e-fe9011398695)
![Screenshot_2025-03-20-02-12-23-06_404e6f93c9a5c8d4eb6a9e4d1cf5a58d](https://github.com/user-attachments/assets/9e455ff2-fafe-405d-9afc-d68b09a2f258)

<br>
<br>

<h3>Setup</h3>
In order for the app to communicate with the computer, a python script must be ran on the computer which is playing media. <br>
You can get the server script <a href="https://github.com/tahirwrth/Meedi/blob/master/server/generic.pyw" title="here">here</a><br>
In the app, press back (or swipe depending on your device), and change the address to your computer's local IP address with port 5000. <br>
Press save and restart the app. As long as your script is running and the app is running, you are good to go! <br>
Ensure your firewall is not blocking traffic to port 5000, and your Android device can find your computer on the local network or else this will not work and will require a workaround such as Tailscale.

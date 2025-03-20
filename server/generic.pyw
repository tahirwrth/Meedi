from flask import Flask, request, jsonify
import keyboard  # For media key simulation
import asyncio
import base64
from io import BytesIO
from PIL import Image
from winrt.windows.media.control import \
    GlobalSystemMediaTransportControlsSessionManager as MediaManager
from winrt.windows.storage.streams import Buffer, InputStreamOptions
import ctypes
from pystray import Icon, MenuItem, Menu
import threading
from PIL import Image, ImageDraw
import os

VK_VOLUME_MUTE = 0xAD
VK_VOLUME_DOWN = 0xAE
VK_VOLUME_UP = 0xAF

async def get_media_info():
    try:
        sessions = await MediaManager.request_async()
        current_session = sessions.get_current_session()

        if current_session:  # there needs to be a media session running
            info = await current_session.try_get_media_properties_async()

            # song_attr[0] != '_' ignores system attributes
            info_dict = {song_attr: info.__getattribute__(song_attr) for song_attr in dir(info) if song_attr[0] != '_'}

            # converts winrt vector to list
            info_dict['genres'] = list(info_dict['genres'])

            # Access thumbnail and read data
            if info.thumbnail is not None:
                thumbnail_stream = await info.thumbnail.open_read_async()
                buffer = Buffer(thumbnail_stream.size)
                await thumbnail_stream.read_async(buffer, buffer.capacity, InputStreamOptions.NONE)
                thumbnail_data = bytes(buffer)
                info_dict['thumbnail'] = base64.b64encode(thumbnail_data).decode('utf-8')
            else:
                info_dict['thumbnail'] = None  # No thumbnail available


            # Get playback status
            playback_info = current_session.get_playback_info()
            info_dict['playback_status'] = playback_info.playback_status.name  # Playing, Paused, etc.

            # Get timeline properties
            timeline_props = current_session.get_timeline_properties()
            info_dict['current_position'] = int(timeline_props.position.total_seconds())  # Current position in ms
            info_dict['duration'] = int(timeline_props.end_time.total_seconds())          # Total duration in ms

            return info_dict
    except Exception as e:
        print('Error fetching media data')
        return None

async def play_pause():
    # Get the current media session
    sessions = await MediaManager.request_async()
    current_session = sessions.get_current_session()

    # Send play/pause command
    if current_session:
        playback_info = current_session.get_playback_info()
        if playback_info.playback_status.name == 'PLAYING':
            await current_session.try_pause_async()
        else:
            await current_session.try_play_async()


async def next_track():
    # Get the current media session
    sessions = await MediaManager.request_async()
    current_session = sessions.get_current_session()

    # Send next track command
    if current_session:
        await current_session.try_skip_next_async()

async def previous_track():
    # Get the current media session
    sessions = await MediaManager.request_async()
    current_session = sessions.get_current_session()

    # Send previous track command
    if current_session:
        await current_session.try_skip_previous_async()

async def seek_to_position(milliseconds: int):
    # Get the current media session
    sessions = await MediaManager.request_async()
    current_session = sessions.get_current_session()

    if current_session:
        # Convert milliseconds to ticks (1 millisecond = 10,000 ticks)
        position_in_ticks = milliseconds * 10_000
        await current_session.try_change_playback_position_async(position_in_ticks)
    else:
        print("No active media session found")

def adjust_volume(action):
    if action == 1:
        ctypes.windll.user32.keybd_event(VK_VOLUME_UP, 0, 0, 0)
    elif action == 2:
        ctypes.windll.user32.keybd_event(VK_VOLUME_DOWN, 0, 0, 0)
    elif action == 0:
        ctypes.windll.user32.keybd_event(VK_VOLUME_MUTE, 0, 0, 0)

app = Flask(__name__)
current_media_info = asyncio.run(get_media_info())



@app.route('/currentTrack', methods=['GET'])
def get_current_track():
    try:
        current_media_info = asyncio.run(get_media_info())
        isPlaying = False
        if(current_media_info['playback_status'] == 'PLAYING'):
            isPlaying = True
        return jsonify({'playing': isPlaying, 'songName': current_media_info['title'], 'artistName': current_media_info['artist'], 'coverImg': current_media_info['thumbnail'], 'currentPos': current_media_info['current_position'], 'duration': current_media_info['duration']}), 200
    except:
        print("Error")
        return jsonify({'playing': None, 'songName': None, 'artistName': None, 'coverImg': None, 'currentPos': None, 'duration': None}), 200
    
@app.route('/seek', methods=['POST'])
def seek():
    try:
        position_ms = request.json.get('position_ms')
        asyncio.run(seek_to_position(position_ms))
        print("Seek to", position_ms / 1000)
    except:
        print("Error seeking")
    return {'status': 'success'}, 200

@app.route('/media', methods=['POST'])
def control_media():
    action = request.json.get('action')
    print(f"Received action: {action}")
    if action == 'play_pause':
        asyncio.run(play_pause())
    elif action == 'next':
        asyncio.run(next_track())
    elif action == 'previous':
        asyncio.run(previous_track())
    elif action == 'volumeUp':
        adjust_volume(1)
    elif action == 'volumeDown':
        adjust_volume(2)
    return 'OK'

def create_icon():
    """Create a system tray icon with a quit option."""
    def quit_app(icon, item):
        icon.stop()  # Stops the tray icon loop and exits the app
        os._exit(0)

    # Create an icon image (small white circle)
    def create_image():
        img = Image.new("RGB", (64, 64), (255, 255, 255))
        draw = ImageDraw.Draw(img)
        draw.ellipse((16, 16, 48, 48), fill=(0, 0, 0))
        return img

    # Define the tray menu
    menu = Menu(MenuItem("Quit", quit_app))
    icon = Icon("MediaInfo", create_image(), menu=menu)
    icon.run()

def start_tray():
    """Run the system tray icon in a separate thread."""
    tray_thread = threading.Thread(target=create_icon, daemon=True)
    tray_thread.start()

if __name__ == '__main__':
    start_tray()
    app.run(host='100.74.79.10', port=5000)

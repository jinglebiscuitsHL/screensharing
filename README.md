# Screensharing demo
This is a proof of concept project meant to demonstrate the screensharing capabilities of Android for purposes of using in Help Lightning. Features this demo hopes to implement:
- basic screen recording
  - by showing that we can record the screen, we should be able to send those frames to OpenTok
- PiP
- screen overlay widget
  - like a pip, this widget is a floating controller for the Help Lightning call.
- force HL to the foreground
  - In the event the screensharing episode is canceled by another user, we would like HL to be forced to the foreground for our screensharing user.
  
# Before running
You'll need to edit the screen resolution in MainActivity.kt. Currently it is hardcoded to a Pixel 7 screen sizes which is 1080 x 2200.

# How to use
- Press the "Start Screen Capture" button
- navigate around your phone being sure to open different apps
- navigate back to this app
- Press the "Stop Screen Capture" button

At this point, a file is saved to the app's storage. You'll need to use AndroidStudio's device explorer to find it. Once found, you can transfer to your computer and view the file to verify that the recording was successful.

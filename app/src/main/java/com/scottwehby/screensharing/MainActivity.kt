package com.scottwehby.screensharing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.IOException


class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null

    private val startScreenCapture =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                mediaProjection =
                    mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
                startRecording()
            } else {
                // TODO: Handle failure
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, ScreenCaptureService::class.java))
        } else {
            startService(Intent(this, ScreenCaptureService::class.java))
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 7)
        }

        setContent {
            ScreenShareApp(
                onStartCapture = {
                    startScreenCapture.launch(mediaProjectionManager.createScreenCaptureIntent())
                },
                onStopCapture = {
                    stopService(Intent(this, ScreenCaptureService::class.java))
                    mediaRecorder?.stop()
                    mediaRecorder?.release()
                    virtualDisplay?.release()
                    mediaProjection?.stop()
                }
            )
        }
    }

    private fun startRecording() {
        mediaRecorder = MediaRecorder()
        mediaRecorder?.let {
            prepareMediaRecorder(it)
        }
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

//        mediaRecorder?.apply {
//            try {
//                prepare()
//                start()
//                Thread.sleep(500)
//                stop()
//                reset()
//                release()
//                Log.d(TAG, "Microphone is available")
//            } catch (e: Exception) {
//                Log.e(TAG, "Microphone is not available", e)
//            }
//        }
        mediaProjection?.let {
            virtualDisplay = it.createVirtualDisplay(
                "ScreenCapture",
                1080, 2200, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )
            mediaRecorder?.start()
        }
    }

    private fun prepareMediaRecorder(mR: MediaRecorder) {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mR.apply {
            val file = File(cacheDir, "screen-capture.mp4")
            if (!file.exists()) {
                Log.e(TAG, "Output file does not exist")
            } else {
                Log.d(TAG, "file exists: ${file.absolutePath}")
            }
            if (!file.canWrite()) {
                Log.e(TAG, "Cannot write to output file")
            }
            Log.i("CAMERA", "Recording")
            // Creating MediaRecorder and specifying video source, output format, encoder, and output file

            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            Log.d(TAG, "setVideoSize: ${metrics.widthPixels} x ${metrics.heightPixels}")
            setVideoSize(1080, 2200)
            setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT)
            setOutputFile(file.absolutePath)


            prepare()
            val surface: Surface = surface
//            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.stop()
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
    }

    companion object {
        const val TAG = "MainActivity"
    }
}

@Composable
fun ScreenShareApp(onStartCapture: () -> Unit, onStopCapture: () -> Unit) {
    Column {
        Button(onClick = onStartCapture) {
            Text("Start Screen Capture")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onStopCapture) {
            Text("Stop Screen Capture")
        }
    }
}
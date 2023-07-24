package com.scottwehby.screensharing

import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.io.File

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
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)

        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            val filePath = File(filesDir, "screen-capture.mp4").absolutePath
            setOutputFile(filePath)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(metrics.widthPixels, metrics.heightPixels)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(3 * 1024 * 1024)
            prepare()
        }

        mediaProjection?.let {
            virtualDisplay = it.createVirtualDisplay(
                "ScreenCapture",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )
            mediaRecorder?.start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.stop()
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
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

package com.scottwehby.screensharing

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File


class MainActivity : ComponentActivity() {

    private val state = mutableStateOf(MainState(isSharing = false, isMuted = false, isPiPMode = false))

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

        registerReceiver(broadcastReceiver, IntentFilter(PIP_ACTION))

        setContent {
            ScreenShareApp(
                state = state,
                onStartCapture = {
                    startScreenSharing()
                },
                onStopCapture = {
                    stopScreenSharing()
                },
            )
        }
    }

    private fun startScreenSharing() {
        Log.d(TAG, "startScreenSharing() called")
        startScreenCapture.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun stopScreenSharing() {
        Log.d(TAG, "stopScreenSharing() called")
        stopService(Intent(this, ScreenCaptureService::class.java))
        mediaRecorder?.stop()
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
        state.value = state.value.copy(isSharing = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePictureInPictureParams()
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
            setVideoSize(SCREEN_HEIGHT, SCREEN_WIDTH)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(3 * SCREEN_HEIGHT * SCREEN_WIDTH)
            prepare()
        }

        mediaProjection?.let {
            virtualDisplay = it.createVirtualDisplay(
                "ScreenCapture",
                SCREEN_HEIGHT, SCREEN_WIDTH, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface, null, null
            )
            mediaRecorder?.start()
        }

        state.value = state.value.copy(isSharing = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePictureInPictureParams()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.stop()
        mediaRecorder?.release()
        virtualDisplay?.release()
        mediaProjection?.stop()
    }

    private val broadcastReceiver = object : BroadcastReceiver() {

        // Called when an item is clicked.
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null || intent.action != PIP_ACTION) {
                return
            }
            when (intent.getIntExtra(EXTRA_CONTROL_TYPE, 0)) {
                CONTROL_TYPE_STOP_SHARING -> exitPiPMode()
                CONTROL_TYPE_MIC -> handleMicAction()
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (state.value.isSharing
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPictureInPictureMode(updatePictureInPictureParams())
        }
    }

    private fun exitPiPMode() {
        Log.d(TAG, "exitPiPMode() and stop sharing")
        val startIntent = Intent(this, MainActivity::class.java)
        startIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        startActivity(startIntent)
        stopScreenSharing()
    }

    private fun handleMicAction() {
        Log.d(TAG, "handleMicAction() called")
        state.value = state.value.copy(isMuted = !state.value.isMuted)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePictureInPictureParams()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        state.value = state.value.copy(isPiPMode = isInPictureInPictureMode)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePictureInPictureParams(): PictureInPictureParams {
        Log.d(TAG, "updatePictureInPictureParams() called")
        val builder = PictureInPictureParams.Builder()
            // Set action items for the picture-in-picture mode. These are the only custom controls
            // available during the picture-in-picture mode.
            .setActions(
                listOf(
                    createRemoteAction(
                        R.drawable.ic_pause,
                        R.string.stop,
                        REQUEST_CODE_STOP_SHARING,
                        CONTROL_TYPE_STOP_SHARING
                    ),
                    createRemoteAction(
                        if (state.value.isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic,
                        if (state.value.isMuted) R.string.unmute else R.string.mute,
                        REQUEST_CODE_MIC_ACTION,
                        CONTROL_TYPE_MIC
                    )
                )
            )
            // Set the aspect ratio of the picture-in-picture mode.
            .setAspectRatio(Rational(16, 9))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder
                // Turn the screen into the picture-in-picture mode if it's hidden by the "Home" button.
                .setAutoEnterEnabled(state.value.isSharing)
                // Disables the seamless resize. The seamless resize works great for videos where the
                // content can be arbitrarily scaled, but you can disable this for non-video content so
                // that the picture-in-picture mode is resized with a cross fade animation.
                .setSeamlessResizeEnabled(false)
        }
        val params = builder.build()
        setPictureInPictureParams(params)
        return params
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createRemoteAction(
        @DrawableRes iconResId: Int,
        @StringRes titleResId: Int,
        requestCode: Int,
        controlType: Int
    ): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(this, iconResId),
            getString(titleResId),
            getString(titleResId),
            PendingIntent.getBroadcast(
                this,
                requestCode,
                Intent(PIP_ACTION)
                    .putExtra(EXTRA_CONTROL_TYPE, controlType),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    companion object {
        const val TAG = "MainActivity"

        private const val SCREEN_WIDTH = 2340
        private const val SCREEN_HEIGHT = 1080

        private const val PIP_ACTION = "pip_action"
        private const val EXTRA_CONTROL_TYPE = "pip_control_type"

        private const val CONTROL_TYPE_STOP_SHARING = 1
        private const val CONTROL_TYPE_MIC = 2

        private const val REQUEST_CODE_STOP_SHARING = 33
        private const val REQUEST_CODE_MIC_ACTION = 34
    }
}

@Composable
fun ScreenShareApp(
    state: State<MainState>,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
) {
    Column {
        if (state.value.isSharing) {
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
            ) {
                Text("Currently screen sharing")
            }
            if (!state.value.isPiPMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStopCapture) {
                    Text("Stop Screen Capture")
                }
            }
        } else {
            Button(onClick = onStartCapture) {
                Text("Start Screen Capture")
            }
        }
    }
}

data class MainState(
    val isSharing: Boolean,
    val isMuted: Boolean,
    val isPiPMode: Boolean,
)
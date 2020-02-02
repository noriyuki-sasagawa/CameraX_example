package net.newstyleservice.cameraxexample.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import kotlinx.android.synthetic.main.main_fragment.*
import net.newstyleservice.cameraxexample.R
import java.io.File
import java.util.concurrent.Executors

class MainFragment : Fragment() {

    private val viewModel: MainViewModel by viewModels()

    private var videoCapture: VideoCapture? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    @SuppressLint("RestrictedApi")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraPreview.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }

        startButton.setOnClickListener {
            if (allPermissionsGranted()) {
                videoCapture?.startRecording(
                    File(requireContext().filesDir.path.plus("/${System.currentTimeMillis()}.mp4"))
                    , Executors.newSingleThreadExecutor(),
                    object : VideoCapture.OnVideoSavedListener {
                        override fun onVideoSaved(file: File) {
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    requireContext(),
                                    "${file.name}保存完了",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onError(
                            videoCaptureError: VideoCapture.VideoCaptureError,
                            message: String,
                            cause: Throwable?
                        ) {
                            Toast.makeText(requireContext(), "エラー", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } else {
                requestPermission()
            }
        }
        endButton.setOnClickListener {
            if (!allPermissionsGranted()) return@setOnClickListener

            // stopカメラ
            videoCapture?.stopRecording()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermission()
        }
    }

    override fun onDestroyView() {
        CameraX.unbindAll()
        super.onDestroyView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                requireActivity().runOnUiThread {
                    startCamera()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                requestPermission()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().apply {
            setTargetResolution(Size(300, 400))
            setLensFacing(CameraX.LensFacing.FRONT)
        }.build()
        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = cameraPreview.parent as ViewGroup
            parent.removeView(cameraPreview)
            parent.addView(cameraPreview, 0)

            cameraPreview.surfaceTexture = it.surfaceTexture
        }
        val imageCaptureConfig = ImageCaptureConfig.Builder()
            .apply {
                setLensFacing(CameraX.LensFacing.FRONT)
                setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
            }.build()

        // Build the image capture use case and attach button click listener
        val imageCapture = ImageCapture(imageCaptureConfig)
        val videoCaptureConfig = VideoCaptureConfig.Builder()
            .apply {
                setTargetAspectRatio(AspectRatio.RATIO_4_3)
                setVideoFrameRate(30)
                setLensFacing(CameraX.LensFacing.FRONT)
                setAudioChannelCount(1)
                setAudioBitRate(160)
                setAudioSampleRate(16000)
                setAudioRecordSource(MediaRecorder.AudioSource.MIC)
            }.build()
        videoCapture = VideoCapture(videoCaptureConfig)

        CameraX.bindToLifecycle(this, preview, imageCapture, videoCapture)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = cameraPreview.width / 2f
        val centerY = cameraPreview.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (cameraPreview.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        cameraPreview.setTransform(matrix)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        /**
         * 必要なパーミッションのリスト
         */
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )

        fun newInstance() = MainFragment()
    }
}

package com.dashcam.pedestrian

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.dashcam.pedestrian.databinding.ActivityMainBinding
import com.dashcam.pedestrian.detector.PedestrianDetector
import com.dashcam.pedestrian.service.DashCamService
import com.dashcam.pedestrian.utils.VideoSegmentManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var pedestrianDetector: PedestrianDetector
    private lateinit var videoSegmentManager: VideoSegmentManager
    
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var isRecording = false
    
    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        var allGranted = true
        permissions.entries.forEach {
            if (!it.value) allGranted = false
        }
        
        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "需要相機和錄音權限", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        pedestrianDetector = PedestrianDetector(this) { position, distance ->
            onPedestrianDetected(position, distance)
        }
        videoSegmentManager = VideoSegmentManager(this)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        binding.btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        binding.btnSettings.setOnClickListener {
            // 打開設定畫面
        }
    }
    
    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        
        if (requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }) {
            startCamera()
        } else {
            requestPermissions.launch(requiredPermissions)
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            
            // Image Analysis for pedestrian detection
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        pedestrianDetector.analyze(imageProxy)
                    }
                }
            
            // Video Capture
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.FHD))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            
            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis, videoCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "相機啟動失敗: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun startRecording() {
        val videoCapture = this.videoCapture ?: return
        
        binding.btnRecord.isEnabled = false
        
        val outputFile = videoSegmentManager.createNewSegmentFile()
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        
        recording = videoCapture.output
            .prepareRecording(this, outputOptions)
            .apply {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        isRecording = true
                        binding.btnRecord.text = "停止錄影"
                        binding.btnRecord.isEnabled = true
                        binding.recordingIndicator.visibility = android.view.View.VISIBLE
                        
                        // 設定 60 秒後自動分段
                        scheduleNextSegment()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            videoSegmentManager.onSegmentCompleted(outputFile)
                        }
                    }
                }
            }
    }
    
    private fun scheduleNextSegment() {
        // 60秒後自動停止當前錄影並開始新的分段
        binding.root.postDelayed({
            if (isRecording) {
                recording?.stop()
                // 立即開始新的分段
                binding.root.postDelayed({
                    if (isRecording) {
                        startRecording()
                    }
                }, 100)
            }
        }, 60_000) // 60秒 = 1分鐘
    }
    
    private fun stopRecording() {
        recording?.stop()
        recording = null
        isRecording = false
        binding.btnRecord.text = "開始錄影"
        binding.recordingIndicator.visibility = android.view.View.GONE
    }
    
    private fun onPedestrianDetected(position: PedestrianDetector.Position, distance: Float) {
        runOnUiThread {
            when (position) {
                PedestrianDetector.Position.LEFT -> {
                    binding.warningLeft.visibility = android.view.View.VISIBLE
                    binding.warningLeft.postDelayed({
                        binding.warningLeft.visibility = android.view.View.GONE
                    }, 2000)
                    showWarning("左側行人接近！", distance)
                }
                PedestrianDetector.Position.RIGHT -> {
                    binding.warningRight.visibility = android.view.View.VISIBLE
                    binding.warningRight.postDelayed({
                        binding.warningRight.visibility = android.view.View.GONE
                    }, 2000)
                    showWarning("右側行人接近！", distance)
                }
                PedestrianDetector.Position.CENTER -> {
                    // 中央可以選擇不顯示或顯示不同警告
                }
            }
        }
    }
    
    private fun showWarning(message: String, distance: Float) {
        // 震動警告
        val vibrator = getSystemService(android.os.Vibrator::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(
                android.os.VibrationEffect.createOneShot(300, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            vibrator.vibrate(300)
        }
        
        // 音效警告 (可選)
        binding.warningText.text = "$message (距離: ${String.format("%.1f", distance)}m)"
        binding.warningText.visibility = android.view.View.VISIBLE
        binding.warningText.postDelayed({
            binding.warningText.visibility = android.view.View.GONE
        }, 2000)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        pedestrianDetector.close()
    }
}

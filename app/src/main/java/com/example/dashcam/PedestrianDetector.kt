package com.dashcam.pedestrian.detector

import android.content.Context
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

/**
 * 使用 ML Kit 進行即時行人偵測
 * 偵測畫面中的行人並判斷其位置（左側、右側、中央）
 */
class PedestrianDetector(
    private val context: Context,
    private val onPedestrianDetected: (Position, Float) -> Unit
) {
    
    companion object {
        private const val TAG = "PedestrianDetector"
        
        // 偵測區域劃分（畫面寬度的比例）
        private const val LEFT_ZONE_END = 0.33f
        private const val RIGHT_ZONE_START = 0.67f
        
        // 警告距離閾值（相對大小）
        private const val WARNING_SIZE_THRESHOLD = 0.15f // 物體佔畫面 15% 以上
        
        // 偵測間隔（避免過於頻繁觸發）
        private const val DETECTION_INTERVAL_MS = 500L
    }
    
    enum class Position {
        LEFT, CENTER, RIGHT
    }
    
    private var objectDetector: ObjectDetector
    private var lastDetectionTime = 0L
    private var isProcessing = false
    
    init {
        // 設定物體偵測選項 - 使用預設模型
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification() // 啟用分類以識別人
            .build()
        
        objectDetector = ObjectDetection.getClient(options)
        
        Log.d(TAG, "PedestrianDetector initialized")
    }
    
    /**
     * 分析每一幀影像
     */
    fun analyze(imageProxy: ImageProxy) {
        // 避免處理過於頻繁
        val currentTime = System.currentTimeMillis()
        if (isProcessing || currentTime - lastDetectionTime < DETECTION_INTERVAL_MS) {
            imageProxy.close()
            return
        }
        
        isProcessing = true
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            
            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    processDetections(detectedObjects, imageProxy.width, imageProxy.height)
                    lastDetectionTime = System.currentTimeMillis()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Object detection failed", e)
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        } else {
            isProcessing = false
            imageProxy.close()
        }
    }
    
    /**
     * 處理偵測結果
     */
    private fun processDetections(
        detectedObjects: List<com.google.mlkit.vision.objects.DetectedObject>,
        imageWidth: Int,
        imageHeight: Int
    ) {
        for (obj in detectedObjects) {
            // 檢查是否為人（category label）
            val isPerson = obj.labels.any { label ->
                label.text.equals("person", ignoreCase = true) ||
                label.text.equals("人", ignoreCase = true)
            }
            
            if (!isPerson && obj.labels.isNotEmpty()) {
                continue // 不是人，跳過
            }
            
            val boundingBox = obj.boundingBox
            val position = determinePosition(boundingBox, imageWidth)
            val relativeSize = calculateRelativeSize(boundingBox, imageWidth, imageHeight)
            
            // 只有當物體夠大時才觸發警告（表示距離較近）
            if (relativeSize > WARNING_SIZE_THRESHOLD) {
                val estimatedDistance = estimateDistance(relativeSize)
                
                Log.d(TAG, "Pedestrian detected - Position: $position, Size: $relativeSize, Distance: ${estimatedDistance}m")
                
                onPedestrianDetected(position, estimatedDistance)
            }
        }
    }
    
    /**
     * 判斷物體在畫面中的位置
     */
    private fun determinePosition(boundingBox: Rect, imageWidth: Int): Position {
        val centerX = boundingBox.centerX().toFloat() / imageWidth
        
        return when {
            centerX < LEFT_ZONE_END -> Position.LEFT
            centerX > RIGHT_ZONE_START -> Position.RIGHT
            else -> Position.CENTER
        }
    }
    
    /**
     * 計算物體相對大小（佔畫面比例）
     */
    private fun calculateRelativeSize(boundingBox: Rect, imageWidth: Int, imageHeight: Int): Float {
        val boxArea = boundingBox.width() * boundingBox.height()
        val imageArea = imageWidth * imageHeight
        return boxArea.toFloat() / imageArea
    }
    
    /**
     * 根據物體大小估算距離（簡化版）
     * 實際應用中可以使用更精確的距離估算方法
     */
    private fun estimateDistance(relativeSize: Float): Float {
        // 簡化的距離估算：假設人的平均身高為 1.7m
        // 距離與大小成反比
        return when {
            relativeSize > 0.4f -> 1.0f  // 非常近
            relativeSize > 0.3f -> 2.0f
            relativeSize > 0.2f -> 3.0f
            relativeSize > 0.15f -> 4.0f
            else -> 5.0f
        }
    }
    
    /**
     * 釋放資源
     */
    fun close() {
        objectDetector.close()
        Log.d(TAG, "PedestrianDetector closed")
    }
}

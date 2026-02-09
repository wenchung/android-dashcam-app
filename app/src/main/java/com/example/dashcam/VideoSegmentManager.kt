package com.dashcam.pedestrian.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 管理行車紀錄器影片分段儲存
 * - 自動建立以日期命名的資料夾
 * - 每個影片檔案以時間戳記命名
 * - 自動清理超過 7 天的舊檔案
 */
class VideoSegmentManager(private val context: Context) {
    
    companion object {
        private const val TAG = "VideoSegmentManager"
        private const val FOLDER_NAME = "DashCam"
        private const val MAX_STORAGE_DAYS = 7
        private const val VIDEO_EXTENSION = ".mp4"
    }
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH-mm-ss", Locale.getDefault())
    
    /**
     * 取得今日影片儲存資料夾
     */
    private fun getTodayFolder(): File {
        val today = dateFormat.format(Date())
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val dashCamDir = File(moviesDir, FOLDER_NAME)
        val todayFolder = File(dashCamDir, today)
        
        if (!todayFolder.exists()) {
            todayFolder.mkdirs()
            Log.d(TAG, "Created folder: ${todayFolder.absolutePath}")
        }
        
        return todayFolder
    }
    
    /**
     * 建立新的影片分段檔案
     * 檔名格式: HH-mm-ss.mp4
     */
    fun createNewSegmentFile(): File {
        val folder = getTodayFolder()
        val timestamp = timeFormat.format(Date())
        val filename = "$timestamp$VIDEO_EXTENSION"
        val file = File(folder, filename)
        
        Log.d(TAG, "Creating new segment: ${file.absolutePath}")
        return file
    }
    
    /**
     * 當影片分段完成時呼叫
     */
    fun onSegmentCompleted(file: File) {
        Log.d(TAG, "Segment completed: ${file.name}, size: ${file.length() / 1024 / 1024}MB")
        
        // 清理舊檔案
        cleanOldFiles()
    }
    
    /**
     * 清理超過指定天數的舊影片
     */
    private fun cleanOldFiles() {
        try {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val dashCamDir = File(moviesDir, FOLDER_NAME)
            
            if (!dashCamDir.exists()) return
            
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MAX_STORAGE_DAYS.toLong())
            val folders = dashCamDir.listFiles()?.filter { it.isDirectory } ?: return
            
            for (folder in folders) {
                try {
                    // 解析資料夾名稱為日期
                    val folderDate = dateFormat.parse(folder.name)
                    
                    if (folderDate != null && folderDate.time < cutoffTime) {
                        // 刪除整個資料夾
                        folder.deleteRecursively()
                        Log.d(TAG, "Deleted old folder: ${folder.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing folder date: ${folder.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning old files", e)
        }
    }
    
    /**
     * 取得所有影片檔案（依日期分組）
     */
    fun getAllVideos(): Map<String, List<File>> {
        val result = mutableMapOf<String, List<File>>()
        
        try {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val dashCamDir = File(moviesDir, FOLDER_NAME)
            
            if (!dashCamDir.exists()) return result
            
            val folders = dashCamDir.listFiles()?.filter { it.isDirectory }?.sortedDescending() ?: return result
            
            for (folder in folders) {
                val videos = folder.listFiles()?.filter { 
                    it.isFile && it.extension == "mp4" 
                }?.sortedDescending() ?: emptyList()
                
                if (videos.isNotEmpty()) {
                    result[folder.name] = videos
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting all videos", e)
        }
        
        return result
    }
    
    /**
     * 取得總儲存空間使用量（MB）
     */
    fun getTotalStorageUsed(): Long {
        var totalSize = 0L
        
        try {
            val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            val dashCamDir = File(moviesDir, FOLDER_NAME)
            
            if (dashCamDir.exists()) {
                dashCamDir.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        totalSize += file.length()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating storage", e)
        }
        
        return totalSize / 1024 / 1024 // 轉換為 MB
    }
    
    /**
     * 刪除指定的影片檔案
     */
    fun deleteVideo(file: File): Boolean {
        return try {
            val deleted = file.delete()
            Log.d(TAG, "Delete video: ${file.name}, success: $deleted")
            deleted
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting video", e)
            false
        }
    }
}

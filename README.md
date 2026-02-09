# 智能行車紀錄器 (Pedestrian Detection Dashcam)

具備行人接近警告功能的 Android 行車紀錄器應用程式。

## 主要功能

### 📹 影片錄製
- **自動分段錄製**：每 60 秒自動建立新的影片檔案
- **高畫質錄影**：支援 Full HD (1080p) 錄製
- **自動儲存管理**：
  - 按日期分類儲存影片（格式：`YYYY-MM-DD/HH-mm-ss.mp4`）
  - 自動清理超過 7 天的舊影片
  - 顯示儲存空間使用量

### 🚶 行人偵測與警告
- **即時行人偵測**：使用 Google ML Kit 進行即時物體辨識
- **左右側警告**：
  - 偵測畫面左側（0-33%）和右側（67-100%）的行人
  - 紅色警告區域即時顯示
  - 震動提示
- **距離估算**：根據物體大小估算行人距離
- **智能觸發**：避免過於頻繁的警告（500ms 間隔）

### 🎨 使用者介面
- **橫向全螢幕**：適合車用環境
- **即時預覽**：顯示相機畫面
- **視覺警告**：
  - 左右側紅色警告區域
  - 警告文字顯示（包含距離資訊）
  - 錄影指示燈
- **簡潔控制**：開始/停止錄影按鈕

## 技術架構

### 核心技術
- **CameraX**：現代化的相機 API，支援影片錄製和即時分析
- **ML Kit Object Detection**：Google 機器學習套件進行物體偵測
- **Kotlin Coroutines**：非同步處理
- **ViewBinding**：型別安全的 UI 綁定

### 專案結構

```
app/src/main/
├── AndroidManifest.xml           # 應用程式配置和權限
├── MainActivity.kt               # 主要 Activity（相機、錄影、UI）
├── detector/
│   └── PedestrianDetector.kt    # 行人偵測邏輯
├── utils/
│   └── VideoSegmentManager.kt   # 影片分段管理
└── res/
    ├── layout/
    │   └── activity_main.xml    # 主畫面佈局
    ├── values/
    │   ├── strings.xml          # 文字資源
    │   └── colors.xml           # 顏色定義
    └── drawable/
        └── circle_red.xml       # 錄影指示燈圖形
```

## 權限需求

應用程式需要以下權限：

- ✅ `CAMERA` - 相機錄影
- ✅ `RECORD_AUDIO` - 錄製聲音
- ✅ `WRITE_EXTERNAL_STORAGE` - 儲存影片（Android 9 以下）
- ✅ `VIBRATE` - 震動警告
- ✅ `FOREGROUND_SERVICE_CAMERA` - 前景服務錄影

## 使用方式

### 1. 建置專案

```bash
# 使用 Android Studio
1. 開啟 Android Studio
2. File > Open > 選擇專案資料夾
3. 等待 Gradle 同步完成
4. Build > Make Project

# 或使用命令列
./gradlew assembleDebug
```

### 2. 執行應用程式

```bash
# 連接 Android 裝置或啟動模擬器
adb devices

# 安裝並執行
./gradlew installDebug
```

### 3. 操作說明

1. **首次啟動**：授予相機、錄音、儲存權限
2. **開始錄影**：點擊紅色「開始錄影」按鈕
3. **自動分段**：錄影會每 60 秒自動分段，無需手動操作
4. **行人警告**：
   - 左側出現行人時，左側會顯示紅色警告區域並震動
   - 右側出現行人時，右側會顯示紅色警告區域並震動
   - 畫面上方顯示警告文字和距離估算
5. **停止錄影**：點擊「停止錄影」按鈕

## 影片儲存位置

影片儲存在應用程式專屬目錄：

```
/Android/data/com.dashcam.pedestrian/files/Movies/DashCam/
├── 2024-01-15/
│   ├── 08-30-00.mp4
│   ├── 08-31-00.mp4
│   └── 08-32-00.mp4
├── 2024-01-16/
│   ├── 09-00-00.mp4
│   └── 09-01-00.mp4
```

- 按日期建立資料夾（`YYYY-MM-DD`）
- 影片檔名為開始錄影時間（`HH-mm-ss.mp4`）
- 自動刪除 7 天前的影片

## 系統需求

- **Android 版本**：Android 7.0 (API 24) 或以上
- **建議版本**：Android 10.0 (API 29) 或以上
- **硬體需求**：
  - 後置相機
  - 至少 2GB RAM
  - 建議 16GB 以上儲存空間

## 偵測參數調整

### 警告靈敏度

在 `PedestrianDetector.kt` 中調整：

```kotlin
// 警告閾值（物體佔畫面比例）
private const val WARNING_SIZE_THRESHOLD = 0.15f  // 預設 15%

// 降低數值 = 更靈敏（距離較遠就警告）
// 提高數值 = 較不靈敏（距離較近才警告）
```

### 偵測區域

在 `PedestrianDetector.kt` 中調整：

```kotlin
// 左側區域（畫面 0-33%）
private const val LEFT_ZONE_END = 0.33f

// 右側區域（畫面 67-100%）
private const val RIGHT_ZONE_START = 0.67f
```

### 偵測頻率

```kotlin
// 偵測間隔（毫秒）
private const val DETECTION_INTERVAL_MS = 500L  // 預設 500ms
```

## 效能優化建議

1. **電池優化**：
   - 使用車充供電
   - 避免在電池低於 20% 時長時間錄影

2. **儲存空間**：
   - 定期備份重要影片到外部儲存
   - 可調整保留天數（在 `VideoSegmentManager.kt` 修改 `MAX_STORAGE_DAYS`）

3. **偵測效能**：
   - 降低偵測頻率可減少 CPU 使用
   - 在光線不足環境下偵測效果會下降

## 已知限制

1. **距離估算**：目前使用簡化的距離估算方法，實際距離可能有誤差
2. **夜間偵測**：在低光環境下，行人偵測準確度會降低
3. **移動中偵測**：高速移動時物體可能來不及偵測
4. **多人場景**：人群密集時可能造成頻繁警告

## 未來改進方向

- [ ] 加入設定畫面（調整靈敏度、保留天數等）
- [ ] 支援前後雙鏡頭錄影
- [ ] GPS 座標和速度記錄
- [ ] 碰撞偵測（使用加速度計）
- [ ] 雲端備份功能
- [ ] 更精確的距離測量（使用深度學習）
- [ ] 車道偏離警告
- [ ] 影片瀏覽和播放功能

## 授權

本專案僅供學習和個人使用。

## 貢獻

歡迎提出問題和改進建議！

## 技術支援

如有問題，請查看：
- [CameraX 官方文件](https://developer.android.com/training/camerax)
- [ML Kit 物體偵測](https://developers.google.com/ml-kit/vision/object-detection)
- [Android 開發者文件](https://developer.android.com)
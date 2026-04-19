# 📱 图片批量重命名 APP

一个 Android 原生应用，用于批量重命名图片文件。

---

## ✨ 功能特性

| 功能 | 说明 |
|------|------|
| 🔢 自定义序号长度 | 3 位=001, 4 位=0001，随意设置 |
| 🏷️ 前缀/后缀 | 如 A001.jpg、001_backup.jpg |
| 📎 批量转换格式 | jpg、png、webp、bmp 等 |
| 🖼️ 支持多种格式 | jpg, jpeg, png, gif, bmp, webp, heic 等 |
| 👁️ 重命名预览 | 执行前先预览，安全可靠 |
| 📁 文件夹选择 | 直接选择要处理的文件夹 |

---

## 🛠️ GitHub 自动打包（推荐）

### 步骤

1. **上传到 GitHub**
   - 创建新仓库
   - 上传所有文件

2. **启用 Actions**
   - 点击 Actions 标签
   - 启用 workflow

3. **运行构建**
   - 点击 Run workflow
   - 等待 15-20 分钟

4. **下载 APK**
   - 点击构建记录
   - 底部 Artifacts → 下载 app-debug

---

## 📸 使用说明

### 1. 授予权限
首次启动需要授予存储权限

### 2. 选择文件夹
点击"选择文件夹"按钮

### 3. 设置规则
- 前缀：如 "A" → A001.jpg
- 后缀：如 "_backup" → 001_backup.jpg
- 名称长度：3 → 001
- 起始编号：从几开始
- 输出格式：jpg/png/webp 等

### 4. 预览并执行
确认无误后点击"开始重命名"

---

## 📂 项目结构

```
BatchImageRenamer/
├── app/
│   ├── src/main/
│   │   ├── java/com/batchrenamer/
│   │   │   └── MainActivity.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   ├── values/
│   │   │   └── xml/
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── .github/workflows/
│   └── main.yml
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## 🔧 支持的图片格式

**输入格式：**
jpg, jpeg, png, gif, bmp, webp, heic, heif, tiff, svg, ico

**输出格式：**
jpg, png, webp, bmp, 原格式

---

## ⚠️ 注意事项

1. **权限**：Android 11+ 需要"管理所有文件"权限
2. **备份**：重命名操作不可逆，建议先备份
3. **冲突处理**：目标文件名已存在会自动添加序号

---

## 📄 开源协议

MIT License

---

有问题欢迎反馈！😊

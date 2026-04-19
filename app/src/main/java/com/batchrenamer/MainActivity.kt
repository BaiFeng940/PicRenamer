package com.batchrenamer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.batchrenamer.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 日志工具类
object AppLogger {
    private const val TAG = "PicRenamer"
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    fun i(message: String) {
        val time = dateFormat.format(Date())
        val log = "[$time] ℹ️ $message"
        Log.i(TAG, log)
        logs.add(log)
    }
    
    fun e(message: String, throwable: Throwable? = null) {
        val time = dateFormat.format(Date())
        val log = "[$time] ❌ $message${if (throwable != null) ": ${throwable.message}" else ""}"
        Log.e(TAG, log, throwable)
        logs.add(log)
    }
    
    fun w(message: String) {
        val time = dateFormat.format(Date())
        val log = "[$time] ⚠️ $message"
        Log.w(TAG, log)
        logs.add(log)
    }
    
    fun getLogs(): String = logs.joinToString("\n")
    
    fun clear() = logs.clear()
}

// 简单的数据类
data class FileInfo(
    val id: Long,
    val path: String,
    val name: String,
    val size: Long
)

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val imageList = mutableListOf<ImageItem>()
    private lateinit var adapter: ImageAdapter
    
    private val outputFormats = arrayOf("jpg", "png", "webp", "bmp", "原格式")
    
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            processSelectedImages(uris)
        } else {
            Toast.makeText(this, "未选择图片", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImagesLauncher.launch("image/*")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            AppLogger.i("========== APP 启动 ==========")
            AppLogger.i("Android 版本：${Build.VERSION.SDK_INT}")
            AppLogger.i("应用版本：1.0")
            
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // 隐藏 ActionBar
            supportActionBar?.hide()
            
            setupUI()
            setupRecyclerView()
            
            AppLogger.i("APP 初始化完成")
        } catch (e: Exception) {
            AppLogger.e("APP 启动失败", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupUI() {
        binding.btnSelectImages.setOnClickListener {
            checkPermissionAndPickImages()
        }
        
        binding.btnSmartSort.setOnClickListener {
            smartSortImages()
        }
        
        binding.btnPreview.setOnClickListener {
            showPreview()
        }
        
        binding.btnRename.setOnClickListener {
            confirmAndRename()
        }
        
        binding.btnLogs.setOnClickListener {
            showLogs()
        }
        
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, outputFormats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFormat.adapter = adapter
    }
    
    private fun setupRecyclerView() {
        try {
            adapter = ImageAdapter(
                onItemClick = { item, position ->
                    item.isSelected = !item.isSelected
                    adapter.notifyItemChanged(position)
                    updateImageCount()
                },
                onSelectionChanged = { item, isSelected ->
                    item.isSelected = isSelected
                    updateImageCount()
                }
            )
            
            binding.rvImages.layoutManager = LinearLayoutManager(this)
            binding.rvImages.adapter = adapter
            
            setupItemTouchHelper()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun setupItemTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                val temp = imageList[fromPosition]
                imageList[fromPosition] = imageList[toPosition]
                imageList[toPosition] = temp
                
                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(binding.rvImages)
    }
    
    private fun checkPermissionAndPickImages() {
        try {
            AppLogger.i("用户点击选择图片按钮")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AppLogger.i("Android 13+，直接使用图片选择器")
                pickImagesLauncher.launch("image/*")
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    AppLogger.i("权限已授予，打开图片选择器")
                    pickImagesLauncher.launch("image/*")
                } else {
                    AppLogger.i("请求存储权限")
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        } catch (e: Exception) {
            AppLogger.e("选择图片失败", e)
            Toast.makeText(this, "选择失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun processSelectedImages(uris: List<Uri>) {
        try {
            AppLogger.i("开始处理 ${uris.size} 张图片")
            val newItems = mutableListOf<ImageItem>()
            
            for ((index, uri) in uris.withIndex()) {
                try {
                    val name = getFileNameFromUri(uri) ?: "image_$index.jpg"
                    val size = getFileSizeFromUri(uri)
                    val realPath = getRealPathFromUri(uri)
                    
                    val item = ImageItem(
                        id = System.currentTimeMillis() + index,
                        uri = uri,
                        path = realPath ?: uri.toString(),
                        name = name,
                        size = size ?: 0
                    )
                    newItems.add(item)
                    AppLogger.i("[$index] 加载：$name (${item.getSizeString()}) - 路径：${realPath ?: "URI only"}")
                } catch (e: Exception) {
                    AppLogger.e("加载图片失败 [index=$index]", e)
                }
            }
            
            if (newItems.isNotEmpty()) {
                imageList.addAll(newItems)
                adapter.submitList(imageList.toList())
                updateImageCount()
                AppLogger.i("成功加载 ${newItems.size} 张图片")
                Toast.makeText(this, "已添加 ${newItems.size} 张图片", Toast.LENGTH_SHORT).show()
            } else {
                AppLogger.w("未能加载任何图片")
                Toast.makeText(this, "未能加载图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            AppLogger.e("处理图片失败", e)
            Toast.makeText(this, "处理失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    it.getString(idx)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getFileSizeFromUri(uri: Uri): Long? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndexOrThrow(OpenableColumns.SIZE)
                    it.getLong(idx)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_data")
                    if (idx >= 0) it.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun smartSortImages() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        val sorted = imageList.sortedWith(compareBy { item ->
            extractNumberFromText(item.getNameWithoutExtension())
        })
        
        imageList.clear()
        imageList.addAll(sorted)
        adapter.submitList(imageList.toList())
        
        Toast.makeText(this, "已按文件名排序", Toast.LENGTH_SHORT).show()
    }
    
    private fun extractNumberFromText(text: String): Int {
        val regex = Regex("\\d+")
        return regex.findAll(text).firstOrNull()?.value?.toIntOrNull() ?: 0
    }
    
    private fun updateImageCount() {
        val selectedCount = imageList.count { it.isSelected }
        binding.tvImageCount.text = "已选择 $selectedCount / ${imageList.size} 张图片"
        
        if (imageList.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.rvImages.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.rvImages.visibility = View.VISIBLE
        }
    }
    
    private fun showPreview() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        val selectedItems = imageList.filter { it.isSelected }
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "请至少选择一张图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        val params = getRenameParams()
        val previewList = selectedItems.mapIndexed { index, item ->
            val num = (params.start + index).toString().padStart(params.length, '0')
            val ext = if (params.format == "原格式") item.getExtension() else params.format
            val newName = "${params.prefix}${num}${params.suffix}.$ext"
            Pair(item.name, newName)
        }
        
        val builder = AlertDialog.Builder(this)
            .setTitle("重命名预览")
            .setAdapter(PreviewAdapter(previewList), null)
            .setPositiveButton("确认", null)
            .setNegativeButton("取消", null)
        
        val dialog = builder.create()
        dialog.show()
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            dialog.dismiss()
            executeRename(selectedItems, params)
        }
    }
    
    private fun confirmAndRename() {
        if (imageList.isEmpty()) {
            Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            return
        }
        showPreview()
    }
    
    private fun getRenameParams(): RenameParams {
        val prefix = binding.etPrefix.text?.toString() ?: ""
        val suffix = binding.etSuffix.text?.toString() ?: ""
        val length = binding.etNameLength.text?.toString()?.toIntOrNull() ?: 3
        val start = binding.etStartNumber.text?.toString()?.toIntOrNull() ?: 1
        val formatPos = binding.spinnerFormat.selectedItemPosition
        val format = outputFormats[formatPos]
        
        return RenameParams(prefix, suffix, length, start, format)
    }
    
    private fun executeRename(items: List<ImageItem>, params: RenameParams) {
        try {
            AppLogger.i("========== 开始重命名 ==========")
            AppLogger.i("图片数量：${items.size}")
            AppLogger.i("前缀：${params.prefix}, 后缀：${params.suffix}, 位数：${params.length}, 起始：${params.start}, 格式：${params.format}")
            
            // 检查是否有真实路径
            val itemsWithPath = items.filter { File(it.path).exists() }
            
            if (itemsWithPath.isEmpty()) {
                Toast.makeText(this, "没有可访问的图片文件", Toast.LENGTH_LONG).show()
                AppLogger.w("没有可访问的图片文件")
                return
            }
            
            if (itemsWithPath.size < items.size) {
                Toast.makeText(this, "部分图片无法访问，将跳过 ${items.size - itemsWithPath.size} 张", Toast.LENGTH_SHORT).show()
            }
            
            // 直接使用原文件夹，不选择新文件夹
            val outputDir = File(itemsWithPath[0].path).parentFile
            if (outputDir == null) {
                Toast.makeText(this, "无法确定输出目录", Toast.LENGTH_SHORT).show()
                return
            }
            
            AppLogger.i("输出目录：$outputDir")
            
            var success = 0
            var failed = 0
            val results = mutableListOf<String>()
            
            for ((index, item) in itemsWithPath.withIndex()) {
                try {
                    val num = (params.start + index).toString().padStart(params.length, '0')
                    val ext = if (params.format == "原格式") item.getExtension() else params.format
                    val newName = "${params.prefix}${num}${params.suffix}.$ext"
                    
                    AppLogger.i("[$index] 处理：${item.name} → $newName")
                    
                    val oldFile = File(item.path)
                    val newFile = File(outputDir, newName)
                    
                    // 处理文件名冲突
                    var finalFile = newFile
                    var counter = 1
                    while (finalFile.exists() && finalFile != oldFile) {
                        val baseName = newFile.nameWithoutExtension
                        finalFile = File(outputDir, "${baseName}_$counter.$ext")
                        counter++
                    }
                    
                    // 复制文件而不是重命名（更安全）
                    if (copyFile(oldFile, finalFile)) {
                        success++
                        results.add("✅ ${item.name} → ${finalFile.name}")
                        AppLogger.i("[$index] 成功：${finalFile.name}")
                        
                        // 删除原文件
                        if (oldFile != finalFile) {
                            oldFile.delete()
                        }
                    } else {
                        failed++
                        results.add("❌ ${item.name}: 复制失败")
                        AppLogger.e("[$index] 复制失败")
                    }
                } catch (e: Exception) {
                    failed++
                    results.add("❌ ${item.name}: ${e.message}")
                    AppLogger.e("[$index] 处理失败：${item.name}", e)
                }
            }
            
            AppLogger.i("========== 重命名完成 ==========")
            AppLogger.i("成功：$success, 失败：$failed")
            
            val message = "重命名完成！\n成功：$success\n失败：$failed"
            AlertDialog.Builder(this)
                .setTitle("结果")
                .setMessage(message)
                .setPositiveButton("确定") { _, _ ->
                    imageList.clear()
                    adapter.submitList(emptyList())
                    updateImageCount()
                }
                .setNeutralButton("查看日志") { _, _ ->
                    showLogs()
                }
                .show()
        } catch (e: Exception) {
            AppLogger.e("重命名异常", e)
            Toast.makeText(this, "重命名失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun copyFile(source: File, destination: File): Boolean {
        return try {
            val input = FileInputStream(source)
            val output = FileOutputStream(destination)
            input.use { input ->
                output.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            AppLogger.e("复制文件失败：${source.name} → ${destination.name}", e)
            false
        }
    }
    
    private fun showLogs() {
        val logs = AppLogger.getLogs()
        AlertDialog.Builder(this)
            .setTitle("操作日志")
            .setMessage(logs.ifEmpty { "暂无日志" })
            .setPositiveButton("确定", null)
            .setNegativeButton("清空") { _, _ ->
                AppLogger.clear()
                Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    data class RenameParams(
        val prefix: String,
        val suffix: String,
        val length: Int,
        val start: Int,
        val format: String
    )
    
    inner class PreviewAdapter(
        private val items: List<Pair<String, String>>
    ) : android.widget.BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(android.R.layout.simple_list_item_2, parent, false)
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val text2 = view.findViewById<TextView>(android.R.id.text2)
            
            text1.text = items[position].first
            text2.text = "→ ${items[position].second}"
            
            return view
        }
    }
}

package com.batchrenamer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

// 简单的数据类替代 Quad
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
    
    // 图片选择器
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            processSelectedImages(uris)
        }
    }
    
    // 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pickImagesLauncher.launch("image/*")
        } else {
            Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupRecyclerView()
    }
    
    private fun setupUI() {
        // 选择图片按钮
        binding.btnSelectImages.setOnClickListener {
            checkPermissionAndPickImages()
        }
        
        // 智能排序
        binding.btnSmartSort.setOnClickListener {
            smartSortImages()
        }
        
        // 预览
        binding.btnPreview.setOnClickListener {
            showPreview()
        }
        
        // 重命名
        binding.btnRename.setOnClickListener {
            confirmAndRename()
        }
        
        // 输出格式 Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, outputFormats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFormat.adapter = adapter
    }
    
    private fun setupRecyclerView() {
        adapter = ImageAdapter(
            onItemClick = { item, position ->
                // 点击切换选择状态
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
        
        // 添加拖拽排序
        setupItemTouchHelper()
    }
    
    private fun setupItemTouchHelper() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                
                // 交换列表中的元素
                val temp = imageList[fromPosition]
                imageList[fromPosition] = imageList[toPosition]
                imageList[toPosition] = temp
                
                adapter.notifyItemMoved(fromPosition, toPosition)
                return true
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不支持滑动删除
            }
        })
        itemTouchHelper.attachToRecyclerView(binding.rvImages)
    }
    
    private fun checkPermissionAndPickImages() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                == PackageManager.PERMISSION_GRANTED) {
                pickImagesLauncher.launch("image/*")
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                == PackageManager.PERMISSION_GRANTED) {
                pickImagesLauncher.launch("image/*")
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }
    
    private fun processSelectedImages(uris: List<Uri>) {
        showLoading("正在加载图片...")
        
        // 在新线程中处理
        Thread {
            val newItems = mutableListOf<ImageItem>()
            
            for (uri in uris) {
                try {
                    val fileInfo = getFileInfo(uri)
                    if (fileInfo != null) {
                        val item = ImageItem(
                            id = fileInfo.first,
                            uri = uri,
                            path = fileInfo.second,
                            name = fileInfo.third,
                            size = fileInfo.fourth
                        )
                        newItems.add(item)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            runOnUiThread {
                imageList.addAll(newItems)
                adapter.submitList(imageList.toList())
                updateImageCount()
                hideLoading()
                
                // 自动检测数字
                if (newItems.isNotEmpty()) {
                    detectNumbersInImages(newItems)
                }
            }
        }.start()
    }
    
    private fun getFileInfo(uri: Uri): FileInfo? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("_id"))
                    val displayName = it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    val size = it.getLong(it.getColumnIndexOrThrow(OpenableColumns.SIZE))
                    
                    // 获取真实路径
                    val path = getRealPathFromUri(uri) ?: uri.path ?: ""
                    
                    FileInfo(id, path, displayName, size)
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_data")
                    if (idx != -1) it.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    private fun detectNumbersInImages(images: List<ImageItem>) {
        showLoading("正在识别图片数字...")
        
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        var processed = 0
        
        for (item in images) {
            try {
                val file = File(item.path)
                if (file.exists()) {
                    val image = InputImage.fromFilePath(this, Uri.fromFile(file))
                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            // 从识别的文本中提取数字
                            val number = extractNumberFromText(visionText.text)
                            item.detectedNumber = number
                            
                            processed++
                            if (processed == images.size) {
                                adapter.submitList(imageList.toList())
                                hideLoading()
                            }
                        }
                        .addOnFailureListener {
                            processed++
                            if (processed == images.size) {
                                hideLoading()
                            }
                        }
                } else {
                    processed++
                }
            } catch (e: Exception) {
                e.printStackTrace()
                processed++
            }
        }
        
        if (processed == images.size) {
            hideLoading()
        }
    }
    
    private fun extractNumberFromText(text: String): Int? {
        // 从文本中提取数字（支持多种格式）
        val regex = Regex("\\d+")
        val matches = regex.findAll(text)
        
        // 尝试找到最可能是标识符的数字
        for (match in matches) {
            val num = match.value.toIntOrNull()
            if (num != null && num > 0 && num < 100000) {
                return num
            }
        }
        
        return null
    }
    
    private fun smartSortImages() {
        // 按检测到的数字排序
        val sorted = imageList.sortedWith(compareBy({ it.detectedNumber == null }, { it.detectedNumber ?: 0 }))
        
        imageList.clear()
        imageList.addAll(sorted)
        adapter.submitList(imageList.toList())
        
        val detectedCount = imageList.count { it.detectedNumber != null }
        Toast.makeText(this, "已按检测到的数字排序（$detectedCount 张图片有数字标识）", Toast.LENGTH_LONG).show()
    }
    
    private fun updateImageCount() {
        val selectedCount = imageList.count { it.isSelected }
        binding.tvImageCount.text = "已选择 $selectedCount / ${imageList.size} 张图片"
        
        // 更新空视图
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
        
        // 显示预览对话框
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
        val prefix = binding.etPrefix.text.toString()
        val suffix = binding.etSuffix.text.toString()
        val length = binding.etNameLength.text.toString().toIntOrNull() ?: 3
        val start = binding.etStartNumber.text.toString().toIntOrNull() ?: 1
        val formatPos = binding.spinnerFormat.selectedItemPosition
        val format = outputFormats[formatPos]
        
        return RenameParams(prefix, suffix, length, start, format)
    }
    
    private fun executeRename(items: List<ImageItem>, params: RenameParams) {
        showLoading("正在重命名...")
        
        Thread {
            var success = 0
            var failed = 0
            val results = mutableListOf<String>()
            
            for ((index, item) in items.withIndex()) {
                try {
                    val num = (params.start + index).toString().padStart(params.length, '0')
                    val ext = if (params.format == "原格式") item.getExtension() else params.format
                    val newName = "${params.prefix}${num}${params.suffix}.$ext"
                    
                    val oldFile = File(item.path)
                    val newFile = File(oldFile.parent, newName)
                    
                    // 处理文件名冲突
                    var finalFile = newFile
                    var counter = 1
                    while (finalFile.exists() && finalFile != oldFile) {
                        val baseName = newFile.nameWithoutExtension
                        finalFile = File(oldFile.parent, "${baseName}_$counter.$ext")
                        counter++
                    }
                    
                    if (oldFile.renameTo(finalFile)) {
                        success++
                        results.add("✅ ${item.name} → ${finalFile.name}")
                    } else {
                        failed++
                        results.add("❌ ${item.name}: 重命名失败")
                    }
                } catch (e: Exception) {
                    failed++
                    results.add("❌ ${item.name}: ${e.message}")
                }
            }
            
            runOnUiThread {
                hideLoading()
                
                val message = "重命名完成！\n成功：$success\n失败：$failed\n\n" + results.joinToString("\n")
                AlertDialog.Builder(this)
                    .setTitle("重命名结果")
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ ->
                        // 清空列表
                        imageList.clear()
                        adapter.submitList(emptyList())
                        updateImageCount()
                    }
                    .show()
            }
        }.start()
    }
    
    private fun showLoading(message: String) {
        // 简单实现，可以用 ProgressDialog
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun hideLoading() {
        // 隐藏加载提示
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

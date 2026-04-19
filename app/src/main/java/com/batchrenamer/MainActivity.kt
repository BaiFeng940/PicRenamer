package com.batchrenamer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
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
import java.io.File

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
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupUI()
            setupRecyclerView()
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pickImagesLauncher.launch("image/*")
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                    == PackageManager.PERMISSION_GRANTED) {
                    pickImagesLauncher.launch("image/*")
                } else {
                    requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "选择失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun processSelectedImages(uris: List<Uri>) {
        try {
            val newItems = mutableListOf<ImageItem>()
            
            for ((index, uri) in uris.withIndex()) {
                try {
                    // 直接使用 URI 和文件名，不依赖真实路径
                    val name = getFileNameFromUri(uri) ?: "image_$index.jpg"
                    val size = getFileSizeFromUri(uri)
                    
                    val item = ImageItem(
                        id = System.currentTimeMillis() + index,
                        uri = uri,
                        path = uri.toString(), // 保存 URI 字符串
                        name = name,
                        size = size ?: 0
                    )
                    newItems.add(item)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            if (newItems.isNotEmpty()) {
                imageList.addAll(newItems)
                adapter.submitList(imageList.toList())
                updateImageCount()
                Toast.makeText(this, "已添加 ${newItems.size} 张图片", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "未能加载图片", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "处理失败：${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
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
    
    private fun getFileInfo(uri: Uri): FileInfo? {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex("_id")
                    val nameIdx = it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndexOrThrow(OpenableColumns.SIZE)
                    
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0
                    val name = it.getString(nameIdx)
                    val size = it.getLong(sizeIdx)
                    
                    FileInfo(id, "", name, size)
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
        
        // 简单排序：按文件名中的数字排序
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
            var success = 0
            var failed = 0
            val results = mutableListOf<String>()
            
            // 提示用户选择输出文件夹
            val intent = android.content.Intent(android.app.action.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folderPickerLauncher.launch(intent)
            
            // 保存参数以便后续使用
            pendingRenameItems = items
            pendingRenameParams = params
            
        } catch (e: Exception) {
            Toast.makeText(this, "重命名失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private var pendingRenameItems: List<ImageItem> = emptyList()
    private var pendingRenameParams: RenameParams? = null
    
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null && pendingRenameParams != null) {
            // 获取持久化权限
            contentResolver.takePersistableUriPermission(
                treeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or 
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            
            // 执行重命名（复制到新文件夹）
            executeRenameInFolder(treeUri, pendingRenameItems!!, pendingRenameParams!!)
        }
    }
    
    private fun executeRenameInFolder(treeUri: Uri, items: List<ImageItem>, params: RenameParams) {
        var success = 0
        var failed = 0
        
        try {
            val docFile = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )
            
            for ((index, item) in items.withIndex()) {
                try {
                    val num = (params.start + index).toString().padStart(params.length, '0')
                    val ext = if (params.format == "原格式") item.getExtension() else params.format
                    val newName = "${params.prefix}${num}${params.suffix}.$ext"
                    
                    // 复制文件到新位置
                    val newDocUri = android.provider.DocumentsContract.createFile(
                        contentResolver,
                        treeUri,
                        "image/$ext",
                        newName
                    )
                    
                    if (newDocUri != null) {
                        // 读取原文件
                        val inputStream = contentResolver.openInputStream(item.uri)
                        val outputStream = contentResolver.openOutputStream(newDocUri)
                        
                        inputStream?.use { input ->
                            outputStream?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        success++
                    } else {
                        failed++
                    }
                } catch (e: Exception) {
                    failed++
                    e.printStackTrace()
                }
            }
            
            val message = "重命名完成！\n成功：$success\n失败：$failed"
            AlertDialog.Builder(this)
                .setTitle("结果")
                .setMessage(message)
                .setPositiveButton("确定") { _, _ ->
                    imageList.clear()
                    adapter.submitList(emptyList())
                    updateImageCount()
                }
                .show()
        } catch (e: Exception) {
            Toast.makeText(this, "重命名失败：${e.message}", Toast.LENGTH_LONG).show()
        }
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

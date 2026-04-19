package com.batchrenamer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.batchrenamer.databinding.ActivityMainBinding
import com.batchrenamer.databinding.DialogPreviewBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedFolder: File? = null
    private var imageFiles: List<File> = emptyList()
    
    private val imageExtensions = arrayOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif",
        "tiff", "tif", "svg", "ico", "raw", "cr2", "nef", "arw"
    )
    
    private val outputFormats = arrayOf("jpg", "png", "webp", "bmp", "原格式")
    
    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        checkPermissions()
    }
    
    private fun setupUI() {
        // 文件夹选择
        binding.btnSelectFolder.setOnClickListener {
            selectFolder()
        }
        
        // 预览按钮
        binding.btnPreview.setOnClickListener {
            showPreview()
        }
        
        // 重命名按钮
        binding.btnRename.setOnClickListener {
            confirmAndRename()
        }
        
        // 输出格式 Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, outputFormats)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFormat.adapter = adapter
        
        // 监听设置变化，更新示例
        setupExampleListener()
    }
    
    private fun setupExampleListener() {
        val listener = { updateExample() }
        
        binding.etPrefix.addTextChangedListener { listener() }
        binding.etSuffix.addTextChangedListener { listener() }
        binding.etNameLength.addTextChangedListener { listener() }
        binding.etStartNumber.addTextChangedListener { listener() }
        binding.spinnerFormat.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateExample()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun updateExample() {
        val prefix = binding.etPrefix.text.toString()
        val length = binding.etNameLength.text.toString().toIntOrNull() ?: 3
        val start = binding.etStartNumber.text.toString().toIntOrNull() ?: 1
        val format = outputFormats[binding.spinnerFormat.selectedItemPosition]
        
        val num = start.toString().padStart(length, '0')
        val ext = if (format == "原格式") "jpg" else format
        val example = "${prefix}${num}${binding.etSuffix.text.toString()}.$ext"
        
        binding.tvExample.text = "示例：$example"
    }
    
    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要管理所有文件权限
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 使用媒体权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    PERMISSION_REQUEST_CODE
                )
            }
        } else {
            // Android 12 及以下
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // 权限已授予
            }
        }
    }
    
    private fun selectFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        startActivityForResult(intent, 200)
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == 200 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 保存 URI 权限
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // 获取文件夹路径
                val path = getFullPathFromTreeUri(uri)
                selectedFolder = File(path ?: uri.toString())
                
                binding.tvFolderPath.text = selectedFolder?.absolutePath ?: "未选择"
                
                // 加载图片文件
                loadImages()
            }
        }
    }
    
    private fun getFullPathFromTreeUri(treeUri: Uri): String? {
        if (treeUri.path == null) return null
        
        var treePath = treeUri.path
        if (treePath!!.contains("/document/tree:")) {
            treePath = treePath.substring(treePath.indexOf("/document/tree:") + 15)
        } else if (treePath.contains("/tree:")) {
            treePath = treePath.substring(treePath.indexOf("/tree:") + 6)
        }
        
        return Environment.getExternalStorageDirectory().absolutePath + "/" + treePath
    }
    
    private fun loadImages() {
        selectedFolder?.let { folder ->
            if (folder.exists() && folder.isDirectory) {
                imageFiles = folder.listFiles { file ->
                    !file.isHidden && imageExtensions.any { ext ->
                        file.name.endsWith(".$ext", ignoreCase = true)
                    }
                }?.sortedBy { it.name } ?: emptyList()
                
                binding.tvFileCount.text = getString(R.string.file_count, imageFiles.size)
                
                if (imageFiles.isEmpty()) {
                    showMessage("未找到图片文件")
                }
            }
        }
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
    
    private fun generateNewName(oldFile: File, index: Int, params: RenameParams): String {
        val num = (params.start + index).toString().padStart(params.length, '0')
        val nameWithoutExt = oldFile.nameWithoutExtension
        
        // 处理扩展名
        val ext = if (params.format == "原格式") {
            oldFile.extension
        } else {
            params.format
        }
        
        return "${params.prefix}${num}${params.suffix}.$ext"
    }
    
    private fun showPreview() {
        if (imageFiles.isEmpty()) {
            showMessage("请先选择文件夹")
            return
        }
        
        val params = getRenameParams()
        val previewList = imageFiles.mapIndexed { index, file ->
            Pair(file.name, generateNewName(file, index, params))
        }
        
        // 显示预览对话框
        val dialogBinding = DialogPreviewBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialogBinding.rvPreview.layoutManager = LinearLayoutManager(this)
        dialogBinding.rvPreview.adapter = PreviewAdapter(previewList)
        
        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirm.setOnClickListener { 
            dialog.dismiss()
            executeRename(params)
        }
        
        dialog.show()
    }
    
    private fun confirmAndRename() {
        if (imageFiles.isEmpty()) {
            showMessage("请先选择文件夹")
            return
        }
        showPreview()
    }
    
    private fun executeRename(params: RenameParams) {
        var success = 0
        var failed = 0
        
        imageFiles.forEachIndexed { index, oldFile ->
            try {
                val newName = generateNewName(oldFile, index, params)
                val newFile = File(oldFile.parent, newName)
                
                // 处理文件名冲突
                var finalFile = newFile
                var counter = 1
                while (finalFile.exists() && finalFile != oldFile) {
                    val baseName = newFile.nameWithoutExtension
                    val ext = newFile.extension
                    finalFile = File(oldFile.parent, "${baseName}_$counter.$ext")
                    counter++
                }
                
                if (oldFile.renameTo(finalFile)) {
                    success++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                failed++
                e.printStackTrace()
            }
        }
        
        showMessage("重命名完成！成功：$success, 失败：$failed")
        
        // 重新加载文件列表
        loadImages()
    }
    
    private fun showMessage(msg: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton("确定", null)
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
    ) : RecyclerView.Adapter<PreviewAdapter.ViewHolder>() {
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvOldName: TextView = view.findViewById(R.id.tvOldName)
            val tvNewName: TextView = view.findViewById(R.id.tvNewName)
        }
        
        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_rename_preview, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.tvOldName.text = items[position].first
            holder.tvNewName.text = "→ ${items[position].second}"
        }
        
        override fun getItemCount() = items.size
    }
}

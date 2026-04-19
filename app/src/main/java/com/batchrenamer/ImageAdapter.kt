package com.batchrenamer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.batchrenamer.databinding.ItemImageBinding
import java.io.File

class ImageAdapter(
    private val onItemClick: (ImageItem, Int) -> Unit,
    private val onSelectionChanged: (ImageItem, Boolean) -> Unit
) : ListAdapter<ImageItem, ImageAdapter.ViewHolder>(ImageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class ViewHolder(
        private val binding: ItemImageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ImageItem, index: Int) {
            binding.tvFileName.text = item.name
            binding.tvFileSize.text = item.getSizeString()
            binding.tvIndex.text = index.toString()
            binding.cbSelect.isChecked = item.isSelected

            // 显示检测到的数字
            if (item.detectedNumber != null) {
                binding.tvDetectedNumber.text = "检测到数字：${item.detectedNumber}"
                binding.tvDetectedNumber.visibility = View.VISIBLE
            } else {
                binding.tvDetectedNumber.visibility = View.GONE
            }

            // 加载图片缩略图
            binding.ivThumbnail.load(File(item.path)) {
                crossfade(true)
                size(160, 160)
            }

            // 点击事件
            binding.root.setOnClickListener {
                onItemClick(item, adapterPosition)
            }

            // 复选框事件
            binding.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                onSelectionChanged(item, isChecked)
            }

            // 拖动提示
            binding.ivDragHandle.alpha = 0.5f
        }
    }

    class ImageDiffCallback : DiffUtil.ItemCallback<ImageItem>() {
        override fun areItemsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ImageItem, newItem: ImageItem): Boolean {
            return oldItem == newItem
        }
    }
}

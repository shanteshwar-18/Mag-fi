package com.magfi.mapper.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.magfi.mapper.databinding.ItemWifiBinding

class WifiAdapter : ListAdapter<Pair<String, Int>, WifiAdapter.WifiViewHolder>(DiffCallback()) {

    class WifiViewHolder(private val binding: ItemWifiBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Pair<String, Int>) {
            binding.tvSsid.text = item.first
            binding.tvRssi.text = "${item.second} dBm"

            // Map RSSI from -100 dBm to -30 dBm → 0% to 100%
            val progress = ((item.second - (-100)) * 100 / ((-30) - (-100))).coerceIn(0, 100)
            binding.pbRssi.progress = progress
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val binding = ItemWifiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return WifiViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Pair<String, Int>>() {
        override fun areItemsTheSame(oldItem: Pair<String, Int>, newItem: Pair<String, Int>): Boolean {
            return oldItem.first == newItem.first
        }

        override fun areContentsTheSame(oldItem: Pair<String, Int>, newItem: Pair<String, Int>): Boolean {
            return oldItem == newItem
        }
    }
}

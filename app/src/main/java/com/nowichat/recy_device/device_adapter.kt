package com.nowichat.recy_device

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.RecyclerView
import com.nowichat.R
import com.nowichat.db.device_db.Companion.device_list

class device_adapter(var list: List<device_data>): RecyclerView.Adapter<device_holder>() {
    private var filteredList: List<device_data> = list

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): device_holder {
        return device_holder(LayoutInflater.from(parent.context).inflate(R.layout.recy_device, null))
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            list
        } else {
            list.filter { 
                it.name.lowercase().contains(query.lowercase()) 
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return filteredList.size
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onBindViewHolder(holder: device_holder, position: Int) {
        holder.device(filteredList[position])
    }
}
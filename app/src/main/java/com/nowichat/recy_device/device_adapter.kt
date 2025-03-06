package com.nowichat.recy_device

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nowichat.R
import com.nowichat.db.device_db.Companion.device_list

class device_adapter(var list: List<device_data>): RecyclerView.Adapter<device_holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): device_holder {
        return device_holder(LayoutInflater.from(parent.context).inflate(R.layout.recy_device, null))
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: device_holder, position: Int) {
        holder.device(list[position])
    }

    fun upgrade(){
        this.list = device_list

        notifyDataSetChanged()
    }
}
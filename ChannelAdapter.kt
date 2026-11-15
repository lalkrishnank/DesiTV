package com.syam.desitv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.syam.desitv.data.Channel

class ChannelAdapter(private val onClick: (Channel) -> Unit) : RecyclerView.Adapter<ChannelAdapter.VH>() {
    private var items: List<Channel> = emptyList()
    private var playingUrl: String? = null

    fun submit(list: List<Channel>, playing: String?) {
        items = list
        playingUrl = playing
        notifyDataSetChanged()
    }

    fun indexOf(url: String?): Int = items.indexOfFirst { it.url == url }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val logo: ImageView = v.findViewById(R.id.logo)
        val number: TextView = v.findViewById(R.id.number)
        val name: TextView = v.findViewById(R.id.name)
        val group: TextView = v.findViewById(R.id.group)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = items[position]
        holder.number.text = ch.number.toString()
        holder.name.text = ch.name
        holder.group.text = ch.group
        holder.group.visibility = if (ch.group.isBlank()) View.GONE else View.VISIBLE
        holder.logo.load(ch.logo.ifBlank { null }) {
            crossfade(false)
            placeholder(R.drawable.ic_tv)
            error(R.drawable.ic_tv)
            fallback(R.drawable.ic_tv)
        }
        holder.itemView.isActivated = ch.url == playingUrl
        holder.itemView.setOnClickListener { onClick(ch) }
    }
}

/*
 * Copyright (c) 2026 Maximilian Vogt
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.servers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.country.CountryInfoRepository

/** RecyclerView adapter rendering a filtered/sorted list of [VpnGateServer]s. */
class ServerListAdapter(
    private val countryInfo: CountryInfoRepository,
    private val onServerClicked: (VpnGateServer) -> Unit,
) : ListAdapter<VpnGateServer, ServerListAdapter.ServerViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vpn_server, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ServerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flag: ImageView = itemView.findViewById(R.id.server_flag)
        private val country: TextView = itemView.findViewById(R.id.server_country)
        private val host: TextView = itemView.findViewById(R.id.server_host)
        private val stats: TextView = itemView.findViewById(R.id.server_stats)

        fun bind(server: VpnGateServer) {
            val ctx = itemView.context

            val flagRes = countryInfo.flagResourceId(server.countryShort)
            if (flagRes != 0) flag.setImageResource(flagRes)
            else flag.setImageResource(R.drawable.flag_unknown)

            country.text = countryInfo.countryName(server.countryShort)
            host.text = server.hostName

            val speed = ctx.getString(R.string.free_servers_stat_speed, formatSpeed(server.speedBps))
            val score = ctx.getString(R.string.free_servers_stat_score, formatCount(server.score))
            val ping = if (server.ping >= 0)
                ctx.getString(R.string.free_servers_stat_ping, server.ping) else null
            val uptime = ctx.getString(R.string.free_servers_stat_uptime, formatUptime(server.uptimeMs))

            stats.text = listOfNotNull(score, speed, ping, uptime).joinToString("  ·  ")

            itemView.setOnClickListener { onServerClicked(server) }
        }
    }

    private fun formatSpeed(bps: Long): String {
        val mbps = bps / 1_000_000.0
        return if (mbps >= 10) "%.0f".format(mbps) else "%.1f".format(mbps)
    }

    private fun formatCount(value: Long): String = when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000.0)
        value >= 1_000 -> "%.1fk".format(value / 1_000.0)
        else -> value.toString()
    }

    private fun formatUptime(ms: Long): String {
        val hours = ms / 3_600_000L
        return if (hours >= 24) "${hours / 24} d" else "$hours h"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VpnGateServer>() {
            override fun areItemsTheSame(a: VpnGateServer, b: VpnGateServer) =
                a.hostName == b.hostName && a.ip == b.ip

            override fun areContentsTheSame(a: VpnGateServer, b: VpnGateServer) = a == b
        }
    }
}

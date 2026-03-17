/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.activities

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.OpenVPNManagement
import com.mavodev.openvpnneo.core.OpenVPNService
import com.mavodev.openvpnneo.core.TrafficHistory
import com.mavodev.openvpnneo.core.VpnStatus
import java.util.*
import kotlin.math.max

class GraphActivity : BaseActivity(), VpnStatus.ByteCountListener {
    
    companion object {
        private const val PREF_USE_LOG = "useLogGraph"
        private const val TIME_PERIOD_SECONDS = 0
        private const val TIME_PERIOD_MINUTES = 1
        private const val TIME_PERIOD_HOURS = 2
    }
    
    private lateinit var listView: ListView
    private lateinit var chartAdapter: ChartDataAdapter
    private lateinit var speedStatus: TextView
    private lateinit var handler: Handler
    
    private var colourIn = 0
    private var colourOut = 0
    private var colourPoint = 0
    private var textColour = 0
    private var firstTs = 0L
    private var logScale = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.graph)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.graph)
        
        setupGraph()
        setupWindowInsets()
    }
    
    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val extraPadding = resources.getDimensionPixelSize(R.dimen.stdpadding)
            view.setPadding(insets.left, insets.top + extraPadding, insets.right, insets.bottom)
            windowInsets
        }
    }
    
    private fun setupGraph() {
        listView = findViewById(R.id.graph_listview)
        speedStatus = findViewById(R.id.speedStatus)
        val logScaleView = findViewById<CheckBox>(R.id.useLogScale)
        
        logScale = getPreferences(MODE_PRIVATE).getBoolean(PREF_USE_LOG, false)
        logScaleView.isChecked = logScale
        
        val charts = LinkedList<Int>()
        charts.add(TIME_PERIOD_SECONDS)
        charts.add(TIME_PERIOD_MINUTES)
        charts.add(TIME_PERIOD_HOURS)
        
        chartAdapter = ChartDataAdapter(this, charts)
        listView.adapter = chartAdapter
        
        colourIn = resources.getColor(R.color.dataIn)
        colourOut = resources.getColor(R.color.dataOut)
        colourPoint = resources.getColor(android.R.color.black)
        
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        textColour = when (currentNightMode) {
            Configuration.UI_MODE_NIGHT_NO -> resources.getColor(android.R.color.primary_text_light)
            Configuration.UI_MODE_NIGHT_YES -> resources.getColor(android.R.color.primary_text_dark)
            else -> resources.getColor(android.R.color.primary_text_light)
        }
        
        logScaleView.setOnCheckedChangeListener { _, isChecked ->
            logScale = isChecked
            chartAdapter.notifyDataSetChanged()
            getPreferences(MODE_PRIVATE).edit().putBoolean(PREF_USE_LOG, isChecked).apply()
        }
        
        handler = Handler()
        triggerRefresh = createTriggerRefresh()
    }
    
    private lateinit var triggerRefresh: Runnable
    
    private fun createTriggerRefresh(): Runnable {
        return Runnable {
            chartAdapter.notifyDataSetChanged()
            handler.postDelayed(triggerRefresh, (OpenVPNManagement.mBytecountInterval * 1500).toLong())
        }
    }
    
    override fun onResume() {
        super.onResume()
        
        VpnStatus.addByteCountListener(this)
        handler.postDelayed(triggerRefresh, (OpenVPNManagement.mBytecountInterval * 1500).toLong())
    }
    
    override fun onPause() {
        super.onPause()
        
        handler.removeCallbacks(triggerRefresh)
        VpnStatus.removeByteCountListener(this)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun updateByteCount(inBytes: Long, outBytes: Long, diffIn: Long, diffOut: Long) {
        if (firstTs == 0L)
            firstTs = System.currentTimeMillis() / 100L
        
        val now = (System.currentTimeMillis() / 100L) - firstTs
        val interval = OpenVPNManagement.mBytecountInterval * 10L
        val res = resources
        
        val netstat = String.format(
            getString(R.string.statusline_bytecount),
            OpenVPNService.humanReadableByteCount(inBytes, false, res),
            OpenVPNService.humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true, res),
            OpenVPNService.humanReadableByteCount(outBytes, false, res),
            OpenVPNService.humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true, res)
        )
        
        runOnUiThread {
            handler.removeCallbacks(triggerRefresh)
            speedStatus.text = netstat
            chartAdapter.notifyDataSetChanged()
            handler.postDelayed(triggerRefresh, (OpenVPNManagement.mBytecountInterval * 1500).toLong())
        }
    }
    
    private inner class ChartDataAdapter(context: Context, trafficData: List<Int>) : ArrayAdapter<Int>(context, 0, trafficData) {
        
        private val mContext: Context = context
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var holder: ViewHolder?
            var view = convertView
            
            if (view == null) {
                holder = ViewHolder()
                view = LayoutInflater.from(mContext).inflate(R.layout.graph_item, parent, false)
                holder.chart = view.findViewById(R.id.chart)
                holder.title = view.findViewById(R.id.tvName)
                view.tag = holder
            } else {
                holder = view.tag as ViewHolder
            }
            
            // Apply styling
            holder.chart.description.isEnabled = false
            holder.chart.setDrawGridBackground(false)
            holder.chart.legend.textColor = textColour
            
            // Set no data text color to match our purple theme
            holder.chart.setNoDataTextColor(resources.getColor(R.color.accent))
            
            val xAxis = holder.chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(true)
            xAxis.textColor = textColour
            
            holder.title.text = when (position) {
                TIME_PERIOD_HOURS -> getString(R.string.avghour)
                TIME_PERIOD_MINUTES -> getString(R.string.avgmin)
                else -> getString(R.string.last5minutes)
            }
            
            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (position) {
                        TIME_PERIOD_HOURS -> String.format(Locale.getDefault(), "%.0f\u2009h ago", (xAxis.axisMaximum - value) / 10.0 / 3600.0)
                        TIME_PERIOD_MINUTES -> String.format(Locale.getDefault(), "%.0f\u2009m ago", (xAxis.axisMaximum - value) / 10.0 / 60.0)
                        else -> String.format(Locale.getDefault(), "%.0f\u2009s ago", (xAxis.axisMaximum - value) / 10.0)
                    }
                }
            }
            
            xAxis.labelCount = 5
            
            val yAxis = holder.chart.axisLeft
            yAxis.labelCount = 5
            yAxis.setLabelCount(5, false)
            
            val res = resources
            yAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    if (logScale && value < 2.1f)
                        return "< 100\u2009bit/s"
                    if (logScale) {
                        val scaledValue = Math.pow(10.0, value.toDouble()) / 8.0
                        return OpenVPNService.humanReadableByteCount(scaledValue.toLong(), true, res)
                    }
                    return OpenVPNService.humanReadableByteCount(value.toLong(), true, res)
                }
            }
            yAxis.textColor = textColour
            
            holder.chart.axisRight.isEnabled = false
            
            val data = getDataSet(position)
            val ymax = data.yMax
            
            if (logScale) {
                yAxis.axisMinimum = 2f
                yAxis.axisMaximum = Math.ceil(ymax.toDouble()).toFloat()
                yAxis.labelCount = Math.ceil(ymax.toDouble() - 2.0).toInt()
            } else {
                yAxis.axisMinimum = 0f
                yAxis.resetAxisMaximum()
                yAxis.labelCount = 6
            }
            
            if (data.getDataSetByIndex(0).entryCount < 3)
                holder.chart.data = null
            else
                holder.chart.data = data
            
            holder.chart.setNoDataText(getString(R.string.notenoughdata))
            holder.chart.invalidate()
            
            return view
        }
        
        private fun getDataSet(timeperiod: Int): LineData {
            val dataIn = LinkedList<Entry>()
            val dataOut = LinkedList<Entry>()
            
            val interval: Long
            val totalInterval: Long
            
            val list: LinkedList<TrafficHistory.TrafficDatapoint> = when (timeperiod) {
                TIME_PERIOD_HOURS -> {
                    VpnStatus.trafficHistory.hours.also { 
                        interval = TrafficHistory.TIME_PERIOD_HOURS.toLong()
                        totalInterval = 0L
                    }
                }
                TIME_PERIOD_MINUTES -> {
                    VpnStatus.trafficHistory.minutes.also { 
                        interval = TrafficHistory.TIME_PERIOD_MINTUES.toLong()
                        totalInterval = (TrafficHistory.TIME_PERIOD_HOURS * TrafficHistory.PERIODS_TO_KEEP).toLong()
                    }
                }
                else -> {
                    VpnStatus.trafficHistory.seconds.also { 
                        interval = (OpenVPNManagement.mBytecountInterval * 1000).toLong()
                        totalInterval = (TrafficHistory.TIME_PERIOD_MINTUES * TrafficHistory.PERIODS_TO_KEEP).toLong()
                    }
                }
            }
            
            val workingList = if (list.size == 0) {
                TrafficHistory.getDummyList()
            } else {
                list
            }
            
            var lastts = 0L
            val zeroValue = if (logScale) 2f else 0f
            
            val now = System.currentTimeMillis()
            
            var firstTimestamp = 0L
            var lastBytecountOut = 0L
            var lastBytecountIn = 0L
            
            // Initialize first timestamp from the first item
            if (workingList.isNotEmpty()) {
                val firstItem = workingList.first
                firstTimestamp = firstItem.timestamp
                lastBytecountIn = firstItem.`in`
                lastBytecountOut = firstItem.`out`
            }
            
            for (tdp in workingList) {
                if (totalInterval != 0L && (now - tdp.timestamp) > totalInterval)
                    continue
                
                val t = (tdp.timestamp - firstTimestamp) / 100f
                
                val `in` = (tdp.`in` - lastBytecountIn) / (interval / 1000f).toFloat()
                val out = (tdp.`out` - lastBytecountOut) / (interval / 1000f).toFloat()
                
                lastBytecountIn = tdp.`in`
                lastBytecountOut = tdp.`out`
                
                var processedIn = `in`
                var processedOut = out
                
                if (logScale) {
                    processedIn = max(2f, Math.log10(`in`.toDouble() * 8.0).toFloat())
                    processedOut = max(2f, Math.log10(out.toDouble() * 8.0).toFloat())
                }
                
                if (lastts > 0L && (tdp.timestamp - lastts > 2 * interval)) {
                    dataIn.add(Entry((lastts - firstTimestamp + interval) / 100f, zeroValue))
                    dataOut.add(Entry((lastts - firstTimestamp + interval) / 100f, zeroValue))
                    
                    dataIn.add(Entry(t - interval / 100f, zeroValue))
                    dataOut.add(Entry(t - interval / 100f, zeroValue))
                }
                
                lastts = tdp.timestamp
                
                dataIn.add(Entry(t, processedIn))
                dataOut.add(Entry(t, processedOut))
            }
            
            if (lastts < now - interval) {
                if (now - lastts > 2 * interval * 1000) {
                    dataIn.add(Entry((lastts - firstTimestamp + interval * 1000) / 100f, zeroValue))
                    dataOut.add(Entry((lastts - firstTimestamp + interval * 1000) / 100f, zeroValue))
                }
                
                dataIn.add(Entry((now - firstTimestamp) / 100f, zeroValue))
                dataOut.add(Entry((now - firstTimestamp) / 100f, zeroValue))
            }
            
            val dataSets = ArrayList<ILineDataSet>()
            
            val indata = LineDataSet(dataIn, getString(R.string.data_in))
            val outdata = LineDataSet(dataOut, getString(R.string.data_out))
            
            setLineDataAttributes(indata, colourIn)
            setLineDataAttributes(outdata, colourOut)
            
            dataSets.add(indata)
            dataSets.add(outdata)
            
            return LineData(dataSets)
        }
        
        private fun setLineDataAttributes(dataSet: LineDataSet, colour: Int) {
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 1f
            dataSet.setDrawCircles(true)
            dataSet.setCircleColor(colourPoint)
            dataSet.setDrawFilled(true)
            dataSet.fillAlpha = 42
            dataSet.fillColor = colour
            dataSet.color = colour
            dataSet.mode = LineDataSet.Mode.LINEAR
            dataSet.setDrawValues(false)
            dataSet.valueTextColor = textColour
        }
    }
    
    private class ViewHolder {
        lateinit var chart: LineChart
        lateinit var title: TextView
    }
}

/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.OpenVPNManagement
import com.mavodev.openvpnneo.core.OpenVPNService.humanReadableByteCount
import com.mavodev.openvpnneo.core.TrafficHistory
import com.mavodev.openvpnneo.core.VpnStatus
import java.util.LinkedList
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Created by arne on 19.05.17.
 */
class GraphFragment : Fragment(), VpnStatus.ByteCountListener {
    private lateinit var mListView: ListView

    private lateinit var mChartAdapter: ChartDataAdapter
    private var mColourIn = 0
    private var mColourOut = 0
    private var mColourPoint = 0
    private var mTextColour = 0

    private var firstTs: Long = 0
    private lateinit var mSpeedStatus: TextView
    private var mLogScale = false

    private lateinit var mHandler: Handler

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.graph, container, false)
        mListView = v.findViewById(R.id.graph_listview)
        mSpeedStatus = v.findViewById(R.id.speedStatus)
        val logScaleView = v.findViewById<CheckBox>(R.id.useLogScale)
        mLogScale = requireActivity().getPreferences(Context.MODE_PRIVATE).getBoolean(PREF_USE_LOG, false)
        logScaleView.isChecked = mLogScale

        val charts = LinkedList<Int>()
        charts.add(TIME_PERIOD_SECDONS)
        charts.add(TIME_PERIOD_MINUTES)
        charts.add(TIME_PERIOD_HOURS)

        mChartAdapter = ChartDataAdapter(requireActivity(), charts)
        mListView.adapter = mChartAdapter

        mColourIn = ContextCompat.getColor(requireContext(), R.color.dataIn)
        mColourOut = ContextCompat.getColor(requireContext(), R.color.dataOut)
        mColourPoint = ContextCompat.getColor(requireContext(), android.R.color.black)

        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_NO ->
                mTextColour = ContextCompat.getColor(requireContext(), android.R.color.primary_text_light)
            Configuration.UI_MODE_NIGHT_YES ->
                mTextColour = ContextCompat.getColor(requireContext(), android.R.color.primary_text_dark)
        }

        logScaleView.setOnCheckedChangeListener { _, isChecked ->
            mLogScale = isChecked
            mChartAdapter.notifyDataSetChanged()
            requireActivity().getPreferences(Context.MODE_PRIVATE).edit().putBoolean(PREF_USE_LOG, isChecked).apply()
        }

        mHandler = Handler(Looper.getMainLooper())

        return v
    }

    private val triggerRefresh: Runnable = object : Runnable {
        override fun run() {
            mChartAdapter.notifyDataSetChanged()
            mHandler.postDelayed(this, (OpenVPNManagement.mBytecountInterval * 1500).toLong())
        }
    }

    override fun onResume() {
        super.onResume()
        VpnStatus.addByteCountListener(this)
        mHandler.postDelayed(triggerRefresh, (OpenVPNManagement.mBytecountInterval * 1500).toLong())
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacks(triggerRefresh)
        VpnStatus.removeByteCountListener(this)
    }

    override fun updateByteCount(bytesIn: Long, bytesOut: Long, diffIn: Long, diffOut: Long) {
        if (firstTs == 0L) firstTs = System.currentTimeMillis() / 100

        val res = requireActivity().resources

        val netstat = String.format(
            getString(R.string.statusline_bytecount),
            humanReadableByteCount(bytesIn, false, res),
            humanReadableByteCount(diffIn / OpenVPNManagement.mBytecountInterval, true, res),
            humanReadableByteCount(bytesOut, false, res),
            humanReadableByteCount(diffOut / OpenVPNManagement.mBytecountInterval, true, res)
        )

        activity?.runOnUiThread {
            mHandler.removeCallbacks(triggerRefresh)
            mSpeedStatus.text = netstat
            mChartAdapter.notifyDataSetChanged()
            mHandler.postDelayed(triggerRefresh, (OpenVPNManagement.mBytecountInterval * 1500).toLong())
        }
    }

    private inner class ChartDataAdapter(private val mContext: Context, trafficData: List<Int>) :
        ArrayAdapter<Int>(mContext, 0, trafficData) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: ViewHolder
            var cv = convertView

            if (cv == null) {
                holder = ViewHolder()
                cv = LayoutInflater.from(mContext).inflate(R.layout.graph_item, parent, false)
                holder.chart = cv.findViewById(R.id.chart)
                holder.title = cv.findViewById(R.id.tvName)
                cv.tag = holder
            } else {
                holder = cv.tag as ViewHolder
            }

            // apply styling
            holder.chart.description.isEnabled = false
            holder.chart.setDrawGridBackground(false)
            holder.chart.legend.textColor = mTextColour

            // Set no data text color to match our purple theme
            holder.chart.setNoDataTextColor(ContextCompat.getColor(context, R.color.accent))

            val xAxis = holder.chart.xAxis
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.setDrawAxisLine(true)
            xAxis.textColor = mTextColour

            when (position) {
                TIME_PERIOD_HOURS -> holder.title.setText(R.string.avghour)
                TIME_PERIOD_MINUTES -> holder.title.setText(R.string.avgmin)
                else -> holder.title.setText(R.string.last5minutes)
            }

            xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (position) {
                        TIME_PERIOD_HOURS ->
                            String.format(Locale.getDefault(), "%.0f\u2009h ago", (xAxis.axisMaximum - value) / 10 / 3600)
                        TIME_PERIOD_MINUTES ->
                            String.format(Locale.getDefault(), "%.0f\u2009m ago", (xAxis.axisMaximum - value) / 10 / 60)
                        else ->
                            String.format(Locale.getDefault(), "%.0f\u2009s ago", (xAxis.axisMaximum - value) / 10)
                    }
                }
            }
            xAxis.setLabelCount(5)

            val yAxis = holder.chart.axisLeft
            yAxis.setLabelCount(5, false)

            val res = requireActivity().resources
            yAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    var v = value
                    if (mLogScale && v < 2.1f) return "< 100\u2009bit/s"
                    if (mLogScale) v = 10.0.pow(v.toDouble()).toFloat() / 8
                    return humanReadableByteCount(v.toLong(), true, res)
                }
            }
            yAxis.textColor = mTextColour

            holder.chart.axisRight.isEnabled = false

            val data = getDataSet(position)
            val ymax = data.yMax

            if (mLogScale) {
                yAxis.axisMinimum = 2f
                yAxis.axisMaximum = ceil(ymax.toDouble()).toFloat()
                yAxis.setLabelCount(ceil((ymax - 2f).toDouble()).toInt())
            } else {
                yAxis.axisMinimum = 0f
                yAxis.resetAxisMaximum()
                yAxis.setLabelCount(6)
            }

            if (data.getDataSetByIndex(0).entryCount < 3) {
                holder.chart.data = null
            } else {
                holder.chart.data = data
            }

            holder.chart.setNoDataText(getString(R.string.notenoughdata))

            holder.chart.invalidate()

            return cv
        }

        private fun getDataSet(timeperiod: Int): LineData {
            val dataIn = LinkedList<Entry>()
            val dataOut = LinkedList<Entry>()

            val interval: Long
            val totalInterval: Long

            var list: LinkedList<TrafficHistory.TrafficDatapoint>
            when (timeperiod) {
                TIME_PERIOD_HOURS -> {
                    list = VpnStatus.trafficHistory.hours
                    interval = TrafficHistory.TIME_PERIOD_HOURS.toLong()
                    totalInterval = 0
                }
                TIME_PERIOD_MINUTES -> {
                    list = VpnStatus.trafficHistory.minutes
                    interval = TrafficHistory.TIME_PERIOD_MINTUES.toLong()
                    totalInterval = TrafficHistory.TIME_PERIOD_HOURS * TrafficHistory.PERIODS_TO_KEEP
                }
                else -> {
                    list = VpnStatus.trafficHistory.seconds
                    interval = (OpenVPNManagement.mBytecountInterval * 1000).toLong()
                    totalInterval = TrafficHistory.TIME_PERIOD_MINTUES * TrafficHistory.PERIODS_TO_KEEP
                }
            }
            if (list.size == 0) {
                list = TrafficHistory.getDummyList()
            }

            var lastts: Long = 0
            val zeroValue: Float = if (mLogScale) 2f else 0f

            val now = System.currentTimeMillis()

            var firstTimestamp: Long = 0
            var lastBytecountOut: Long = 0
            var lastBytecountIn: Long = 0

            for (tdp in list) {
                if (totalInterval != 0L && (now - tdp.timestamp) > totalInterval) continue

                if (firstTimestamp == 0L) {
                    firstTimestamp = list.peek().timestamp
                    lastBytecountIn = list.peek().`in`
                    lastBytecountOut = list.peek().out
                }

                val t = (tdp.timestamp - firstTimestamp) / 100f

                var inRate = (tdp.`in` - lastBytecountIn) / (interval / 1000).toFloat()
                var outRate = (tdp.out - lastBytecountOut) / (interval / 1000).toFloat()

                lastBytecountIn = tdp.`in`
                lastBytecountOut = tdp.out

                if (mLogScale) {
                    inRate = max(2f, log10(inRate * 8.0).toFloat())
                    outRate = max(2f, log10(outRate * 8.0).toFloat())
                }

                if (lastts > 0 && (tdp.timestamp - lastts > 2 * interval)) {
                    dataIn.add(Entry((lastts - firstTimestamp + interval) / 100f, zeroValue))
                    dataOut.add(Entry((lastts - firstTimestamp + interval) / 100f, zeroValue))

                    dataIn.add(Entry(t - interval / 100f, zeroValue))
                    dataOut.add(Entry(t - interval / 100f, zeroValue))
                }

                lastts = tdp.timestamp

                dataIn.add(Entry(t, inRate))
                dataOut.add(Entry(t, outRate))
            }
            if (lastts < now - interval) {
                if (now - lastts > 2 * interval * 1000) {
                    dataIn.add(Entry((lastts - firstTimestamp + interval * 1000) / 100f, zeroValue))
                    dataOut.add(Entry((lastts - firstTimestamp + interval * 1000) / 100f, zeroValue))
                }

                dataIn.add(Entry(((now - firstTimestamp) / 100).toFloat(), zeroValue))
                dataOut.add(Entry(((now - firstTimestamp) / 100).toFloat(), zeroValue))
            }

            val dataSets = ArrayList<ILineDataSet>()

            val indata = LineDataSet(dataIn, getString(R.string.data_in))
            val outdata = LineDataSet(dataOut, getString(R.string.data_out))

            setLineDataAttributes(indata, mColourIn)
            setLineDataAttributes(outdata, mColourOut)

            dataSets.add(indata)
            dataSets.add(outdata)

            return LineData(dataSets)
        }

        private fun setLineDataAttributes(dataSet: LineDataSet, colour: Int) {
            dataSet.lineWidth = 2f
            dataSet.circleRadius = 1f
            dataSet.setDrawCircles(true)
            dataSet.setCircleColor(mColourPoint)
            dataSet.setDrawFilled(true)
            dataSet.fillAlpha = 42
            dataSet.fillColor = colour
            dataSet.color = colour
            dataSet.mode = LineDataSet.Mode.LINEAR

            dataSet.setDrawValues(false)
            dataSet.valueTextColor = mTextColour
        }
    }

    private class ViewHolder {
        lateinit var chart: LineChart
        lateinit var title: TextView
    }

    companion object {
        private const val PREF_USE_LOG = "useLogGraph"
        private const val TIME_PERIOD_SECDONS = 0
        private const val TIME_PERIOD_MINUTES = 1
        private const val TIME_PERIOD_HOURS = 2
    }
}

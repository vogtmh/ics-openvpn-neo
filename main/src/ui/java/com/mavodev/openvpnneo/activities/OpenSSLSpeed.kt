/*
 * Copyright (c) 2012-2017 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

package com.mavodev.openvpnneo.activities

import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.Switch
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.core.NativeUtils
import com.mavodev.openvpnneo.core.OpenVPNService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class OpenSSLSpeed : BaseActivity() {
    private lateinit var mAdapter: SpeedArrayAdapter
    private lateinit var mListView: ListView
    private lateinit var mStartStopButton: Button
    private lateinit var mAlgorithmListView: ListView
    private var mTestRunning = false
    private var mStopRequested = false


    internal class SpeedArrayAdapter(private val mContext: Context) :
        ArrayAdapter<SpeedResult>(mContext, 0) {
        private val mInflater: LayoutInflater

        init {
            mInflater = LayoutInflater.from(mContext)

        }

        internal data class ViewHolder(
            var ciphername: TextView,
            var speed: TextView,
            var blocksize: TextView,
            var blocksInTime: TextView
        )

        override fun getView(position: Int, v: View?, parent: ViewGroup): View {
            var view = v
            val res = getItem(position)
            if (view == null) {
                view = mInflater.inflate(R.layout.speedviewitem, parent, false)!!
                val holder = ViewHolder(
                    view.findViewById(R.id.ciphername),
                    view.findViewById(R.id.speed),
                    view.findViewById(R.id.blocksize),
                    view.findViewById(R.id.blocksintime)
                )
                view.tag = holder
            }

            val holder = view.tag as ViewHolder

            val total = res!!.count * res.length
            val size = OpenVPNService.humanReadableByteCount(
                res.length.toLong(),
                false,
                mContext.resources
            )

            holder.blocksize.text = size
            holder.ciphername.text = res.algorithm

            if (res.failed) {
                holder.blocksInTime.setText(R.string.openssl_error)
                holder.speed.text = "-"
            } else if (res.running) {
                holder.blocksInTime.setText(R.string.running_test)
                holder.speed.text = "-"
            } else {
                val totalBytes =
                    OpenVPNService.humanReadableByteCount(total.toLong(), false, mContext.resources)
                // TODO: Fix localisation here
                val blockPerSec = OpenVPNService.humanReadableByteCount(
                    (total / res.time).toLong(),
                    false,
                    mContext.resources
                ) + "/s"
                holder.speed.text = blockPerSec
                holder.blocksInTime.text = String.format(
                    Locale.ENGLISH,
                    "%d blocks (%s) in %2.1fs",
                    res.count.toLong(),
                    totalBytes,
                    res.time
                )
            }

            return view

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.openssl_speed)
        setUpEdgeEdgeInsetsListener(getWindow().getDecorView().getRootView(), R.id.speed_root)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        findViewById<View>(R.id.start_stop_button).setOnClickListener { _ -> 
            if (mTestRunning) {
                // Stop the test
                mStopRequested = true
                Log.d("OpenSSLSpeed", "Stop requested by user")
            } else {
                // Start the test
                mStopRequested = false
                mTestRunning = true
                updateButtonState()
                runAlgorithms(getSelectedAlgorithms())
            }
        }

        // Initialize new UI elements
        mStartStopButton = findViewById(R.id.start_stop_button)
        mAlgorithmListView = findViewById(R.id.algorithm_list)

        // Set up algorithm list with checkboxes
        setupAlgorithmList()
        
        // Initialize results list
        mListView = findViewById(R.id.results)
        mAdapter = SpeedArrayAdapter(this)
        mListView.adapter = mAdapter
        
        // Set initial button state
        updateButtonState()
    }

    private fun updateButtonState() {
        val hasSelectedAlgorithms = getSelectedAlgorithmsCount() > 0
        
        if (mTestRunning) {
            mStartStopButton.text = "STOP"
            mStartStopButton.setBackgroundResource(R.drawable.stop_button_bg)
            mStartStopButton.isEnabled = true
        } else {
            mStartStopButton.text = "START"
            mStartStopButton.setBackgroundResource(R.drawable.start_button_bg)
            mStartStopButton.isEnabled = hasSelectedAlgorithms
        }
    }
    
    private fun getSelectedAlgorithmsCount(): Int {
        val adapter = mAlgorithmListView.adapter as AlgorithmAdapter
        var count = 0
        
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            if (item.isSelected) {
                count++
            }
        }
        
        Log.d("OpenSSLSpeed", "Selected algorithms count: $count")
        return count
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Check if we came from settings
                if (intent.getBooleanExtra("from_settings", false)) {
                    // Simply go back
                    finish()
                    true
                } else {
                    // Use default behavior (go to profiles)
                    super.onOptionsItemSelected(item)
                }
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun runAlgorithms(algorithms: String) {
        // Clear old results before starting new test
        mAdapter.clear()
        
        mStopRequested = false  // Reset stop flag for new test
        mTestRunning = true
        updateButtonState()
        lifecycleScope.launch {
            runSpeedTest(algorithms)
        }
    }

    private fun stopSpeedTest() {
        mStopRequested = true
        mTestRunning = false
        updateButtonState()
        
        // Mark all running tests as stopped
        for (i in 0 until mAdapter.count) {
            val result = mAdapter.getItem(i)
            if (result != null && result.running) {
                result.running = false
                result.failed = true
            }
        }
        mAdapter.notifyDataSetChanged()
    }


    internal class SpeedResult(var algorithm: String) {
        var failed = false

        var count: Double = 0.toDouble()
        var time: Double = 0.toDouble()
        var speed: Double = 0.toDouble()
        var length: Int = 0
        var running = true
    }

    internal suspend fun showResults(vararg values: SpeedResult) {
        withContext(Dispatchers.Main) {
            for (r in values) {
                // Always add/update results
                val existingIndex = findExistingResultIndex(r.algorithm, r.length)
                if (existingIndex >= 0) {
                    // Replace existing result
                    val existingResult = mAdapter.getItem(existingIndex)
                    if (existingResult != null) {
                        mAdapter.remove(existingResult)
                    }
                    mAdapter.insert(r, existingIndex)
                } else {
                    // Add new result
                    mAdapter.add(r)
                }
                mAdapter.notifyDataSetChanged()
            }
        }
    }
    
    private fun findExistingResultIndex(algorithm: String, length: Int): Int {
        for (i in 0 until mAdapter.count) {
            val result = mAdapter.getItem(i)
            if (result != null && result.algorithm == algorithm && result.length == length) {
                return i
            }
        }
        return -1
    }

    suspend fun runSpeedTest(algorithms: String) {
        Log.d("OpenSSLSpeed", "=== Starting speed test with algorithms: '$algorithms' ===")
        withContext(Dispatchers.IO)
        {
            val mResult = Vector<SpeedResult>()

            for (algorithm in algorithms.split(" ")) {
                Log.d("OpenSSLSpeed", "Processing algorithm: '$algorithm'")
                
                // Check if stop was requested before starting each test
                if (mStopRequested) {
                    Log.d("OpenSSLSpeed", "Stop requested, breaking out of algorithm loop")
                    break
                }
                
                // Test each algorithm with multiple sizes (original behavior)
                // Skip 16b and 16k as they are not relevant for VPN
                var i = 1
                while (i < NativeUtils.openSSLlengths.size - 1) {
                    // Check if stop was requested before each iteration
                    if (mStopRequested) {
                        Log.d("OpenSSLSpeed", "Stop requested, breaking out of size loop")
                        break
                    }
                    
                    val result = SpeedResult(algorithm)
                    result.length = NativeUtils.openSSLlengths[i]
                    result.running = true
                    Log.d("OpenSSLSpeed", "Testing algorithm '$algorithm' with length: ${result.length}")
                    
                    // Show running result immediately for responsiveness
                    showResults(result)
                    
                    val resi = NativeUtils.getOpenSSLSpeed(algorithm, i)
                    
                    if (resi == null) {
                        Log.e("OpenSSLSpeed", "NativeUtils.getOpenSSLSpeed returned null for algorithm '$algorithm' with length ${result.length}")
                        result.failed = true
                    } else {
                        Log.d("OpenSSLSpeed", "NativeUtils.getOpenSSLSpeed returned: time=${resi[0]}, count=${resi[1]}, speed=${resi[2]}")
                        result.time = resi[0]
                        result.count = resi[1]
                        result.speed = resi[2]
                    }
                    result.running = false
                    mResult.add(result)
                    showResults(result) // Update with final result
                    i++
                }
                
                // Check if stop was requested after completing all sizes for this algorithm
                if (mStopRequested) {
                    break
                }
            }
            
            Log.d("OpenSSLSpeed", "=== Speed test completed ===")
            
            // Update button state back to START
            mTestRunning = false
            updateButtonState()
        }
    }

    private fun setupAlgorithmList() {
        Log.d("OpenSSLSpeed", "Setting up algorithm list")
        val allAlgorithms = listOf(
            "aes-256-gcm", "chacha20-poly1305", "aes-128-cbc", "sha1"
        )
        val algorithmItems = mutableListOf<AlgorithmItem>()
        
        for (algorithm in allAlgorithms) {
            // Enable aes-256-gcm by default, others disabled
            val isSelected = (algorithm == "aes-256-gcm")
            algorithmItems.add(AlgorithmItem(algorithm, isSelected))
            Log.d("OpenSSLSpeed", "Added algorithm to list: '$algorithm', selected: $isSelected")
        }
        
        val adapter = AlgorithmAdapter(this, algorithmItems) { updateButtonState() }
        mAlgorithmListView.adapter = adapter
        Log.d("OpenSSLSpeed", "Algorithm list setup complete with ${algorithmItems.size} items")
        
        // Update button state based on default selection
        updateButtonState()
    }
    
    private fun getSelectedAlgorithms(): String {
        val adapter = mAlgorithmListView.adapter as AlgorithmAdapter
        val selectedAlgorithms = mutableListOf<String>()
        
        Log.d("OpenSSLSpeed", "Getting selected algorithms from ${adapter.count} items")
        
        for (i in 0 until adapter.count) {
            val item = adapter.getItem(i)
            Log.d("OpenSSLSpeed", "Algorithm $i: '${item.algorithm}', selected: ${item.isSelected}")
            
            if (item.isSelected) {
                selectedAlgorithms.add(item.algorithm)
                Log.d("OpenSSLSpeed", "Added selected algorithm: '${item.algorithm}'")
            }
        }
        
        val result = selectedAlgorithms.joinToString(" ")
        Log.d("OpenSSLSpeed", "Final selected algorithms string: '$result'")
        return result
    }
    
    private fun getAlgorithmTestNumber(algorithmName: String): Int {
        val algorithmToTestNumber = mapOf(
            "aes-256-gcm" to 1,
            "chacha20-poly1305" to 2, 
            "aes-128-cbc" to 3,
            "sha1" to 4
        )
        val testNumber = algorithmToTestNumber[algorithmName] ?: 1
        Log.d("OpenSSLSpeed", "Mapped algorithm '$algorithmName' to test number: $testNumber")
        return testNumber
    }
    
    data class AlgorithmItem(
        val algorithm: String,
        var isSelected: Boolean = false
    )

    class AlgorithmAdapter(
        private val context: Context,
        private val algorithms: List<AlgorithmItem>,
        private val onSelectionChanged: () -> Unit
    ) : ArrayAdapter<AlgorithmItem>(context, android.R.layout.simple_list_item_multiple_choice) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.algorithm_checkbox_item, parent, false)
            val item = algorithms[position]
            
            val switch = view.findViewById<Switch>(R.id.algorithm_switch)
            val text = view.findViewById<TextView>(R.id.algorithm_text)
            
            switch.isChecked = item.isSelected
            text.text = item.algorithm
            
            // Add click listener to toggle selection
            switch.setOnCheckedChangeListener { _, isChecked ->
                Log.d("OpenSSLSpeed", "Switch toggled for algorithm '${item.algorithm}', new state: $isChecked")
                item.isSelected = isChecked
                onSelectionChanged() // Notify parent to update button state
            }
            
            return view
        }
        
        override fun getCount(): Int = algorithms.size
        
        override fun getItem(position: Int): AlgorithmItem = algorithms[position]
    }
}

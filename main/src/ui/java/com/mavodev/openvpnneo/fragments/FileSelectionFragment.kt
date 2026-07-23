/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.ListFragment
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.activities.FileSelect
import java.io.File
import java.util.Locale
import java.util.TreeMap
import java.util.Vector

class FileSelectionFragment : ListFragment() {

    private var path: MutableList<String> = ArrayList()
    private lateinit var myPath: TextView
    private lateinit var mList: ArrayList<HashMap<String, Any>>

    private lateinit var selectButton: Button

    private var parentPath: String? = null

    // Remove external storage access - use SAF directory browsing instead
    private var currentPath = "/" // Default to root for SAF

    private val formatFilter: Array<String>? = null

    private var selectedFile: File? = null
    private val lastPositions = HashMap<String, Int>()
    private var mStartPath: String? = null
    private lateinit var mInlineImport: CompoundButton
    private lateinit var mClearButton: Button
    private var mHideImport = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.setOnItemLongClickListener { _, itemView, position, id ->
            onListItemClick(listView, itemView, position, id)
            onFileSelectionClick()
            true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.file_dialog_main, container, false)

        myPath = v.findViewById(R.id.path)

        mInlineImport = v.findViewById(R.id.doinline)

        if (mHideImport) {
            mInlineImport.visibility = View.GONE
            mInlineImport.isChecked = false
        }

        selectButton = v.findViewById(R.id.fdButtonSelect)
        selectButton.isEnabled = false
        selectButton.setOnClickListener { onFileSelectionClick() }

        mClearButton = v.findViewById(R.id.fdClear)
        mClearButton.setOnClickListener {
            (activity as? FileSelect)?.clearData()
        }
        if (activity !is FileSelect || !(activity as FileSelect).showClear()) {
            mClearButton.visibility = View.GONE
            mClearButton.isEnabled = false
        }

        return v
    }

    private fun onFileSelectionClick() {
        selectedFile?.let {
            if (mInlineImport.isChecked) {
                (requireActivity() as FileSelect).importFile(it.path)
            } else {
                (requireActivity() as FileSelect).setFile(it.path)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        @Suppress("DEPRECATION")
        super.onActivityCreated(savedInstanceState)

        val startPath = (requireActivity() as FileSelect).selectPath
        mStartPath = startPath
        getDir(startPath ?: "/")
    }

    fun refresh() {
        // Remove external storage access - no directory browsing with SAF
        getDir("/") // Default to root for SAF
    }

    private fun getDir(dirPath: String) {
        val useAutoSelection = dirPath.length < currentPath.length

        val position = lastPositions[parentPath]

        getDirImpl(dirPath)

        if (position != null && useAutoSelection) {
            listView.setSelection(position)
        }
    }

    private fun getDirImpl(dirPath: String) {
        currentPath = dirPath

        val item = ArrayList<String>()
        path = ArrayList()
        mList = ArrayList()

        var f = File(currentPath)
        var files = f.listFiles()
        if (files == null) {
            currentPath = ROOT
            f = File(currentPath)
            files = f.listFiles()

            if (files == null) files = arrayOf()
        }

        myPath.text = "${getText(R.string.location)}: $currentPath"

        if (currentPath != ROOT) {
            item.add(ROOT)
            addItem(ROOT, R.drawable.ic_baseline_folder_24)
            path.add(ROOT)

            item.add("../")
            addItem("../", R.drawable.ic_baseline_folder_24)
            val parent = f.parent ?: ROOT
            path.add(parent)
            parentPath = parent
        }

        val dirsMap = TreeMap<String, String>()
        val dirsPathMap = TreeMap<String, String>()
        val filesMap = TreeMap<String, String>()
        val filesPathMap = TreeMap<String, String>()

        // add default locations
        for (dir in getExternalStorages()) {
            // You got to love the P8 Lite to have null in this list ....
            dirsMap[dir] = dir
            dirsPathMap[dir] = dir
        }

        for (file in files) {
            if (file.isDirectory) {
                val dirName = file.name
                dirsMap[dirName] = dirName
                dirsPathMap[dirName] = file.path
            } else {
                val fileName = file.name
                val fileNameLwr = fileName.lowercase(Locale.getDefault())

                if (formatFilter != null) {
                    var contains = false
                    for (aFormatFilter in formatFilter) {
                        val formatLwr = aFormatFilter.lowercase(Locale.getDefault())
                        if (fileNameLwr.endsWith(formatLwr)) {
                            contains = true
                            break
                        }
                    }
                    if (contains) {
                        filesMap[fileName] = fileName
                        filesPathMap[fileName] = file.path
                    }
                } else {
                    filesMap[fileName] = fileName
                    filesPathMap[fileName] = file.path
                }
            }
        }
        item.addAll(dirsMap.tailMap("").values)
        item.addAll(filesMap.tailMap("").values)
        path.addAll(dirsPathMap.tailMap("").values)
        path.addAll(filesPathMap.tailMap("").values)

        val fileList = SimpleAdapter(
            activity, mList, R.layout.file_dialog_row,
            arrayOf(ITEM_KEY, ITEM_IMAGE), intArrayOf(R.id.fdrowtext, R.id.fdrowimage)
        )

        for (dir in dirsMap.tailMap("").values) {
            addItem(dir, R.drawable.ic_baseline_folder_24)
        }

        for (file in filesMap.tailMap("").values) {
            addItem(file, R.drawable.ic_baseline_file_present_24)
        }

        fileList.notifyDataSetChanged()

        listAdapter = fileList
    }

    private fun addItem(fileName: String, imageId: Int) {
        val item = HashMap<String, Any>()
        item[ITEM_KEY] = fileName
        item[ITEM_IMAGE] = imageId
        mList.add(item)
    }

    private fun getExternalStorages(): Collection<String> {
        // Remove external storage access - SAF doesn't need directory listing
        return Vector()
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val file = File(path[position])

        if (file.isDirectory) {
            selectButton.isEnabled = false

            if (file.canRead()) {
                lastPositions[currentPath] = position
                getDir(path[position])
            } else {
                Toast.makeText(
                    activity,
                    "[${file.name}] ${requireActivity().getText(R.string.cant_read_folder)}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            selectedFile = file
            v.isSelected = true
            selectButton.isEnabled = true
        }
    }

    fun setNoInLine() {
        mHideImport = true
    }

    companion object {
        private const val ITEM_KEY = "key"
        private const val ITEM_IMAGE = "image"
        private const val ROOT = "/"
    }
}

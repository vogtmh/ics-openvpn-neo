/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */
package com.mavodev.openvpnneo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.activities.FileSelect

class InlineFileTab : Fragment() {

    private var mInlineData: EditText? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        mInlineData?.setText((requireActivity() as FileSelect).inlineData)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val v = inflater.inflate(R.layout.file_dialog_inline, container, false)
        mInlineData = v.findViewById(R.id.inlineFileData)
        return v
    }

    fun setData(data: String?) {
        mInlineData?.setText(data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, MENU_SAVE, 0, R.string.menu_use_inline_data)
            .setIcon(android.R.drawable.ic_menu_save)
            .setAlphabeticShortcut('u')
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_SAVE) {
            (requireActivity() as FileSelect).saveInlineData(null, mInlineData?.text.toString())
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val MENU_SAVE = 0
    }
}

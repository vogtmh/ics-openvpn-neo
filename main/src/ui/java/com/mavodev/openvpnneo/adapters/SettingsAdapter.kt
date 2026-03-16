package com.mavodev.openvpnneo.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import com.mavodev.openvpnneo.R
import com.mavodev.openvpnneo.model.SettingItem
import com.mavodev.openvpnneo.model.SettingType

class SettingsAdapter(
    context: Context,
    private val settings: MutableList<SettingItem>,
    private val onSettingChanged: (String, Boolean) -> Unit
) : ArrayAdapter<SettingItem>(context, R.layout.settings_item, settings) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.settings_item, parent, false)
        val setting = settings[position]

        val titleView = view.findViewById<TextView>(R.id.settings_item_title)
        val subtitleView = view.findViewById<TextView>(R.id.settings_item_subtitle)
        val toggle = view.findViewById<Switch>(R.id.settings_toggle)
        val actionButton = view.findViewById<Button>(R.id.settings_action)
        val leftContainer = view.findViewById<View>(R.id.settings_item_left)
        val controlContainer = view.findViewById<View>(R.id.settings_control_container)

        titleView.text = setting.title
        subtitleView.text = setting.description
        subtitleView.visibility = if (setting.description.isEmpty()) View.GONE else View.VISIBLE

        // Reset all views to default state first
        controlContainer.visibility = View.VISIBLE
        leftContainer.isFocusable = true
        titleView.setTextAppearance(android.R.style.TextAppearance_Small)
        titleView.setTypeface(null, android.graphics.Typeface.NORMAL)
        toggle.visibility = View.GONE
        actionButton.visibility = View.GONE

        when (setting.type) {
            SettingType.CATEGORY -> {
                controlContainer.visibility = View.GONE
                leftContainer.background = null
                leftContainer.isFocusable = false
                titleView.setTextAppearance(android.R.style.TextAppearance_Medium)
                titleView.setTypeface(null, android.graphics.Typeface.BOLD)
                actionButton.visibility = View.GONE
                
                // Further reduce padding for category headers
                view.setPadding(view.paddingLeft, 2, view.paddingRight, 1)
            }
            
            SettingType.TOGGLE_SLIDER -> {
                toggle.visibility = View.VISIBLE
                actionButton.visibility = View.GONE
                toggle.isChecked = setting.value
                toggle.setOnCheckedChangeListener { _, isChecked ->
                    setting.value = isChecked
                    onSettingChanged(setting.key, isChecked)
                }
                
                titleView.setTextAppearance(android.R.style.TextAppearance_Small)
                titleView.setTypeface(null, android.graphics.Typeface.BOLD)
                
                // Reset padding for regular items
                view.setPadding(view.paddingLeft, 6, view.paddingRight, 6)
                
                leftContainer.setOnClickListener {
                    toggle.isChecked = !toggle.isChecked
                }
            }
            
            SettingType.ACTION -> {
                toggle.visibility = View.GONE
                actionButton.visibility = View.VISIBLE
                
                titleView.setTextAppearance(android.R.style.TextAppearance_Small)
                titleView.setTypeface(null, android.graphics.Typeface.BOLD)
                
                // Special handling for "clearapi" - disable if no apps allowed
                if (setting.key == "clearapi") {
                    val allowedApps = context.getSharedPreferences("com.mavodev.openvpnneo_preferences", Context.MODE_PRIVATE)
                        .getStringSet("allowed_apps", emptySet()) ?: emptySet()
                    
                    if (allowedApps.isEmpty()) {
                        // Disable the item visually
                        titleView.setTextColor(context.getResources().getColor(android.R.color.darker_gray))
                        actionButton.alpha = 0.5f // Make button semi-transparent
                        leftContainer.isClickable = false
                        leftContainer.isFocusable = false
                        leftContainer.background = null
                        actionButton.isClickable = false
                        actionButton.isFocusable = false
                        actionButton.isEnabled = false
                    } else {
                        // Enable the item
                        titleView.setTextColor(context.getResources().getColor(android.R.color.primary_text_light))
                        actionButton.alpha = 1.0f
                        leftContainer.isClickable = false
                        leftContainer.isFocusable = false
                        leftContainer.background = null
                        actionButton.isClickable = true
                        actionButton.isFocusable = true
                        actionButton.isEnabled = true
                    }
                } else {
                    // For other action items, disable title click, enable button click
                    leftContainer.isClickable = false
                    leftContainer.isFocusable = false
                    leftContainer.background = null
                    actionButton.isClickable = true
                    actionButton.isFocusable = true
                    actionButton.isEnabled = true
                }
                
                // Reset padding for regular items
                view.setPadding(view.paddingLeft, 6, view.paddingRight, 6)
                
                // Move action to button click
                actionButton.setOnClickListener {
                    setting.action?.invoke()
                }
                
                leftContainer.setOnClickListener {
                    // Do nothing - action moved to button
                }
            }
        }

        return view
    }

    fun updateSettingValue(key: String, value: Boolean) {
        val settingIndex = settings.indexOfFirst { it.key == key }
        if (settingIndex != -1) {
            settings[settingIndex] = settings[settingIndex].copy(value = value)
            notifyDataSetChanged()
        }
    }
}

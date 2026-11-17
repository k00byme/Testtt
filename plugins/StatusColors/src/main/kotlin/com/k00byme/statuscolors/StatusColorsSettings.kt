package com.k00byme.statuscolors

import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.api.SettingsAPI
import com.aliucord.fragments.SettingsPage

class StatusColorsSettings(private val settings: SettingsAPI) : SettingsPage() {
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        setActionBarTitle("Status Colors")
        setActionBarSubtitle(null)

        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(16)
            setPadding(padding, dp(8), padding, padding)
        }
        addView(root)

        root.addView(TextView(context).apply {
            text = "Choose a separate hex color for each Discord presence. Valid values are saved automatically."
            textSize = 14f
            setPadding(0, dp(8), 0, dp(16))
        })

        addColorField(root, "Online", "onlineColor", "#3BA55C")
        addColorField(root, "Idle", "idleColor", "#FAA61A")
        addColorField(root, "Do Not Disturb", "dndColor", "#ED4245")
        addColorField(root, "Offline / Invisible", "offlineColor", "#747F8D")

        root.addView(TextView(context).apply {
            text = "These changes affect only how status indicators look in this Aliucord installation."
            textSize = 12f
            setPadding(0, dp(18), 0, dp(8))
        })
    }

    private fun addColorField(parent: LinearLayout, label: String, key: String, default: String) {
        val context = parent.context
        parent.addView(TextView(context).apply {
            text = label
            textSize = 15f
            setPadding(0, dp(12), 0, dp(4))
        })

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        parent.addView(
            row,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val input = EditText(context).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            hint = default
            setText(settings.getString(key, default))
        }
        row.addView(input, LinearLayout.LayoutParams(0, dp(48), 1f))

        val preview = View(context).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor(default))
            }
        }
        val margin = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
            marginStart = dp(12)
        }
        row.addView(preview, margin)

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val value = s?.toString()?.trim().orEmpty()
                val normalized = if (value.startsWith("#")) value else "#$value"
                val valid = normalized.matches(Regex("^#[0-9A-Fa-f]{6}$"))
                input.error = if (valid) null else "Use #RRGGBB"
                if (valid) {
                    settings.setString(key, normalized.uppercase())
                    preview.background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(Color.parseColor(normalized))
                    }
                }
            }
        })
    }

    private fun dp(value: Int): Int = (value * requireContext().resources.displayMetrics.density).toInt()
}

package com.admknight.nginx

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.plugins.Plugin
import com.admknight.nginxprovider.BuildConfig
import androidx.core.content.edit

class NginxSettingsFragment(private val plugin: Plugin, private val sharedPref: SharedPreferences) :
    BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val res = plugin.resources ?: return null
        val id = res.getIdentifier("nginx_settings", "layout", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) {
            // Fallback if layout missing
            val context = context ?: return null
            val layout = android.widget.LinearLayout(context).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }
            return layout
        }
        return inflater.inflate(res.getLayout(id), container, false)
    }

    private fun <T : View> View.findView(name: String): T? {
        val res = plugin.resources ?: return null
        val id = res.getIdentifier(name, "id", BuildConfig.LIBRARY_PACKAGE_NAME)
        if (id == 0) return null
        return this.findViewById(id)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val urlInput = view.findView<EditText>("url_input") // Assuming these IDs exist or using generic ones
        val userInput = view.findView<EditText>("user_input")
        val passInput = view.findView<EditText>("pass_input")
        val saveButton = view.findView<Button>("save_button")

        urlInput?.setText(sharedPref.getString("nginx_url", ""))
        userInput?.setText(sharedPref.getString("nginx_user", ""))
        passInput?.setText(sharedPref.getString("nginx_pass", ""))

        saveButton?.setOnClickListener {
            val url = urlInput?.text?.toString()?.trim() ?: ""
            val user = userInput?.text?.toString()?.trim() ?: ""
            val pass = passInput?.text?.toString()?.trim() ?: ""

            sharedPref.edit {
                putString("nginx_url", url)
                putString("nginx_user", user)
                putString("nginx_pass", pass)
            }

            showToast("Settings saved. Please restart the app.")
            dismiss()
        }
    }
}

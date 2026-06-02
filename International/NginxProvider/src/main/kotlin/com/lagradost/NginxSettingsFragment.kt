package com.lagradost

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.plugins.Plugin

class NginxSettingsFragment(private val plugin: Plugin) : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val id = plugin.resources!!.getIdentifier("nginx_settings", "layout", "com.lagradost.nginx")
        return if (id != 0) {
            val layout = plugin.resources!!.getLayout(id)
            inflater.inflate(layout, container, false)
        } else null
    }
}

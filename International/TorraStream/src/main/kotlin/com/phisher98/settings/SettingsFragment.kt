package com.admknight.torrastream.settings

import android.content.SharedPreferences
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.admknight.torrastream.BuildConfig
import com.admknight.torrastream.TorraStreamProvider

class SettingsFragment(
    plugin: TorraStreamProvider,
    private val sharedPref: SharedPreferences
) : BottomSheetDialogFragment() {
}

/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */
package org.omnirom.omnistyle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

/** Browses installed header packs. Tapping a header applies it only in [pickerMode]. */
open class BrowseHeaderActivity : ComponentActivity() {

    protected open val pickerMode: Boolean = false

    @get:StringRes
    protected open val titleRes: Int = R.string.browse_header

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initialPackKey = intent?.getStringExtra(EXTRA_HEADER_PACK)
        setContent {
            OmniStyleTheme {
                HeaderPickerScreen(
                    title = stringResource(titleRes),
                    pickerMode = pickerMode,
                    initialPackKey = initialPackKey,
                    onBack = ::finish,
                )
            }
        }
    }

    companion object {
        /** "package" or "package/activity", the same form Settings stores for a daylight pack. */
        const val EXTRA_HEADER_PACK = "header_pack"
    }
}

/** Entry point used by Settings. */
class PickHeaderActivity : BrowseHeaderActivity() {
    override val pickerMode = true
    override val titleRes = R.string.pick_header
}

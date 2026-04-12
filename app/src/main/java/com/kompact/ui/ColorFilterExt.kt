package com.kompact.ui

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.kompact.model.ColorFilterConfig
import com.kompact.util.toAndroidColorMatrix

internal fun ColorFilterConfig.toComposeColorFilter(): ColorFilter? {
    val androidMatrix = this.toAndroidColorMatrix() ?: return null
    return ColorFilter.colorMatrix(ColorMatrix(androidMatrix.array))
}

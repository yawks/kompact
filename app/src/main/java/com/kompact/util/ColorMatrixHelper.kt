package com.kompact.util

import android.graphics.ColorMatrix
import com.kompact.model.ColorFilterConfig
import com.kompact.model.ColorFilterType

internal fun ColorFilterConfig.toAndroidColorMatrix(): android.graphics.ColorMatrix? {
    if (this.filterType == ColorFilterType.NONE) return null

    val androidMatrix = android.graphics.ColorMatrix()
    when (this.filterType) {
        ColorFilterType.GRAYSCALE -> androidMatrix.setSaturation(0f)
        ColorFilterType.SEPIA -> {
            androidMatrix.setSaturation(0f)
            val sepiaMatrix = android.graphics.ColorMatrix()
            sepiaMatrix.setScale(1.2f, 1.0f, 0.8f, 1.0f)
            androidMatrix.postConcat(sepiaMatrix)
        }
        ColorFilterType.VINTAGE -> {
            androidMatrix.setSaturation(0.6f)
            val vintageMatrix = android.graphics.ColorMatrix(floatArrayOf(
                0.9f, 0.5f, 0.1f, 0f, 0f,
                0.3f, 0.8f, 0.1f, 0f, 0f,
                0.2f, 0.3f, 0.5f, 0f, 0f,
                0f,   0f,   0f, 1f, 0f
            ))
            androidMatrix.postConcat(vintageMatrix)
        }
        ColorFilterType.COOL -> {
            val coolMatrix = android.graphics.ColorMatrix()
            coolMatrix.setScale(0.9f, 1.0f, 1.2f, 1.0f)
            androidMatrix.postConcat(coolMatrix)
        }
        ColorFilterType.WARM -> {
            val warmMatrix = android.graphics.ColorMatrix()
            warmMatrix.setScale(1.2f, 1.0f, 0.8f, 1.0f)
            androidMatrix.postConcat(warmMatrix)
        }
        ColorFilterType.CUSTOM -> {
            val hsvColor = android.graphics.Color.HSVToColor(floatArrayOf(this.hue, 1f, 1f))
            val r = android.graphics.Color.red(hsvColor) / 255f
            val g = android.graphics.Color.green(hsvColor) / 255f
            val b = android.graphics.Color.blue(hsvColor) / 255f
            
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f
            
            val mat = floatArrayOf(
                lumR * r * 2f, lumG * r * 2f, lumB * r * 2f, 0f, 0f,
                lumR * g * 2f, lumG * g * 2f, lumB * g * 2f, 0f, 0f,
                lumR * b * 2f, lumG * b * 2f, lumB * b * 2f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
            val hueMatrix = android.graphics.ColorMatrix(mat)
            androidMatrix.postConcat(hueMatrix)
        }
        else -> return null
    }
    
    // Apply intensity
    if (this.intensity < 1.0f || this.intensity > 1.0f) {
        val finalArr = androidMatrix.array
        val identityMatrix = android.graphics.ColorMatrix()
        val identArr = identityMatrix.array
        
        for (i in 0 until 20) {
            finalArr[i] = identArr[i] + (finalArr[i] - identArr[i]) * this.intensity
        }
        androidMatrix.set(finalArr)
    }

    return androidMatrix
}

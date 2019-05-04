package com.pinnacleimagingsystems.ambientviewer2

import com.pinnacleimagingsystems.ambientviewer2.camera.ColorInfo

data class AlgorithmParameters(
        val parameter: Float,
        val useColorInfo: Boolean,
        val colorInfo: ColorInfo
)
package com.aesthetic.framed.data.model

enum class CameraAspectRatio(
    val displayName: String,
    val aspectX: Int,
    val aspectY: Int
) {
    RATIO_4_3("3:4", 3, 4),
    RATIO_4_5("4:5", 4, 5),
    RATIO_1_1("1:1", 1, 1),
    RATIO_9_16("9:16", 9, 16);

    val ratioFloat: Float
        get() = aspectX.toFloat() / aspectY.toFloat()
}

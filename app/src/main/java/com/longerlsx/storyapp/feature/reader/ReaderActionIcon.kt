package com.longerlsx.storyapp.feature.reader

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/** Small native vector glyphs. The parent button owns the accessible action name. */
@Composable
internal fun ReaderActionIcon(label: String, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            val stroke = Stroke(1.65f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.65f, StrokeCap.Round)
            when {
                label == "目录" -> for (y in listOf(6f, 12f, 18f)) {
                    drawCircle(tint, 0.9f, Offset(4f, y)); line(8f, y, 21f, y)
                }
                label == "设置" -> {
                    line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                    line(8f, 3f, 8f, 9f); line(16f, 9f, 16f, 15f); line(10f, 15f, 10f, 21f)
                }
                label == "夜间" -> drawPath(Path().apply {
                    moveTo(20f, 15f); cubicTo(13f, 19f, 5f, 11f, 9f, 4f)
                    cubicTo(-1f, 8f, 5f, 24f, 16f, 20f); cubicTo(18f, 19f, 20f, 17f, 20f, 15f)
                    close()
                }, tint, style = stroke)
                label == "日间" -> {
                    drawCircle(tint, 4f, Offset(12f, 12f), style = stroke)
                    for (i in 0..7) {
                        val a = i * Math.PI.toFloat() / 4
                        line(12 + 7 * cos(a), 12 + 7 * sin(a), 12 + 10 * cos(a), 12 + 10 * sin(a))
                    }
                }
                label.startsWith("暂停") -> {
                    drawRoundRect(tint, Offset(6f, 4f), Size(3f, 16f))
                    drawRoundRect(tint, Offset(15f, 4f), Size(3f, 16f))
                }
                label.startsWith("停止") -> drawRect(tint, Offset(5f, 5f), Size(14f, 14f), style = stroke)
                label == "向上" -> { line(6f, 15f, 12f, 9f); line(12f, 9f, 18f, 15f) }
                label == "向下" || label == "收起听书面板" -> { line(6f, 9f, 12f, 15f); line(12f, 15f, 18f, 9f) }
                label.startsWith("开始") || label.startsWith("播放") ||
                    label.startsWith("继续") || label.startsWith("重试") -> drawPath(Path().apply {
                    moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close()
                }, tint, style = stroke)
                else -> {
                    drawPath(Path().apply {
                        moveTo(4f, 17f); lineTo(4f, 11f)
                        arcTo(Rect(4f, 3f, 20f, 19f), 180f, 180f, false); lineTo(20f, 17f)
                    }, tint, style = stroke)
                    drawRoundRect(tint, Offset(3f, 12f), Size(4f, 8f), style = stroke)
                    drawRoundRect(tint, Offset(17f, 12f), Size(4f, 8f), style = stroke)
                }
            }
        }
    }
}

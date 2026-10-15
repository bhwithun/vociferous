package com.brian.dialbuddy.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.dialbuddy.ui.theme.DigitStroke
import com.brian.dialbuddy.ui.theme.TitleBand
import com.brian.dialbuddy.ui.theme.TitleInk
import kotlin.math.sqrt

@Composable
fun RaggedCard(
    seed: Int = 0,
    title: String? = null,
    band: Color = TitleBand,
    ink: Color = TitleInk,
    stroke: Color = DigitStroke,
    body: Color = Color.Transparent,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var headerBottom by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.drawBehind {
            val path = raggedWirePath(seed)
            if (body.alpha > 0f) {
                drawPath(path, body)
            }
            if (headerBottom > 0f) {
                val cap = Path().apply {
                    addRect(Rect(0f, 0f, size.width, headerBottom))
                }
                val filled = Path()
                filled.op(path, cap, PathOperation.Intersect)
                drawPath(filled, band)
            }
            drawPath(
                path = path,
                color = stroke,
                style = Stroke(width = 1.25.dp.toPx()),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { headerBottom = it.height.toFloat() },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = if (title == null) 8.dp else 10.dp)
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            color = ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .padding(top = 16.dp, bottom = 36.dp),
                content = content,
            )
        }
        overlay()
    }
}

private fun DrawScope.raggedWirePath(seed: Int): Path {
    val amp = 6.dp.toPx()
    val margin = 2.dp.toPx()
    val left = margin + amp
    val top = margin + amp
    val right = size.width - margin - amp
    val bottom = size.height - margin - amp
    val path = Path()
    val points = ArrayList<Offset>(96)
    points += edgePoints(Offset(left, top), Offset(right, top), Offset(0f, -1f), seed, 0, amp)
    points += edgePoints(Offset(right, top), Offset(right, bottom), Offset(1f, 0f), seed, 1, amp)
    points += edgePoints(Offset(right, bottom), Offset(left, bottom), Offset(0f, 1f), seed, 2, amp)
    points += edgePoints(Offset(left, bottom), Offset(left, top), Offset(-1f, 0f), seed, 3, amp)
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    for (index in 1 until points.size) {
        path.lineTo(points[index].x, points[index].y)
    }
    path.close()
    return path
}

private fun edgePoints(from: Offset, to: Offset, normal: Offset, seed: Int, edge: Int, amp: Float): List<Offset> {
    val delta = to - from
    val length = sqrt(delta.x * delta.x + delta.y * delta.y)
    if (length < 1f) return listOf(from)
    val dir = Offset(delta.x / length, delta.y / length)
    val points = ArrayList<Offset>()
    points.add(from)
    var traveled = 0f
    var step = 0
    while (traveled < length - 14f) {
        val span = 18f + hash01(seed, edge, step) * 30f
        traveled += span
        if (traveled > length - 14f) break
        val bite = if (hash01(seed, edge, step + 11) > 0.8f) 1.15f else 0.55f
        val signed = (hash01(seed, edge, step + 5) * 2f - 1f) * amp * bite
        val along = Offset(from.x + dir.x * traveled, from.y + dir.y * traveled)
        points.add(Offset(along.x + normal.x * signed, along.y + normal.y * signed))
        step++
    }
    return points
}

private fun hash01(seed: Int, edge: Int, index: Int): Float {
    var mixed = seed * 196613 + edge * 374761393 + index * 668265263
    mixed = mixed xor (mixed ushr 13)
    mixed *= 1274126177
    mixed = mixed xor (mixed ushr 16)
    return (mixed and 0xFFFF) / 65535f
}

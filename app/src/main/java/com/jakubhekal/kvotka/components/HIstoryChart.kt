package com.jakubhekal.kvotka.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.math.MathUtils
import kotlin.math.max

@Composable
fun HistoryChart(
    modifier: Modifier = Modifier,
    values: List<Long>,
    labels: List<String>,
    limits: List<Long>? = null,
    barWidth: Dp = 16.dp,
    onSelect: (index: Int) -> Unit = { _ -> }) {

    if (values.isEmpty()) return

    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    var selectedIndex by remember { mutableIntStateOf(values.lastIndex) }
    val textMeasurer = rememberTextMeasurer()
    val textOffset = textMeasurer.measure("A", typography.bodyMedium).size.height + 24
    val safeLimits = limits ?: List(values.size) { 0L }
    val maxValue = max(values.maxOrNull() ?: 0L, limits?.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Canvas(modifier = Modifier
        .pointerInput(values.size) {
            detectTapGestures(
                onTap = { offset ->
                    values.forEachIndexed { index, _ ->
                        if (offset.x > index * (size.width / values.count()) && offset.x < (index + 1) * (size.width / values.count())) {
                            onSelect(index)
                            selectedIndex = index
                        }
                    }
                },
            )
        }.then(modifier)) {

        val normalizedHeight = size.height - textOffset

        if (limits != null) {
            val path = Path()
            limits.forEachIndexed { index, limit ->
                val x = index * (size.width / values.count()) + (size.width / values.count()) / 2
                val y = normalizedHeight - (normalizedHeight * MathUtils.clamp(limit / maxValue.toFloat(), 0f,1f))
                val offset = barWidth.toPx() * 1.2f
                if (index == 0) {
                    path.moveTo(x - offset, y)
                } else {
                    path.lineTo(x - offset, y)
                }
                path.lineTo(x, y)
                path.lineTo(x + offset, y)
            }

            drawPath(
                path = path,
                color = colorScheme.tertiaryContainer,
                style = Stroke(
                    width = 16f,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 12.dp.toPx()), 0f)
                ),
            )
        }

        values.forEachIndexed { index, value ->
            val barSize = Size(barWidth.toPx(), normalizedHeight * MathUtils.clamp(value / maxValue.toFloat(), 0f,1f))
            val barGap = size.width / values.count() - barSize.width
            val barOffset = Offset(index * (size.width / values.count()) + (barGap / 2), normalizedHeight - barSize.height)

            drawRoundRect(
                topLeft = barOffset,
                color = if(selectedIndex == index) colorScheme.primary else colorScheme.secondaryContainer,
                size = barSize,
                cornerRadius = CornerRadius(barSize.width / 2f)
            )
        }

        labels.forEachIndexed { index, label ->
            val textStyle = typography.bodyMedium.copy(
                color = if(selectedIndex == index) colorScheme.onBackground else colorScheme.outline
            )
            val textLayoutResult = textMeasurer.measure(label, textStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                style = textStyle,
                topLeft = Offset(index * (size.width / values.count()) + (size.width / values.count() - textLayoutResult.size.width) / 2 , size.height - textLayoutResult.size.height),
            )
        }

    }
}

package com.freeplay.toolbox.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private const val THRESHOLD = 300f      // 触发刷新的下拉像素
private const val MAX_OVERSCROLL = 480f // 最大下拉视觉距离

/**
 * 主流风格下拉刷新：下拉出现一个渐显的圆球，箭头随下拉角度翻转，
 * 松手后球体变成旋转加载圈，刷新结束时回弹收起。
 *
 * 通过 nestedScroll 累积顶部过度滚动，不拦截触摸事件，
 * 因此不会影响列表本身的滑动。
 */
@Composable
fun PullToRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var raw by remember { mutableFloatStateOf(0f) }
    val progress = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    // 下拉距离 → 平滑进度（0..1）
    LaunchedEffect(raw, refreshing) {
        val target = if (refreshing) 1f else (raw / THRESHOLD).coerceIn(0f, 1f)
        progress.animateTo(
            target,
            animationSpec = if (refreshing) tween(180) else spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    // 达到阈值即触发刷新（一次）
    LaunchedEffect(raw, refreshing) {
        if (!refreshing && raw >= THRESHOLD && !armed) {
            armed = true
            onRefresh()
        }
    }

    val connection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val atTop = listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0 && atTop) {
                    raw = (raw + available.y).coerceAtMost(MAX_OVERSCROLL)
                } else if (available.y < 0) {
                    raw = (raw + available.y).coerceAtLeast(0f)
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier
            .nestedScroll(connection)
            // 只观察抬手事件，不消费触摸，不影响列表滑动
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.changes.any { it.changedToUp() }) {
                            raw = 0f
                            armed = false
                        }
                    }
                }
            }
    ) {
        content()

        val visible = refreshing || progress.value > 0.01f
        if (visible) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = (progress.value * 46f + 4f).dp.toPx()
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                BallIndicator(
                    progress = progress.value,
                    refreshing = refreshing,
                )
            }
        }
    }
}

/** 球体指示器：下拉时放大并翻转箭头，刷新时变旋转弧 */
@Composable
private fun BallIndicator(progress: Float, refreshing: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val ballColor = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
        ),
        label = "angle",
    )

    Canvas(Modifier.size(46.dp)) {
        val p = if (refreshing) 1f else progress
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseR = size.minDimension / 2f

        // 阴影/光晕
        drawCircle(
            color = Color.Black.copy(alpha = 0.12f * p),
            radius = baseR * (0.52f + 0.12f * p),
            center = center + Offset(0f, 1.dp.toPx()),
        )
        // 球体
        drawCircle(
            color = surface.copy(alpha = 0.35f + 0.65f * p),
            radius = baseR * (0.50f + 0.16f * p),
            center = center,
        )
        // 球体描边
        drawCircle(
            color = accent.copy(alpha = 0.25f + 0.55f * p),
            radius = baseR * (0.50f + 0.16f * p),
            center = center,
            style = Stroke(width = 1.6.dp.toPx()),
        )

        val r = baseR * 0.40f
        if (refreshing) {
            // 旋转加载弧
            rotate(angle, pivot = center) {
                drawArc(
                    color = accent,
                    startAngle = 0f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        } else {
            // 箭头：随下拉从 0° 转到 180°
            rotate(180f * p, pivot = center) {
                val w = 2.6.dp.toPx()
                val len = baseR * 0.34f
                drawLine(
                    color = accent.copy(alpha = 0.55f + 0.45f * p),
                    start = Offset(center.x - len, center.y),
                    end = Offset(center.x + len, center.y),
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                )
                // 箭头尖端
                drawLine(
                    color = accent.copy(alpha = 0.55f + 0.45f * p),
                    start = Offset(center.x + len, center.y),
                    end = Offset(center.x + len * 0.25f, center.y - len * 0.75f),
                    strokeWidth = w,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

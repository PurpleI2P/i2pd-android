package org.purplei2p.i2pd

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Onboarding hint pointing at one control: the screen is dimmed except a window around [target]
 * (bounds in window coordinates), with a callout below it (above if there is no room) and an arrow
 * to the control. Any tap, Back or [action] dismisses it.
 */
@Composable
fun CoachMark(target: Rect, text: String, action: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        // Full screen and edge-to-edge like the activity, so window coordinates of the target match
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The scrim is drawn here, with the hole; the default dim of the dialog window would cover it
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)

        val density = LocalDensity.current
        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 8.dp.toPx() }
        val arrow = with(density) { 10.dp.toPx() }
        val hole = target.inflate(gap)
        val scrim = Color.Black.copy(alpha = 0.7f)
        val calloutColor = MaterialTheme.colorScheme.inverseSurface

        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }) {
            Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                drawRect(scrim)
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = hole.topLeft,
                    size = hole.size,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    blendMode = BlendMode.Clear,
                )
                // Arrow from the callout to the control, in the callout color
                val below = calloutBelow(hole, size.height)
                val tipY = if (below) hole.bottom else hole.top
                val baseY = if (below) hole.bottom + arrow + 1f else hole.top - arrow - 1f
                drawPath(
                    Path().apply {
                        moveTo(target.center.x, tipY)
                        lineTo(target.center.x - arrow, baseY)
                        lineTo(target.center.x + arrow, baseY)
                        close()
                    },
                    calloutColor,
                )
            }
            Layout(
                content = {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = calloutColor),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp)) {
                            Text(text, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(end = 8.dp))
                            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                                Text(action, color = MaterialTheme.colorScheme.inversePrimary)
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val maxWidth = minOf(constraints.maxWidth - 2 * margin.toInt(), 360.dp.roundToPx())
                val card = measurables.single().measure(Constraints(maxWidth = maxWidth))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val x = (target.center.x - card.width / 2f).coerceIn(margin, constraints.maxWidth - margin - card.width)
                    val y = if (calloutBelow(hole, constraints.maxHeight.toFloat())) hole.bottom + arrow else hole.top - arrow - card.height
                    card.place(x.toInt(), y.toInt())
                }
            }
        }
    }
}

/** The callout goes below a control in the upper half of the screen, above one in the lower half. */
private fun calloutBelow(hole: Rect, height: Float) = hole.center.y < height / 2

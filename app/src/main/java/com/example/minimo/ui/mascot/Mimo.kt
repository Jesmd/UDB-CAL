package com.example.minimo.ui.mascot

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.minimo.ui.glass.rememberAnimationsEnabled

/** How MIMO looks: eyes tell the mood. */
enum class MimoMood {
    /** Round eyes. */
    Normal,

    /** Closed, smiling eyes: something went well. */
    Happy,

    /** Flat dashes: working on it. */
    Focused,
}

private val Navy = Color(0xFF0B1B3A)
private val Blue = Color(0xFF007AFF)
private val BlueShade = Color(0xFF0062D9)
private val HeadShade = Color(0xFFE1E6F1)
private val Mint = Color(0xFF8FE3D0)

private const val DESIGN = 200f

/**
 * MIMO, the app's mascot: a round blue body with a white head and a dark visor, drawn flat with a navy outline.
 *
 * @param animated bobs gently and blinks; ignored when the system has animations turned off.
 */
@Composable
fun Mimo(
    modifier: Modifier = Modifier,
    mood: MimoMood = MimoMood.Normal,
    size: Dp = 140.dp,
    animated: Boolean = false,
) {
    val motion = rememberMotion(animated && rememberAnimationsEnabled())
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val unit = this.size.minDimension / DESIGN
        scale(unit, unit, pivot = Offset.Zero) {
            translate(top = motion.bob.value) { drawMimo(mood, motion.blink.value) }
        }
    }
}

private class Motion(val bob: State<Float>, val blink: State<Float>)

private val StillBob = mutableFloatStateOf(0f)
private val StillBlink = mutableFloatStateOf(1f)

@Composable
private fun rememberMotion(enabled: Boolean): Motion {
    if (!enabled) return Motion(StillBob, StillBlink)
    val transition = rememberInfiniteTransition(label = "mimo")
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "mimoBob",
    )
    // Eyes stay open, then close for a moment.
    val blink = transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3900
                0.08f at 4010
                1f at 4200
            },
        ),
        label = "mimoBlink",
    )
    return Motion(bob, blink)
}

internal fun DrawScope.drawMimo(mood: MimoMood, eyeOpen: Float) {
    val outline = 4.5f
    val body = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(36f, 74f), Size(128f, 118f))) }

    // Ground shadow.
    drawOval(Color.Black.copy(alpha = 0.10f), Offset(52f, 188f), Size(96f, 10f))

    // Body, with a flat shade on the lower right.
    drawPath(body, Blue)
    clipPath(body) {
        drawOval(BlueShade, Offset(62f, 100f), Size(130f, 112f))
        // The head throws a flat shadow on the body.
        drawRoundRect(Navy.copy(alpha = 0.28f), Offset(40f, 80f), Size(120f, 22f), CornerRadius(11f))
    }
    drawPath(body, Navy, style = Stroke(outline))

    // "MIMO" badge: three little marks stand in for text so it stays crisp at every size.
    drawMimoLabel()

    // Antenna.
    drawLine(Navy, Offset(100f, 30f), Offset(100f, 14f), strokeWidth = outline, cap = StrokeCap.Round)
    drawCircle(Mint, 6f, Offset(100f, 11f))
    drawCircle(Navy, 6f, Offset(100f, 11f), style = Stroke(outline * 0.8f))

    // Head: white dome with a flat shade on the right.
    val head = Path().apply {
        moveTo(42f, 74f)
        cubicTo(42f, 44f, 66f, 30f, 100f, 30f)
        cubicTo(134f, 30f, 158f, 44f, 158f, 74f)
        lineTo(158f, 80f)
        quadraticTo(158f, 90f, 148f, 90f)
        lineTo(52f, 90f)
        quadraticTo(42f, 90f, 42f, 80f)
        close()
    }
    drawPath(head, Color.White)
    clipPath(head) {
        drawOval(HeadShade, Offset(96f, 24f), Size(90f, 100f))
    }
    drawPath(head, Navy, style = Stroke(outline))

    // Visor and eyes.
    drawRoundRect(Navy, Offset(58f, 48f), Size(84f, 30f), CornerRadius(15f))
    drawEyes(mood, eyeOpen)
}

private fun DrawScope.drawEyes(mood: MimoMood, open: Float) {
    val y = 63f
    val left = 80f
    val right = 120f
    when (mood) {
        MimoMood.Normal -> for (x in listOf(left, right)) {
            scale(1f, open.coerceAtLeast(0.08f), pivot = Offset(x, y)) {
                drawCircle(Mint, 6.5f, Offset(x, y), style = Stroke(3.2f))
            }
        }
        MimoMood.Happy -> for (x in listOf(left, right)) {
            val arc = Path().apply {
                moveTo(x - 7f, y + 3f)
                quadraticTo(x, y - 8f, x + 7f, y + 3f)
            }
            drawPath(arc, Mint, style = Stroke(3.4f, cap = StrokeCap.Round))
        }
        MimoMood.Focused -> for (x in listOf(left, right)) {
            drawLine(Mint, Offset(x - 7f, y), Offset(x + 7f, y), strokeWidth = 3.6f, cap = StrokeCap.Round)
        }
    }
}

private fun DrawScope.drawMimoLabel() {
    // Letters M-I-M-O built from strokes, white on the blue body.
    val w = 3.8f
    val top = 114f
    val bottom = 134f
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(Color.White, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)
    // M
    line(66f, bottom, 66f, top); line(66f, top, 73f, bottom - 8f); line(73f, bottom - 8f, 80f, top); line(80f, top, 80f, bottom)
    // I
    line(89f, top, 89f, bottom)
    // M
    line(98f, bottom, 98f, top); line(98f, top, 105f, bottom - 8f); line(105f, bottom - 8f, 112f, top); line(112f, top, 112f, bottom)
    // O
    drawRoundRect(Color.White, Offset(122f, top), Size(16f, bottom - top), CornerRadius(8f), style = Stroke(w))
}

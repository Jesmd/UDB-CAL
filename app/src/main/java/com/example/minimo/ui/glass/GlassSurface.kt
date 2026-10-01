package com.example.minimo.ui.glass

import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.minimo.ui.theme.glass
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------------------------
// Backdrop: what glass bends and blurs.
//
// A screen records its content into a [GraphicsLayer] (the "source"). A glass element that floats above that content
// (a top bar, the tab bar) draws a blurred copy of the part of the source that sits behind it. That is what makes it
// look like glass over scrolling content. Only Android 12+ can blur; older phones get a more opaque frosted fill.
//
// Rule: a glass surface may only use a backdrop whose source does NOT contain that surface, otherwise it would try
// to draw itself. Cards that live inside the content therefore never use a backdrop.
// ---------------------------------------------------------------------------------------------------------------

@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** The backdrop glass elements use by default. `null` where the glass would be inside its own source. */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** Marks this content as the thing glass elements blur. */
fun Modifier.glassSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.coordinates = it }
    .drawWithContent {
        backdrop.layer.record { this@drawWithContent.drawContent() }
        drawLayer(backdrop.layer)
    }

internal val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private class GlassHolder {
    var coordinates: LayoutCoordinates? = null
}

private val saturationBoost = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) })

/**
 * Turns this element into a piece of liquid glass: blurred backdrop (when there is one), a translucent fill, a
 * specular rim that is brighter where the light hits, and a soft shadow.
 *
 * @param backdrop what to blur; `null` for a plain frosted fill.
 * @param tint paints the glass with a color (prominent buttons, selected lens).
 * @param pressed 0..1 read while drawing, brightens the glass while it is pressed.
 * @param visibility 0..1 fades the whole effect (a top bar that appears as content scrolls under it).
 * @param rim `false` for edge-to-edge strips, which draw their own hairline.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    tint: Color? = null,
    shadowElevation: Dp = 12.dp,
    blurRadius: Dp = 22.dp,
    pressed: () -> Float = ZeroProvider,
    visibility: () -> Float = OneProvider,
    rim: Boolean = true,
    solid: Boolean = false,
): Modifier {
    val colors = MaterialTheme.glass
    val density = LocalDensity.current
    val holder = remember { GlassHolder() }
    val canBlur = backdrop != null && blurSupported && !solid
    val blurLayer = if (canBlur) rememberGraphicsLayer() else null
    val blurEffect = remember(blurRadius, density) {
        if (blurSupported) {
            val px = with(density) { blurRadius.toPx() }
            BlurEffect(px, px, TileMode.Clamp)
        } else {
            null
        }
    }

    return this
        .onGloballyPositioned { holder.coordinates = it }
        .drawWithContent {
            val visible = visibility().coerceIn(0f, 1f)
            if (visible > 0.001f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                val press = pressed().coerceIn(0f, 1f)
                // Cards sit on the soft ambient background and can stay translucent; only a glass that should blur but
                // cannot (old phones) or a dialog needs the more opaque fill.
                if (shadowElevation > 0.dp && visible > 0.5f) {
                    drawSoftShadow(
                        path,
                        shadowElevation,
                        colors.shadow.copy(alpha = if (colors.isDark) 0.55f else 0.15f),
                    )
                }
                val strongFill = solid || (backdrop != null && !blurSupported)

                clipPath(path) {
                    if (canBlur) drawBlurredBackdrop(backdrop, holder, blurLayer, blurEffect, visible)

                    // Translucent body.
                    val top = if (strongFill) colors.fillSolidTop else colors.fillTop
                    val bottom = if (strongFill) colors.fillSolidBottom else colors.fillBottom
                    drawRect(Brush.verticalGradient(listOf(top, bottom)), alpha = visible)

                    if (tint != null) {
                        // A tinted glass is more saturated at the bottom and catches light on the top.
                        val tintAlpha = if (strongFill) 0.96f else 0.88f
                        drawRect(
                            Brush.verticalGradient(
                                listOf(tint.copy(alpha = tintAlpha * 0.88f), tint.copy(alpha = tintAlpha)),
                            ),
                            alpha = visible,
                        )
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = 0.34f),
                                0.55f to Color.White.copy(alpha = 0f),
                            ),
                            alpha = visible,
                        )
                    }

                    if (press > 0f) {
                        drawRect(Color.White.copy(alpha = (if (colors.isDark) 0.16f else 0.30f) * press), alpha = visible)
                    }

                    if (rim) {
                        // Light that slips just inside the edge.
                        drawOutline(
                            outline,
                            Brush.verticalGradient(
                                0f to Color.White.copy(alpha = if (colors.isDark) 0.26f else 0.70f),
                                0.45f to Color.Transparent,
                            ),
                            style = Stroke(width = 3.dp.toPx()),
                            alpha = visible,
                        )
                    }
                }

                if (rim) {
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            0f to colors.rimLight,
                            0.38f to colors.rimLight.copy(alpha = colors.rimLight.alpha * 0.16f),
                            0.62f to colors.rimShade.copy(alpha = colors.rimShade.alpha * 0.6f),
                            1f to colors.rimLight.copy(alpha = colors.rimLight.alpha * 0.55f),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        style = Stroke(width = (if (press > 0f) 1.6.dp else 1.dp).toPx()),
                        alpha = visible,
                    )
                }
            }
            drawContent()
        }
}

/**
 * Soft shadow made of a few widening strokes, drawn only *outside* the shape. The platform shadow
 * (`Modifier.shadow`) also shows through translucent surfaces, which spoils the glass look.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSoftShadow(path: Path, elevation: Dp, color: Color) {
    val reach = elevation.toPx() * 1.5f
    val steps = 8
    clipPath(path, ClipOp.Difference) {
        translate(top = elevation.toPx() * 0.45f) {
            for (i in steps downTo 1) {
                val fraction = i / steps.toFloat()
                drawPath(
                    path,
                    color.copy(alpha = color.alpha * 1.7f / steps * (1f - fraction * 0.55f)),
                    style = Stroke(width = reach * fraction * 2f, join = StrokeJoin.Round),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlurredBackdrop(
    backdrop: GlassBackdrop?,
    holder: GlassHolder,
    blurLayer: GraphicsLayer?,
    effect: BlurEffect?,
    alpha: Float,
) {
    val source = backdrop?.coordinates
    val me = holder.coordinates
    if (backdrop == null || blurLayer == null || source == null || me == null || !source.isAttached || !me.isAttached) return
    val offset = source.localPositionOf(me, Offset.Zero)
    blurLayer.renderEffect = effect
    blurLayer.colorFilter = saturationBoost
    blurLayer.alpha = alpha
    blurLayer.record(size = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))) {
        translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
    }
    drawLayer(blurLayer)
}

private val ZeroProvider: () -> Float = { 0f }
private val OneProvider: () -> Float = { 1f }

// ---------------------------------------------------------------------------------------------------------------
// Touch feedback: iOS-like springy press + haptic tick.
// ---------------------------------------------------------------------------------------------------------------

@Stable
class GlassPress internal constructor(
    val interactionSource: MutableInteractionSource,
    private val progress: State<Float>,
) {
    /** 0 at rest, 1 while pressed (springs past 1 a little when pressed fast). */
    val value: Float get() = progress.value
    val provider: () -> Float = { progress.value }
}

@Composable
fun rememberGlassPress(): GlassPress {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val progress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.52f, stiffness = Spring.StiffnessMedium),
        label = "glassPress",
    )
    return remember(source, progress) { GlassPress(source, progress) }
}

/** Shrinks the element a little while it is pressed. */
fun Modifier.glassPressScale(press: GlassPress, pressedScale: Float = 0.94f): Modifier = graphicsLayer {
    val scale = 1f - (1f - pressedScale) * press.value
    scaleX = scale
    scaleY = scale
}

/** Clickable without the Material ripple (the glass reacts by itself), with a haptic tick. */
@Composable
fun Modifier.glassClickable(
    press: GlassPress,
    onClick: () -> Unit,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    haptic: Boolean = true,
): Modifier {
    val feedback = LocalHapticFeedback.current
    return clickable(
        interactionSource = press.interactionSource,
        indication = null,
        enabled = enabled,
        role = role,
        onClickLabel = onClickLabel,
    ) {
        if (haptic) feedback.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Ambient background: soft colored light the glass refracts.
// ---------------------------------------------------------------------------------------------------------------

/** Slow 0..1 phase that drifts the background blobs; shared so every screen shows the same picture. */
val LocalAmbientPhase = staticCompositionLocalOf<State<Float>> { StaticPhase }

private val StaticPhase: State<Float> = mutableFloatStateOf(0.15f)

/** `true` unless the user turned system animations off (accessibility "remove animations"). */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
}

/** Provides the drifting phase to everything below. Call once, near the root. */
@Composable
fun AmbientPhaseProvider(content: @Composable () -> Unit) {
    val animate = rememberAnimationsEnabled()
    val phase: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "ambient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
            label = "ambientPhase",
        )
    } else {
        StaticPhase
    }
    CompositionLocalProvider(LocalAmbientPhase provides phase, content = content)
}

@Composable
fun GlassBackground(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.glass
    val base = MaterialTheme.colorScheme.background
    val phase = LocalAmbientPhase.current
    Box(
        modifier
            .fillMaxSize()
            .background(base)
            .drawBehind {
                val angle = phase.value * 2f * PI.toFloat()
                val w = size.width
                val h = size.height
                val reach = maxOf(w, h)
                fun blob(color: Color, cx: Float, cy: Float, radius: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = radius),
                        radius = radius,
                        center = Offset(cx, cy),
                    )
                }
                blob(colors.blobA, w * (0.18f + 0.10f * cos(angle)), h * (0.12f + 0.05f * sin(angle)), reach * 0.62f)
                blob(colors.blobB, w * (0.92f + 0.06f * sin(angle * 2f)), h * (0.46f + 0.06f * cos(angle)), reach * 0.55f)
                blob(colors.blobC, w * (0.30f + 0.12f * sin(angle)), h * (0.92f + 0.03f * cos(angle * 2f)), reach * 0.60f)
            },
    )
}

/** A static, full-size glass-free background for places that cover other content (sync panel, error pages). */
@Composable
fun GlassCover(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize()) {
        GlassBackground()
        content()
    }
}

/** Shape that has no rounding, named so call sites read well. */
val GlassStrip: Shape = RectangleShape

/** One-off entrance animation 0 -> 1 with a springy overshoot, for dialogs and success marks. */
@Composable
fun rememberEntrance(): State<Float> {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animatable.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f))
    }
    return animatable.asState()
}

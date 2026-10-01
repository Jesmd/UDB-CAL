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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.example.minimo.ui.theme.GlassColors
import com.example.minimo.ui.theme.glass
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------------------------
// Backdrop: what glass bends and blurs.
//
// A screen records its content into a [GraphicsLayer] (the "source"). A glass element that floats above that content
// (a top bar, the tab bar) draws a blurred copy of the part of the source that sits behind it. That is what makes it
// look like glass over scrolling content. Only Android 12+ can blur; older phones get a more opaque frosted fill.
// On Android 13+ the edge of the glass also bends what is behind it, like the rim of a lens (see GlassShaders.kt).
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

/**
 * How the edge of a glass bends what is behind it (Android 13+; elsewhere the glass is only blurred).
 *
 * @param amount how far, at the very edge, the picture is pulled in from outside the glass.
 * @param bevel how wide the bent band along the edge is.
 * @param dispersion how much the colors separate in that band.
 */
@Stable
data class GlassRefraction(val amount: Dp, val bevel: Dp, val dispersion: Float = 0.06f) {
    companion object {
        /** Bars and wide buttons. */
        val Standard = GlassRefraction(amount = 16.dp, bevel = 14.dp, dispersion = 0.035f)

        /** Small round controls. */
        val Compact = GlassRefraction(amount = 12.dp, bevel = 10.dp, dispersion = 0.035f)

        /** The selection lens of the tab bar. */
        val Lens = GlassRefraction(amount = 12.dp, bevel = 10.dp, dispersion = 0.05f)
    }
}

private class GlassHolder {
    var coordinates: LayoutCoordinates? = null

    /** The last effect built, so it is only rebuilt when something it depends on changes. */
    var effectKey: EffectKey? = null
    var effect: RenderEffect? = null
}

private data class EffectKey(
    val layerWidth: Int,
    val layerHeight: Int,
    val originX: Int,
    val originY: Int,
    val width: Float,
    val height: Float,
    val radius: Float,
    val bevel: Float,
    val amount: Float,
    val magnify: Float,
    val dispersion: Float,
    val blur: Float,
)

private fun saturationFilter(amount: Float) = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(amount) })

private fun Color.withAlpha(factor: Float): Color = copy(alpha = alpha * factor)

/** Corner radius of a rounded outline (all corners are the same here), 0 for anything else. */
private fun Outline.cornerRadiusPx(): Float = (this as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f

/**
 * Turns this element into a piece of liquid glass.
 *
 * From the back to the front: a soft shadow, what is behind the glass (blurred, more vivid and, on Android 13+, bent
 * at the edge like the rim of a lens), a faint translucent body, the tint, and the light on the edge: a thin bright
 * line with a soft glow inside it, brighter on the sides that face the light.
 *
 * @param backdrop what to blur; `null` for a plain frosted fill.
 * @param tint paints the glass with a color (prominent buttons, selected lens).
 * @param blurRadius how blurred the backdrop is.
 * @param pressed 0..1 read while drawing, brightens the glass while it is pressed.
 * @param visibility 0..1 fades the whole effect (a top bar that appears as content scrolls under it).
 * @param rim `false` for edge-to-edge strips, which draw their own hairline.
 * @param refraction how the edge bends the backdrop; `null` for none (strips).
 * @param magnify read while drawing: above 1 the glass also zooms what is inside it, like a magnifying lens.
 * @param rimStrength scales the light on the edge.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    tint: Color? = null,
    shadowElevation: Dp = 12.dp,
    blurRadius: Dp = 10.dp,
    pressed: () -> Float = ZeroProvider,
    visibility: () -> Float = OneProvider,
    rim: Boolean = true,
    solid: Boolean = false,
    refraction: GlassRefraction? = GlassRefraction.Standard,
    magnify: () -> Float = OneProvider,
    rimStrength: Float = 1f,
): Modifier {
    val colors = MaterialTheme.glass
    val density = LocalDensity.current
    val holder = remember { GlassHolder() }
    val canBlur = backdrop != null && blurSupported && !solid
    val blurLayer = if (canBlur) rememberGraphicsLayer() else null
    val blurPx = with(density) { blurRadius.toPx() }
    val plainBlur = remember(blurPx) { if (blurSupported) BlurEffect(blurPx, blurPx, TileMode.Clamp) else null }
    val saturation = remember(colors.saturation) { saturationFilter(colors.saturation) }

    return this
        .onGloballyPositioned { holder.coordinates = it }
        .drawWithContent {
            val visible = visibility().coerceIn(0f, 1f)
            if (visible > 0.001f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = Path().apply { addOutline(outline) }
                val radius = outline.cornerRadiusPx()
                val press = pressed().coerceIn(0f, 1f)
                if (shadowElevation > 0.dp && visible > 0.5f) {
                    drawGlassShadow(path, shadowElevation.toPx(), colors)
                }
                // Cards sit on the soft ambient background and can stay translucent; only a glass that should blur but
                // cannot (old phones) or a dialog needs the more opaque fill.
                val strongFill = solid || (backdrop != null && !blurSupported)

                clipPath(path) {
                    if (canBlur && backdrop != null && blurLayer != null) {
                        drawGlassBackdrop(
                            backdrop = backdrop,
                            holder = holder,
                            layer = blurLayer,
                            plainBlur = plainBlur,
                            blurPx = blurPx,
                            refraction = refraction,
                            radius = radius,
                            magnify = magnify(),
                            saturation = saturation,
                            alpha = visible,
                        )
                    }

                    // Translucent body. The backdrop already gives the glass its body, so it needs less of it.
                    val bodyScale = if (canBlur) 0.62f else 1f
                    val top = if (strongFill) colors.fillSolidTop else colors.fillTop.withAlpha(bodyScale)
                    val bottom = if (strongFill) colors.fillSolidBottom else colors.fillBottom.withAlpha(bodyScale)
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
                                0f to Color.White.copy(alpha = 0.30f),
                                0.55f to Color.White.copy(alpha = 0f),
                            ),
                            alpha = visible,
                        )
                    }

                    if (press > 0f) {
                        drawRect(Color.White.copy(alpha = (if (colors.isDark) 0.14f else 0.28f) * press), alpha = visible)
                    }

                    if (rim) drawGlassRim(outline, radius, colors, rimStrength, press, visible)
                }

                if (rim) {
                    drawOutline(outline, colors.edge, alpha = visible, style = Stroke(width = 0.75.dp.toPx()))
                }
            }
            drawContent()
        }
}

/**
 * The light on the edge. On Android 13+ a shader works it out from the direction each bit of the edge faces; before
 * that it is a gradient that is brighter at the top left and the bottom right.
 */
private fun DrawScope.drawGlassRim(
    outline: Outline,
    radius: Float,
    colors: GlassColors,
    rimStrength: Float,
    press: Float,
    visible: Float,
) {
    val shader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassShaders.rim else null
    if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        shader.update(
            width = size.width,
            height = size.height,
            radius = radius,
            rimWidth = 1.2.dp.toPx(),
            glowWidth = 7.dp.toPx(),
            strength = colors.rimStrength * rimStrength,
            pressed = press,
        )
        drawRect(shader.brush, alpha = visible)
        return
    }
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

/**
 * A soft, light shadow: a few widening strokes drawn only *outside* the shape, so it adds up to a smooth falloff and
 * never shows through the translucent glass (the platform shadow does). Glass floats, so it is gentle: it only
 * separates the glass from what is under it.
 */
private fun DrawScope.drawGlassShadow(path: Path, elevationPx: Float, colors: GlassColors) {
    val alpha = colors.shadowAlpha * (elevationPx / 16.dp.toPx()).coerceIn(0.2f, 1.25f)
    if (alpha <= 0.005f) return
    val reach = elevationPx * 1.1f
    val steps = 10
    clipPath(path, ClipOp.Difference) {
        translate(top = elevationPx * 0.35f) {
            for (i in steps downTo 1) {
                val fraction = i / steps.toFloat()
                // Adds up to `alpha` at the edge and fades out to nothing at `reach`.
                val stepAlpha = alpha * (2 * (steps - i) + 1) / (steps * steps).toFloat()
                drawPath(
                    path,
                    colors.shadow.copy(alpha = stepAlpha),
                    style = Stroke(width = reach * fraction * 2f, join = StrokeJoin.Round),
                )
            }
        }
    }
}

/**
 * Draws the part of [backdrop] that sits behind this glass: blurred, more vivid and, when the phone can, bent along
 * the edge. The picture is recorded with a margin around the glass, because the edge pulls in what is just outside it.
 */
private fun DrawScope.drawGlassBackdrop(
    backdrop: GlassBackdrop,
    holder: GlassHolder,
    layer: GraphicsLayer,
    plainBlur: BlurEffect?,
    blurPx: Float,
    refraction: GlassRefraction?,
    radius: Float,
    magnify: Float,
    saturation: ColorFilter,
    alpha: Float,
) {
    val source = backdrop.coordinates
    val me = holder.coordinates
    if (source == null || me == null || !source.isAttached || !me.isAttached) return
    val origin = source.localPositionOf(me, Offset.Zero)
    val width = size.width.roundToInt().coerceAtLeast(1)
    val height = size.height.roundToInt().coerceAtLeast(1)

    val shader = if (refraction != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) GlassShaders.refraction else null
    val amountPx = if (shader != null && refraction != null) refraction.amount.toPx() else 0f
    // How much of the picture is recorded around the glass; none where there is nothing to bend.
    val margin = if (shader != null) ceil(amountPx).toInt() + 2 else 0
    val marginLeft = minOf(margin, floor(origin.x).toInt().coerceAtLeast(0))
    val marginTop = minOf(margin, floor(origin.y).toInt().coerceAtLeast(0))
    val marginRight = minOf(margin, (source.size.width - ceil(origin.x).toInt() - width).coerceAtLeast(0))
    val marginBottom = minOf(margin, (source.size.height - ceil(origin.y).toInt() - height).coerceAtLeast(0))
    val layerWidth = width + marginLeft + marginRight
    val layerHeight = height + marginTop + marginBottom

    if (shader != null && refraction != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val key = EffectKey(
            layerWidth, layerHeight, marginLeft, marginTop, size.width, size.height, radius,
            refraction.bevel.toPx(), amountPx, magnify, refraction.dispersion, blurPx,
        )
        if (holder.effectKey != key) {
            holder.effect = shader.effect(
                layerWidth = layerWidth.toFloat(),
                layerHeight = layerHeight.toFloat(),
                originX = marginLeft.toFloat(),
                originY = marginTop.toFloat(),
                width = size.width,
                height = size.height,
                radius = radius,
                bevel = key.bevel,
                amount = amountPx,
                magnify = magnify,
                dispersion = refraction.dispersion,
                blur = blurPx,
            )
            holder.effectKey = key
        }
        layer.renderEffect = holder.effect
    } else {
        layer.renderEffect = plainBlur
    }
    layer.colorFilter = saturation
    layer.alpha = alpha
    layer.record(size = IntSize(layerWidth, layerHeight)) {
        translate(-(origin.x - marginLeft), -(origin.y - marginTop)) { drawLayer(backdrop.layer) }
    }
    translate(-marginLeft.toFloat(), -marginTop.toFloat()) { drawLayer(layer) }
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

/** The drift takes a minute, so it only needs to move this many times per loop to look smooth. */
private const val AmbientSteps = 900f

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
        val raw = rememberInfiniteTransition(label = "ambient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(60_000, easing = LinearEasing), RepeatMode.Restart),
            label = "ambientPhase",
        )
        // Stepped: the background is redrawn (and the glass over it re-rendered) a dozen times a second, not sixty.
        remember(raw) { derivedStateOf { floor(raw.value * AmbientSteps) / AmbientSteps } }
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
                blob(colors.blobA, w * (0.16f + 0.10f * cos(angle)), h * (0.10f + 0.05f * sin(angle)), reach * 0.55f)
                blob(colors.blobB, w * (0.94f + 0.06f * sin(angle * 2f)), h * (0.42f + 0.06f * cos(angle)), reach * 0.50f)
                blob(colors.blobC, w * (0.24f + 0.12f * sin(angle)), h * (0.88f + 0.03f * cos(angle * 2f)), reach * 0.52f)
                blob(colors.blobD, w * (0.08f + 0.05f * cos(angle * 3f)), h * (0.56f + 0.04f * sin(angle)), reach * 0.38f)
                blob(colors.blobE, w * (0.62f + 0.06f * sin(angle * 2f)), h * (0.70f + 0.04f * cos(angle)), reach * 0.30f)
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

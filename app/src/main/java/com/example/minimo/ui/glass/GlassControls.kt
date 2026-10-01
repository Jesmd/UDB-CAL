package com.example.minimo.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import com.example.minimo.ui.theme.glass
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(26.dp)

/** A frosted panel that sits on the page. It has no backdrop of its own: the page behind it is soft color already. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    shape: Shape = CardShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    val press = rememberGlassPress()
    val base = if (onClick != null) modifier.glassPressScale(press, 0.97f) else modifier
    val surface = base.glassSurface(
        shape = shape,
        backdrop = null,
        shadowElevation = 6.dp,
        pressed = press.provider,
    )
    val interactive = if (onClick != null) {
        surface.glassClickable(press, onClick, role = Role.Button, onClickLabel = onClickLabel)
    } else {
        surface
    }
    Column(interactive, content = content)
}

/**
 * Fades a disabled control. Each part fades by itself: a single faded layer would cut the shadow off in a rectangle
 * around the control.
 */
private fun Modifier.fadeWhenDisabled(enabled: Boolean): Modifier = graphicsLayer {
    alpha = if (enabled) 1f else 0.45f
    compositingStrategy = CompositingStrategy.ModulateAlpha
}

enum class GlassButtonStyle {
    /** Filled with the accent color. The main action of a screen. */
    Prominent,

    /** Clear glass with accent text. */
    Regular,

    /** Filled red. */
    Destructive,

    /** Clear glass with red text. Less shouting than [Destructive], for actions next to other content. */
    Danger,
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: GlassButtonStyle = GlassButtonStyle.Prominent,
    icon: ImageVector? = null,
    backdrop: GlassBackdrop? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val press = rememberGlassPress()
    val tint = when (style) {
        GlassButtonStyle.Prominent -> colors.primary
        GlassButtonStyle.Destructive -> colors.error
        GlassButtonStyle.Regular, GlassButtonStyle.Danger -> null
    }
    val filled = tint != null
    val contentColor = when (style) {
        GlassButtonStyle.Prominent -> colors.onPrimary
        GlassButtonStyle.Destructive -> colors.onError
        GlassButtonStyle.Regular -> colors.primary
        GlassButtonStyle.Danger -> colors.error
    }
    Row(
        modifier
            .fadeWhenDisabled(enabled)
            .glassPressScale(press)
            .heightIn(min = if (compact) 40.dp else 52.dp)
            .glassSurface(
                shape = CircleShape,
                backdrop = backdrop,
                tint = tint,
                shadowElevation = if (filled) 8.dp else 5.dp,
                blurRadius = 8.dp,
                refraction = if (compact) GlassRefraction.Compact else GlassRefraction.Standard,
                pressed = press.provider,
            )
            .glassClickable(press, onClick, enabled = enabled, role = Role.Button)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = contentColor, textAlign = TextAlign.Center)
    }
}

/** A round glass button with an icon, 48dp so it is easy to hit. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    enabled: Boolean = true,
) {
    val press = rememberGlassPress()
    Box(
        modifier
            .fadeWhenDisabled(enabled)
            .glassPressScale(press, 0.9f)
            .size(48.dp)
            .glassSurface(
                CircleShape,
                backdrop = backdrop,
                shadowElevation = 6.dp,
                blurRadius = 8.dp,
                refraction = GlassRefraction.Compact,
                pressed = press.provider,
            )
            .glassClickable(press, onClick, enabled = enabled, role = Role.Button)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** iOS-style switch: the thumb is a little glass bead that stretches while you hold it. */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val press = rememberGlassPress()
    val haptic = LocalHapticFeedback.current
    val position by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "switchPosition",
    )
    val track by animateColorAsState(
        targetValue = if (checked) colors.tertiary else colors.onSurface.copy(alpha = 0.18f),
        label = "switchTrack",
    )
    val trackWidth = 56.dp
    val trackHeight = 34.dp
    val thumb = 28.dp
    val inset = 3.dp
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = press.interactionSource,
                indication = null,
                onValueChange = {
                    haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onCheckedChange(it)
                },
            ),
    ) {
        Box(
            Modifier
                .offset {
                    val travel = trackWidth - thumb - inset * 2
                    IntOffset((inset + travel * position).roundToPx(), inset.roundToPx())
                }
                .graphicsLayer {
                    scaleX = 1f + 0.22f * press.value
                    transformOrigin = TransformOrigin(if (position > 0.5f) 1f else 0f, 0.5f)
                }
                .size(thumb)
                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.3f))
                .background(Color.White, CircleShape)
                .border(0.5.dp, Color.Black.copy(alpha = 0.06f), CircleShape),
        )
    }
}

/** Filled, rounded text field with an accent ring while it has focus. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val ring by animateColorAsState(
        targetValue = when {
            isError -> scheme.error
            focused -> scheme.primary
            else -> Color.Transparent
        },
        label = "fieldRing",
    )
    val shape = RoundedCornerShape(18.dp)
    val fill = scheme.onSurface.copy(alpha = if (MaterialTheme.glass.isDark) 0.10f else 0.06f)
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        shape = shape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            errorContainerColor = scheme.error.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = scheme.primary,
        ),
        modifier = modifier.border(BorderStroke(1.5.dp, ring), shape),
    )
}

/** Slider with an iOS-style white thumb. */
@Composable
fun GlassSliderThumb(interactionSource: MutableInteractionSource) {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "thumbScale",
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .size(30.dp)
            .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.32f))
            .background(Color.White, CircleShape)
            .border(0.5.dp, Color.Black.copy(alpha = 0.08f), CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSliderTrack(state: SliderState) {
    SliderDefaults.Track(
        sliderState = state,
        modifier = Modifier.height(8.dp),
        colors = SliderDefaults.colors(
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f),
        ),
        drawStopIndicator = null,
        drawTick = { _, _ -> },
        thumbTrackGapSize = 0.dp,
        trackInsideCornerSize = 0.dp,
    )
}

enum class NoticeKind { Info, Warning, Error }

/** A soft tinted note, for banners and warnings. */
@Composable
fun GlassNotice(
    text: String,
    modifier: Modifier = Modifier,
    kind: NoticeKind = NoticeKind.Info,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when (kind) {
        NoticeKind.Info -> scheme.primary
        NoticeKind.Warning -> MaterialTheme.glass.warning
        NoticeKind.Error -> scheme.error
    }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .glassSurface(shape, backdrop = null, shadowElevation = 0.dp)
            .background(color.copy(alpha = 0.12f), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.weight(1f))
    }
}

/** A small rounded label. */
@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.13f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Small caps title above a group of settings or a block of results. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp)
            .semantics { heading() },
    )
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
}

/** A thin determinate bar, used for page loading. */
@Composable
fun GlassProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "progress")
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind {
                drawRect(color.copy(alpha = 0.14f))
                drawRect(color, size = Size(size.width * animated, size.height))
            },
    )
}

/**
 * How a course stands, as a bar out of 10: what was earned, what can still be earned (lighter), and a tick at the
 * pass mark.
 */
@Composable
fun GradeBar(
    earned: Float,
    possible: Float,
    passMark: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val colors = MaterialTheme.colorScheme
    val earnedAnimated by animateFloatAsState(
        (earned / 10f).coerceIn(0f, 1f),
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "earned",
    )
    val possibleAnimated by animateFloatAsState(
        (possible / 10f).coerceIn(0f, 1f),
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
        label = "possible",
    )
    val mark = (passMark / 10f).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(colors.onSurface.copy(alpha = 0.10f), cornerRadius = radius)
                drawRoundRect(colors.primary.copy(alpha = 0.28f), size = Size(size.width * possibleAnimated, size.height), cornerRadius = radius)
                drawRoundRect(colors.primary, size = Size(size.width * earnedAnimated, size.height), cornerRadius = radius)
                val x = size.width * mark
                drawLine(
                    color = colors.onSurface.copy(alpha = 0.55f),
                    start = Offset(x, size.height * 0.18f),
                    end = Offset(x, size.height * 0.82f),
                    strokeWidth = 2.dp.toPx(),
                )
            },
    )
}

/** Row used inside cards for a label on the left and a control or value on the right. */
@Composable
fun GlassRow(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier.fillMaxWidth().padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

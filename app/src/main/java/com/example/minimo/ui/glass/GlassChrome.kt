package com.example.minimo.ui.glass

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.minimo.ui.theme.glass
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Height reserved at the bottom by the floating tab bar (0 when it is hidden). Set by the app root. */
val LocalTabBarInset = compositionLocalOf { 0.dp }

private val TopBarContentHeight = 56.dp

// ---------------------------------------------------------------------------------------------------------------
// Scaffold
// ---------------------------------------------------------------------------------------------------------------

/**
 * Screen frame: ambient background, content that scrolls *under* a floating glass top bar, and room at the bottom
 * for the tab bar. The content gets [PaddingValues] to use as its content padding.
 *
 * @param captureBackdrop whether the top bar blurs the content under it. Turn it off for screens whose content is
 * a WebView, which never goes under the bar.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    captureBackdrop: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabInset = LocalTabBarInset.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val bottom = when {
        keyboardOpen -> 16.dp
        else -> maxOf(tabInset, navBottom + 16.dp)
    }
    val top = statusTop + if (topBar != null) TopBarContentHeight else 0.dp
    val sourceModifier = if (captureBackdrop) Modifier.glassSource(backdrop) else Modifier

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(sourceModifier)) {
            GlassBackground()
            Box(Modifier.fillMaxSize().imePadding()) {
                content(PaddingValues(top = top, bottom = bottom))
            }
        }
        CompositionLocalProvider(LocalGlassBackdrop provides if (captureBackdrop) backdrop else null) {
            if (topBar != null) Box(Modifier.align(Alignment.TopStart).fillMaxWidth()) { topBar() }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (keyboardOpen) 12.dp else bottom)) { snackbarHost() }
        }
    }
}

/**
 * Top bar that is invisible at rest and turns into blurred glass (with the title fading in) as content scrolls
 * under it. Pass the content's scroll progress as [progress]; use `{ 1f }` when the bar should always show.
 */
@Composable
fun GlassTopBar(
    title: String,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val hairline = MaterialTheme.colorScheme.outline
    Box(
        modifier
            .fillMaxWidth()
            .glassSurface(GlassStrip, shadowElevation = 0.dp, visibility = progress, rim = false)
            .drawBehind {
                val alpha = (progress() * 0.55f).coerceIn(0f, 1f)
                drawLine(
                    hairline.copy(alpha = alpha),
                    Offset(0f, size.height - 0.5.dp.toPx()),
                    Offset(size.width, size.height - 0.5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(TopBarContentHeight)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
                    .graphicsLayer { alpha = progress() }
                    .semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/**
 * Big title at the top of the content, as in iOS. It scrolls away while the top bar's title fades in. It is
 * inset 4dp: put it inside content that already has the usual 16dp side padding.
 */
@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 6.dp).semantics { heading() },
    )
}

/** How far a list has scrolled, 0 at rest to 1 once the large title is gone. */
@Composable
fun LazyListState.topBarProgress(): () -> Float {
    val distance = with(LocalDensity.current) { 52.dp.toPx() }
    return {
        if (firstVisibleItemIndex > 0) 1f else (firstVisibleItemScrollOffset / distance).coerceIn(0f, 1f)
    }
}

@Composable
fun ScrollState.topBarProgress(): () -> Float {
    val distance = with(LocalDensity.current) { 52.dp.toPx() }
    return { (value / distance).coerceIn(0f, 1f) }
}

// ---------------------------------------------------------------------------------------------------------------
// Tab bar
// ---------------------------------------------------------------------------------------------------------------

data class GlassTab(val label: String, val icon: ImageVector)

/** Height the floating tab bar takes, including its margin below it. */
val TabBarHeight = 66.dp
val TabBarBottomMargin = 12.dp

/**
 * Floating pill with a glass "lens" under the selected tab. The lens slides between tabs with a springy stretch, and
 * you can drag it across the bar.
 */
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var touching by remember { mutableStateOf(false) }
    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.64f, stiffness = 300f),
        label = "tabLens",
    )
    val grow by animateFloatAsState(
        targetValue = if (touching) 1.07f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
        label = "tabLensGrow",
    )
    val padding = 5.dp
    val count = tabs.size
    // The gesture loop must survive selection changes, so it reads the latest values instead of restarting.
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .height(TabBarHeight)
            .glassSurface(CircleShape, shadowElevation = 20.dp, blurRadius = 26.dp)
            .pointerInput(count) {
                awaitEachGesture {
                    val inner = size.width - 2 * padding.toPx()
                    fun indexAt(x: Float) = ((x - padding.toPx()) / (inner / count)).toInt().coerceIn(0, count - 1)
                    val down = awaitFirstDown()
                    touching = true
                    try {
                        var last = currentSelected
                        fun pick(x: Float) {
                            val index = indexAt(x)
                            if (index != last) {
                                last = index
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                currentOnSelect(index)
                            }
                        }
                        pick(down.position.x)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                pick(change.position.x)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        touching = false
                    }
                }
            },
    ) {
        val itemWidth = (maxWidth - padding * 2) / count
        // The lens.
        Box(
            Modifier
                .offset { IntOffset((padding + itemWidth * position).roundToPx(), padding.roundToPx()) }
                .width(itemWidth)
                .fillMaxHeight()
                .padding(vertical = padding)
                .graphicsLayer {
                    val stretch = 1f + 0.16f * min(1f, abs(selectedIndex - position))
                    scaleX = stretch * grow
                    scaleY = grow
                }
                .glassSurface(
                    CircleShape,
                    backdrop = null,
                    shadowElevation = 0.dp,
                    pressed = { if (touching) 0.5f else 0f },
                )
                .background(colors.primary.copy(alpha = 0.12f), CircleShape),
        )
        Row(Modifier.padding(horizontal = padding).fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val tint by androidx.compose.animation.animateColorAsState(
                    if (selected) colors.primary else colors.onSurface.copy(alpha = 0.62f),
                    label = "tabTint",
                )
                val bump by animateFloatAsState(
                    if (selected) 1.1f else 1f,
                    spring(dampingRatio = 0.45f, stiffness = 450f),
                    label = "tabBump",
                )
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = selected
                            onClick(label = null) {
                                onSelect(index)
                                true
                            }
                        },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.graphicsLayer {
                            scaleX = bump
                            scaleY = bump
                        },
                    )
                    Text(tab.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Dialog and toast
// ---------------------------------------------------------------------------------------------------------------

/**
 * Alert / form dialog as a sheet of glass that pops in with a spring. When the phone can blur what is behind
 * windows (Android 12+), the screen behind is blurred like in iOS.
 *
 * Dialogs live in their own window, so they cannot sample the screen's backdrop: they use a frosted fill instead.
 */
@Composable
fun GlassDialog(
    title: String,
    onDismiss: () -> Unit,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val windowBlur = remember(context) { crossWindowBlurAvailable(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // The dialog has its own window: dim it, and blur the screen behind it where the phone can.
        val dialogView = LocalView.current
        SideEffect {
            val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setDimAmount(if (windowBlur) 0.22f else 0.42f)
            if (windowBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.also { it.blurBehindRadius = 30 }
            }
        }
        val entrance by rememberEntrance()
        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val scale = 0.86f + 0.14f * entrance
                        scaleX = scale
                        scaleY = scale
                        alpha = entrance.coerceIn(0f, 1f)
                    }
                    // Swallows taps so touching the dialog does not dismiss it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    .glassSurface(
                        RoundedCornerShape(32.dp),
                        backdrop = null,
                        shadowElevation = 28.dp,
                        solid = !windowBlur,
                    )
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), content = buttons)
            }
        }
    }
}

private fun crossWindowBlurAvailable(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        runCatching {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).isCrossWindowBlurEnabled
        }.getOrDefault(false)

/** Floating message pill for [SnackbarHost]. */
@Composable
fun GlassSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        Box(
            Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 460.dp)
                .glassSurface(RoundedCornerShape(24.dp), shadowElevation = 18.dp, blurRadius = 24.dp)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

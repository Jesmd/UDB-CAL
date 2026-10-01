package com.example.minimo.ui.glass

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.dp
import com.example.minimo.ui.theme.MinimoTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class GlassRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Composable
    private fun Sample(dark: Boolean) {
        val listState = rememberLazyListState()
        var selected by remember { mutableStateOf(0) }
        var on by remember { mutableStateOf(true) }
        var text by remember { mutableStateOf("Cálculo II") }
        MinimoTheme(darkTheme = dark) {
            AmbientPhaseProvider {
                val backdrop = rememberGlassBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().glassSource(backdrop)) {
                        GlassScaffold(
                            topBar = {
                                GlassTopBar("Materias", listState.topBarProgress(), actions = {
                                    GlassButton("Sincronizar", {}, style = GlassButtonStyle.Regular, icon = Icons.Filled.Add, compact = true, backdrop = LocalGlassBackdrop.current)
                                    GlassIconButton(Icons.Filled.Add, "Agregar", {})
                                })
                            },
                        ) { padding ->
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(16.dp, padding.calculateTopPadding(), 16.dp, 120.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                item { LargeTitle("Materias") }
                                items(3) { i ->
                                    GlassCard(Modifier.fillMaxWidth(), onClick = {}) {
                                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Materia número $i")
                                            GradeBar(earned = 4f + i, possible = 8f, passMark = 6f)
                                            GlassNotice("Los porcentajes suman 90%", kind = NoticeKind.Warning, icon = Icons.Filled.Info)
                                        }
                                    }
                                }
                                item {
                                    GlassCard(Modifier.fillMaxWidth()) {
                                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            GlassTextField(text, { text = it }, "Nombre", Modifier.fillMaxWidth())
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Modo", Modifier.weight(1f))
                                                GlassSwitch(on, { on = it })
                                            }
                                            GlassButton("Guardar", {}, Modifier.fillMaxWidth())
                                            GlassButton("Eliminar", {}, Modifier.fillMaxWidth(), style = GlassButtonStyle.Danger)
                                        }
                                    }
                                }
                                items(3) { i ->
                                    GlassCard(Modifier.fillMaxWidth()) { Text("Más abajo $i", Modifier.padding(30.dp)) }
                                }
                            }
                        }
                    }
                    CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                        GlassTabBar(
                            tabs = listOf(GlassTab("Materias", Icons.AutoMirrored.Filled.List), GlassTab("Ajustes", Icons.Filled.Settings)),
                            selectedIndex = selected,
                            onSelect = { selected = it },
                            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun rendersLight() = render(false, "light")

    @Test
    fun rendersDark() = render(true, "dark")

    @Test
    fun dialogShowsItsContentAndButtons() {
        var dismissed = false
        rule.setContent {
            MinimoTheme(darkTheme = false) {
                GlassDialog(
                    title = "Eliminar",
                    onDismiss = { dismissed = true },
                    buttons = { GlassButton("Cancelar", { dismissed = true }, modifier = Modifier.weight(1f)) },
                ) { Text("¿Seguro?") }
            }
        }
        rule.onNodeWithText("Eliminar").assertIsDisplayed()
        rule.onNodeWithText("¿Seguro?").assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        assert(dismissed)
    }

    /** Renders the dialog on its own (build/glass-dialog.png) to look at how its glass is drawn. */
    @Test
    fun rendersDialog() {
        rule.setContent {
            MinimoTheme(darkTheme = true) {
                GlassDialog(
                    title = "Nueva materia",
                    onDismiss = {},
                    buttons = {
                        GlassButton("Cancelar", {}, Modifier.weight(1f), style = GlassButtonStyle.Regular)
                        GlassButton("Guardar", {}, Modifier.weight(1f))
                    },
                ) {
                    GlassTextField("", {}, "Nombre", Modifier.fillMaxWidth())
                    GlassTextField("", {}, "Código (opcional)", Modifier.fillMaxWidth())
                }
            }
        }
        rule.waitForIdle()
        val bitmap = rule.onNode(isDialog()).captureToImage().asAndroidBitmap()
        val out = File("build/glass-dialog.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun tabBarSelectsOnTap() {
        var selected = 0
        rule.setContent {
            MinimoTheme(darkTheme = false) {
                GlassTabBar(
                    tabs = listOf(GlassTab("Materias", Icons.AutoMirrored.Filled.List), GlassTab("Ajustes", Icons.Filled.Settings)),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
            }
        }
        rule.onNodeWithText("Ajustes").performClick()
        assert(selected == 1)
    }

    private fun render(dark: Boolean, name: String) {
        rule.setContent { Sample(dark) }
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        val out = File("build/glass-$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

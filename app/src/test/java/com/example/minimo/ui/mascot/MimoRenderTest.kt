package com.example.minimo.ui.mascot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.minimo.R
import com.example.minimo.ui.theme.MinimoTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MimoRenderTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun rendersEveryMood() {
        rule.setContent {
            MinimoTheme(darkTheme = false) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MimoMood.entries.forEach { Mimo(mood = it, size = 120.dp) }
                }
            }
        }
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        save(bitmap, "mimo-moods")
    }

    @Test
    fun rendersLauncherIcon() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val icon = checkNotNull(context.getDrawable(R.mipmap.ic_launcher))
        val size = 432
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.parseColor("#DDE3EE"))
        // Round mask, like most launchers.
        canvas.clipPath(Path().apply { addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW) })
        icon.setBounds(0, 0, size, size)
        icon.draw(canvas)
        save(bitmap, "launcher-icon")
        assertEquals(size, bitmap.width)
        assertEquals("MinimoApp", context.getString(R.string.app_name))
    }

    private fun save(bitmap: Bitmap, name: String) {
        val out = File("build/$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

package com.example.minimo.ui.glass

import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.RenderEffect as PlatformRenderEffect

// ---------------------------------------------------------------------------------------------------------------
// The two AGSL shaders that make the glass look like glass (Android 13+). Older phones fall back to a plain blur and
// drawn edges, see GlassSurface.kt.
//
// Both describe the shape as a rounded rectangle: `sdRoundRect` is the distance to its edge (negative inside) and
// `outwardNormal` is the direction the edge faces, which is what tells light and refraction which way to go.
// ---------------------------------------------------------------------------------------------------------------

/**
 * Bends what is behind the glass near its edge, like the thick rim of a lens: the content next to the edge is pulled
 * in from outside the shape and squeezed into a narrow band. The three color channels are bent by slightly different
 * amounts, which leaves the faint rainbow fringe real glass has. [magnify] > 1 also zooms the whole inside, for the
 * selection lens of the tab bar.
 */
internal const val RefractionAgsl = """
uniform shader content;
uniform float2 layerSize;
uniform float2 origin;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float amount;
uniform float magnify;
uniform float dispersion;

float sdRoundRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 outwardNormal(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    if (q.x > 0.0 && q.y > 0.0) return normalize(q) * s;
    if (q.x > q.y) return float2(s.x, 0.0);
    return float2(0.0, s.y);
}

half4 sampleAt(float2 c) {
    return content.eval(clamp(c, float2(0.5), layerSize - 0.5));
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 center = origin + hs;
    float2 p = coord - center;
    float e = -sdRoundRect(p, hs, radius);
    float2 base = center + (coord - center) / magnify;
    float2 shift = float2(0.0);
    if (e < bevel && amount > 0.0) {
        float u = clamp(1.0 - e / bevel, 0.0, 1.0);
        float f = 1.0 - sqrt(1.0 - u * u);
        shift = outwardNormal(p, hs, radius) * (f * amount);
    }
    if (dispersion > 0.0) {
        half4 r = sampleAt(base + shift * (1.0 + dispersion));
        half4 g = sampleAt(base + shift);
        half4 b = sampleAt(base + shift * (1.0 - dispersion));
        return half4(r.r, g.g, b.b, g.a);
    }
    return sampleAt(base + shift);
}
"""

/**
 * The light on the edge of the glass: a thin bright line plus a soft glow just inside it, strongest on the edges that
 * face the light (top left) and weaker on the ones that face away. Output is white with premultiplied alpha.
 */
internal const val RimAgsl = """
uniform float2 size;
uniform float radius;
uniform float rimWidth;
uniform float glowWidth;
uniform float strength;
uniform float2 light;
uniform float pressed;

float sdRoundRect(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 outwardNormal(float2 p, float2 hs, float r) {
    float2 q = abs(p) - hs + r;
    float2 s = float2(p.x < 0.0 ? -1.0 : 1.0, p.y < 0.0 ? -1.0 : 1.0);
    if (q.x > 0.0 && q.y > 0.0) return normalize(q) * s;
    if (q.x > q.y) return float2(s.x, 0.0);
    return float2(0.0, s.y);
}

half4 main(float2 coord) {
    float2 hs = size * 0.5;
    float2 p = coord - hs;
    float e = -sdRoundRect(p, hs, radius);
    if (e < 0.0) return half4(0.0);
    float2 n = outwardNormal(p, hs, radius);
    float toward = max(dot(n, light), 0.0);
    float away = max(dot(n, -light), 0.0);
    float lit = pow(toward, 1.6) + 0.55 * pow(away, 1.6);
    float rim = 1.0 - smoothstep(0.0, rimWidth, e);
    float glow = pow(1.0 - smoothstep(0.0, glowWidth, e), 2.0);
    float v = rim * (0.22 + 0.78 * lit) + glow * (0.05 + 0.30 * lit) * (1.0 + pressed);
    v = clamp(v * strength, 0.0, 1.0);
    return half4(v, v, v, v);
}
"""

/** The compiled shaders, shared by every glass surface. `null` where the phone cannot run them. */
internal object GlassShaders {
    val refraction: RefractionShader? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RefractionShader() }.getOrNull() else null
    }

    val rim: RimShader? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RimShader() }.getOrNull() else null
    }
}

/**
 * One instance serves every surface: the uniforms are set right before each use, and a shader or effect keeps the
 * values it was created with, so surfaces drawn one after another do not disturb each other. Everything runs on the
 * main thread.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class RefractionShader {
    private val shader = RuntimeShader(RefractionAgsl)

    /**
     * A blur of [blur] px followed by the edge refraction, for a layer of [layerWidth] x [layerHeight] px in which
     * the glass of [width] x [height] px starts at [originX], [originY].
     */
    fun effect(
        layerWidth: Float,
        layerHeight: Float,
        originX: Float,
        originY: Float,
        width: Float,
        height: Float,
        radius: Float,
        bevel: Float,
        amount: Float,
        magnify: Float,
        dispersion: Float,
        blur: Float,
    ): RenderEffect {
        shader.setFloatUniform("layerSize", layerWidth, layerHeight)
        shader.setFloatUniform("origin", originX, originY)
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("bevel", bevel)
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("magnify", magnify)
        shader.setFloatUniform("dispersion", dispersion)
        val refraction = PlatformRenderEffect.createRuntimeShaderEffect(shader, "content")
        val effect = if (blur > 0f) {
            PlatformRenderEffect.createChainEffect(
                refraction,
                PlatformRenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP),
            )
        } else {
            refraction
        }
        return effect.asComposeRenderEffect()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class RimShader {
    private val shader = RuntimeShader(RimAgsl)

    /** Paints the highlight; set it up with [update] right before drawing. */
    val brush: Brush = ShaderBrush(shader)

    fun update(width: Float, height: Float, radius: Float, rimWidth: Float, glowWidth: Float, strength: Float, pressed: Float) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("rimWidth", rimWidth)
        shader.setFloatUniform("glowWidth", glowWidth)
        shader.setFloatUniform("strength", strength)
        // The light comes from the top left.
        shader.setFloatUniform("light", -0.7071f, -0.7071f)
        shader.setFloatUniform("pressed", pressed)
    }
}

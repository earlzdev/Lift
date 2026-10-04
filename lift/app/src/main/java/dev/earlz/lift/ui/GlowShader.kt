package dev.earlz.lift.ui

import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * AGSL-шейдер: переливающийся ободок вокруг вырезанного объекта и волна света,
 * которая расходится от пальца по контуру.
 *
 * Где край объекта, шейдер узнаёт из [GlowField] — заранее размытой маски: около 0.5
 * на краю, к 1 внутри, к 0 снаружи. Одно чтение на пиксель вместо десятков.
 */
private const val GLOW_AGSL = """
uniform shader content;
uniform shader field;      // размытая маска объекта (GlowField), в координатах слоя
uniform float2 size;
uniform float2 origin;     // точка касания в координатах слоя
uniform float progress;    // 0..1 — насколько далеко ушла волна
uniform float rim;         // 0..1 — яркость постоянного ободка
uniform float radius;      // ширина свечения в пикселях
uniform float time;        // секунды — цвета ободка медленно бегут по кругу

const float TAU = 6.2831853;

// Перелив розовый → оранжевый → голубой → фиолетовый, по кругу
half3 iris(float t) {
    half3 c0 = half3(1.00, 0.36, 0.66);
    half3 c1 = half3(1.00, 0.62, 0.28);
    half3 c2 = half3(0.30, 0.86, 1.00);
    half3 c3 = half3(0.62, 0.42, 1.00);
    float s = fract(t) * 4.0;
    float f = smoothstep(0.0, 1.0, fract(s));
    if (s < 1.0) return mix(c0, c1, f);
    if (s < 2.0) return mix(c1, c2, f);
    if (s < 3.0) return mix(c2, c3, f);
    return mix(c3, c0, f);
}

half4 main(float2 p) {
    half4 c = content.eval(p);
    float a = c.a;

    // Снаружи у края поле ещё не погасло — там светится; внутри у края поле
    // уже меньше единицы — там блик
    float b = field.eval(p).a;
    float outer = clamp(b * 2.0, 0.0, 1.0) * (1.0 - a);
    float inner = clamp((1.0 - b) * 2.5, 0.0, 1.0) * a;

    // Цвет зависит от угла вокруг центра объекта и медленно вращается со временем
    float2 v = p - size * 0.5;
    float hue = atan(v.y, v.x) / TAU + time * 0.12;
    half3 rimColor = iris(hue);

    // Волна: светлое кольцо, расходящееся от пальца и гаснущее к концу
    // Фронт доходит ровно до самого дальнего угла слоя — так скорость волны
    // не зависит от того, где коснулись, и край она проходит заметно
    float dist = length(p - origin);
    float maxDist = max(max(length(origin), length(origin - float2(size.x, 0.0))),
                        max(length(origin - float2(0.0, size.y)), length(origin - size)));
    float front = progress * maxDist;
    float band = (dist - front) / (radius * 4.0);
    float wave = exp(-band * band) * (1.0 - progress) * step(0.001, progress);
    half3 waveColor = mix(iris(hue + 0.25), half3(1.0), 0.55);

    float rimGlow = clamp(outer * rim, 0.0, 1.0);
    float waveGlow = clamp(outer * wave * 2.2, 0.0, 1.0);
    float glow = clamp(rimGlow + waveGlow, 0.0, 1.0);
    half3 glowColor = (rimColor * rimGlow + waveColor * waveGlow) / max(glow, 0.001);

    float shine = clamp(inner * wave * 0.9 + inner * rim * 0.2, 0.0, 1.0);

    // Цвета в слое premultiplied: свечение кладём «под» объект, блик — поверх
    half3 rgb = c.rgb + mix(rimColor, waveColor, wave) * shine * a + glowColor * glow * (1.0 - a);
    float outA = a + glow * (1.0 - a);
    return half4(min(rgb, half3(outA)), outA);
}
"""

class GlowShader {
    private val shader = RuntimeShader(GLOW_AGSL)
    private var boundField: GlowField? = null

    /** Подключает поле свечения; поле в [GlowField.DOWNSCALE] раз меньше слоя — растягиваем матрицей. */
    fun setField(field: GlowField) {
        if (boundField === field) return
        boundField = field
        val bitmapShader = BitmapShader(field.bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
            setLocalMatrix(Matrix().apply {
                setScale(GlowField.DOWNSCALE.toFloat(), GlowField.DOWNSCALE.toFloat())
            })
        }
        shader.setInputShader("field", bitmapShader)
    }

    /** Новый RenderEffect на каждый кадр: эффект запоминает значения uniform-ов при создании. */
    fun renderEffect(
        width: Float,
        height: Float,
        origin: Offset,
        progress: Float,
        rim: Float,
        radius: Float,
        time: Float,
    ): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("origin", origin.x, origin.y)
        shader.setFloatUniform("progress", progress)
        shader.setFloatUniform("rim", rim)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("time", time)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}

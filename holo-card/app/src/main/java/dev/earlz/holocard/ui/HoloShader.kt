package dev.earlz.holocard.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL-шейдер голографической фольги: считает свет, который зависит от наклона телефона.
 * Рисуется прямоугольником поверх карты в режиме сложения (BlendMode.Plus) — свет просто
 * прибавляется к пикселям карты. Так не нужен отдельный буфер на каждый кадр, как с RenderEffect.
 *  - блик — круглое пятно света с ореолом, едет против наклона (как отражение лампы);
 *  - фольга — радужные кольца вокруг блика, сдвигаются при наклоне;
 *  - искорки — мелкие точки, которые вспыхивают на разных углах;
 *  - металл (для банковской карты) — радуга приглушена, остаётся в основном белый блик.
 */
private const val HOLO_AGSL = """
uniform float4 card;       // прямоугольник карты в слое: left, top, right, bottom
uniform float4 foilRect;   // выделенная область: картинка у коллекционной, голограмма у банковской
uniform float foilInside;  // сила фольги внутри foilRect, 0..1
uniform float foilOutside; // сила фольги снаружи foilRect, 0..1
uniform float metal;       // 0 — коллекционная карта, 1 — металл: радуга приглушена
uniform float2 tilt;       // наклон телефона, -1..1
uniform float time;        // секунды — лёгкое «дыхание» искорок

const float TAU = 6.2831853;

// Радуга: плавно по кругу через все цвета
half3 spectrum(float t) {
    return half3(0.5) + half3(0.5) * cos(TAU * (t + half3(0.0, 0.33, 0.67)));
}

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

bool inside(float2 p, float4 r) {
    return p.x >= r.x && p.x <= r.z && p.y >= r.y && p.y <= r.w;
}

half4 main(float2 p) {
    if (!inside(p, card)) return half4(0.0);

    float2 size = card.zw - card.xy;
    float2 uv = (p - card.xy) / size;            // 0..1 по карте

    // Блик: отражение одной «лампы» — круглое яркое пятно с мягким ореолом, едет против наклона
    float2 glareCenter = float2(0.5, 0.4) - tilt * float2(0.6, 0.5);
    float2 d = uv - glareCenter;
    d.x *= size.x / size.y;
    float r2 = dot(d, d);
    float glare = exp(-r2 * 5.0);       // большое мягкое пятно
    float hotspot = exp(-r2 * 22.0);    // чуть ярче в самом центре

    // Фольга: радужные кольца вокруг пятна, как свет лампы в голограмме; наклон их сдвигает
    float rings = sqrt(r2) * 3.5 + tilt.x * 0.6 - tilt.y * 0.4;
    half3 rainbow = spectrum(rings);
    float foil = inside(p, foilRect) ? foilInside : foilOutside;
    // Радуга чуть заметна везде, а возле пятна — сильно; на металле слабее
    float holo = foil * (0.15 + 0.85 * glare) * (0.35 + 0.65 * (1.0 - metal));

    // Искорки: редкие круглые точки только на фольге; каждая «смотрит» в свою сторону
    // и вспыхивает, только когда наклон совпал с её направлением
    float2 cell = floor(p / 10.0);
    float rnd = hash(cell);
    float2 spot = (cell + 0.2 + 0.6 * float2(hash(cell + 7.1), hash(cell + 3.7))) * 10.0;
    float dot2 = smoothstep(1.8, 0.4, length(p - spot));
    float angle = rnd * TAU;
    float facing = dot(float2(cos(angle), sin(angle)), tilt) * 2.5 + sin(time * 1.5 + rnd * 20.0) * 0.2;
    float sparkle = step(0.96, rnd) * dot2 * smoothstep(0.7, 1.0, facing) * step(0.5, foil) * foil;

    half3 light = rainbow * holo * 0.6
                + half3(1.0) * (glare * 0.4 + hotspot * 0.12)
                + half3(1.0) * sparkle * 0.8;
    // Цвет premultiplied: яркость не может быть больше прозрачности, иначе её обрежет.
    // Карта под светом непрозрачная, так что прибавка к прозрачности ей не вредит
    light = min(light, half3(1.0));
    return half4(light, max(light.r, max(light.g, light.b)));
}
"""

class HoloShader {
    private val shader = RuntimeShader(HOLO_AGSL)

    /** Кисть со светом для текущего кадра: значения uniform-ов фиксируются при отрисовке. */
    fun brush(
        card: Rect,
        foilRect: Rect,
        foilInside: Float,
        foilOutside: Float,
        metal: Float,
        tilt: Offset,
        time: Float,
    ): ShaderBrush {
        shader.setFloatUniform("card", card.left, card.top, card.right, card.bottom)
        shader.setFloatUniform("foilRect", foilRect.left, foilRect.top, foilRect.right, foilRect.bottom)
        shader.setFloatUniform("foilInside", foilInside)
        shader.setFloatUniform("foilOutside", foilOutside)
        shader.setFloatUniform("metal", metal)
        shader.setFloatUniform("tilt", tilt.x, tilt.y)
        shader.setFloatUniform("time", time)
        return ShaderBrush(shader)
    }
}

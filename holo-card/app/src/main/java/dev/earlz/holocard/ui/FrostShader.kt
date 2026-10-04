package dev.earlz.holocard.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL-иней для замороженной карты. Рисуется поверх карты обычным наложением.
 *
 * Лёд нарастает от краёв к центру неровным фронтом (фронт «размыт» шумом — так он похож
 * на настоящий иней на стекле), внутри — морозные прожилки и редкие блёстки.
 */
private const val FROST_AGSL = """
uniform float4 card;     // прямоугольник карты: left, top, right, bottom
uniform float amount;    // 0 — льда нет, 1 — карта заморожена целиком

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
}

// Плавный шум: случайные значения в узлах сетки, между ними — гладкая интерполяция
float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1, 0)), u.x),
               mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), u.x), u.y);
}

// Шум из нескольких «октав» — крупные пятна плюс мелкие детали
float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 p) {
    if (amount <= 0.001) return half4(0.0);
    float2 size = card.zw - card.xy;
    float2 uv = (p - card.xy) / size;

    // Расстояние до ближайшего края: 0 у края, 1 в центре
    float2 e = min(uv, 1.0 - uv) * 2.0;
    float edge = min(e.x, e.y * size.y / size.x * 1.6);

    float2 q = uv * float2(size.x / size.y, 1.0) * 5.0;
    float n = fbm(q);

    // Фронт льда: идёт от краёв к центру, неровный из-за шума
    float grow = amount * 1.35;
    float coverage = 1.0 - smoothstep(grow - 0.12, grow, edge + n * 0.45);

    // Морозные прожилки: тонкие светлые линии вдоль изгибов шума
    float veins = smoothstep(0.92, 1.0, abs(sin(fbm(q * 2.3 + 4.0) * 28.0)));
    // Блёстки льда
    float glint = step(0.985, hash(floor(p / 3.0))) * 0.8;

    float alpha = coverage * (0.5 + 0.3 * n + 0.35 * veins + glint);
    alpha = clamp(alpha, 0.0, 0.95);
    half3 ice = mix(half3(0.80, 0.90, 1.0), half3(1.0), veins * 0.7 + glint);
    // premultiplied: цвет умножен на прозрачность
    return half4(ice * alpha, alpha);
}
"""

class FrostShader {
    private val shader = RuntimeShader(FROST_AGSL)

    fun brush(card: Rect, amount: Float): ShaderBrush {
        shader.setFloatUniform("card", card.left, card.top, card.right, card.bottom)
        shader.setFloatUniform("amount", amount)
        return ShaderBrush(shader)
    }
}

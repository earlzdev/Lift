package dev.earlz.holocard.model

import androidx.compose.ui.graphics.Color

/** Узор на металле — у каждой карты свой. */
enum class CardPattern { Brushed, Waves, Guilloche }

/** Форма голограммы на лицевой стороне. */
enum class HoloShape { Square, Circle, Stripe }

/** Как выглядит карта: металл, узор, голограмма, реквизиты. Банк вымышленный. */
data class BankCard(
    val id: String,
    val title: String,
    /** Надпись на карте. */
    val brand: String,
    val pattern: CardPattern,
    val hologram: HoloShape,
    /** Градиент металла: от светлого к тёмному и обратно — так металл «играет». */
    val metal: List<Color>,
    /** Цвет надписей и значков на карте. */
    val ink: Color,
    val number: String,
    val holder: String,
    val expiry: String,
    val cvv: String,
    /** Баланс в центах, чтобы не возиться с дробями. */
    val balanceCents: Long,
)

object DemoBank {
    val cards = listOf(
        BankCard(
            id = "graphite",
            title = "Graphite",
            brand = "COMPOSE BLACK",
            pattern = CardPattern.Brushed,
            hologram = HoloShape.Square,
            metal = listOf(Color(0xFF3A3D44), Color(0xFF16171B), Color(0xFF2E3138), Color(0xFF101114)),
            ink = Color.White,
            number = "4276 1408 2025 0310",
            holder = "EARL DEVELOPER",
            expiry = "12/30",
            cvv = "703",
            balanceCents = 18_425_050,
        ),
        BankCard(
            id = "aurora",
            title = "Aurora",
            brand = "AURORA",
            pattern = CardPattern.Waves,
            hologram = HoloShape.Circle,
            metal = listOf(Color(0xFF2B2F7A), Color(0xFF0E1236), Color(0xFF155A6B), Color(0xFF0B0F2A)),
            ink = Color.White,
            number = "5536 9137 4402 1984",
            holder = "EARL DEVELOPER",
            expiry = "08/29",
            cvv = "512",
            balanceCents = 5_290_000,
        ),
        BankCard(
            id = "champagne",
            title = "Champagne",
            brand = "PRIVATE",
            pattern = CardPattern.Guilloche,
            hologram = HoloShape.Stripe,
            metal = listOf(Color(0xFFE8D9B5), Color(0xFFB59A62), Color(0xFFF1E6C8), Color(0xFF9C8250)),
            ink = Color(0xFF2B2416),
            number = "2200 7001 0042 7777",
            holder = "EARL DEVELOPER",
            expiry = "03/31",
            cvv = "248",
            balanceCents = 125_000_000,
        ),
    )
}

/** «$184,250.50» — доллары с запятыми между тысячами и центами после точки. */
fun formatUsd(cents: Long): String {
    val dollars = (cents / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return "$" + dollars + "." + (cents % 100).toString().padStart(2, '0')
}

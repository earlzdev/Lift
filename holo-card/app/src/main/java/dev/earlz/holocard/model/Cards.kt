package dev.earlz.holocard.model

import androidx.compose.ui.graphics.Color

/** Как выглядит карта: металл, цвет надписей, реквизиты. Банк вымышленный. */
data class BankCard(
    val id: String,
    val title: String,
    /** Градиент металла: от светлого к тёмному и обратно — так металл «играет». */
    val metal: List<Color>,
    /** Цвет надписей и значков на карте. */
    val ink: Color,
    val number: String,
    val holder: String,
    val expiry: String,
    val cvv: String,
    val balance: Long,
)

data class Transaction(val title: String, val subtitle: String, val amount: Long, val emoji: String)

object DemoBank {
    val cards = listOf(
        BankCard(
            id = "graphite",
            title = "Графит",
            metal = listOf(Color(0xFF3A3D44), Color(0xFF16171B), Color(0xFF2E3138), Color(0xFF101114)),
            ink = Color.White,
            number = "4276 1408 2025 0310",
            holder = "EARL DEVELOPER",
            expiry = "12/30",
            cvv = "703",
            balance = 184_250,
        ),
        BankCard(
            id = "aurora",
            title = "Полярное сияние",
            metal = listOf(Color(0xFF2B2F7A), Color(0xFF0E1236), Color(0xFF155A6B), Color(0xFF0B0F2A)),
            ink = Color.White,
            number = "5536 9137 4402 1984",
            holder = "EARL DEVELOPER",
            expiry = "08/29",
            cvv = "512",
            balance = 52_900,
        ),
        BankCard(
            id = "champagne",
            title = "Шампанское",
            metal = listOf(Color(0xFFE8D9B5), Color(0xFFB59A62), Color(0xFFF1E6C8), Color(0xFF9C8250)),
            ink = Color(0xFF2B2416),
            number = "2200 7001 0042 7777",
            holder = "EARL DEVELOPER",
            expiry = "03/31",
            cvv = "248",
            balance = 1_250_000,
        ),
    )

    val transactions = listOf(
        Transaction("Кофейня «Рекомпозиция»", "Сегодня, 09:12", -340, "☕"),
        Transaction("Зарплата", "Вчера", 240_000, "💼"),
        Transaction("Google Play", "Вчера", -2_190, "🎮"),
        Transaction("Такси", "2 октября", -684, "🚕"),
        Transaction("Перевод от Ани", "1 октября", 1_500, "💸"),
        Transaction("Продукты", "30 сентября", -3_427, "🛒"),
        Transaction("Кинотеатр", "29 сентября", -1_100, "🎬"),
    )
}

/** «184 250 ₽» — с пробелами между тысячами, как пишут суммы в банках. */
fun formatRub(value: Long, sign: Boolean = false): String {
    val digits = kotlin.math.abs(value).toString().reversed().chunked(3).joinToString(" ").reversed()
    val prefix = when {
        !sign -> ""
        value > 0 -> "+"
        value < 0 -> "−"
        else -> ""
    }
    return "$prefix$digits ₽"
}

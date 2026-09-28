/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.screens.wrapped

data class MessagePair(val range: LongRange, val tease: String, val reveal: String)

object WrappedRepository {
    private val messages = listOf(
        MessagePair(0L..999L, "Semoga Anda tidak kecewa...", "Hanya **%d menit**. Baru pemanasan ya?"),
        MessagePair(0L..999L, "Baru coba-coba, ya?", "**%d menit** hanyalah celupan singkat di samudra musik."),
        MessagePair(0L..999L, "Jadwal padat bulan ini?", "**%d menit** singkat, manis, dan pas."),
        MessagePair(0L..999L, "Kata orang diam itu emas...", "Tapi Anda memilih **%d menit** nada musik."),

        MessagePair(1000L..4999L, "Sepertinya Anda baru menemukan NanzBeatles...", "Dan Anda mendedikasikan **%d menit** untuk alunan lagu."),
        MessagePair(1000L..4999L, "Anda punya kesibukan lain di luar musik.", "**%d menit** adalah keseimbangan yang sehat. Luar biasa."),
        MessagePair(1000L..4999L, "Tidak terlalu sepi, tidak terlalu ramai.", "Suasana yang pas selama **%d menit**."),
        MessagePair(1000L..4999L, "Persinggahan santai dalam hari-hari Anda.", "Terima kasih telah mampir selama **%d menit**."),

        MessagePair(5000L..14999L, "Musik benar-benar bagian dari hidup Anda.", "**%d menit** adalah lagu tema yang solid untuk perjalanan Anda."),
        MessagePair(5000L..14999L, "Kami sering melihat Anda di sini.", "Selalu menciptakan suasana terbaik selama **%d menit**."),
        MessagePair(5000L..14999L, "Perjalanan Anda pasti menyenangkan.", "**%d menit** penuh dengan melodi indah."),
        MessagePair(5000L..14999L, "Konsisten. Andal. Berirama.", "Anda tahu apa yang Anda suka, selama **%d menit**."),

        MessagePair(15000L..39999L, "Apakah Anda pernah melepas headphone?", "**%d menit** membuktikan musik adalah oksigen Anda."),
        MessagePair(15000L..39999L, "Baterai ponsel Anda memohon istirahat.", "Tapi telinga Anda sangat menikmati **%d menit** ini."),
        MessagePair(15000L..39999L, "Energi Pemeran Utama terpancar!", "Hidup Anda terasa seperti film selama **%d menit**."),
        MessagePair(15000L..39999L, "Berjalan, bekerja, hingga tidur...", "Selalu ada lagu yang berputar selama **%d menit** ini."),

        MessagePair(40000L..Long.MAX_VALUE, "Apakah Anda... baik-baik saja?", "Anda benar-benar hidup di aplikasi ini selama **%d menit**."),
        MessagePair(40000L..Long.MAX_VALUE, "Kami agak khawatir dengan telinga Anda.", "Perilaku 1% teratas. **%d menit** ini benar-benar legendaris."),
        MessagePair(40000L..Long.MAX_VALUE, "Keheningan terasa menakutkan, kan?", "Dinding alunan suara tiada henti selama **%d menit**."),
        MessagePair(40000L..Long.MAX_VALUE, "Penguji Ketahanan Sejati.", "Anda membuat pemutar musik bekerja lembur selama **%d menit**.")
    )

    fun getMessage(minutes: Long): MessagePair {
        val possibleMessages = messages.filter { minutes in it.range }
        val chosenMessage = if (possibleMessages.isNotEmpty()) {
            possibleMessages.random()
        } else {
            // Fallback for safety
            MessagePair(0L..Long.MAX_VALUE, "Sepertinya hitungan terlewat!", "Tapi Anda jelas mendengarkan **%d menit** musik.")
        }
        return chosenMessage.copy(
            reveal = chosenMessage.reveal.format(minutes)
        )
    }
}

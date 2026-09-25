package com.oguz.ezanvakti

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Diyanet'in verdiği 6 vakit. Uyarı ve ekranda Güneş hariç 5'i kullanılır. */
enum class Vakit(val ad: String, val yonelme: String) {
    IMSAK("İmsak", "İmsak'a"),
    GUNES("Güneş", "Güneş'e"),
    OGLE("Öğle", "Öğle'ye"),
    IKINDI("İkindi", "İkindi'ye"),
    AKSAM("Akşam", "Akşam'a"),
    YATSI("Yatsı", "Yatsı'ya");

    companion object {
        /** Uyarı çalan ve ekranda görünen 5 vakit. */
        val BES = listOf(IMSAK, OGLE, IKINDI, AKSAM, YATSI)
    }
}

/**
 * Bir günün vakitleri. [dakika] dizisi Vakit.ordinal sırasıyla,
 * gece yarısından itibaren dakika cinsinden (ör. 05:07 → 307).
 */
data class GunVakitleri(
    val yil: Int,
    val ay: Int,   // 1..12
    val gun: Int,
    val dakika: IntArray,
    val kaynak: String
) {
    val anahtar: String get() = tarihAnahtari(yil, ay, gun)

    fun saat(v: Vakit): String = dakikaYazi(dakika[v.ordinal])

    /** Vaktin epoch milisaniyesi (Türkiye saatiyle). */
    fun millis(v: Vakit): Long {
        val c = Zaman.takvim()
        c.clear()
        c.set(yil, ay - 1, gun, 0, 0, 0)
        return c.timeInMillis + dakika[v.ordinal] * 60_000L
    }

    /** Vakitler sıralı ve makul aralıkta mı? Hatalı API verisini elemek için. */
    fun gecerliMi(): Boolean {
        if (dakika.size != 6) return false
        for (i in 1 until 6) if (dakika[i] <= dakika[i - 1]) return false
        return dakika[0] in 120..480 && dakika[5] in 1020..1439
    }
}

fun tarihAnahtari(y: Int, m: Int, d: Int) =
    String.format(Locale.US, "%04d-%02d-%02d", y, m, d)

fun dakikaYazi(dk: Int) = String.format(Locale.US, "%02d:%02d", dk / 60, dk % 60)

/** "05:07" → 307, hatalıysa -1 */
fun saatCoz(s: String?): Int {
    if (s == null) return -1
    val p = s.trim().split(":")
    if (p.size < 2) return -1
    val h = p[0].trim().toIntOrNull() ?: return -1
    val m = p[1].trim().takeWhile { it.isDigit() }.toIntOrNull() ?: return -1
    if (h !in 0..23 || m !in 0..59) return -1
    return h * 60 + m
}

object Zaman {
    /** Uygulama sadece Ankara için: telefonun saat dilimi yanlış olsa da Türkiye saati kullanılır. */
    val TR: TimeZone = TimeZone.getTimeZone("Europe/Istanbul")

    fun takvim(): Calendar = Calendar.getInstance(TR, Locale("tr", "TR"))

    fun takvim(millis: Long): Calendar = takvim().apply { timeInMillis = millis }

    fun anahtar(c: Calendar) =
        tarihAnahtari(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))

    /** "1 sa 12 dk" / "12 dk" / "1 dk'dan az" */
    fun kalanYazi(ms: Long): String {
        val toplamDk = ((ms + 59_999) / 60_000).toInt()   // yukarı yuvarla
        if (ms < 60_000) return "1 dk'dan az"
        val sa = toplamDk / 60
        val dk = toplamDk % 60
        return when {
            sa == 0 -> "$dk dk"
            dk == 0 -> "$sa sa"
            else -> "$sa sa $dk dk"
        }
    }

    private val AYLAR = arrayOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"
    )
    private val GUNLER = arrayOf("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi")

    fun tarihYazi(c: Calendar): String =
        "${c.get(Calendar.DAY_OF_MONTH)} ${AYLAR[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}, " +
            GUNLER[c.get(Calendar.DAY_OF_WEEK) - 1]

    fun kisaTarih(anahtar: String): String {
        val p = anahtar.split("-")
        if (p.size != 3) return anahtar
        return "${p[2].toInt()} ${AYLAR[p[1].toInt() - 1]}"
    }
}

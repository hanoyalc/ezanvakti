package com.oguz.ezanvakti

import android.content.Context

/** Uygulamanın tüm ayarları: sadece aç/kapat ve ses seçimi (+ iç kayıtlar). */
class Ayarlar(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("ayarlar", Context.MODE_PRIVATE)

    var acik: Boolean
        get() = sp.getBoolean("acik", true)
        set(v) = sp.edit().putBoolean("acik", v).apply()

    var ses: String
        get() = sp.getString("ses", Sesler.LISTE.first().kimlik) ?: Sesler.LISTE.first().kimlik
        set(v) = sp.edit().putString("ses", v).apply()

    /** İç kullanım: son internet denemesi zamanı */
    var sonDeneme: Long
        get() = sp.getLong("sonDeneme", 0L)
        set(v) = sp.edit().putLong("sonDeneme", v).apply()
}

/** Uygulama içindeki hazır sesler (res/raw). */
object Sesler {
    data class Ses(val kimlik: String, val ad: String)

    val LISTE = listOf(
        Ses("zil", "Zil"),
        Ses("can", "Çan"),
        Ses("melodi", "Melodi"),
        Ses("bip", "Bip bip")
    )

    /** Sabah vakti için çalınacak ezan dosyası: res/raw/sabah_ezani.(mp3|ogg) */
    const val SABAH_EZANI = "sabah_ezani"

    fun kaynakId(ctx: Context, ad: String): Int =
        ctx.resources.getIdentifier(ad, "raw", ctx.packageName)
}

package com.oguz.ezanvakti

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vakit verisini internetten çeker. Sıra:
 *  1) Diyanet resmi API (awqatsalah) — hesap bilgisi derlemede verildiyse
 *  2) Aracı API (ezanvakti.emushaf.net) — Diyanet verisini aynen yayınlar
 * İkisi de olmazsa boş liste döner; depo hesaplamaya düşer.
 */
object VakitIndirici {
    private const val TAG = "VakitIndirici"

    /** Diyanet ilçe kodu: Ankara merkez */
    const val ANKARA_ILCE_ID = 9206

    const val KAYNAK_DIYANET = "diyanet"
    const val KAYNAK_ARACI = "araci"

    private const val DIYANET_KOK = "https://awqatsalah.diyanet.gov.tr"
    private const val ARACI_URL = "https://ezanvakti.emushaf.net/vakitler/$ANKARA_ILCE_ID"

    fun indir(): List<GunVakitleri> {
        if (BuildConfig.DIYANET_EMAIL.isNotBlank() && BuildConfig.DIYANET_PASSWORD.isNotBlank()) {
            try {
                val l = diyanet()
                if (l.isNotEmpty()) return l
            } catch (e: Exception) {
                Log.w(TAG, "Diyanet API başarısız", e)
            }
        }
        try {
            val l = araci()
            if (l.isNotEmpty()) return l
        } catch (e: Exception) {
            Log.w(TAG, "Aracı API başarısız", e)
        }
        return emptyList()
    }

    // ---------------- Diyanet resmi API ----------------

    private fun diyanet(): List<GunVakitleri> {
        val giris = JSONObject()
            .put("email", BuildConfig.DIYANET_EMAIL)
            .put("password", BuildConfig.DIYANET_PASSWORD)
        val girisCevap = JSONObject(istek("$DIYANET_KOK/Auth/Login", "POST", giris.toString(), null))
        val token = girisCevap.optJSONObject("data")?.optString("accessToken").orEmpty()
        if (token.isEmpty()) throw IllegalStateException("Diyanet token alınamadı")

        val cevap = JSONObject(
            istek("$DIYANET_KOK/api/PrayerTime/Monthly/$ANKARA_ILCE_ID", "GET", null, token)
        )
        val dizi = cevap.optJSONArray("data") ?: return emptyList()
        val sonuc = ArrayList<GunVakitleri>()
        for (i in 0 until dizi.length()) {
            val o = dizi.getJSONObject(i)
            val tarih = tarihCoz(o.optString("gregorianDateShortIso8601"))
                ?: tarihCoz(o.optString("gregorianDateShort"))
                ?: continue
            val dk = intArrayOf(
                saatCoz(o.optString("fajr")),
                saatCoz(o.optString("sunrise")),
                saatCoz(o.optString("dhuhr")),
                saatCoz(o.optString("asr")),
                saatCoz(o.optString("maghrib")),
                saatCoz(o.optString("isha"))
            )
            if (dk.any { it < 0 }) continue
            sonuc.add(GunVakitleri(tarih[0], tarih[1], tarih[2], dk, KAYNAK_DIYANET))
        }
        return sonuc
    }

    // ---------------- Aracı API ----------------

    private fun araci(): List<GunVakitleri> {
        val dizi = JSONArray(istek(ARACI_URL, "GET", null, null))
        val sonuc = ArrayList<GunVakitleri>()
        for (i in 0 until dizi.length()) {
            val o = dizi.getJSONObject(i)
            val tarih = tarihCoz(o.optString("MiladiTarihKisa"))
                ?: tarihCoz(o.optString("MiladiTarihKisaIso8601"))
                ?: continue
            val dk = intArrayOf(
                saatCoz(o.optString("Imsak")),
                saatCoz(o.optString("Gunes")),
                saatCoz(o.optString("Ogle")),
                saatCoz(o.optString("Ikindi")),
                saatCoz(o.optString("Aksam")),
                saatCoz(o.optString("Yatsi"))
            )
            if (dk.any { it < 0 }) continue
            sonuc.add(GunVakitleri(tarih[0], tarih[1], tarih[2], dk, KAYNAK_ARACI))
        }
        return sonuc
    }

    // ---------------- Yardımcılar ----------------

    /** "24.09.2026", "2026-09-24", "2026-09-24T00:00:00" → [2026, 9, 24] */
    private fun tarihCoz(s: String?): IntArray? {
        if (s.isNullOrBlank()) return null
        val t = s.trim()
        Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})""").find(t)?.let {
            val (g, a, y) = it.destructured
            return intArrayOf(y.toInt(), a.toInt(), g.toInt())
        }
        Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""").find(t)?.let {
            val (y, a, g) = it.destructured
            return intArrayOf(y.toInt(), a.toInt(), g.toInt())
        }
        return null
    }

    private fun istek(adres: String, yontem: String, govde: String?, token: String?): String {
        val c = URL(adres).openConnection() as HttpURLConnection
        try {
            c.requestMethod = yontem
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "EzanVakti-Android/1.0")
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            if (govde != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(govde.toByteArray(Charsets.UTF_8)) }
            }
            val kod = c.responseCode
            if (kod !in 200..299) throw IllegalStateException("HTTP $kod: $adres")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

package com.oguz.ezanvakti

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * İnternet yoksa ve kayıtlı veri bittiyse kullanılan yedek: astronomik hesap.
 * Diyanet yöntemi: İmsak 18°, Yatsı 17°, İkindi Şafii (gölge = 1 boy),
 * temkinler: Güneş -7, Öğle +5, İkindi +5, Akşam +8, Yatsı +1 dk.
 * (Resmi temkinler İkindi +4, Akşam +7; Eylül 2026 Diyanet tablosuyla
 *  karşılaştırınca öğleden sonraki vakitler ~1 dk erken çıktığı için +1 eklendi.)
 * Diyanet tablosundan genelde 0-2 dk sapar.
 */
object VakitHesap {
    // Ankara merkez (Kızılay civarı)
    private const val ENLEM = 39.9208
    private const val BOYLAM = 32.8541
    private const val SAAT_FARKI = 3.0

    private val TEMKIN = intArrayOf(0, -7, 5, 5, 8, 1)

    private fun dr(d: Double) = Math.toRadians(d)
    private fun rd(r: Double) = Math.toDegrees(r)
    private fun sabitle(a: Double, b: Double) = a - b * floor(a / b)

    private fun julyen(y0: Int, m0: Int, d: Int): Double {
        var y = y0
        var m = m0
        if (m <= 2) { y -= 1; m += 12 }
        val a = y / 100
        val b = 2 - a + a / 4
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** (deklinasyon, zaman denklemi) */
    private fun gunes(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = sabitle(357.529 + 0.98560028 * d, 360.0)
        val q = sabitle(280.459 + 0.98564736 * d, 360.0)
        val l = sabitle(q + 1.915 * sin(dr(g)) + 0.020 * sin(dr(2 * g)), 360.0)
        val e = 23.439 - 0.00000036 * d
        val ra = rd(atan2(cos(dr(e)) * sin(dr(l)), cos(dr(l)))) / 15.0
        val eqt = q / 15.0 - sabitle(ra, 24.0)
        val decl = rd(asin(sin(dr(e)) * sin(dr(l))))
        return Pair(decl, eqt)
    }

    fun hesapla(yil: Int, ay: Int, gun: Int): GunVakitleri {
        val jd = julyen(yil, ay, gun) - BOYLAM / (15.0 * 24.0)

        fun ogleSaati(t: Double) = sabitle(12.0 - gunes(jd + t).second, 24.0)

        fun aciZamani(aci: Double, t: Double, sabahtan: Boolean): Double {
            val decl = gunes(jd + t).first
            val ogle = ogleSaati(t)
            var x = (-sin(dr(aci)) - sin(dr(decl)) * sin(dr(ENLEM))) / (cos(dr(decl)) * cos(dr(ENLEM)))
            x = x.coerceIn(-1.0, 1.0)
            val fark = rd(acos(x)) / 15.0
            return if (sabahtan) ogle - fark else ogle + fark
        }

        fun ikindi(t: Double): Double {
            val decl = gunes(jd + t).first
            val aci = -rd(atan(1.0 / (1.0 + tan(dr(abs(ENLEM - decl))))))
            return aciZamani(aci, t, false)
        }

        var s = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val f = s.map { it / 24.0 }
            s = doubleArrayOf(
                aciZamani(18.0, f[0], true),
                aciZamani(0.833, f[1], true),
                ogleSaati(f[2]),
                ikindi(f[3]),
                aciZamani(0.833, f[4], false),
                aciZamani(17.0, f[5], false)
            )
        }
        val dk = IntArray(6) { i ->
            val saat = s[i] + SAAT_FARKI - BOYLAM / 15.0
            (saat * 60.0).roundToInt() + TEMKIN[i]
        }
        return GunVakitleri(yil, ay, gun, dk, KAYNAK_HESAP)
    }

    const val KAYNAK_HESAP = "hesap"
}

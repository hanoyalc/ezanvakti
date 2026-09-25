package com.oguz.ezanvakti

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * İki alarm kurar:
 *  - EZAN: sıradaki vakitte ses çalar (uyarılar açıksa). setAlarmClock ile kurulur;
 *    Doze'dan etkilenmeyen en güvenilir yöntem budur.
 *  - TIK: widget'ı her vakit geçişinde ve gece yarısında yeniler.
 * Her alarm çalınca bir sonrakini kurar (zincir).
 */
object Zamanlayici {
    private const val TAG = "Zamanlayici"
    private const val KOD_EZAN = 1
    private const val KOD_TIK = 2

    data class Siradaki(val vakit: Vakit, val gun: GunVakitleri, val millis: Long)

    /** Şu andan sonraki ilk vakit (bugün ya da yarın). */
    fun siradaki(ctx: Context, simdi: Long = System.currentTimeMillis()): Siradaki {
        val c = Zaman.takvim(simdi)
        repeat(3) {
            val g = VakitDeposu.gun(ctx, c)
            for (v in Vakit.BES) {
                val t = g.millis(v)
                if (t > simdi) return Siradaki(v, g, t)
            }
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        // Buraya düşmemeli
        val g = VakitDeposu.gun(ctx, c)
        return Siradaki(Vakit.IMSAK, g, g.millis(Vakit.IMSAK))
    }

    private fun sonrakiGeceYarisi(simdi: Long): Long {
        val c = Zaman.takvim(simdi)
        c.add(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 5)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun kesinAlarmIzniVar(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(AlarmManager::class.java)
        return am.canScheduleExactAlarms()
    }

    private fun pi(ctx: Context, kod: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, kod, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Tüm alarmları yeniden kurar. Her durumda çağırmak güvenlidir. */
    fun kur(ctx: Context) {
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val simdi = System.currentTimeMillis()
        val s = siradaki(app, simdi)
        val kesin = kesinAlarmIzniVar(app)

        // ---- Ezan alarmı ----
        val ezanIntent = Intent(app, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.EYLEM_EZAN)
            .putExtra(AlarmReceiver.EK_VAKIT, s.vakit.ordinal)
            .putExtra(AlarmReceiver.EK_ZAMAN, s.millis)
        val ezanPi = pi(app, KOD_EZAN, ezanIntent)
        if (Ayarlar(app).acik) {
            try {
                if (kesin) {
                    val goster = PendingIntent.getActivity(
                        app, 0, Intent(app, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(s.millis, goster), ezanPi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.millis, ezanPi)
                }
                Log.i(TAG, "Ezan alarmı: ${s.vakit.ad} ${s.gun.anahtar} ${s.gun.saat(s.vakit)} (kesin=$kesin)")
            } catch (e: SecurityException) {
                Log.e(TAG, "Kesin alarm izni yok", e)
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, s.millis, ezanPi)
            }
        } else {
            am.cancel(ezanPi)
        }

        // ---- Widget tık alarmı ----
        val tikZamani = minOf(s.millis, sonrakiGeceYarisi(simdi))
        val tikPi = pi(app, KOD_TIK, Intent(app, AlarmReceiver::class.java).setAction(AlarmReceiver.EYLEM_TIK))
        try {
            if (kesin) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tikZamani, tikPi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tikZamani, tikPi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, tikZamani, tikPi)
        }
    }
}

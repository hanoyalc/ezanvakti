package com.oguz.ezanvakti

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * Ana ekran widget'ı: bugünün 5 vakti + sıradaki vakit ve geri sayım.
 * Geri sayım Chronometer ile yapılır (launcher kendisi sayar, pil harcamaz).
 * Vakit geçişlerinde Zamanlayici'nin TIK alarmı widget'ı yeniler.
 */
class VakitWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        guncelle(ctx, mgr, ids)
        // Periyodik güncelleme aynı zamanda alarm zincirinin koptuysa onarılmasını sağlar.
        Zamanlayici.kur(ctx)
    }

    companion object {
        private val SATIR = intArrayOf(R.id.wSatir0, R.id.wSatir1, R.id.wSatir2, R.id.wSatir3, R.id.wSatir4)
        private val AD = intArrayOf(R.id.wAd0, R.id.wAd1, R.id.wAd2, R.id.wAd3, R.id.wAd4)
        private val SAAT = intArrayOf(R.id.wSaat0, R.id.wSaat1, R.id.wSaat2, R.id.wSaat3, R.id.wSaat4)

        fun hepsiniGuncelle(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, VakitWidget::class.java))
            if (ids.isNotEmpty()) guncelle(ctx, mgr, ids)
        }

        private fun guncelle(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val simdi = System.currentTimeMillis()
            val bugun = VakitDeposu.bugun(ctx)
            val s = Zamanlayici.siradaki(ctx, simdi)
            val siradakiBugunMu = s.gun.anahtar == bugun.anahtar

            val rv = RemoteViews(ctx.packageName, R.layout.widget_vakit)

            Vakit.BES.forEachIndexed { i, v ->
                rv.setTextViewText(AD[i], v.ad)
                rv.setTextViewText(SAAT[i], bugun.saat(v))
                val vurgulu = siradakiBugunMu && v == s.vakit
                val renk = if (vurgulu) Color.BLACK else Color.WHITE
                rv.setTextColor(AD[i], renk)
                rv.setTextColor(SAAT[i], renk)
                rv.setInt(SATIR[i], "setBackgroundResource", if (vurgulu) R.drawable.satir_vurgu else 0)
            }

            rv.setTextViewText(R.id.wSiradaki, "${s.vakit.yonelme} kalan")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val taban = SystemClock.elapsedRealtime() + (s.millis - simdi)
                rv.setChronometer(R.id.wKalan, taban, null, true)
                rv.setChronometerCountDown(R.id.wKalan, true)
                rv.setViewVisibility(R.id.wKalan, View.VISIBLE)
                rv.setViewVisibility(R.id.wKalanYedek, View.GONE)
            } else {
                // Android 6: geri sayım desteklenmiyor, vakit saatini göster
                rv.setTextViewText(R.id.wSiradaki, s.vakit.ad)
                rv.setTextViewText(R.id.wKalanYedek, s.gun.saat(s.vakit))
                rv.setViewVisibility(R.id.wKalan, View.GONE)
                rv.setViewVisibility(R.id.wKalanYedek, View.VISIBLE)
            }

            val ac = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.wKok, ac)

            for (id in ids) mgr.updateAppWidget(id, rv)
        }
    }
}

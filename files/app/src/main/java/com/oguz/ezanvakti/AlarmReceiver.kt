package com.oguz.ezanvakti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EYLEM_EZAN = "com.oguz.ezanvakti.EZAN"
        const val EYLEM_TIK = "com.oguz.ezanvakti.TIK"
        const val EK_VAKIT = "vakit"
        const val EK_ZAMAN = "zaman"

        /** Alarm bu kadar geç gelirse (telefon kapalıydı vb.) ses çalınmaz. */
        private const val MAKS_GECIKME_MS = 20 * 60 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        if (intent.action == EYLEM_EZAN) {
            val vakitNo = intent.getIntExtra(EK_VAKIT, -1)
            val zaman = intent.getLongExtra(EK_ZAMAN, 0L)
            val gecikme = System.currentTimeMillis() - zaman
            if (Ayarlar(app).acik && vakitNo >= 0 && gecikme < MAKS_GECIKME_MS) {
                val s = Intent(app, EzanService::class.java)
                    .setAction(EzanService.EYLEM_CAL)
                    .putExtra(EK_VAKIT, vakitNo)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(s)
                    else app.startService(s)
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Servis başlatılamadı", e)
                }
            } else {
                Log.i("AlarmReceiver", "Ezan atlandı (gecikme=${gecikme / 1000}s)")
            }
        }

        // Bir sonraki alarmları kur, widget'ı yenile, gerekirse veriyi yenile.
        val bekle = goAsync()
        Thread {
            try {
                Zamanlayici.kur(app)
                VakitWidget.hepsiniGuncelle(app)
                if (VakitDeposu.gerekirseYenile(app)) {
                    Zamanlayici.kur(app)
                    VakitWidget.hepsiniGuncelle(app)
                }
            } catch (e: Exception) {
                Log.e("AlarmReceiver", "Hata", e)
            } finally {
                bekle.finish()
            }
        }.start()
    }
}

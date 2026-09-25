package com.oguz.ezanvakti

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Açılış, saat/tarih değişimi ve uygulama güncellemesinde alarmları yeniden kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("BootReceiver", "Alındı: ${intent.action}")
        val app = context.applicationContext
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
                Log.e("BootReceiver", "Hata", e)
            } finally {
                bekle.finish()
            }
        }.start()
    }
}

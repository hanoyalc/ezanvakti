package com.oguz.ezanvakti

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

/** Ses çalarken kilit ekranının üstünde açılan, tek büyük "DURDUR" butonlu ekran. */
class AlarmActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm)

        val vakit = EzanService.calanVakit
        findViewById<TextView>(R.id.tvAlarmVakit).text =
            if (vakit != null) "${vakit.ad} vakti" else "Ezan vakti"
        findViewById<TextView>(R.id.tvAlarmSaat).text =
            vakit?.let { VakitDeposu.bugun(this).saat(it) } ?: ""

        findViewById<Button>(R.id.btnDurdur).setOnClickListener {
            EzanService.durdur(this)
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        EzanService.bittiDinleyici = { runOnUiThread { finish() } }
        // Servis henüz başlamamış olabilir; kısa süre sonra hâlâ çalmıyorsa kapan.
        window.decorView.postDelayed({
            if (!EzanService.caliyor && !isFinishing) finish()
        }, 2000)
    }

    override fun onStop() {
        super.onStop()
        EzanService.bittiDinleyici = null
    }
}

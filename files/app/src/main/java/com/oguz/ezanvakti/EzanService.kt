package com.oguz.ezanvakti

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * Vakit sesini çalan ön plan servisi. Bildirimde ve tam ekran
 * AlarmActivity'de büyük "Durdur" butonu vardır.
 */
class EzanService : Service() {

    companion object {
        const val EYLEM_CAL = "com.oguz.ezanvakti.CAL"
        const val EYLEM_DURDUR = "com.oguz.ezanvakti.DURDUR"
        const val KANAL = "ezan_kanali"
        private const val BILDIRIM_NO = 42
        /** Güvenlik: ne olursa olsun bu süreden sonra susar. */
        private const val MAKS_SURE_MS = 7 * 60 * 1000L
        private const val TAG = "EzanService"

        @Volatile var caliyor: Boolean = false
            private set
        @Volatile var calanVakit: Vakit? = null
            private set

        /** AlarmActivity ses bitince kapanmak için bunu dinler. */
        var bittiDinleyici: (() -> Unit)? = null

        fun durdur(ctx: Context) {
            ctx.startService(Intent(ctx, EzanService::class.java).setAction(EYLEM_DURDUR))
        }

        fun kanalOlustur(ctx: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val k = NotificationChannel(KANAL, "Ezan vakti", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Vakit geldiğinde gösterilir"
                setSound(null, null)          // sesi uygulama kendisi çalar
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(k)
        }
    }

    private var oynatici: MediaPlayer? = null
    private var kalanTekrar = 0
    private val handler = Handler(Looper.getMainLooper())
    private val zamanAsimi = Runnable { bitir() }
    private var odakIstegi: AudioFocusRequest? = null
    private val odakDinleyici = AudioManager.OnAudioFocusChangeListener { degisim ->
        // Telefon çalarsa / arama gelirse sus
        if (degisim == AudioManager.AUDIOFOCUS_LOSS || degisim == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) bitir()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            EYLEM_CAL -> {
                val vakit = Vakit.entries.getOrNull(intent.getIntExtra(AlarmReceiver.EK_VAKIT, -1)) ?: Vakit.OGLE
                onPlanaGec(vakit)
                cal(vakit)
            }
            EYLEM_DURDUR -> bitir()
            else -> if (!caliyor) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun onPlanaGec(vakit: Vakit) {
        kanalOlustur(this)
        val tamEkran = PendingIntent.getActivity(
            this, 10,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val durdurPi = PendingIntent.getService(
            this, 11,
            Intent(this, EzanService::class.java).setAction(EYLEM_DURDUR),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        @Suppress("DEPRECATION")
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, KANAL)
        else Notification.Builder(this).setPriority(Notification.PRIORITY_MAX)

        val bildirim = b
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("${vakit.ad} vakti")
            .setContentText("Susturmak için dokunun")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setContentIntent(tamEkran)
            .setFullScreenIntent(tamEkran, true)
            .addAction(Notification.Action.Builder(null, "DURDUR", durdurPi).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(BILDIRIM_NO, bildirim, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(BILDIRIM_NO, bildirim)
        }
    }

    private fun cal(vakit: Vakit) {
        sesiBirak()
        caliyor = true
        calanVakit = vakit

        // Sabah: ezan dosyası varsa onu çal. Diğer vakitler (veya dosya yoksa): seçilen kısa ses.
        val ezanId = if (vakit == Vakit.IMSAK) Sesler.kaynakId(this, Sesler.SABAH_EZANI) else 0
        val sesId: Int
        if (ezanId != 0) {
            sesId = ezanId
            kalanTekrar = 0
        } else {
            sesId = Sesler.kaynakId(this, Ayarlar(this).ses).takeIf { it != 0 }
                ?: Sesler.kaynakId(this, Sesler.LISTE.first().kimlik)
            kalanTekrar = if (vakit == Vakit.IMSAK) 2 else 1   // toplam 3 / 2 kez
        }

        odakAl()
        try {
            oynatici = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setWakeMode(this@EzanService, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(this@EzanService, Uri.parse("android.resource://$packageName/$sesId"))
                setOnCompletionListener { mp ->
                    if (kalanTekrar > 0) {
                        kalanTekrar--
                        handler.postDelayed({ if (caliyor) mp.start() }, 800)
                    } else {
                        bitir()
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer hata $what/$extra")
                    bitir(); true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ses çalınamadı", e)
            bitir()
            return
        }
        handler.postDelayed(zamanAsimi, MAKS_SURE_MS)
    }

    @Suppress("DEPRECATION")
    private fun odakAl() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val r = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
                .setOnAudioFocusChangeListener(odakDinleyici)
                .build()
            odakIstegi = r
            am.requestAudioFocus(r)
        } else {
            am.requestAudioFocus(odakDinleyici, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    @Suppress("DEPRECATION")
    private fun odakBirak() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            odakIstegi?.let { am.abandonAudioFocusRequest(it) }
            odakIstegi = null
        } else {
            am.abandonAudioFocus(odakDinleyici)
        }
    }

    private fun sesiBirak() {
        handler.removeCallbacksAndMessages(null)
        oynatici?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        oynatici = null
    }

    private fun bitir() {
        sesiBirak()
        odakBirak()
        caliyor = false
        calanVakit = null
        bittiDinleyici?.invoke()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (caliyor) {
            sesiBirak()
            odakBirak()
            caliyor = false
            bittiDinleyici?.invoke()
        }
        super.onDestroy()
    }
}

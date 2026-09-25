package com.oguz.ezanvakti

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var tvTarih: TextView
    private lateinit var tvSiradaki: TextView
    private lateinit var kurulumKart: View
    private lateinit var tvKurulum: TextView
    private lateinit var btnKurulum: Button
    private lateinit var listVakitler: LinearLayout
    private lateinit var btnAcKapa: Button
    private lateinit var rgSes: RadioGroup
    private lateinit var btnDinle: Button
    private lateinit var tvSabahNot: TextView
    private lateinit var btnWidget: Button
    private lateinit var tvKaynak: TextView

    private lateinit var ayar: Ayarlar
    private val handler = Handler(Looper.getMainLooper())
    private var onizleme: MediaPlayer? = null
    private var sonGosterilenAnahtar = ""

    private val saatGuncelle = object : Runnable {
        override fun run() {
            ekraniYenile()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ayar = Ayarlar(this)
        EzanService.kanalOlustur(this)

        tvTarih = findViewById(R.id.tvTarih)
        tvSiradaki = findViewById(R.id.tvSiradaki)
        kurulumKart = findViewById(R.id.kurulumKart)
        tvKurulum = findViewById(R.id.tvKurulum)
        btnKurulum = findViewById(R.id.btnKurulum)
        listVakitler = findViewById(R.id.listVakitler)
        btnAcKapa = findViewById(R.id.btnAcKapa)
        rgSes = findViewById(R.id.rgSes)
        btnDinle = findViewById(R.id.btnDinle)
        tvSabahNot = findViewById(R.id.tvSabahNot)
        btnWidget = findViewById(R.id.btnWidget)
        tvKaynak = findViewById(R.id.tvKaynak)

        sesSecimiKur()

        btnAcKapa.setOnClickListener {
            ayar.acik = !ayar.acik
            acKapaGoster()
            Zamanlayici.kur(this)
        }

        btnDinle.setOnClickListener { onizlemeDegistir() }
        // Kurulumu yapan kişi için gizli test: uzun basınca gerçek alarm akışını (Durdur ekranı dahil) başlatır.
        btnDinle.setOnLongClickListener {
            onizlemeDurdur()
            val i = Intent(this, EzanService::class.java)
                .setAction(EzanService.EYLEM_CAL)
                .putExtra(AlarmReceiver.EK_VAKIT, Vakit.OGLE.ordinal)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
            startActivity(Intent(this, AlarmActivity::class.java))
            true
        }

        btnWidget.setOnClickListener { widgetEkle() }

        // Gizli: kaynak yazısına uzun basınca veriyi hemen yenilemeyi dener.
        tvKaynak.setOnLongClickListener {
            Toast.makeText(this, "Vakitler indiriliyor…", Toast.LENGTH_SHORT).show()
            veriYenile(zorla = true, sonucGoster = true)
            true
        }

        val sabahVar = Sesler.kaynakId(this, Sesler.SABAH_EZANI) != 0
        tvSabahNot.text = if (sabahVar) "Sabah vaktinde sabah ezanı okunur. Diğer vakitlerde seçtiğiniz ses çalar."
        else "Seçtiğiniz ses her vakitte çalar."
    }

    override fun onResume() {
        super.onResume()
        acKapaGoster()
        ekraniYenile()
        kurulumGoster()
        widgetButonGoster()
        handler.post(saatGuncelle)
        Zamanlayici.kur(this)
        VakitWidget.hepsiniGuncelle(this)
        veriYenile(zorla = false, sonucGoster = false)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(saatGuncelle)
        onizlemeDurdur()
    }

    // ---------------- Vakitler ----------------

    private fun ekraniYenile() {
        val simdi = System.currentTimeMillis()
        val c = Zaman.takvim(simdi)
        tvTarih.text = Zaman.tarihYazi(c)

        val bugun = VakitDeposu.bugun(this)
        val s = Zamanlayici.siradaki(this, simdi)
        tvSiradaki.text = "${s.vakit.yonelme} ${Zaman.kalanYazi(s.millis - simdi)}"

        val anahtar = bugun.anahtar + s.vakit.name + bugun.kaynak
        if (anahtar != sonGosterilenAnahtar) {
            sonGosterilenAnahtar = anahtar
            vakitListesiCiz(bugun, if (s.gun.anahtar == bugun.anahtar) s.vakit else null)
            tvKaynak.text = VakitDeposu.kaynakYazi(this)
        }
    }

    private fun vakitListesiCiz(g: GunVakitleri, vurgu: Vakit?) {
        listVakitler.removeAllViews()
        for (v in Vakit.BES) {
            val satir = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                if (v == vurgu) setBackgroundResource(R.drawable.satir_vurgu)
            }
            val ad = TextView(this).apply {
                text = v.ad
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                setTextColor(Color.BLACK)
                if (v == vurgu) setTypeface(typeface, Typeface.BOLD)
            }
            val saat = TextView(this).apply {
                text = g.saat(v)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
            }
            satir.addView(ad, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            satir.addView(saat)
            listVakitler.addView(satir)
        }
    }

    private fun veriYenile(zorla: Boolean, sonucGoster: Boolean) {
        val app = applicationContext
        Thread {
            val oldu = try { VakitDeposu.gerekirseYenile(app, zorla) } catch (e: Exception) { false }
            if (oldu) {
                Zamanlayici.kur(app)
                VakitWidget.hepsiniGuncelle(app)
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (oldu) {
                    sonGosterilenAnahtar = ""
                    ekraniYenile()
                }
                if (sonucGoster) {
                    Toast.makeText(
                        this,
                        if (oldu) "Vakitler güncellendi" else "İnternetten alınamadı",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // ---------------- Aç / Kapat ----------------

    private fun acKapaGoster() {
        if (ayar.acik) {
            btnAcKapa.text = "EZAN UYARISI AÇIK\n(kapatmak için dokunun)"
            btnAcKapa.setBackgroundResource(R.drawable.btn_yesil)
        } else {
            btnAcKapa.text = "EZAN UYARISI KAPALI\n(açmak için dokunun)"
            btnAcKapa.setBackgroundResource(R.drawable.btn_gri)
        }
    }

    // ---------------- Ses seçimi ----------------

    private fun sesSecimiKur() {
        rgSes.removeAllViews()
        for (s in Sesler.LISTE) {
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = s.ad
                tag = s.kimlik
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setPadding(dp(8), dp(14), dp(8), dp(14))
            }
            rgSes.addView(rb)
            if (s.kimlik == ayar.ses) rb.isChecked = true
        }
        rgSes.setOnCheckedChangeListener { grup, secili ->
            val rb = grup.findViewById<RadioButton>(secili) ?: return@setOnCheckedChangeListener
            ayar.ses = rb.tag as String
            if (onizleme != null) { onizlemeDurdur(); onizlemeDegistir() }
        }
    }

    private fun onizlemeDegistir() {
        if (onizleme != null) { onizlemeDurdur(); return }
        val id = Sesler.kaynakId(this, ayar.ses)
        if (id == 0) return
        try {
            onizleme = MediaPlayer().apply {
                // Alarm kanalından çalar: buradaki ses seviyesi gerçek uyarıyla aynıdır.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@MainActivity, Uri.parse("android.resource://$packageName/$id"))
                setOnCompletionListener { onizlemeDurdur() }
                prepare()
                start()
            }
            btnDinle.text = "SUSTUR"
        } catch (e: Exception) {
            onizlemeDurdur()
        }
    }

    private fun onizlemeDurdur() {
        onizleme?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        onizleme = null
        btnDinle.text = "DİNLE"
    }

    // ---------------- Widget ----------------

    private fun widgetButonGoster() {
        val mgr = AppWidgetManager.getInstance(this)
        val varMi = mgr.getAppWidgetIds(ComponentName(this, VakitWidget::class.java)).isNotEmpty()
        val destek = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mgr.isRequestPinAppWidgetSupported
        btnWidget.visibility = if (!varMi && destek) View.VISIBLE else View.GONE
    }

    private fun widgetEkle() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = AppWidgetManager.getInstance(this)
        if (mgr.isRequestPinAppWidgetSupported) {
            mgr.requestPinAppWidget(ComponentName(this, VakitWidget::class.java), null, null)
        }
    }

    // ---------------- Kurulum adımları ----------------

    private enum class Adim { SES, BILDIRIM, KESIN_ALARM, PIL, TAM_EKRAN }

    private fun eksikAdim(): Adim? {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) == 0) return Adim.SES

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Adim.BILDIRIM

        if (!Zamanlayici.kesinAlarmIzniVar(this)) return Adim.KESIN_ALARM

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) return Adim.PIL

        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) return Adim.TAM_EKRAN
        }
        return null
    }

    private fun kurulumGoster() {
        val adim = eksikAdim()
        if (adim == null) {
            kurulumKart.visibility = View.GONE
            return
        }
        kurulumKart.visibility = View.VISIBLE
        when (adim) {
            Adim.SES -> {
                tvKurulum.text = "Alarm sesi kapalı. Ezan duyulmaz."
                btnKurulum.text = "SESİ AÇ"
            }
            Adim.BILDIRIM -> {
                tvKurulum.text = "Vakit geldiğinde ekranda uyarı gösterebilmek için izin gerekiyor.\n\nAçılan pencerede \"İzin ver\"e basın."
                btnKurulum.text = "DEVAM"
            }
            Adim.KESIN_ALARM -> {
                tvKurulum.text = "Ezanın tam vaktinde okunması için izin gerekiyor.\n\nAçılan ekranda anahtarı açın, sonra geri dönün."
                btnKurulum.text = "DEVAM"
            }
            Adim.PIL -> {
                tvKurulum.text = "Telefonun pil tasarrufu ezanı engelleyebilir.\n\nAçılan pencerede \"İzin ver\"e basın."
                btnKurulum.text = "DEVAM"
            }
            Adim.TAM_EKRAN -> {
                tvKurulum.text = "Ezan okunurken büyük \"Durdur\" düğmesinin çıkması için izin gerekiyor.\n\nAçılan ekranda anahtarı açın, sonra geri dönün."
                btnKurulum.text = "DEVAM"
            }
        }
        btnKurulum.setOnClickListener { adimUygula(adim) }
    }

    @SuppressLint("BatteryLife")
    private fun adimUygula(adim: Adim) {
        try {
            when (adim) {
                Adim.SES -> {
                    val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maks = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    try {
                        am.setStreamVolume(
                            AudioManager.STREAM_ALARM, (maks * 0.8).toInt().coerceAtLeast(1),
                            AudioManager.FLAG_SHOW_UI
                        )
                    } catch (e: SecurityException) {
                        // Rahatsız Etmeyin açıkken izin verilmeyebilir: ses ayarlarını aç
                        startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
                    }
                    kurulumGoster()
                }
                Adim.BILDIRIM -> {
                    val sp = getSharedPreferences("kurulum", MODE_PRIVATE)
                    val dahaOnceSoruldu = sp.getBoolean("bildirimSoruldu", false)
                    if (Build.VERSION.SDK_INT >= 33 &&
                        (!dahaOnceSoruldu || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))
                    ) {
                        sp.edit().putBoolean("bildirimSoruldu", true).apply()
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    } else if (Build.VERSION.SDK_INT >= 26) {
                        // Kullanıcı kalıcı reddetmiş: ayar ekranına götür
                        startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        )
                    }
                }
                Adim.KESIN_ALARM -> if (Build.VERSION.SDK_INT >= 31) startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
                )
                Adim.PIL -> startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
                Adim.TAM_EKRAN -> if (Build.VERSION.SDK_INT >= 34) startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))
                )
            }
        } catch (e: Exception) {
            // Bazı üreticilerde bu ekranlar yok: uygulama bilgi ekranına düş
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        kurulumGoster()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

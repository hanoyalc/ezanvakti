package com.oguz.ezanvakti

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import kotlin.math.abs

/**
 * Vakitleri cihazda saklar. Bir gün için kayıtlı veri yoksa hesaplamaya düşer,
 * böylece uygulama hiçbir zaman "vakit yok" durumuna girmez.
 */
object VakitDeposu {
    private const val TAG = "VakitDeposu"
    private const val DOSYA = "vakitler.json"

    /** Kayıtlı gün sayısı bu değerin altına inince yenileme denenir. */
    private const val YENILEME_ESIGI_GUN = 7
    /** Başarısız denemeler arası en az bekleme. */
    private const val DENEME_ARALIGI_MS = 6 * 60 * 60 * 1000L
    /** API verisi hesaptan bu kadar dakikadan fazla saparsa şüpheli sayılır. */
    private const val MAKS_SAPMA_DK = 15

    private val kilit = Any()
    private var onbellek: MutableMap<String, GunVakitleri>? = null

    private fun dosya(ctx: Context) = File(ctx.filesDir, DOSYA)

    private fun yukle(ctx: Context): MutableMap<String, GunVakitleri> {
        onbellek?.let { return it }
        val map = HashMap<String, GunVakitleri>()
        try {
            val f = dosya(ctx)
            if (f.exists()) {
                val kok = JSONObject(f.readText())
                val gunler = kok.getJSONObject("gunler")
                val it = gunler.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val o = gunler.getJSONObject(k)
                    val arr = o.getJSONArray("dk")
                    val dk = IntArray(6) { i -> arr.getInt(i) }
                    val p = k.split("-")
                    map[k] = GunVakitleri(p[0].toInt(), p[1].toInt(), p[2].toInt(), dk, o.optString("kaynak", "?"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Kayıt okunamadı", e)
        }
        onbellek = map
        return map
    }

    private fun kaydet(ctx: Context, map: Map<String, GunVakitleri>) {
        val gunler = JSONObject()
        for ((k, g) in map) {
            val arr = JSONArray()
            g.dakika.forEach { arr.put(it) }
            gunler.put(k, JSONObject().put("dk", arr).put("kaynak", g.kaynak))
        }
        val kok = JSONObject().put("gunler", gunler)
        val tmp = File(ctx.filesDir, "$DOSYA.tmp")
        tmp.writeText(kok.toString())
        tmp.renameTo(dosya(ctx))
    }

    /** Verilen günün vakitleri: önce kayıt, yoksa hesap. */
    fun gun(ctx: Context, c: Calendar): GunVakitleri = synchronized(kilit) {
        val k = Zaman.anahtar(c)
        yukle(ctx)[k] ?: VakitHesap.hesapla(
            c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun bugun(ctx: Context): GunVakitleri = gun(ctx, Zaman.takvim())

    /** Bugünden itibaren art arda kaç günün kayıtlı verisi var ve sonuncusu hangi gün. */
    fun kayitDurumu(ctx: Context): Pair<Int, String?> = synchronized(kilit) {
        val map = yukle(ctx)
        val c = Zaman.takvim()
        var n = 0
        var son: String? = null
        while (true) {
            val k = Zaman.anahtar(c)
            if (!map.containsKey(k)) break
            son = k
            n++
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        Pair(n, son)
    }

    /**
     * Gerekirse internetten yeni veri çeker. Ağ işi yapar: ana thread'den çağırma.
     * @param zorla true ise eşik ve bekleme süresine bakmadan dener.
     * @return veri güncellendiyse true
     */
    fun gerekirseYenile(ctx: Context, zorla: Boolean = false): Boolean {
        val (gunSayisi, _) = kayitDurumu(ctx)
        val ayar = Ayarlar(ctx)
        val simdi = System.currentTimeMillis()
        if (!zorla) {
            if (gunSayisi >= YENILEME_ESIGI_GUN) return false
            if (simdi - ayar.sonDeneme < DENEME_ARALIGI_MS) return false
        }
        ayar.sonDeneme = simdi

        val yeni = VakitIndirici.indir()
        if (yeni.isEmpty()) {
            Log.i(TAG, "Hiçbir kaynaktan veri alınamadı, hesaplama kullanılmaya devam ediyor")
            return false
        }

        // Güvenlik: hesaptan çok sapan veya sırası bozuk günleri at.
        val temiz = yeni.filter { g ->
            if (!g.gecerliMi()) return@filter false
            val h = VakitHesap.hesapla(g.yil, g.ay, g.gun)
            (0 until 6).all { i -> abs(g.dakika[i] - h.dakika[i]) <= MAKS_SAPMA_DK }
        }
        if (temiz.isEmpty()) {
            Log.w(TAG, "İndirilen veri doğrulamadan geçmedi")
            return false
        }

        synchronized(kilit) {
            val map = yukle(ctx)
            temiz.forEach { map[it.anahtar] = it }
            // Dünden eski günleri sil
            val dun = Zaman.takvim().apply { add(Calendar.DAY_OF_MONTH, -1) }
            val sinir = Zaman.anahtar(dun)
            map.keys.filter { it < sinir }.forEach { map.remove(it) }
            kaydet(ctx, map)
        }
        Log.i(TAG, "${temiz.size} gün kaydedildi (${temiz.first().kaynak})")
        return true
    }

    /** Ekranda gösterilecek kısa kaynak açıklaması. */
    fun kaynakYazi(ctx: Context): String {
        val bugun = bugun(ctx)
        val (_, son) = kayitDurumu(ctx)
        return when (bugun.kaynak) {
            VakitIndirici.KAYNAK_DIYANET, VakitIndirici.KAYNAK_ARACI ->
                "Kaynak: Diyanet" + (son?.let { " (${Zaman.kisaTarih(it)} tarihine kadar kayıtlı)" } ?: "")
            else -> "Kaynak: hesaplama (internetten alınamadı, 1-2 dk fark olabilir)"
        }
    }
}

package com.suzgec.sender

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Demo Gönderici'nin KENDİ sahte gelen kutusu (emülatör "uygulamadan da sil" demosu, 25 Kasım).
 * Mesaj biçimli demo gönderilince buraya eklenir; bildirimindeki "Sil" eylemi yalnız bu listeden siler.
 * Başka hiçbir uygulamanın verisine dokunmaz.
 */
object FakeInbox {
    private const val PREFS = "sahte_mesajlar"
    private const val KEY = "liste"
    const val ACTION_DELETE = "com.suzgec.sender.SAHTE_SIL"
    const val EXTRA_UID = "uid"
    const val EXTRA_NOTIF_ID = "notifId"

    data class Item(val uid: Long, val id: String, val app: String, val title: String, val text: String, val time: Long)

    fun prefs(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Item> {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Item(o.getLong("uid"), o.getString("id"), o.getString("app"), o.getString("title"), o.getString("text"), o.getLong("time"))
        }
    }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("uid", it.uid).put("id", it.id).put("app", it.app).put("title", it.title).put("text", it.text).put("time", it.time))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    @Synchronized
    fun add(ctx: Context, d: Demo): Long {
        val now = System.currentTimeMillis()
        val uid = maxOf(now, (all(ctx).maxOfOrNull { it.uid } ?: 0L) + 1)
        save(ctx, all(ctx) + Item(uid, d.id, d.app, d.title, d.text, now))
        Log.i("SuzgecSender", "Sahte kutuya eklendi: ${d.id} (Sahte Mesajlar: ${all(ctx).size})")
        return uid
    }

    @Synchronized
    fun remove(ctx: Context, uid: Long): Item? {
        val items = all(ctx)
        val hit = items.firstOrNull { it.uid == uid } ?: return null
        save(ctx, items - hit)
        return hit
    }

    fun clear(ctx: Context) = save(ctx, emptyList())

    /** Mesaj biçimli demolar sahte kutuya girer (medya, navigasyon, ilerleme, kısa ömürlü ve grup özeti girmez). */
    fun accepts(d: Demo): Boolean = d.kind in setOf(Kind.PLAIN, Kind.ONE_TO_ONE, Kind.GROUP_CHAT, Kind.TELEGRAM_CHANNEL)
}

/** "Sil" eylemi: yalnız Demo Gönderici'nin kendi sahte kutusundan siler (dışa kapalı alıcı). */
class FakeDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FakeInbox.ACTION_DELETE) return
        val uid = intent.getLongExtra(FakeInbox.EXTRA_UID, -1)
        val removed = FakeInbox.remove(context, uid)
        val notifId = intent.getIntExtra(FakeInbox.EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) context.getSystemService(NotificationManager::class.java).cancel(notifId)
        if (removed != null) {
            Log.i("SuzgecSender", "Sahte kutudan silindi: ${removed.id} (Sahte Mesajlar: ${FakeInbox.all(context).size})")
        } else {
            Log.i("SuzgecSender", "Sahte kutuda yok (zaten silinmiş): uid=$uid")
        }
    }
}

package com.suzgec.app.delivery

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.suzgec.app.MainActivity
import com.suzgec.app.R
import com.suzgec.app.SuzgecApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

private const val TAG = "Suzgec"
private const val CHANNEL = "teslim_ozeti"
private const val SUMMARY_ID = 1
const val EXTRA_FROM_DELIVERY = "suzgec.fromDelivery"

/**
 * Günde 3 teslim (CLAUDE.md §1, §4.6): seçilen saatlerde sessiz bir özet bildirimi.
 * AlarmManager.setWindow(RTC_WAKEUP, t, 10 dk) → [DeliveryReceiver] → özet → bir sonrakini kur.
 */
object Delivery {

    const val WINDOW_MS = 10 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** [now]'dan sonraki ilk teslim zamanı (gün içi dakika listesinden). */
    fun nextTrigger(now: Long, minutes: List<Int>): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        for (dayOffset in 0..1) {
            for (m in minutes.sorted()) {
                val c = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (c.timeInMillis > now) return c.timeInMillis
            }
        }
        error("teslim saati yok")
    }

    /** [now]'dan önceki son teslim zamanı (kaçan teslim kontrolü için). */
    fun previousTrigger(now: Long, minutes: List<Int>): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        for (dayOffset in 0 downTo -1) {
            for (m in minutes.sortedDescending()) {
                val c = (cal.clone() as Calendar).apply {
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, m / 60); set(Calendar.MINUTE, m % 60)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                if (c.timeInMillis <= now) return c.timeInMillis
            }
        }
        error("teslim saati yok")
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, DeliveryReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Bir sonraki teslimi kur (varsa eskisinin yerine). */
    suspend fun schedule(context: Context) {
        val app = context.applicationContext as SuzgecApp
        val t = nextTrigger(System.currentTimeMillis(), app.settings.deliveryMinutesNow())
        scheduleAt(context, t)
    }

    fun scheduleAt(context: Context, t: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.setWindow(AlarmManager.RTC_WAKEUP, t, WINDOW_MS, pendingIntent(context))
        Log.i(TAG, "Sonraki teslim kuruldu: ${java.text.DateFormat.getDateTimeInstance().format(t)} (+10 dk pencere)")
    }

    /** Teslim bildiriminin metni (CLAUDE.md §1). */
    fun summaryText(normal: Int, onemsiz: Int): String =
        if (normal + onemsiz == 0) "Bildirim Kutunuz Temiz"
        else "Bildirimleriniz var! " + listOfNotNull(
            normal.takeIf { it > 0 }?.let { "$it Normal" },
            onemsiz.takeIf { it > 0 }?.let { "$it Önemsiz" },
        ).joinToString(", ")

    /** Özet bildirimini gönder, son teslim zamanını kaydet, bir sonrakini kur. */
    suspend fun deliver(context: Context) {
        val app = context.applicationContext as SuzgecApp
        val dao = app.database.notificationDao()
        val now = System.currentTimeMillis()
        dao.cleanOldDeleted(now) // Silinenler'de 7 günden eskiler kutudan çıkar
        // Yalnız kutuda duranlar sayılır: kullanıcı kutuyu temizlediyse "Bildirim Kutunuz Temiz".
        val text = summaryText(dao.countInBox("NORMAL"), dao.countInBox("ONEMSIZ"))
        postSummary(context, text)
        app.settings.setLastDelivery(System.currentTimeMillis())
        Log.i(TAG, "Teslim: $text")
        schedule(context)
    }

    /** Uygulama açılınca: son teslim saati geçmiş ama teslim yapılmamışsa şimdi yap; zamanlayıcıyı tazele. */
    suspend fun checkMissed(context: Context) {
        val app = context.applicationContext as SuzgecApp
        val now = System.currentTimeMillis()
        val last = app.settings.lastDelivery()
        if (last == 0L) {
            app.settings.setLastDelivery(now) // ilk kurulum: geçmiş teslimi telafi etme
        } else if (previousTrigger(now, app.settings.deliveryMinutesNow()) > last) {
            Log.i(TAG, "Kaçan teslim bulundu, şimdi gönderiliyor")
            deliver(context)
            return
        }
        schedule(context)
    }

    private fun postSummary(context: Context, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            // Varsayılan önem + ses/titreşim kapalı: sessiz ama panelde tam görünür.
            // (IMPORTANCE_LOW, Android 12'de "Sessiz" bölümünde metni gizli tek satıra küçülüyor.)
            nm.deleteNotificationChannel("teslim") // ilk sürümdeki düşük önemli kanal
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Teslim özeti", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Günde 3 kez, sessiz"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_FROM_DELIVERY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_suzgec)
            .setContentTitle("Süzgeç")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        try {
            nm.notify(SUMMARY_ID, n)
        } catch (e: SecurityException) {
            Log.w(TAG, "Bildirim izni yok, teslim özeti gösterilemedi", e)
        }
    }

    fun launch(block: suspend () -> Unit) = scope.launch { block() }
}

/** Teslim alarmı. */
class DeliveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Delivery.launch {
            try { Delivery.deliver(context) } finally { pending.finish() }
        }
    }
}

/** Yeniden başlatma, saat ya da saat dilimi değişince teslimi yeniden kur. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Teslim yeniden kuruluyor: ${intent.action}")
        val pending = goAsync()
        Delivery.launch {
            try { Delivery.schedule(context) } finally { pending.finish() }
        }
    }
}

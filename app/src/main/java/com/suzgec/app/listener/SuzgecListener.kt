package com.suzgec.app.listener

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.suzgec.app.BuildConfig
import com.suzgec.app.SuzgecApp
import com.suzgec.app.data.Dismiss
import com.suzgec.app.data.NotificationRecord
import com.suzgec.app.data.Upsert
import com.suzgec.app.sound.AlertThrottle
import com.suzgec.app.model.Personalization
import com.suzgec.app.rules.Decision
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.PreFilter
import com.suzgec.app.rules.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "Suzgec"

/** Özet güncellenince kontrol: 5 sn kuralındaki sohbet bildirimlerinin kararı bitsin. */
private const val SUMMARY_CHECK_AFTER_POST_MS = 6_000L

/** Bekleme bittikten sonra karar + uyarı için uyanık kalma payı. */
private const val WAKE_AFTER_WAIT_MS = 2_000L

/** Süzgeç bir sohbet bildirimini sildikten sonra kontrol. */
private const val SUMMARY_CHECK_AFTER_CANCEL_MS = 1_000L

/** `:sender` test uygulamasının uygulama adını taklit etmek için koyduğu extra (yalnız debug). */
const val EXTRA_FAKE_APP = "suzgec.debug.fakeApp"

/**
 * `:sender`'ın taklit ettiği paket adı (yalnız debug): kural katmanı paket listelerini (öncelikli uygulama,
 * alışveriş/sosyal/haber/e-posta, Telegram) bununla dener. Kayıttaki paket adı yine com.suzgec.sender kalır.
 */
const val EXTRA_FAKE_PKG = "suzgec.debug.fakePkg"

/**
 * Bildirimlerin hedef sayfaları. PendingIntent Room'a yazılamadığı için key'e göre
 * bellekte tutulur; süreç yeniden başlarsa boşalır ve uygulamanın ana ekranı açılır.
 */
object IntentCache {
    private val map = ConcurrentHashMap<String, PendingIntent>()
    fun put(key: String, pi: PendingIntent?) { if (pi != null) map[key] = pi else map.remove(key) }
    fun get(key: String): PendingIntent? = map[key]
    fun clear() = map.clear()
}

/**
 * 1. katman: bildirime erişim.
 * onNotificationPosted → ön süzgeç (dokunma / 5 sn bekle) → kural katmanı → karar uygula
 * (kayda yaz, Normal/Önemsiz/Silinenler ise panelden sil).
 */
class SuzgecListener : NotificationListenerService() {

    // Tüm durum (pending) ana iş parçacığında değişir; veritabanı işleri IO'da.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app get() = application as SuzgecApp
    private val dao by lazy { app.database.notificationDao() }
    private val engine by lazy { app.ruleEngine }

    /** 5 sn kuralı: bekleyen bildirimler (key → iş) ve bekleme bitiş zamanları. */
    private val pending = HashMap<String, Job>()
    private val deadlines = HashMap<String, Long>()

    /** Dokunulmaz olarak kaydedilmiş, hâlâ panelde duran bildirimler. */
    private val untouchedKeys = HashSet<String>()

    /** Bu süreçte ses çalınmış bildirimler (FLAG_ONLY_ALERT_ONCE için). */
    private val alertedKeys = HashSet<String>()

    /** Beklemeden (erken) karar verilen bildirimler: 5 sn içindeki güncellemesi ikinci kez uyarmaz. */
    private val earlyGate = EarlyGate()

    /** Aynı sohbetten 2 dakika içinde gelen yeni mesaj tekrar uyarmaz. */
    private val throttle = AlertThrottle()

    /** Grup anahtarı → Süzgeç'in bu gruptan son bildirim silme zamanı (altı boşalan özet için). */
    private val lastGroupCancel = HashMap<String, Long>()

    override fun onListenerConnected() {
        Log.i(TAG, "Dinleyici bağlandı")
        // Duraklatılmadıysa telefonu öncelik moduna al.
        app.appScope.launch { if (!app.paused.value) app.dnd.enable() }
        // Modeli önceden yükle; ilk bildirim beklemesin.
        scope.launch(Dispatchers.Default) { app.textModel; app.ruleSettings }
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        // Panelde zaten duranlar: daha önce işlenmediyse beklemeden işle.
        scope.launch {
            for (sbn in active) {
                if (sbn.packageName == packageName) continue
                val existing = withContext(Dispatchers.IO) { dao.findByKey(sbn.key) }
                if (existing != null && existing.postTime == sbn.postTime) continue
                IntentCache.put(sbn.key, sbn.notification.contentIntent)
                handle(sbn, waitedMs = 0, allowWait = false, alert = false)
            }
        }
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Dinleyici bağlantısı koptu, yeniden bağlanma isteniyor")
        // Süzgeç bildirimleri göremezken telefon sessiz kalmasın.
        app.appScope.launch { app.dnd.restore() }
        requestRebind(ComponentName(this, SuzgecListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // Süzgeç'in kendi bildirimleri
        IntentCache.put(sbn.key, sbn.notification.contentIntent)
        handle(sbn, waitedMs = 0, allowWait = true, alert = true)
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        if (sbn.packageName == packageName) return
        untouchedKeys.remove(sbn.key)
        val waiting = pending.remove(sbn.key)
        if (waiting != null) {
            waiting.cancel()
            deadlines.remove(sbn.key)
            Log.i(TAG, "5 sn içinde kalktı, yok sayıldı: ${sbn.key}")
            return
        }
        // Hedef sayfa önbellekte kalır: Süzgeç bildirimi panelden sildikten sonra da
        // kutudan açılabilmeli.
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.markRemoved(sbn.key, now, reason)
            // Kullanıcı Kritik/Önemli bildirimi telefonun panelinde açtı ya da kaydırdı: kutudan da çıkar.
            // Süzgeç'in kendi cancelNotification'ı (REASON_LISTENER_CANCEL) sayılmaz.
            if (Dismiss.isUserPanelRemoval(reason) && dao.dismissFromPanel(sbn.key, now) > 0) {
                Log.i(TAG, "Panelde kullanıcı kaldırdı (neden=$reason), kutudan da çıktı: ${sbn.key}")
            }
        }
        Log.d(TAG, "Kalktı: ${sbn.key} (neden=$reason)")
    }

    override fun onDestroy() {
        app.appScope.launch { app.dnd.restore() }
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(sbn: StatusBarNotification, waitedMs: Long, allowWait: Boolean, alert: Boolean) {
        // Kural katmanındaki bir hata servisi çökertmesin: bildirim olduğu gibi kalır.
        try {
            handleUnsafe(sbn, waitedMs, allowWait, alert)
        } catch (e: Exception) {
            Log.e(TAG, "Karar verilemedi, bildirime dokunulmadı: ${sbn.key}", e)
        }
    }

    private fun handleUnsafe(sbn: StatusBarNotification, waitedMs: Long, allowWait: Boolean, alert: Boolean) {
        if (app.paused.value) return // duraklatıldı: hiçbir bildirime dokunma
        val facts = extractFacts(this, sbn)
        if (facts.packageName.startsWith("org.telegram")) {
            Log.i(
                TAG, "Telegram işaretleri: kanal=${facts.channelId}, kısayol=${facts.shortcutId}, " +
                    "yanıt=${facts.hasReplyAction}, grup=${facts.isGroupConversation} | ${facts.title}",
            )
        }

        val pre = engine.preFilter(facts)
        if (pre !is PreFilter.Untouched) untouchedKeys.remove(sbn.key)
        when (pre) {
            is PreFilter.Untouched -> {
                cancelWait(sbn.key)
                // Grup özeti her güncellendiğinde: altındaki bildirimleri Süzgeç sildiyse özet de silinir.
                // Bekleme, 5 sn kuralındaki sohbet bildirimlerinin kararını beklemek için.
                if (facts.isGroupSummary) scheduleSummaryCheck(sbn.groupKey, SUMMARY_CHECK_AFTER_POST_MS)
                // İlerleme/müzik gibi sık güncellenen bildirimleri her seferinde yazma.
                if (!untouchedKeys.add(sbn.key)) return
                val d = Decision(Category.DOKUNMA, null, "Dokunulmaz: ${pre.reason}", "dokunma")
                apply(sbn, facts, d, waitedMs, alert = false)
            }
            is PreFilter.Wait -> if (allowWait) {
                // Güvenlik ve kumar beklemeden önce (25 Kasım): eşleşirse bugünkü kararın aynısı, beklemeden.
                // Eşleşmezse aşağıdaki bekleme aynen çalışır; bekleme sonunda classify bu kontrolleri yine yapar.
                val early = engine.earlyDecision(facts, app.ruleSettings.value)
                if (early != null) {
                    cancelWait(sbn.key)
                    // Aynı bildirim 5 sn içinde yeniden erken karar alırsa (güncelleme) ikinci kez uyarma.
                    val again = !earlyGate.firstWithin(sbn.key, SystemClock.elapsedRealtime(), pre.delayMs)
                    Log.i(TAG, "Beklemeden karar (${early.rule}): ${facts.appLabel} | ${facts.title}")
                    decide(sbn, facts, waitedMs = 0, alert = alert && !again, precomputed = early)
                    return
                }
                // Güncelleme gelirse bekleme yeniden başlamaz; ilk geliş zamanından sayılır.
                val now = SystemClock.elapsedRealtime()
                val deadline = deadlines.getOrPut(sbn.key) { now + pre.delayMs }
                pending.remove(sbn.key)?.cancel()
                Log.i(TAG, "Bekliyor (${pre.reason}): ${facts.appLabel} | ${facts.title}")
                // Ekran kapalıyken işlemci uyursa delay sayılmaz (24 Kasım: 5 sn bekleme 9–15 sn sürdü).
                // Bekleme + karar + uyarı süresince işlemci uyanık kalır; süre dolunca kilit kendiliğinden bırakılır.
                val remaining = (deadline - now).coerceAtLeast(0)
                val wake = waitWakeLock(remaining + WAKE_AFTER_WAIT_MS)
                pending[sbn.key] = scope.launch {
                    try {
                        delay(remaining)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        // Bildirim 5 sn içinde kalktı ya da güncellendi: kilidi hemen bırak.
                        wake?.let { if (it.isHeld) it.release() }
                        throw e
                    }
                    pending.remove(sbn.key)
                    val start = deadline - pre.delayMs
                    deadlines.remove(sbn.key)
                    handle(sbn, waitedMs = SystemClock.elapsedRealtime() - start, allowWait = false, alert = alert)
                }
            } else {
                decide(sbn, facts, waitedMs, alert)
            }
            PreFilter.Proceed -> {
                cancelWait(sbn.key)
                decide(sbn, facts, waitedMs, alert)
            }
        }
    }

    private fun scheduleSummaryCheck(groupKey: String, delayMs: Long) {
        if (lastGroupCancel[groupKey] == null) return
        scope.launch {
            delay(delayMs)
            try {
                cancelOrphanSummaries(groupKey)
            } catch (e: Exception) {
                Log.e(TAG, "Özet kontrolü yapılamadı: $groupKey", e)
            }
        }
    }

    /** Altı boşalan özeti sil (SummaryCleanup); kayda "özet silindi" yaz. */
    private suspend fun cancelOrphanSummaries(groupKey: String) {
        if (app.paused.value) return
        val active = (try { activeNotifications } catch (e: Exception) { null } ?: return)
            .filter { it.packageName != packageName }
            .map {
                SummaryCleanup.PanelItem(
                    it.key, it.groupKey, (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
                )
            }
        val keys = SummaryCleanup.summariesToCancel(active, groupKey, lastGroupCancel[groupKey], SystemClock.elapsedRealtime())
        for (key in keys) {
            Log.i(TAG, "Altı boşalan özet silindi (altındaki bildirimleri Süzgeç sildi): $key")
            withContext(Dispatchers.IO) {
                dao.setDecisionText(key, "Dokunulmaz: grup özeti → özet silindi (altındaki bildirimleri Süzgeç sildi)", "ozet_silindi")
            }
            cancelNotification(key)
        }
    }

    /** Bekleme için kısa, zaman aşımlı, sayaçsız uyanık kalma kilidi; alınamazsa null (bekleme yine çalışır). */
    private fun waitWakeLock(timeoutMs: Long): PowerManager.WakeLock? = try {
        (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Suzgec:bekleme")
            .apply { setReferenceCounted(false); acquire(timeoutMs) }
    } catch (e: Exception) {
        Log.w(TAG, "Uyanık kalma kilidi alınamadı", e)
        null
    }

    private fun cancelWait(key: String) {
        pending.remove(key)?.cancel()
        deadlines.remove(key)
    }

    /** Kural + model kararı arka planda (model ilk seferde yüklenir), uygulama ana iş parçacığında. */
    private fun decide(
        sbn: StatusBarNotification, facts: NotificationFacts, waitedMs: Long, alert: Boolean,
        precomputed: Decision? = null,
    ) {
        scope.launch {
            try {
                // Kullanıcı bu bildirimi elle taşıdıysa güncellemesi yeniden sınıflanmaz (24 Kasım LinkedIn).
                val existing = withContext(Dispatchers.IO) { dao.findByKey(sbn.key) }
                val kept = existing?.takeIf { Upsert.isUserMoved(it) }?.let { keptDecision(it) }
                val d = kept ?: precomputed ?: withContext(Dispatchers.Default) {
                    val t0 = SystemClock.elapsedRealtimeNanos()
                    engine.classify(facts, app.ruleSettings.value).also {
                        val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
                        Log.d(TAG, "Sınıflandırma süresi: %.2f ms".format(ms))
                    }
                }
                if (kept != null) Log.i(TAG, "Kullanıcının taşıdığı kayıt, kategori korunuyor: ${kept.category.label}")
                apply(sbn, facts, d, waitedMs, alert)
            } catch (e: Exception) {
                Log.e(TAG, "Karar verilemedi, bildirime dokunulmadı: ${sbn.key}", e)
            }
        }
    }

    /**
     * Kararı kayda yaz; Normal/Önemsiz/Silinenler ise yazdıktan sonra panelden sil.
     * [alert]: yeni gelen bildirim (yeniden bağlanmada okunan eski bildirim değil) → Kritik/Önemli sesi.
     */
    private fun apply(sbn: StatusBarNotification, facts: NotificationFacts, d: Decision, waitedMs: Long, alert: Boolean) {
        if (app.paused.value) return // karar verilirken duraklatıldıysa dokunma
        if (alert && (d.category == Category.KRITIK || d.category == Category.ONEMLI)) {
            // Güncellemelerde "yalnız bir kez uyar" bayrağına uy.
            val onlyOnce = (sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0
            if (alertedKeys.add(sbn.key) || !onlyOnce) {
                val conv = AlertThrottle.conversationKey(sbn.packageName, sbn.notification.shortcutId, sbn.key)
                val now = SystemClock.elapsedRealtime()
                if (throttle.allow(conv, now, force = d.category == Category.KRITIK)) {
                    app.alertPlayer.play(d.category)
                } else {
                    Log.i(TAG, "Uyarı yok: aynı sohbetten 2 dk içinde yeni mesaj ($conv)")
                }
            }
        }
        val remove = d.category.removeFromPanel
        val rec = toRecord(sbn, facts, d, waitedMs, remove)
        Log.i(
            TAG,
            "Karar: ${d.category.label} [${d.rule}] ${facts.appLabel} | ${facts.title} | ${facts.body}" +
                " (bekleme=${waitedMs} ms, panelden sil=$remove)"
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) { dao.upsertByKey(rec) }
            } catch (e: Exception) {
                Log.e(TAG, "Kayıt yazılamadı: ${sbn.key}", e)
            }
            // Kayıt yazıldıktan sonra sil: onNotificationRemoved kaydı bulabilsin.
            if (remove) {
                try {
                    cancelNotification(sbn.key)
                    if ((sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0) {
                        lastGroupCancel[sbn.groupKey] = SystemClock.elapsedRealtime()
                        scheduleSummaryCheck(sbn.groupKey, SUMMARY_CHECK_AFTER_CANCEL_MS)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Panelden silinemedi: ${sbn.key}", e)
                }
            }
        }
    }
}

/** Kullanıcının taşıdığı kaydın kararı (kategori, neden, kural korunur). */
private fun keptDecision(r: NotificationRecord): Decision? {
    val cat = r.suzgecCategory?.let { runCatching { Category.valueOf(it) }.getOrNull() } ?: return null
    return Decision(cat, r.contentType, r.reason ?: Upsert.MOVED, r.rule ?: "kullanici", r.score)
}

internal fun extractFacts(context: Context, sbn: StatusBarNotification): NotificationFacts {
    val n = sbn.notification
    val extras = n.extras
    val messaging = try {
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
    } catch (e: Exception) { null }
    val last = messaging?.messages?.lastOrNull()
    val lastMessage = last?.text?.toString()
    val person = last?.person
    val fakeApp = if (BuildConfig.DEBUG) extras.getString(EXTRA_FAKE_APP) else null
    val fakePkg = if (BuildConfig.DEBUG) extras.getString(EXTRA_FAKE_PKG) else null
    val replyAction = (n.actions.orEmpty().asList() + Notification.WearableExtender(n).actions)
        .any { a -> a.remoteInputs?.isNotEmpty() == true }

    return NotificationFacts(
        packageName = fakePkg ?: sbn.packageName,
        appLabel = fakeApp ?: appLabelOf(context, sbn),
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: messaging?.conversationTitle?.toString(),
        text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: lastMessage,
        bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        category = n.category,
        isOngoing = sbn.isOngoing,
        isForegroundService = (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
        isGroupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
        hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
            extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true,
        timeoutAfterMs = n.timeoutAfter,
        isGroupConversation = messaging?.isGroupConversation,
        sender = person?.name?.toString(),
        senderIsContact = person?.uri?.startsWith("content://com.android.contacts") == true,
        channelId = n.channelId,
        shortcutId = n.shortcutId,
        hasReplyAction = replyAction,
        appCategory = appCategoryOf(context, sbn.packageName),
    )
}

private fun toRecord(
    sbn: StatusBarNotification,
    f: NotificationFacts,
    d: Decision,
    waitedMs: Long,
    cancelled: Boolean,
): NotificationRecord {
    val n = sbn.notification
    val now = System.currentTimeMillis()
    return NotificationRecord(
        key = sbn.key,
        packageName = sbn.packageName,
        appLabel = f.appLabel,
        title = f.title,
        text = f.text,
        bigText = f.bigText,
        subText = n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        category = f.category,
        channelId = n.channelId,
        postTime = sbn.postTime,
        flags = n.flags,
        isOngoing = f.isOngoing,
        isGroupSummary = f.isGroupSummary,
        isGroupConversation = f.isGroupConversation,
        hasContentIntent = n.contentIntent != null,
        firstSeen = now,
        lastUpdated = now,
        suzgecCategory = d.category.name,
        score = d.score,
        contentType = d.contentType,
        reason = d.reason,
        rule = d.rule,
        waitedMs = waitedMs,
        cancelledBySuzgec = cancelled,
        senderHash = Personalization.senderHash(f.sender),
    )
}

private val labelCache = ConcurrentHashMap<String, String>()
private val appCategoryCache = ConcurrentHashMap<String, Int>()

/** ApplicationInfo.category (haber uygulamaları: CATEGORY_NEWS); bilinmiyorsa -1. */
private fun appCategoryOf(context: Context, pkg: String): Int = appCategoryCache.getOrPut(pkg) {
    try {
        context.packageManager.getApplicationInfo(pkg, 0).category
    } catch (e: PackageManager.NameNotFoundException) {
        -1
    }
}

private fun appLabelOf(context: Context, sbn: StatusBarNotification): String =
    labelCache.getOrPut(sbn.packageName) {
        val pm = context.packageManager
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Paket görünür değilse bildirimin taşıdığı ApplicationInfo'yu dene.
            @Suppress("DEPRECATION")
            val info = sbn.notification.extras.getParcelable<ApplicationInfo>("android.appInfo")
            info?.loadLabel(pm)?.toString() ?: sbn.packageName
        }
    }

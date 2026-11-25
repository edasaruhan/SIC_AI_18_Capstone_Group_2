package com.suzgec.app.dnd

import android.app.NotificationManager
import android.app.NotificationManager.Policy
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.suzgec.app.settings.DndBackup
import com.suzgec.app.settings.SuzgecSettings
import com.suzgec.app.sound.Ringer

/**
 * 2. katman: Rahatsız Etmeyin (CLAUDE.md §4.2).
 * Süzgeç çalışırken telefon öncelik modunda: aramalar (herkes), tekrar arayanlar ve alarmlar serbest.
 * Bildirimler panelde görünmeye devam eder (görsel kısıtlama yok); yalnız sesleri kapanır.
 * Kritik/Önemli sesini Süzgeç kendisi alarm sesi olarak çalar.
 * Asla INTERRUPTION_FILTER_NONE kullanılmaz (alarmları da susturur).
 * Açarken kullanıcının zil modu da saklanır; kapanınca (duraklat / servis kapanışı) geri yüklenir.
 */
class DndController(
    private val context: Context,
    private val settings: SuzgecSettings,
    /** Kullanıcının zil modu (bkz. SuzgecApp.readRinger). */
    private val ringerMode: () -> Int,
) {

    private val nm get() = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    /** Öncelik modunu aç; önceki durumu yalnız ilk seferde kaydet (çökme sonrası üzerine yazılmasın). */
    suspend fun enable(): Boolean {
        if (!hasAccess()) {
            Log.w(TAG, "Rahatsız Etmeyin erişimi yok; öncelik modu açılamadı")
            return false
        }
        if (settings.dndBackup() == null) {
            val p = nm.notificationPolicy
            val visual = if (Build.VERSION.SDK_INT >= 28) p.suppressedVisualEffects else 0
            settings.saveDndBackup(
                DndBackup(
                    nm.currentInterruptionFilter, p.priorityCategories, p.priorityCallSenders, p.priorityMessageSenders, visual,
                    ringerMode = ringerMode(),
                )
            )
        }
        var cats = Policy.PRIORITY_CATEGORY_CALLS or Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        // Android 9+: alarmlar ve medya ayrı kategoriler. Medya serbest bırakılmazsa öncelik modu
        // müzik ve video sesini de keser (CLAUDE.md'de listelenmedi; müzik çalar dokunulmaz olduğu için eklendi).
        if (Build.VERSION.SDK_INT >= 28) {
            cats = cats or Policy.PRIORITY_CATEGORY_ALARMS or Policy.PRIORITY_CATEGORY_MEDIA
        }
        nm.notificationPolicy = Policy(
            cats,
            Policy.PRIORITY_SENDERS_ANY, // aramalar: herkes
            Policy.PRIORITY_SENDERS_ANY, // mesaj kategorisi seçili değil, etkisiz
            0, // görsel etki kısıtlaması yok: bildirimler panelde görünür
        )
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        Log.i(TAG, "Rahatsız Etmeyin: öncelik modu açıldı (aramalar, tekrar arayanlar, alarmlar, medya serbest)")
        return true
    }

    /** Süzgeç'ten önceki duruma dön. */
    suspend fun restore() {
        val b = settings.dndBackup() ?: return
        if (hasAccess()) {
            nm.notificationPolicy = if (Build.VERSION.SDK_INT >= 28) {
                Policy(b.priorityCategories, b.priorityCallSenders, b.priorityMessageSenders, b.suppressedVisualEffects)
            } else {
                Policy(b.priorityCategories, b.priorityCallSenders, b.priorityMessageSenders)
            }
            // UNKNOWN (0) geçerli bir hedef değil: o durumda Rahatsız Etmeyin'i kapat.
            val f = if (b.filter in 1..4) b.filter else NotificationManager.INTERRUPTION_FILTER_ALL
            nm.setInterruptionFilter(f)
            Log.i(TAG, "Rahatsız Etmeyin: önceki duruma dönüldü (filtre=$f)")
            restoreRinger(b.ringerMode)
        }
        settings.clearDndBackup()
    }

    /** Kullanıcının zil modunu geri koy (Rahatsız Etmeyin kapandıktan sonra; zaten aynıysa dokunma). */
    private fun restoreRinger(mode: Int) {
        if (mode !in 0..2) return
        val am = context.getSystemService(AudioManager::class.java)
        if (am.ringerMode == mode) return
        try {
            am.ringerMode = mode
            Log.i(TAG, "Zil modu geri yüklendi: ${Ringer.label(mode)}")
        } catch (e: SecurityException) {
            Log.w(TAG, "Zil modu geri yüklenemedi", e)
        }
    }

    private companion object { const val TAG = "Suzgec" }
}

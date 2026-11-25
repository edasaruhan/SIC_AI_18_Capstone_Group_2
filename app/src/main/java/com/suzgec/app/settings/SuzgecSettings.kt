package com.suzgec.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "suzgec_ayarlar")

/** Süzgeç açılmadan önceki Rahatsız Etmeyin durumu; Süzgeç duraklayınca buna dönülür. */
data class DndBackup(
    val filter: Int,
    val priorityCategories: Int,
    val priorityCallSenders: Int,
    val priorityMessageSenders: Int,
    val suppressedVisualEffects: Int,
    /** Kullanıcının zil modu (AudioManager.RINGER_MODE_*); Süzgeç açıkken değiştirirse güncellenir. -1: bilinmiyor. */
    val ringerMode: Int = -1,
)

/**
 * Kullanıcı ayarları ve küçük kalıcı durumlar (DataStore). Ayarlar ekranı 27 Kasım'da;
 * şimdilik varsayılanlar kullanılıyor.
 */
class SuzgecSettings(private val context: Context) {

    private object K {
        val PAUSED = booleanPreferencesKey("duraklatildi")
        val DELIVERY_TIMES = stringPreferencesKey("teslim_saatleri") // "540,780,1140" (gün içindeki dakika)
        val LAST_DELIVERY = longPreferencesKey("son_teslim")
        val DND_ACTIVE = booleanPreferencesKey("dnd_suzgec_acti")
        val DND_FILTER = intPreferencesKey("dnd_eski_filtre")
        val DND_CATS = intPreferencesKey("dnd_eski_kategoriler")
        val DND_CALLS = intPreferencesKey("dnd_eski_arayanlar")
        val DND_MSGS = intPreferencesKey("dnd_eski_mesajlar")
        val DND_VISUAL = intPreferencesKey("dnd_eski_gorsel")
        val DND_RINGER = intPreferencesKey("dnd_zil_modu")
        val LAST_RINGER = intPreferencesKey("son_zil_modu")
        val GAMBLING_FILTER = booleanPreferencesKey("kumar_filtresi")
        val SHOPPING_FILTER = booleanPreferencesKey("alisveris_filtresi")
        val SOCIAL_FILTER = booleanPreferencesKey("sosyal_filtresi")
        val PRIORITY_APPS = stringPreferencesKey("oncelikli_uygulamalar") // "paket=KRITIK;paket2=ONEMLI;paket3=PANELDE"
        val USER_NAME = stringPreferencesKey("kullanici_adi")
        val WIZARD_DONE = booleanPreferencesKey("sihirbaz_bitti")
        val DEVELOPER_MODE = booleanPreferencesKey("gelistirici_modu")
    }

    companion object {
        /** Varsayılan teslim saatleri: 09:00, 13:00, 19:00. */
        val DEFAULT_DELIVERY_MINUTES = listOf(9 * 60, 13 * 60, 19 * 60)

        /** Grup mesajlarında aranan ad; kullanıcı ayarlardan değiştirmediyse (CLAUDE.md §1). */
        const val DEFAULT_USER_NAME = "Süleyman"

        /** Öncelikli uygulama seçeneklerinin üçüncüsü: "Panelde bırak". */
        const val PANEL_LEVEL = "PANELDE"
    }

    private val data get() = context.dataStore.data

    val paused: Flow<Boolean> = data.map { it[K.PAUSED] ?: false }
    suspend fun setPaused(v: Boolean) { context.dataStore.edit { it[K.PAUSED] = v } }

    // --- Bağımlılık filtreleri: kumar varsayılan açık, alışveriş ve sosyal medya kapalı ---
    val gamblingFilter: Flow<Boolean> = data.map { it[K.GAMBLING_FILTER] ?: true }
    val shoppingFilter: Flow<Boolean> = data.map { it[K.SHOPPING_FILTER] ?: false }
    val socialFilter: Flow<Boolean> = data.map { it[K.SOCIAL_FILTER] ?: false }
    suspend fun setGamblingFilter(v: Boolean) { context.dataStore.edit { it[K.GAMBLING_FILTER] = v } }
    suspend fun setShoppingFilter(v: Boolean) { context.dataStore.edit { it[K.SHOPPING_FILTER] = v } }
    suspend fun setSocialFilter(v: Boolean) { context.dataStore.edit { it[K.SOCIAL_FILTER] = v } }

    // --- Kullanıcının adı (grup mesajında geçerse Önemli) ---
    val userName: Flow<String> = data.map { it[K.USER_NAME] ?: DEFAULT_USER_NAME }
    suspend fun setUserName(v: String) { context.dataStore.edit { it[K.USER_NAME] = v.trim() } }

    // --- Öncelikli uygulamalar: paket adı → "KRITIK", "ONEMLI" ya da "PANELDE" (Panelde bırak) ---
    val priorityApps: Flow<Map<String, String>> = data.map { p ->
        p[K.PRIORITY_APPS].orEmpty().split(';').mapNotNull { e ->
            val i = e.indexOf('='); if (i <= 0) null else e.substring(0, i) to e.substring(i + 1)
        }.toMap()
    }
    /** [level] null ise uygulama öncelikli listeden çıkarılır. */
    suspend fun setPriorityApp(pkg: String, level: String?) {
        context.dataStore.edit { p ->
            val m = p[K.PRIORITY_APPS].orEmpty().split(';').mapNotNull { e ->
                val i = e.indexOf('='); if (i <= 0) null else e.substring(0, i) to e.substring(i + 1)
            }.toMap().toMutableMap()
            if (level == null) m.remove(pkg) else m[pkg] = level
            p[K.PRIORITY_APPS] = m.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    // --- Kurulum sihirbazı ---
    val wizardDone: Flow<Boolean> = data.map { it[K.WIZARD_DONE] ?: false }
    suspend fun setWizardDone(v: Boolean) { context.dataStore.edit { it[K.WIZARD_DONE] = v } }

    // --- Gizli geliştirici menüsü (Ayarlar'da sürüm satırına 7 dokunuş) ---
    val developerMode: Flow<Boolean> = data.map { it[K.DEVELOPER_MODE] ?: false }
    suspend fun setDeveloperMode(v: Boolean) { context.dataStore.edit { it[K.DEVELOPER_MODE] = v } }

    suspend fun setDeliveryMinutes(minutes: List<Int>) {
        context.dataStore.edit { it[K.DELIVERY_TIMES] = minutes.sorted().joinToString(",") }
    }

    val deliveryMinutes: Flow<List<Int>> = data.map { p ->
        p[K.DELIVERY_TIMES]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.sorted()
            ?.takeIf { it.isNotEmpty() } ?: DEFAULT_DELIVERY_MINUTES
    }
    suspend fun deliveryMinutesNow(): List<Int> = deliveryMinutes.first()

    suspend fun lastDelivery(): Long = data.first()[K.LAST_DELIVERY] ?: 0L
    suspend fun setLastDelivery(t: Long) { context.dataStore.edit { it[K.LAST_DELIVERY] = t } }

    suspend fun dndBackup(): DndBackup? {
        val p = data.first()
        if (p[K.DND_ACTIVE] != true) return null
        return DndBackup(
            p[K.DND_FILTER] ?: 1, p[K.DND_CATS] ?: 0, p[K.DND_CALLS] ?: 0, p[K.DND_MSGS] ?: 0, p[K.DND_VISUAL] ?: 0, p[K.DND_RINGER] ?: -1,
        )
    }

    suspend fun saveDndBackup(b: DndBackup) {
        context.dataStore.edit {
            it[K.DND_ACTIVE] = true
            it[K.DND_FILTER] = b.filter
            it[K.DND_CATS] = b.priorityCategories
            it[K.DND_CALLS] = b.priorityCallSenders
            it[K.DND_MSGS] = b.priorityMessageSenders
            it[K.DND_VISUAL] = b.suppressedVisualEffects
            it[K.DND_RINGER] = b.ringerMode
        }
    }

    /** Son güvenilir okunan zil modu: telefon yeniden başlayınca sistem yayını yoksa buna düşülür. -1: yok. */
    suspend fun lastRinger(): Int = data.first()[K.LAST_RINGER] ?: -1
    suspend fun setLastRinger(mode: Int) { context.dataStore.edit { it[K.LAST_RINGER] = mode } }

    /** Süzgeç açıkken kullanıcı zil modunu değiştirdi: geri yüklemede bu mod kullanılır. */
    suspend fun setDndRinger(mode: Int) {
        context.dataStore.edit { if (it[K.DND_ACTIVE] == true) it[K.DND_RINGER] = mode }
    }

    suspend fun clearDndBackup() { context.dataStore.edit { it[K.DND_ACTIVE] = false } }
}

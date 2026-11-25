package com.suzgec.app

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.suzgec.app.data.Correction
import com.suzgec.app.data.SuzgecDatabase
import com.suzgec.app.model.Counts
import com.suzgec.app.model.PersonalKey
import com.suzgec.app.model.Personalization
import com.suzgec.app.rules.Category
import com.suzgec.app.rules.RuleSettings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import com.suzgec.app.dnd.DndController
import com.suzgec.app.model.TextModel
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.Rules
import com.suzgec.app.settings.SuzgecSettings
import com.suzgec.app.sound.AlertPlayer
import com.suzgec.app.sound.Ringer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SuzgecApp : Application() {

    /** Uygulama ömrü boyunca süren işler (servis kapanırken DND geri yükleme gibi). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SuzgecDatabase by lazy { SuzgecDatabase.get(this) }
    val settings: SuzgecSettings by lazy { SuzgecSettings(this) }
    val dnd: DndController by lazy { DndController(this, settings) { readRinger().mode } }
    val alertPlayer: AlertPlayer by lazy { AlertPlayer(this) { readRinger() } }

    /** Son güvenilir okunan zil modu (DataStore'dan yüklenir); sistem yayını yoksa buna düşülür. */
    @Volatile private var savedRinger = -1

    /** Kullanıcının zil modu; güvenilir okunduysa son kayıt olarak saklanır (CLAUDE.md §4.2). */
    fun readRinger(): Ringer.Reading {
        val r = Ringer.read(this, savedRinger)
        if (r.reliable && r.mode != savedRinger) {
            savedRinger = r.mode
            appScope.launch { settings.setLastRinger(r.mode); settings.setDndRinger(r.mode) }
        }
        return r
    }

    override fun onCreate() {
        super.onCreate()
        savedRinger = runBlocking { settings.lastRinger() }
        watchRinger()
    }

    /**
     * Kullanıcı Süzgeç açıkken zil modunu değiştirirse (normal / titreşim / sessiz) bunu sakla:
     * Rahatsız Etmeyin kapanınca bu moda dönülür. Uyarı sesi ise modu çalma anında okur (AlertPlayer).
     */
    private fun watchRinger() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (isInitialStickyBroadcast) return
                val r = readRinger()
                Log.i("Suzgec", "Zil modu değişti: ${Ringer.label(r.mode)} (${r.source})")
            }
        }
        Ringer.register(this, receiver, Ringer.filter())
        val r = readRinger()
        Log.i("Suzgec", "Zil modu: ${Ringer.label(r.mode)} (${r.source})")
    }

    /** "Süzgeç'i duraklat": açıkken dinleyici hiçbir bildirime dokunmaz, DND önceki hâline döner. */
    val paused: StateFlow<Boolean> by lazy {
        val initial = runBlocking { settings.paused.first() }
        settings.paused.stateIn(appScope, SharingStarted.Eagerly, initial)
    }

    /**
     * Kural katmanının kullandığı ayarlar: filtreler, öncelikli uygulamalar ve kişiselleştirme
     * sayaçları (düzeltmelerden). Ayar ya da düzeltme değişince kendiliğinden güncellenir.
     */
    val ruleSettings: StateFlow<RuleSettings> by lazy {
        val prefs = combine(
            settings.gamblingFilter, settings.shoppingFilter, settings.socialFilter, settings.priorityApps,
            settings.userName,
        ) { g, s, so, apps, name ->
            RuleSettings(
                gamblingFilter = g, shoppingFilter = s, socialFilter = so,
                priorityApps = apps.mapNotNull { (pkg, lvl) ->
                    runCatching { Category.valueOf(lvl) }.getOrNull()
                        ?.takeIf { it == Category.KRITIK || it == Category.ONEMLI }?.let { pkg to it }
                }.toMap(),
                panelApps = apps.filterValues { it == SuzgecSettings.PANEL_LEVEL }.keys,
                userName = name,
            )
        }
        val counts = database.notificationDao().observeCorrections().map(::countCorrections)
        val initial = runBlocking { prefs.first().copy(personal = counts.first()) }
        combine(prefs, counts) { p, c -> p.copy(personal = c) }
            .stateIn(appScope, SharingStarted.Eagerly, initial)
    }

    fun setPaused(value: Boolean) {
        appScope.launch {
            settings.setPaused(value)
            if (value) dnd.restore() else dnd.enable()
            Log.i("Suzgec", if (value) "Süzgeç duraklatıldı" else "Süzgeç devam ediyor")
        }
    }

    /** Metin modeli. İlk erişimde 3,9 MB JSON okunur: ana iş parçacığında çağırma. */
    val textModel: TextModel? by lazy {
        val t0 = SystemClock.elapsedRealtime()
        try {
            TextModel.parse(readAsset("model_tfidf_lr.json")).also {
                Log.i("Suzgec", "Metin modeli yüklendi (${SystemClock.elapsedRealtime() - t0} ms)")
            }
        } catch (e: Exception) {
            Log.e("Suzgec", "Metin modeli yüklenemedi; kural dışı bildirimler 'Model bekliyor' kalacak", e)
            null
        }
    }

    /** Kural katmanı (hızlı yüklenir) + model (classify sırasında, arka planda yüklenir). */
    val ruleEngine: RuleEngine by lazy {
        RuleEngine(Rules.parse(readAsset("rules.json"))) { textModel }
    }

    private fun readAsset(name: String) = assets.open(name).bufferedReader().use { it.readText() }
}

/** Düzeltmeleri (gönderen, uygulama, tür) anahtarına göre yukarı/aşağı sayar (§7). */
fun countCorrections(list: List<Correction>): Map<PersonalKey, Counts> {
    val m = HashMap<PersonalKey, Counts>()
    for (c in list) {
        val type = c.contentType ?: continue
        val from = Personalization.rank(c.fromCategory) ?: continue
        val to = Personalization.rank(c.toCategory) ?: continue
        if (from == to) continue
        val key = PersonalKey(c.senderHash, c.appLabel, type)
        val old = m[key] ?: Counts(0, 0)
        m[key] = if (to > from) old.copy(up = old.up + 1) else old.copy(down = old.down + 1)
    }
    return m
}

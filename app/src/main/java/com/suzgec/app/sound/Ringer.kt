package com.suzgec.app.sound

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import com.suzgec.app.rules.Category

/**
 * Kullanıcının zil modu (normal / titreşim / sessiz), CLAUDE.md §4.2.
 *
 * Rahatsız Etmeyin açıkken Android uygulamalara `AudioManager.getRingerMode()` ile hep SESSİZ
 * gösterir ("external" mod); kullanıcının seçtiği asıl mod "internal" moddur. Onu okumanın açık API'si
 * yok, ama sistem her değişimde yapışkan (sticky) `INTERNAL_RINGER_MODE_CHANGED_ACTION` yayını
 * gönderir: alıcısız `registerReceiver` son yayını döndürür. Yayın yoksa son kaydedilen moda düşülür ([read]).
 */
object Ringer {
    const val ACTION_INTERNAL = "android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION"

    fun filter() = IntentFilter().apply {
        addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        addAction(ACTION_INTERNAL)
    }

    /** Okunan zil modu ve nereden okunduğu. [reliable]: son kayıt olarak saklanabilir mi. */
    data class Reading(val mode: Int, val source: String, val reliable: Boolean)

    /**
     * Kullanıcının seçtiği zil modu (AudioManager.RINGER_MODE_*). Sıra:
     * 1) sistemin yapışkan INTERNAL yayını; 2) Rahatsız Etmeyin kapalıysa AudioManager;
     * 3) [saved]: DataStore'daki son bilinen mod (telefon yeniden başladıysa ve yayın yoksa);
     * 4) son çare AudioManager (DND açıkken hep SESSİZ der).
     */
    fun read(context: Context, saved: Int): Reading {
        val sticky = try {
            register(context, null, IntentFilter(ACTION_INTERNAL))
        } catch (_: Exception) {
            null
        }
        val internal = sticky?.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1) ?: -1
        val dndOff = context.getSystemService(NotificationManager::class.java).currentInterruptionFilter ==
            NotificationManager.INTERRUPTION_FILTER_ALL
        return choose(internal, dndOff, context.getSystemService(AudioManager::class.java).ringerMode, saved)
    }

    /** [read]'in karar sırası (birim testi için ayrı). */
    fun choose(internal: Int, dndOff: Boolean, external: Int, saved: Int): Reading = when {
        internal in 0..2 -> Reading(internal, "sistem", reliable = true)
        dndOff -> Reading(external, "AudioManager", reliable = true)
        saved in 0..2 -> Reading(saved, "son kayıt", reliable = false)
        else -> Reading(external, "AudioManager (DND açık)", reliable = false)
    }

    fun register(context: Context, receiver: BroadcastReceiver?, filter: IntentFilter): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

    fun label(mode: Int) = when (mode) {
        AudioManager.RINGER_MODE_NORMAL -> "normal"
        AudioManager.RINGER_MODE_VIBRATE -> "titreşim"
        AudioManager.RINGER_MODE_SILENT -> "sessiz"
        else -> "bilinmiyor($mode)"
    }
}

/** Bir Kritik/Önemli uyarısında ne çalınacağı. */
data class AlertPlan(val sound: Boolean, val vibrate: Boolean) {
    companion object {
        /**
         * Normal mod → ses + titreşim; titreşim modu → yalnız titreşim; sessiz mod → hiçbiri.
         * Kritik/Önemli dışındaki kategoriler hiç uyarmaz.
         */
        fun of(category: Category, ringerMode: Int): AlertPlan {
            if (category != Category.KRITIK && category != Category.ONEMLI) return AlertPlan(false, false)
            return when (ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AlertPlan(sound = true, vibrate = true)
                AudioManager.RINGER_MODE_VIBRATE -> AlertPlan(sound = false, vibrate = true)
                else -> AlertPlan(sound = false, vibrate = false)
            }
        }
    }
}

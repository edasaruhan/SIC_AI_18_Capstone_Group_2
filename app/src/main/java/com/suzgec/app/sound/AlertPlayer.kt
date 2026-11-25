package com.suzgec.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.suzgec.app.rules.Category

/**
 * Kritik/Önemli sesi (CLAUDE.md §4.2). Telefon öncelik modunda olduğu için ses alarm kanalından
 * (USAGE_ALARM) çalınır; alarmlar serbest olduğundan duyulur. Titreşim de alarm öznitelikli.
 * Kritik ≈ 3,5 sn ses (tekrarlı) + uzun titreşim; Önemli ≈ 1 sn ses + kısa titreşim.
 * Alarm kanalı zil modunu yok saydığı için kullanıcının zil moduna Süzgeç kendisi uyar ([AlertPlan]).
 */
class AlertPlayer(private val context: Context, private val ringer: () -> Ringer.Reading) {

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    private val alarmAttrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Kullanıcının zil moduna uyar: normal → ses + titreşim, titreşim → yalnız titreşim,
     * sessiz → hiçbiri. Mod çalma anında okunur (Rahatsız Etmeyin açıkken de asıl mod, bkz. [Ringer]).
     */
    fun play(category: Category) {
        val (soundMs, pattern) = when (category) {
            Category.KRITIK -> 3500L to longArrayOf(0, 600, 200, 600, 200, 900)
            Category.ONEMLI -> 1000L to longArrayOf(0, 250)
            else -> return
        }
        val reading = ringer()
        val plan = AlertPlan.of(category, reading.mode)
        main.post {
            stop()
            if (plan.sound) playSound(soundMs, loop = category == Category.KRITIK)
            if (plan.vibrate) vibrate(pattern)
            val what = when {
                plan.sound -> "ses ${soundMs} ms + titreşim"
                plan.vibrate -> "yalnız titreşim"
                else -> "ses ve titreşim yok"
            }
            Log.i("Suzgec", "Uyarı: ${category.label}, zil modu ${Ringer.label(reading.mode)} (${reading.source}) → $what")
        }
    }

    private fun playSound(ms: Long, loop: Boolean) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(alarmAttrs)
            mp.setDataSource(context, uri)
            mp.isLooping = loop
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            player = mp
            main.postDelayed({ if (player === mp) stop() }, ms)
        } catch (e: Exception) {
            Log.e("Suzgec", "Ses çalınamadı", e)
        }
    }

    private fun stop() {
        player?.let {
            try { if (it.isPlaying) it.stop() } catch (_: IllegalStateException) {}
            it.release()
        }
        player = null
    }

    private fun vibrate(pattern: LongArray) {
        val vib: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vib.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(pattern, -1)
        if (Build.VERSION.SDK_INT >= 33) {
            vib.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(effect, alarmAttrs)
        }
    }
}

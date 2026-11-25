package com.suzgec.app.listener

import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Composable
import com.suzgec.app.rules.Decision

/**
 * Emülatör demosu kancası ("uygulamanın içinden de sil", 25 Kasım; yalnız `emulator-silme-demo` dalı).
 *
 * Bu arayüz boştur. İş yapan uygulaması YALNIZ `src/emulatorDemo/` kaynak kümesindedir; `standart` varyantta
 * (M51 ve release) [InAppDelete.hook] hiçbir şey yapmaz, Ayarlar'a bölüm eklemez (CLAUDE.md §0 teknik kilit).
 */
interface InAppDeleteHook {
    /** Ayarlar'da bu kancanın bölümü gösterilsin mi (standart: hayır). */
    val hasSettings: Boolean get() = false

    /** Ayarlar bölümü (standart: boş). */
    @Composable
    fun SettingsContent() {
    }

    /** Karar kayda yazılıp bildirim panelden kaldırıldıktan sonra çağrılır (standart: hiçbir şey yapmaz). */
    suspend fun onDecided(context: Context, sbn: StatusBarNotification, decision: Decision) {
    }
}

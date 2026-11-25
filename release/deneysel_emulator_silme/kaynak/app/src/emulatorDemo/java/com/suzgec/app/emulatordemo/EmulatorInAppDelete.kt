package com.suzgec.app.emulatordemo

import android.content.Context
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.suzgec.app.DeviceKind
import com.suzgec.app.SuzgecApp
import com.suzgec.app.listener.InAppDeleteHook
import com.suzgec.app.rules.Decision
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SuzgecDemo"
private const val PREFS = "emulator_demo"
private const val KEY_ENABLED = "uygulamadan_sil"

/** Kayda yazılan neden eki; Silinenler'de "Neden burada?" satırında görünür. */
const val DELETED_IN_APP_SUFFIX = " · Uygulamadan da silindi (demo)"

/**
 * emulatorDemo: kumar kararıyla Silinenler'e giden Demo Gönderici bildiriminin "Sil" eylemini bir kez tetikler.
 * Demo Gönderici yalnız KENDİ sahte gelen kutusundan o kaydı siler. Kilitler: [InAppDeletePolicy].
 */
object EmulatorInAppDelete : InAppDeleteHook {
    private val fired: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val emulator: Boolean by lazy { DeviceKind.isEmulator() }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun isEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ENABLED, false)

    override val hasSettings: Boolean get() = true

    @Composable
    override fun SettingsContent() {
        val ctx = LocalContext.current
        var on by remember { mutableStateOf(emulator && isEnabled(ctx)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Uygulamadan da sil (deneme)", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (emulator) "Yalnız emülatör demosu: kumar bildirimi Silinenler'e gidince Demo Gönderici'nin " +
                        "sahte gelen kutusundan da silinir. Başka hiçbir uygulamaya dokunulmaz."
                    else "Gerçek cihaz: devre dışı.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = on,
                enabled = emulator,
                onCheckedChange = {
                    on = it
                    prefs(ctx).edit().putBoolean(KEY_ENABLED, it).apply()
                    Log.i(TAG, "Uygulamadan da sil (deneme): ${if (it) "açık" else "kapalı"}")
                },
            )
        }
    }

    override suspend fun onDecided(context: Context, sbn: StatusBarNotification, decision: Decision) {
        val actions = sbn.notification.actions.orEmpty()
        val r = InAppDeletePolicy.decide(
            packageName = sbn.packageName,
            actionTitles = actions.map { it.title?.toString() },
            category = decision.category,
            contentType = decision.contentType,
            isEmulator = emulator,
            enabled = isEnabled(context),
            alreadyFired = sbn.key in fired,
        )
        when (r.verdict) {
            InAppDeletePolicy.Verdict.GERCEK_CIHAZ -> Log.w(TAG, "gerçek cihaz: devre dışı")
            InAppDeletePolicy.Verdict.SIL -> {
                fired.add(sbn.key)
                actions[r.actionIndex].actionIntent.send()
                Log.i(TAG, "uygulamadan_silindi: ${sbn.packageName} | ${decision.rule} | ${sbn.key}")
                (context.applicationContext as SuzgecApp).database.notificationDao()
                    .setDecisionText(sbn.key, decision.reason + DELETED_IN_APP_SUFFIX, decision.rule)
            }
            else -> Log.i(TAG, "Uygulamadan silme yok: ${r.verdict} (${sbn.packageName}, ${decision.category.label})")
        }
    }
}

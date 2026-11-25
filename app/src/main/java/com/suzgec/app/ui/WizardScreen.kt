package com.suzgec.app.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suzgec.app.SuzgecApp

/** Pil optimizasyonundan muaf mı? */
fun isBatteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Süzgeç sürekli çalışan bir bildirim dinleyicisi; muafiyet gerekçeli (CLAUDE.md §3)
private fun requestBatteryExemption(context: Context) {
    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    try {
        context.startActivity(i)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}

/**
 * Kurulum sihirbazı (CLAUDE.md §4): bildirim erişimi → Rahatsız Etmeyin erişimi → pil muafiyeti
 * → teslim saatleri → bağımlılık seçimi. [refresh] her onResume'da artar: kullanıcı ayarlardan
 * dönünce izin durumları yeniden okunur.
 */
@Composable
fun WizardScreen(app: SuzgecApp, refresh: Int, onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    @Suppress("UNUSED_EXPRESSION") refresh // izin durumlarını yeniden okut

    val listener = isListenerEnabled(context)
    val dnd = app.dnd.hasAccess()
    val battery = isBatteryExempt(context)
    val total = 5

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Süzgeç kurulumu · Adım ${step + 1}/$total", style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (step + 1f) / total }, modifier = Modifier.fillMaxWidth())

            when (step) {
                0 -> Step(
                    title = "Bildirimlerini görebilmem gerekiyor",
                    body = "Süzgeç gelen her bildirimi bu telefonda okur ve sınıflar; hiçbir yere göndermez. " +
                        "Açılan ekranda Süzgeç'i bul ve bildirim erişimine izin ver.",
                    done = listener,
                    doneText = "Bildirim erişimi verildi",
                    action = "Bildirim erişimini aç" to { openListenerSettings(context) },
                )
                1 -> Step(
                    title = "Sessizliği Süzgeç yönetsin",
                    body = "Süzgeç telefonu Rahatsız Etmeyin'in öncelik modunda tutar: aramalar, tekrar arayanlar, " +
                        "alarmlar, müzik ve video sesi her zaman açık. Kritik ve Önemli bildirimlerin sesini Süzgeç " +
                        "kendisi çalar. İstediğin an Ayarlar'dan duraklatabilirsin.",
                    done = dnd,
                    doneText = "Rahatsız Etmeyin erişimi var",
                    action = "Rahatsız Etmeyin erişimini aç" to {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                )
                2 -> {
                    Step(
                        title = "Arka planda kapanmasın",
                        body = "Telefon Süzgeç'i pil tasarrufu için kapatırsa bildirimler süzülmez. " +
                            "Pil optimizasyonundan muaf tutulması gerekiyor.",
                        done = battery,
                        doneText = "Pil optimizasyonundan muaf",
                        action = "Pil muafiyeti iste" to { requestBatteryExemption(context) },
                    )
                    OutlinedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val samsung = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
                            Text(
                                if (samsung) "Samsung telefonlarda ayrıca" else "Samsung telefonlarda ayrıca (bu telefon ${Build.MANUFACTURER})",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Ayarlar → Pil ve cihaz bakımı → Pil → Arka plan kullanım sınırları bölümünde Süzgeç " +
                                    "\"Uyku\" ya da \"Derin uyku\" listesinde olmamalı. Süzgeç'in uygulama bilgilerinde " +
                                    "pil ayarı \"Kısıtlanmamış\" (Sınırsız) olmalı.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (samsung) {
                                OutlinedButton(onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                                    )
                                }) { Text("Süzgeç'in uygulama bilgilerini aç") }
                            }
                        }
                    }
                }
                3 -> {
                    Text("Ne zaman bakmak istersin?", style = MaterialTheme.typography.headlineSmall)
                    DeliveryTimesSection(app)
                    UserNameSection(app)
                }
                4 -> {
                    Text("Görmek istemediğin içerikler", style = MaterialTheme.typography.headlineSmall)
                    FiltersSection(app)
                }
            }

            Spacer(Modifier.padding(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Geri") }
                val canGo = when (step) {
                    0 -> listener // bildirim erişimi olmadan Süzgeç çalışamaz
                    else -> true
                }
                Button(enabled = canGo, onClick = { if (step < total - 1) step++ else onFinish() }) {
                    Text(
                        when {
                            step == total - 1 -> "Bitir"
                            step == 1 && !dnd -> "Şimdilik geç"
                            step == 2 && !battery -> "Şimdilik geç"
                            else -> "Devam"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, done: Boolean, doneText: String, action: Pair<String, () -> Unit>) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(body, style = MaterialTheme.typography.bodyLarge)
    if (done) {
        Text("✓ $doneText", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    } else {
        Button(onClick = action.second) { Text(action.first) }
    }
}

package com.suzgec.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suzgec.app.SuzgecApp
import com.suzgec.app.BuildConfig
import com.suzgec.app.DeviceKind
import com.suzgec.app.stats.Weekly
import android.widget.Toast
import androidx.compose.foundation.clickable
import com.suzgec.app.delivery.Delivery
import com.suzgec.app.rules.Category
import com.suzgec.app.settings.SuzgecSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch

/** Ayarlar: teslim saatleri, bağımlılık filtreleri, öncelikli uygulamalar, duraklat. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(app: SuzgecApp, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val paused by app.paused.collectAsStateWithLifecycle()
    val priority by app.settings.priorityApps.collectAsStateWithLifecycle(initialValue = emptyMap())
    val context = LocalContext.current
    val apps = remember { launcherApps(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ayarlar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Teslim saatleri") }
            item { DeliveryTimesSection(app) }
            item { HorizontalDivider() }
            item { SectionTitle("Adın") }
            item { UserNameSection(app) }
            item { HorizontalDivider() }
            item { SectionTitle("Görmek istemediğin içerikler") }
            item { FiltersSection(app) }
            item { HorizontalDivider() }
            item { SectionTitle("Öncelikli uygulamalar") }
            item {
                Text(
                    "Kritik / Önemli: uygulamanın bildirimleri sesli gelir (kampanyalar hariç). " +
                        "Panelde bırak: bildirimleri telefonun panelinde kalır, Süzgeç dokunmaz ve ses çalmaz " +
                        "(klavye, kamera gibi uygulamalar için). Kod, şifre ve kumar kuralları her zaman önce çalışır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val sorted = apps.sortedWith(compareBy({ it.pkg !in priority }, { it.label.lowercase() }))
            items(sorted, key = { it.pkg }) { a ->
                PriorityRow(a.label, priority[a.pkg]) { level ->
                    scope.launch { app.settings.setPriorityApp(a.pkg, level) }
                }
            }
            item { HorizontalDivider() }
            item {
                SwitchRow(
                    title = "Süzgeç'i duraklat",
                    desc = "Duraklatınca bildirimlere dokunulmaz, Rahatsız Etmeyin önceki hâline döner.",
                    checked = paused,
                ) { app.setPaused(it) }
            }
            item { HorizontalDivider() }
            item { VersionAndDeveloperSection(app) }
        }
    }
}

/** Geliştirici menüsünü açmak için sürüm satırına art arda dokunma sayısı. */
private const val DEV_TAPS = 7

/**
 * "Süzgeç 1.0" satırı; 7 kez dokununca gizli geliştirici menüsü açılır (kalıcı, DataStore).
 * Deneme seçenekleri yalnız burada (demo videosu için "Teslimi şimdi gönder" dahil).
 */
@Composable
private fun VersionAndDeveloperSection(app: SuzgecApp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dev by app.settings.developerMode.collectAsStateWithLifecycle(initialValue = false)
    var taps by remember { mutableStateOf(0) }
    var confirmSample by remember { mutableStateOf(false) }
    val emulator = remember { DeviceKind.isEmulator() }
    fun toast(s: String) = Toast.makeText(context, s, Toast.LENGTH_SHORT).show()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Süzgeç ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().clickable {
                if (dev) return@clickable
                taps++
                val left = DEV_TAPS - taps
                when {
                    left <= 0 -> { scope.launch { app.settings.setDeveloperMode(true) }; toast("Geliştirici menüsü açıldı") }
                    left <= 3 -> toast("Geliştirici menüsüne $left dokunuş kaldı")
                }
            }.padding(vertical = 8.dp),
        )
        if (dev) {
            SectionTitle("Geliştirici menüsü")
            Text(
                "Deneme ve demo içindir; günlük kullanımda gerekmez.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DevButton("Teslimi şimdi gönder") {
                app.appScope.launch { Delivery.deliver(context.applicationContext) }
                toast("Teslim özeti gönderildi")
            }
            DevButton("1 dk sonraya teslim kur") {
                Delivery.scheduleAt(context, System.currentTimeMillis() + 60_000)
                toast("Teslim 1–11 dk içinde (10 dk pencere)")
            }
            DevButton("Öğrenilen düzeltmeleri sıfırla") {
                app.appScope.launch { app.database.notificationDao().clearCorrections() }
                toast("Kişisel düzeltmeler silindi")
            }
            DevButton(
                if (emulator) "Örnek haftalık veri yükle…" else "Örnek haftalık veri yükle (gerçek cihazda kapalı)",
                enabled = emulator,
            ) { confirmSample = true }
            DevButton("Kurulum sihirbazını yeniden aç") { scope.launch { app.settings.setWizardDone(false) } }
            DevButton("Geliştirici menüsünü kapat") { taps = 0; scope.launch { app.settings.setDeveloperMode(false) } }
        }
    }
    if (confirmSample) {
        AlertDialog(
            onDismissRequest = { confirmSample = false },
            title = { Text("Örnek veri eklensin mi?") },
            text = {
                Text(
                    "Gerçek verinin yanına örnek veri eklenecek: son 7 günden örnek bildirimler ve kutu girişleri. " +
                        "Örnek bildirimler kutuya da düşer; CSV dışa aktarmaya girmez."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSample = false
                    app.appScope.launch { Weekly.loadSample(app.database.notificationDao()) }
                    toast("Örnek hafta yüklendi")
                }) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { confirmSample = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun DevButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Günde 3 teslim saati; değişince zamanlayıcı yeniden kurulur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliveryTimesSection(app: SuzgecApp) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val minutes by app.settings.deliveryMinutes.collectAsStateWithLifecycle(
        initialValue = com.suzgec.app.settings.SuzgecSettings.DEFAULT_DELIVERY_MINUTES,
    )
    var editing by remember { mutableStateOf<Int?>(null) } // düzenlenen sıra

    Text(
        "Normal ve Önemsiz bildirimler bu saatlerde tek bir sessiz özetle gelir. Kutuyu istediğin an açabilirsin.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        minutes.forEachIndexed { i, m ->
            OutlinedButton(onClick = { editing = i }) { Text("%02d:%02d".format(m / 60, m % 60)) }
        }
    }
    editing?.let { i ->
        val state = rememberTimePickerState(minutes[i] / 60, minutes[i] % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${i + 1}. teslim saati") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    val new = minutes.toMutableList().also { it[i] = state.hour * 60 + state.minute }.distinct()
                    editing = null
                    scope.launch {
                        app.settings.setDeliveryMinutes(new)
                        Delivery.schedule(context)
                    }
                }) { Text("Kaydet") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Vazgeç") } },
        )
    }
}

/** Bağımlılık filtreleri + Yeşilay YEDAM 115 bilgisi (CLAUDE.md §1). */
@Composable
fun FiltersSection(app: SuzgecApp) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val gambling by app.settings.gamblingFilter.collectAsStateWithLifecycle(initialValue = true)
    val shopping by app.settings.shoppingFilter.collectAsStateWithLifecycle(initialValue = false)
    val social by app.settings.socialFilter.collectAsStateWithLifecycle(initialValue = false)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Açtığın filtrelerdeki bildirimler doğrudan ${Category.SILINENLER.label}'e gider; istersen oradan geri alabilirsin.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SwitchRow("Kumar, bahis ve şans oyunları", "Bahis, casino ve bonus tanıtımları. Varsayılan olarak açık.", gambling) {
            scope.launch { app.settings.setGamblingFilter(it) }
        }
        SwitchRow("Alışveriş kampanyaları", "Yalnız kampanya ve indirimler; sipariş ve kargo bildirimleri gelmeye devam eder.", shopping) {
            scope.launch { app.settings.setShoppingFilter(it) }
        }
        SwitchRow("Sosyal medya etkileşimleri", "Beğeni, takip, canlı yayın; mesajlar gelmeye devam eder.", social) {
            scope.launch { app.settings.setSocialFilter(it) }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Destek almak istersen", fontWeight = FontWeight.SemiBold)
                Text(
                    "Kumar, alışveriş ya da sosyal medyada problemli kullanım yaşadığını düşünüyorsan Yeşilay " +
                        "Danışmanlık Merkezi (YEDAM) ücretsiz ve gizli destek veriyor.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:115")))
                }) { Text("YEDAM 115'i ara") }
            }
        }
    }
}

/** Grup mesajlarında aranacak ad (CLAUDE.md §5): geçerse mesaj Önemli olur. */
@Composable
fun UserNameSection(app: SuzgecApp) {
    val scope = rememberCoroutineScope()
    val saved by app.settings.userName.collectAsStateWithLifecycle(initialValue = SuzgecSettings.DEFAULT_USER_NAME)
    var text by remember(saved) { mutableStateOf(saved) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Bir grup sohbetinde adın geçerse (@bahsetme dahil) mesaj Önemli olur.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Adın") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && text != saved) scope.launch { app.settings.setUserName(text) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { scope.launch { app.settings.setUserName(text) } }),
        )
    }
}

@Composable
private fun PriorityRow(label: String, level: String?, onChange: (String?) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(selected = level == null, onClick = { onChange(null) }, label = { Text("Yok") })
            FilterChip(
                selected = level == Category.ONEMLI.name, onClick = { onChange(Category.ONEMLI.name) },
                label = { Text("${Category.ONEMLI.symbol} Önemli") },
            )
            FilterChip(
                selected = level == Category.KRITIK.name, onClick = { onChange(Category.KRITIK.name) },
                label = { Text("${Category.KRITIK.symbol} Kritik") },
            )
            FilterChip(
                selected = level == SuzgecSettings.PANEL_LEVEL, onClick = { onChange(SuzgecSettings.PANEL_LEVEL) },
                label = { Text("Panelde bırak") },
            )
        }
    }
}

data class AppEntry(val pkg: String, val label: String)

/** Ana ekranda simgesi olan uygulamalar (Süzgeç'in kendisi hariç). */
fun launcherApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    val list = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    return list.map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != context.packageName }
        .distinctBy { it.pkg }
}

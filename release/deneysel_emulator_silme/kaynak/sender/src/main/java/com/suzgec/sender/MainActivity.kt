package com.suzgec.sender

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Demo bildirimlerini tek tek gönderen basit liste + Demo Gönderici'nin kendi sahte gelen kutusu. */
class MainActivity : ComponentActivity() {
    private var showInbox by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        showInbox = intent?.getStringExtra("ekran") == "sahte"
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding()) {
                        if (showInbox) InboxScreen(onBack = { showInbox = false }) else SendScreen(onInbox = { showInbox = true })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("ekran") == "sahte") showInbox = true
    }

    @Composable
    private fun SendScreen(onInbox: () -> Unit) {
        val inbox = rememberInbox()
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Demo gönderici", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                getSystemService(android.app.NotificationManager::class.java).cancelAll()
            }) { Text("Hepsini kaldır") }
        }
        TextButton(onClick = onInbox, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("Sahte Mesajlar: ${inbox.size} →")
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(DEMOS) { i, d ->
                FilledTonalButton(onClick = { send(this@MainActivity, d) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("${i + 1}. ${d.app} — ${d.title.ifEmpty { d.text.take(30) }}")
                        Text("Beklenen: ${d.expected}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }

    @Composable
    private fun InboxScreen(onBack: () -> Unit) {
        val inbox = rememberInbox()
        val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale("tr")) }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("←") }
            Text("Sahte Mesajlar: ${inbox.size}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { FakeInbox.clear(this@MainActivity) }) { Text("Boşalt") }
        }
        Text(
            "Demo Gönderici'nin kendi sahte gelen kutusu (emülatör demosu). Gerçek mesaj yoktur.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(inbox.reversed(), key = { it.uid }) { m ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Text(m.app, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text(fmt.format(Date(m.time)), style = MaterialTheme.typography.labelSmall)
                        }
                        if (m.title.isNotEmpty()) Text(m.title, style = MaterialTheme.typography.titleSmall)
                        Text(m.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Sahte kutunun canlı hâli (Sil eylemi arka planda çalışınca ekran kendiliğinden güncellenir). */
@Composable
private fun rememberInbox(): List<FakeInbox.Item> {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(FakeInbox.all(ctx)) }
    DisposableEffect(Unit) {
        val prefs = FakeInbox.prefs(ctx)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> items = FakeInbox.all(ctx) }
        prefs.registerOnSharedPreferenceChangeListener(l)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(l) }
    }
    return items
}

package com.suzgec.app.ui

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.suzgec.app.data.NotificationDao
import com.suzgec.app.data.NotificationRecord
import com.suzgec.app.listener.IntentCache
import com.suzgec.app.rules.Category
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("d MMM HH:mm:ss", Locale.forLanguageTag("tr"))

/** Geçici kayıt ekranı: dinleyicinin yakaladığı tüm bildirimler, en yeni üstte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(dao: NotificationDao) {
    val records by dao.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bildirim kaydı (${records.size})") },
                actions = {
                    IconButton(onClick = { confirmClear = true }, enabled = records.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, contentDescription = "Kaydı temizle")
                    }
                },
            )
        },
    ) { padding ->
        if (records.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Henüz bildirim yok.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Bildirim erişimi açık. Yeni gelen bildirimler burada listelenecek.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val listState = rememberLazyListState()
            // Yeni bildirim gelince, kullanıcı zaten üstteyse en üste kaydır.
            LaunchedEffect(records.firstOrNull()?.id, records.firstOrNull()?.lastUpdated) {
                if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { it.id }) { rec ->
                    RecordCard(rec, onClick = { openTarget(context, rec) })
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Kayıt temizlensin mi?") },
            text = { Text("Listedeki tüm bildirim kayıtları silinir. Paneldeki bildirimlere dokunulmaz.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { dao.clearAll() }
                }) { Text("Temizle") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Vazgeç") } },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecordCard(rec: NotificationRecord, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .alpha(if (rec.removed) 0.6f else 1f),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rec.appLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    timeFmt.format(Date(rec.postTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!rec.title.isNullOrBlank()) {
                Text(rec.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val body = rec.bigText ?: rec.text
            if (!body.isNullOrBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            val cat = rec.suzgecCategory?.let { runCatching { Category.valueOf(it) }.getOrNull() }
            if (cat != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${cat.symbol} ${cat.label}" + (rec.contentType?.let { "  ·  tür $it" } ?: ""),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cat.color,
                    )
                }
                rec.reason?.let {
                    Text(
                        "Neden: $it" + if (rec.waitedMs > 0) " (${rec.waitedMs / 1000.0} sn beklendi)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rec.category?.let { Tag("tür: $it") }
                if (rec.isOngoing) Tag("kalıcı")
                if (rec.isGroupSummary) Tag("grup özeti")
                when (rec.isGroupConversation) {
                    true -> Tag("grup sohbeti")
                    false -> Tag("birebir sohbet")
                    null -> {}
                }
                if (rec.updateCount > 0) Tag("güncellendi ×${rec.updateCount}")
                if (rec.cancelledBySuzgec) Tag("Süzgeç panelden sildi")
                else if (rec.removed) Tag("panelden kalktı")
            }
            Text(
                rec.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(label: String) {
    AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

/** Bildirimin hedef sayfasını aç; yoksa uygulamanın ana ekranını aç. */
internal fun openTarget(context: Context, rec: NotificationRecord) {
    // Arşiv satırı (Upsert.ARCHIVE_MARK) asıl bildirimin hedef sayfasını kullanır.
    val pi = IntentCache.get(com.suzgec.app.data.Upsert.baseKey(rec.key))
    if (pi != null) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= 36) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS
                } else {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                val opts = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode)
                pi.send(context, 0, null, null, null, null, opts.toBundle())
            } else {
                pi.send()
            }
            return
        } catch (e: PendingIntent.CanceledException) {
            Log.w("Suzgec", "Hedef sayfa iptal edilmiş, ana ekran açılıyor: ${rec.key}")
        }
    }
    val launch = context.packageManager.getLaunchIntentForPackage(rec.packageName)
    if (launch != null) {
        context.startActivity(launch)
    } else {
        Toast.makeText(context, "Bu bildirimin açılacak bir sayfası yok.", Toast.LENGTH_SHORT).show()
    }
}

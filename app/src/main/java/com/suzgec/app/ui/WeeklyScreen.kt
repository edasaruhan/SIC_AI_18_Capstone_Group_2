package com.suzgec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.suzgec.app.rules.Category
import com.suzgec.app.stats.WeeklyStats
import java.util.Locale

/** Haftalık özet: son 7 gün. Ödül/rozet yok (CLAUDE.md §1); yalnız sayılar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyScreen(stats: WeeklyStats?, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Haftalık özet") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Son 7 gün", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (stats == null) {
                Text("Hesaplanıyor…")
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat("${stats.removedBySuzgec}", "bildirim panelden kaldırıldı", Modifier.weight(1f))
                Stat("${stats.silenced}", "bildirimin sesi kesildi", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(
                    "${stats.deletedGambling} + ${stats.deletedCampaign}",
                    "kumar + kampanya Silinenler'e gitti", Modifier.weight(1f),
                )
                Stat("${stats.opensOutsideDelivery}", "kez kutu teslim saatleri dışında açıldı", Modifier.weight(1f))
            }
            val pct = String.format(Locale.ROOT, "%.0f", stats.alertReduction * 100)
            Text(
                "Sınıflanan ${stats.classified} bildirimin %$pct'i sessiz geldi; yalnız Kritik ve Önemli olanlar ses çıkardı.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Kategori dağılımı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val order = listOf(Category.KRITIK, Category.ONEMLI, Category.NORMAL, Category.ONEMSIZ, Category.SILINENLER, Category.DOKUNMA)
            val max = order.maxOf { stats.byCategory[it] ?: 0 }.coerceAtLeast(1)
            order.forEach { c ->
                val n = stats.byCategory[c] ?: 0
                Bar("${c.symbol} ${c.label}", n, n.toFloat() / max, c)
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Yatay çubuk: ad + simge + renk + sayı (yalnız renge dayanmaz). */
@Composable
private fun Bar(label: String, n: Int, fraction: Float, c: Category) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.color, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(130.dp))
        Box(Modifier.weight(1f).height(20.dp)) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceAtLeast(0.01f))
                    .clip(RoundedCornerShape(4.dp)).background(c.color)
            )
        }
        Text("$n", modifier = Modifier.width(48.dp).padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

package com.suzgec.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.suzgec.app.data.Dismiss
import com.suzgec.app.data.NotificationRecord
import com.suzgec.app.model.Ordering
import androidx.compose.foundation.layout.size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.suzgec.app.rules.Category
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val boxTimeFmt = SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr"))

private val BOX_ORDER = Ordering.BOX_ORDER

/**
 * Kaydırarak kutudan çıkarmak için satır genişliğinin ne kadarı kaydırılmalı (yanlışlıkla tetiklenmesin).
 * Yatay kaydırma; dikey kaydırma (liste) ve ileride eklenecek uzun basıp sürükleyerek sıralama ile çakışmaz.
 */
private const val SWIPE_THRESHOLD = 0.4f

/**
 * Kutu içi sıra (CLAUDE.md §7): kategori, sonra sıra değeri (elle sıra ya da önem puanı + sıra puanı),
 * sonra zaman (yeni üstte). Bkz. [Ordering]; çift ve elle sıra yoksa eski sırayla aynı.
 */
fun sortBox(list: List<NotificationRecord>, deltas: Map<Ordering.Key, Double> = emptyMap()): List<NotificationRecord> =
    Ordering.sortBox(list, deltas)

/** Bir menü satırı. */
data class MenuAction(val label: String, val onClick: () -> Unit)

/** Kutudan çıkarma eylemleri (kayıt silinmez; bkz. [Dismiss]). */
class BoxActions(
    /** Bildirime dokunup uygulamasını açtı. */
    val onOpened: (NotificationRecord) -> Unit,
    /** Kaydırma / Tümünü temizle: ids, yöntem. */
    val onDismiss: (List<Long>, String) -> Unit,
    /** Snackbar'daki "Geri al". */
    val onUndo: (List<Long>) -> Unit,
)

/** Kutudan çıkar ve altta "Geri al" Snackbar'ı göster. */
private class Remover(
    private val actions: BoxActions,
    private val snackbar: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    fun remove(ids: List<Long>, method: String) {
        if (ids.isEmpty()) return
        actions.onDismiss(ids, method)
        val msg = if (ids.size == 1) "Kaldırıldı" else "${ids.size} bildirim kaldırıldı"
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(msg, actionLabel = "Geri al", duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) actions.onUndo(ids)
        }
    }
}

@Composable
private fun rememberRemover(actions: BoxActions, snackbar: SnackbarHostState): Remover {
    val scope = rememberCoroutineScope()
    return remember(actions, snackbar) { Remover(actions, snackbar, scope) }
}

/** Bildirim kutusu: Tümü / Kritik / Önemli / Normal / Önemsiz. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScreen(
    records: List<NotificationRecord>,
    deletedCount: Int,
    paused: Boolean,
    dndMissing: Boolean,
    onOpenDeleted: () -> Unit,
    onResume: () -> Unit,
    onGrantDnd: () -> Unit,
    onMove: (NotificationRecord, Category) -> Unit,
    actions: BoxActions,
    menu: List<MenuAction>,
    /** Sürüklemelerden öğrenilen sıra puanları (yalnız kategori içi sıra). */
    orderDeltas: Map<Ordering.Key, Double> = emptyMap(),
    /** Tek kategori sekmesinde sürükleme: (görünen liste, eski konum, yeni konum). */
    onReorder: (List<NotificationRecord>, Int, Int) -> Unit = { _, _, _ -> },
) {
    val sorted = remember(records, orderDeltas) { sortBox(records, orderDeltas) }
    val tabs = listOf<Category?>(null) + BOX_ORDER
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val remover = rememberRemover(actions, snackbar)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Süzgeç") },
                actions = {
                    OutlinedButton(onClick = onOpenDeleted) { Text("${Category.SILINENLER.symbol} Silinenler ($deletedCount)") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menü") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        menu.forEach { a ->
                            DropdownMenuItem(text = { Text(a.label) }, onClick = { menuOpen = false; a.onClick() })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (paused) {
                Banner("Süzgeç duraklatıldı: bildirimlere dokunmuyor, Rahatsız Etmeyin kapalı.", "Devam et", onResume)
            } else if (dndMissing) {
                Banner("Sesleri Süzgeç'in yönetebilmesi için Rahatsız Etmeyin erişimi gerekli.", "Erişim ver", onGrantDnd)
            }
            PrimaryScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                tabs.forEachIndexed { i, c ->
                    val n = if (c == null) sorted.size else sorted.count { it.suzgecCategory == c.name }
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(if (c == null) "Tümü ($n)" else "${c.symbol} ${c.label} ($n)") },
                    )
                }
            }
            val shown = tabs[tab].let { c -> if (c == null) sorted else sorted.filter { it.suzgecCategory == c.name } }
            if (shown.isEmpty()) {
                Text(
                    if (sorted.isEmpty()) "Bildirim Kutunuz Temiz" else "Bu sekmede bildirim yok.",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                var moving by remember { mutableStateOf<NotificationRecord?>(null) }
                ClearAllRow(shown.size) { remover.remove(shown.map { it.id }, Dismiss.CLEAR_ALL) }
                val note: (NotificationRecord) -> String? = { Ordering.note(it, orderDeltas) }
                if (tabs[tab] == null) {
                    // "Tümü": sürükleme yok (kategoriler arası taşıma "Taşı…" ile).
                    RecordList(
                        shown,
                        onOpened = actions.onOpened,
                        onSwiped = { remover.remove(listOf(it.id), Dismiss.SWIPED) },
                        orderNote = note,
                    ) { rec ->
                        TextButton(onClick = { moving = rec }) { Text("Taşı…") }
                    }
                } else {
                    ReorderableRecordList(
                        shown,
                        onOpened = actions.onOpened,
                        onSwiped = { remover.remove(listOf(it.id), Dismiss.SWIPED) },
                        orderNote = note,
                        onReorder = onReorder,
                        onMoveCategory = { moving = it },
                    )
                }
                moving?.let { rec ->
                    MoveDialog(rec, onDismiss = { moving = null }) { c -> moving = null; onMove(rec, c) }
                }
            }
        }
    }
}

/** Silinenler: geri alınabilir (başka kategoriye taşıma); kaydırma ve Tümünü temizle kutudaki gibi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeletedScreen(
    records: List<NotificationRecord>,
    onBack: () -> Unit,
    onRestore: (NotificationRecord) -> Unit,
    actions: BoxActions,
) {
    val snackbar = remember { SnackbarHostState() }
    val remover = rememberRemover(actions, snackbar)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${Category.SILINENLER.symbol} Silinenler (${records.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Görmek istemediğin içerikler burada durur. Yanlışlıkla buraya düşen bir bildirimi geri alabilirsin. " +
                    "7 günden eskiler kendiliğinden kalkar.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (records.isEmpty()) {
                Text("Silinen bildirim yok.", modifier = Modifier.padding(24.dp))
            } else {
                ClearAllRow(records.size) { remover.remove(records.map { it.id }, Dismiss.CLEAR_ALL) }
                RecordList(
                    records,
                    onOpened = actions.onOpened,
                    onSwiped = { remover.remove(listOf(it.id), Dismiss.SWIPED) },
                ) { rec ->
                    OutlinedButton(onClick = { onRestore(rec) }) { Text("Geri al (Normal'e taşı)") }
                }
            }
        }
    }
}

@Composable
private fun ClearAllRow(count: Int, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onClear) { Text("Tümünü temizle ($count)") }
    }
}

@Composable
private fun Banner(text: String, action: String, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Button(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordList(
    records: List<NotificationRecord>,
    onOpened: (NotificationRecord) -> Unit,
    onSwiped: (NotificationRecord) -> Unit,
    orderNote: (NotificationRecord) -> String? = { null },
    extra: @Composable (NotificationRecord) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(records, key = { it.id }) { rec ->
            // rememberSwipeToDismissBoxState kaydırılmış durumu (rememberSaveable) saklar: "Geri al" ile aynı
            // anahtarla dönen satır ekran dışında, görünmez kalıyordu. Düz remember: dönen satır yeni durumla başlar.
            val state = remember {
                SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { total -> total * SWIPE_THRESHOLD })
            }
            SwipeToDismissBox(
                state = state,
                backgroundContent = { SwipeBackground(state.dismissDirection) },
                modifier = Modifier.animateItem(),
                onDismiss = { onSwiped(rec) },
            ) {
                RecordCard(rec, onClick = { openTarget(context, rec); onOpened(rec) }, orderNote = orderNote(rec), extra = extra)
            }
        }
    }
}

/**
 * Tek kategori sekmesi: sürükleyerek sıralama (CLAUDE.md §7). Sürükleme yalnız sağdaki ≡ tutamaçtan (48 dp)
 * dokunup çekince hemen başlar; yatay kaydırarak kaldırma, dikey liste kaydırma ve "Taşı…" aynen çalışır. Satır menüsünde
 * yedek yol: "Yukarı taşı / Aşağı taşı". Bırakınca [onReorder] (görünen liste, eski konum, yeni konum).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderableRecordList(
    records: List<NotificationRecord>,
    onOpened: (NotificationRecord) -> Unit,
    onSwiped: (NotificationRecord) -> Unit,
    orderNote: (NotificationRecord) -> String?,
    onReorder: (List<NotificationRecord>, Int, Int) -> Unit,
    onMoveCategory: (NotificationRecord) -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Sürükleme sırasında yerel kopya; veritabanı yeni sırayı yazınca kayıtlar değişir ve kopya yenilenir.
    var display by remember(records) { mutableStateOf(records) }
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = display.indexOfFirst { it.id == from.key }
        val t = display.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) display = display.toMutableList().apply { add(t, removeAt(f)) }
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(display, key = { it.id }) { rec ->
            ReorderableItem(reorderState, key = rec.id) { dragging ->
                val state = remember {
                    SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, positionalThreshold = { total -> total * SWIPE_THRESHOLD })
                }
                SwipeToDismissBox(
                    state = state,
                    backgroundContent = { SwipeBackground(state.dismissDirection) },
                    gesturesEnabled = !dragging,
                    onDismiss = { onSwiped(rec) },
                ) {
                    val index = display.indexOf(rec)
                    RecordCard(
                        rec,
                        onClick = { openTarget(context, rec); onOpened(rec) },
                        orderNote = orderNote(rec),
                        elevated = dragging,
                        handle = {
                            // Tutamaçtan dokunup çekince sürükleme hemen başlar (uzun basma yok, 25 Kasım).
                            // Dokunma alanı 48 dp; başlarken kısa titreşim, satır yükselir (elevated).
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(48.dp)
                                    .semantics { contentDescription = "Sıralamak için sürükle" }
                                    .draggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                            dragFrom = records.indexOfFirst { it.id == rec.id }
                                        },
                                        onDragStopped = {
                                            val f = dragFrom
                                            val t = display.indexOfFirst { it.id == rec.id }
                                            dragFrom = null
                                            if (f != null && f >= 0 && t >= 0 && f != t) onReorder(records, f, t)
                                        },
                                    ),
                            ) {
                                Text(
                                    "≡",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onMoveCategory(rec) }) { Text("Taşı…") }
                            RowMenu(
                                canUp = index > 0,
                                canDown = index in 0 until display.lastIndex,
                                onUp = { onReorder(records, records.indexOf(rec), records.indexOf(rec) - 1) },
                                onDown = { onReorder(records, records.indexOf(rec), records.indexOf(rec) + 1) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Satır menüsü: sürüklemenin erişilebilir yedek yolu. */
@Composable
private fun RowMenu(canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "Satır menüsü") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Yukarı taşı") }, enabled = canUp, onClick = { open = false; onUp() })
            DropdownMenuItem(text = { Text("Aşağı taşı") }, enabled = canDown, onClick = { open = false; onDown() })
        }
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    val align = if (direction == SwipeToDismissBoxValue.EndToStart) Alignment.CenterEnd else Alignment.CenterStart
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = align,
    ) {
        Text("Kaldır", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RecordCard(
    rec: NotificationRecord,
    onClick: () -> Unit,
    orderNote: String? = null,
    elevated: Boolean = false,
    handle: (@Composable () -> Unit)? = null,
    extra: @Composable (NotificationRecord) -> Unit,
) {
    ElevatedCard(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = if (elevated) CardDefaults.elevatedCardElevation(defaultElevation = 8.dp) else CardDefaults.elevatedCardElevation(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val cat = runCatching { Category.valueOf(rec.suzgecCategory ?: "") }.getOrNull()
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (cat != null) {
                    Text(
                        "${cat.symbol} ${cat.label}",
                        color = cat.color,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(
                    rec.appLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    boxTimeFmt.format(Date(rec.postTime)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                handle?.invoke()
            }
            if (!rec.title.isNullOrBlank()) {
                Text(rec.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val body = rec.bigText ?: rec.text
            if (!body.isNullOrBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            rec.reason?.let {
                Text(
                    "Neden burada? $it" + (orderNote?.let { n -> " · $n" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra(rec)
        }
    }
}

/** Kategori düzeltmesi: Süzgeç bundan öğrenir (kişiselleştirme v1). */
@Composable
private fun MoveDialog(rec: NotificationRecord, onDismiss: () -> Unit, onPick: (Category) -> Unit) {
    val current = rec.suzgecCategory
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nereye taşınsın?") },
        text = {
            Column {
                Text(
                    "${rec.appLabel}: ${rec.title ?: rec.text.orEmpty()}".take(80),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                listOf(Category.KRITIK, Category.ONEMLI, Category.NORMAL, Category.ONEMSIZ, Category.SILINENLER)
                    .filter { it.name != current }
                    .forEach { c ->
                        TextButton(onClick = { onPick(c) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${c.symbol} ${c.label}", color = c.color, modifier = Modifier.fillMaxWidth())
                        }
                    }
                Text(
                    "Süzgeç bu düzeltmeyi hatırlar: aynı göndericiden gelen benzer bildirimler bir dahakine buna göre sınıflanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

package com.suzgec.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.suzgec.app.data.BoxOpen
import com.suzgec.app.data.BoxOpenCount
import com.suzgec.app.model.Ordering
import com.suzgec.app.delivery.Delivery
import com.suzgec.app.data.Dismiss
import com.suzgec.app.ui.BoxActions
import com.suzgec.app.ui.BoxScreen
import com.suzgec.app.ui.DeletedScreen
import com.suzgec.app.ui.LogScreen
import com.suzgec.app.ui.MenuAction
import com.suzgec.app.ui.PermissionScreen
import com.suzgec.app.ui.SettingsScreen
import com.suzgec.app.ui.SuzgecTheme
import com.suzgec.app.ui.WizardScreen
import com.suzgec.app.ui.WeeklyScreen
import com.suzgec.app.stats.Weekly
import com.suzgec.app.stats.WeeklyStats
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import android.util.Log
import com.suzgec.app.ui.isListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

private enum class Screen { BOX, DELETED, SETTINGS, WEEKLY, LOG }

class MainActivity : ComponentActivity() {

    private var hasAccess by mutableStateOf(false)
    private var dndMissing by mutableStateOf(false)
    private var screen by mutableStateOf(Screen.BOX)
    /** onResume'da artar: sihirbaz izin durumlarını yeniden okur. */
    private var resumeTick by mutableIntStateOf(0)

    private val app get() = application as SuzgecApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        // Uygulama açılınca kaçan teslimi kontrol et ve zamanlayıcıyı tazele.
        lifecycleScope.launch(Dispatchers.IO) { Delivery.checkMissed(this@MainActivity) }

        val dao = app.database.notificationDao()
        // Silinenler'de 7 günden eskiler kutudan çıkar (kayıt silinmez).
        lifecycleScope.launch(Dispatchers.IO) { dao.cleanOldDeleted(System.currentTimeMillis()) }
        // Kutudan çıkarma: kişiselleştirmeye (δ) girmez, yalnız kayda geçer.
        val boxActions = BoxActions(
            onOpened = { rec -> lifecycleScope.launch(Dispatchers.IO) { dao.dismiss(listOf(rec.id), System.currentTimeMillis(), Dismiss.OPENED) } },
            onDismiss = { ids, method -> lifecycleScope.launch(Dispatchers.IO) { dao.dismiss(ids, System.currentTimeMillis(), method) } },
            onUndo = { ids -> lifecycleScope.launch(Dispatchers.IO) { dao.undismiss(ids) } },
        )
        val wizardInitial = runBlocking { app.settings.wizardDone.first() }
        setContent {
            SuzgecTheme {
                val wizardDone by app.settings.wizardDone.collectAsStateWithLifecycle(initialValue = wizardInitial)
                if (!wizardDone) {
                    WizardScreen(app, resumeTick) { finishWizard() }
                    return@SuzgecTheme
                }
                if (!hasAccess) {
                    PermissionScreen()
                    return@SuzgecTheme
                }
                val box by dao.observeBox().collectAsStateWithLifecycle(initialValue = emptyList())
                val deleted by dao.observeDeleted().collectAsStateWithLifecycle(initialValue = emptyList())
                // Sürükleyerek sıralama: çiftler → sıra puanları (yalnız kategori içi sıra; kategori kararına girmez).
                val orderPairs by dao.observeOrderPairs().collectAsStateWithLifecycle(initialValue = emptyList())
                val orderDeltas = remember(orderPairs) { Ordering.deltas(orderPairs) }
                val paused by app.paused.collectAsStateWithLifecycle()
                BackHandler(enabled = screen != Screen.BOX) { screen = Screen.BOX }
                when (screen) {
                    Screen.BOX -> BoxScreen(
                        records = box,
                        deletedCount = deleted.size,
                        paused = paused,
                        dndMissing = dndMissing,
                        onOpenDeleted = { screen = Screen.DELETED },
                        onResume = { app.setPaused(false) },
                        onGrantDnd = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        },
                        onMove = { rec, c ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                dao.moveByUser(rec.id, c.name, c.label, System.currentTimeMillis())
                            }
                        },
                        actions = boxActions,
                        menu = menu(paused),
                        orderDeltas = orderDeltas,
                        onReorder = { list, from, to ->
                            val pairsNow = orderPairs
                            lifecycleScope.launch(Dispatchers.IO) {
                                val r = Ordering.reorder(list, from, to, pairsNow, System.currentTimeMillis())
                                    ?: return@launch
                                dao.applyReorder(r.ranks, r.pairs)
                                Log.i("Suzgec", "Sıralama: ${list[from].appLabel} $from → $to, ${r.pairs.size} çift")
                            }
                        },
                    )
                    Screen.DELETED -> DeletedScreen(
                        records = deleted,
                        onBack = { screen = Screen.BOX },
                        onRestore = { rec ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                dao.restoreFromDeleted(rec.id, System.currentTimeMillis())
                            }
                        },
                        actions = boxActions,
                    )
                    Screen.SETTINGS -> SettingsScreen(app) { screen = Screen.BOX }
                    Screen.WEEKLY -> {
                        var stats by remember { mutableStateOf<WeeklyStats?>(null) }
                        LaunchedEffect(Unit) { stats = withContext(Dispatchers.IO) { Weekly.compute(dao) } }
                        WeeklyScreen(stats) { screen = Screen.BOX }
                    }
                    Screen.LOG -> LogScreen(dao)
                }
            }
        }
    }

    private fun finishWizard() {
        app.appScope.launch {
            app.settings.setWizardDone(true)
            if (!app.paused.value) app.dnd.enable()
            Delivery.schedule(this@MainActivity)
        }
        screen = Screen.BOX
    }

    private fun menu(paused: Boolean): List<MenuAction> = buildList {
        add(MenuAction("Haftalık özet") { screen = Screen.WEEKLY })
        add(MenuAction("Ayarlar") { screen = Screen.SETTINGS })
        add(MenuAction(if (paused) "Süzgeç'i sürdür" else "Süzgeç'i duraklat") { app.setPaused(!paused) })
        add(MenuAction("Bildirim kaydı (tümü)") { screen = Screen.LOG })
        // Deneme seçenekleri gizli geliştirici menüsünde (Ayarlar → sürüm satırına 7 dokunuş).
        add(MenuAction("Verileri dışa aktar") {
            lifecycleScope.launch {
                val f = withContext(Dispatchers.IO) { Weekly.exportCsv(this@MainActivity, app.database.notificationDao()) }
                Log.i("Suzgec", "CSV yazıldı: ${f.absolutePath}")
                Toast.makeText(this@MainActivity, "Dışa aktarıldı: ${f.parentFile?.absolutePath}", Toast.LENGTH_LONG).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        // Kullanıcı ayarlardan döndüğünde izinleri yeniden kontrol et.
        hasAccess = isListenerEnabled(this)
        dndMissing = !app.dnd.hasAccess()
        if (hasAccess) {
            // DND erişimi yeni verildiyse öncelik modunu aç.
            if (!dndMissing && !app.paused.value) app.appScope.launch { app.dnd.enable() }
            recordBoxOpen()
        }
    }

    /** Kutu açılışını say (teslimden sonraki 30 dk içinde mi?) ve kutudakileri görüldü say. */
    private fun recordBoxOpen() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                val afterDelivery = now - app.settings.lastDelivery() < 30 * 60 * 1000L
                val dao = app.database.notificationDao()
                // 5 dakika içindeki tekrar girişler sayılmaz (BoxOpenCount).
                if (BoxOpenCount.shouldCount(dao.lastBoxOpen(), now, afterDelivery)) {
                    dao.insertBoxOpen(BoxOpen(time = now, afterDelivery = afterDelivery))
                }
                dao.markBoxSeen(now)
            }
        }
    }
}

package com.suzgec.app.data

import android.service.notification.NotificationListenerService
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Room migration'ı ve kutu sorguları, gerçek SQLite (bundled, JVM) üzerinde.
 * Şemalar Room'un dışa aktardığı app/schemas/…/N.json dosyalarından kurulur.
 */
class MigrationAndBoxSqlTest {

    private val driver = BundledSQLiteDriver()
    private lateinit var db: SQLiteConnection

    private fun schema(version: Int) =
        JSONObject(File("schemas/com.suzgec.app.data.SuzgecDatabase/$version.json").readText()).getJSONObject("database")

    /** N.json'daki CREATE TABLE / INDEX komutlarıyla boş veritabanı kur. */
    private fun create(conn: SQLiteConnection, version: Int) {
        val entities = schema(version).getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            val table = e.getString("tableName")
            conn.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
            val idx = e.optJSONArray("indices") ?: continue
            for (j in 0 until idx.length()) {
                conn.execSQL(idx.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
    }

    private fun rows(conn: SQLiteConnection, sql: String): List<List<String?>> = conn.prepare(sql).use { st ->
        buildList {
            while (st.step()) add((0 until st.getColumnCount()).map { if (st.isNull(it)) null else st.getText(it) })
        }
    }

    private fun long(sql: String): Long = db.prepare(sql).use { it.step(); it.getLong(0) }

    /** Room'un şema doğrulamasının baktığı bilgiler: sütunlar ve indeksler. */
    private fun shape(conn: SQLiteConnection, table: String): Pair<Set<List<String?>>, Set<List<String?>>> {
        val cols = rows(conn, "PRAGMA table_info(`$table`)").map { it.drop(1) }.toSet() // cid hariç
        val idx = rows(conn, "PRAGMA index_list(`$table`)").map { listOf(it[1], it[2]) }.toSet() // ad, unique
        return cols to idx
    }

    private var nextId = 1

    private fun insert(category: String, postTime: Long = 1_000L, dismissedAt: Long? = null, key: String = "k${nextId}") {
        val id = nextId++
        db.execSQL(
            "INSERT INTO notifications (id, `key`, packageName, appLabel, postTime, flags, isOngoing, isGroupSummary, " +
                "hasContentIntent, firstSeen, lastUpdated, updateCount, removed, waitedMs, cancelledBySuzgec, senderHash, " +
                "restoredByUser, suzgecCategory, dismissedAt) VALUES ($id, '$key', 'p', 'Uygulama', $postTime, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, '', 0, " +
                "'$category', ${dismissedAt ?: "NULL"})"
        )
    }

    private fun countInBox(category: String) = long(BoxSql.COUNT_IN_BOX.replace(":category", "'$category'"))

    @Before
    fun setUp() {
        db = driver.open(":memory:")
        create(db, 5)
        // v5'te gerçek kullanımdan bir kayıt
        db.execSQL(
            "INSERT INTO notifications (`key`, packageName, appLabel, title, postTime, flags, isOngoing, isGroupSummary, " +
                "hasContentIntent, firstSeen, lastUpdated, updateCount, removed, waitedMs, cancelledBySuzgec, senderHash, " +
                "restoredByUser, suzgecCategory, reason) VALUES ('eski', 'com.whatsapp', 'WhatsApp', 'Annem', 42, 0, 0, 0, 1, 42, 42, 0, 0, 0, 0, 'h', 0, 'ONEMLI', 'Birebir')"
        )
        MIGRATION_5_6.migrate(db)
        nextId = 100
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun arsivSatiriKumarKaydiniKorur() {
        // Aynı key: önce kumar (Silinenler), sonra Önemli. Eski satır arşivlenir, asıl key ile yeni satır açılır.
        val key = "0|com.whatsapp|1|sohbet|10285"
        insert("SILINENLER", postTime = 1_000, key = key)
        db.execSQL(BoxSql.RENAME_KEY.replace(":newKey", "'$key#arsiv-0'").replace(":id", "100"))
        insert("ONEMLI", postTime = 2_000, key = key) // tekil indeks ihlali olmaz
        assertEquals(1L, long(BoxSql.DELETED.replace("SELECT *", "SELECT COUNT(*)").substringBefore(" ORDER BY")))
        assertEquals(1L, countInBox("ONEMLI") - 1) // eski v5 kaydı + yeni Önemli
        // Panelden kaldırma (sistem_paneli) yalnız asıl key'deki satıra uygulanır; arşiv satırı etkilenmez.
        db.execSQL(BoxSql.DISMISS_FROM_PANEL.replace(":time", "9").replace(":key", "'$key'"))
        assertEquals(listOf(listOf("SILINENLER", null), listOf("ONEMLI", "9")), rows(db,
            "SELECT suzgecCategory, dismissedAt FROM notifications WHERE `key` LIKE '0|com.whatsapp|1|sohbet%' ORDER BY id"))
        assertEquals(1L, long(BoxSql.DELETED.replace("SELECT *", "SELECT COUNT(*)").substringBefore(" ORDER BY")))
        // Aynı kategori güncellemesi tek satırda kalır (arşiv yok): toplam 2 satır.
        assertEquals(2L, long("SELECT COUNT(*) FROM notifications WHERE `key` LIKE '0|com.whatsapp|1|sohbet%'"))
    }

    @Test
    fun migrationSemasiOdaninBekledigiV6IleAyni() {
        val fresh = driver.open(":memory:")
        try {
            create(fresh, 6)
            for (table in listOf("notifications", "box_opens", "corrections")) {
                assertEquals("tablo $table", shape(fresh, table), shape(db, table))
            }
        } finally {
            fresh.close()
        }
    }

    @Test
    fun migration6den7yeEkleyiciVeOdaninBekledigiIleAyni() {
        // setUp: v5 → v6 (eski kayıt var). Şimdi v6 → v7: yalnız yeni tablo + boş bırakılabilir sütun.
        MIGRATION_6_7.migrate(db)
        val fresh = driver.open(":memory:")
        try {
            create(fresh, 7)
            for (table in listOf("notifications", "box_opens", "corrections", "order_pairs")) {
                assertEquals("tablo $table", shape(fresh, table), shape(db, table))
            }
        } finally {
            fresh.close()
        }
        // Eski kayıt olduğu gibi duruyor; yeni sütun boş, çift tablosu boş.
        assertEquals(
            listOf(listOf("eski", "WhatsApp", "Annem", "ONEMLI", "Birebir", null)),
            rows(db, "SELECT `key`, appLabel, title, suzgecCategory, reason, manualRank FROM notifications"),
        )
        assertEquals(0L, long("SELECT COUNT(*) FROM order_pairs"))
        assertEquals(1L, countInBox("ONEMLI"))
        // Sürükleme sonucu yazılabiliyor.
        db.execSQL("UPDATE notifications SET manualRank = 2.37 WHERE `key` = 'eski'")
        db.execSQL(
            "INSERT INTO order_pairs (time, category, aboveId, belowId, aboveApp, aboveSender, belowApp, belowSender) " +
                "VALUES (1, 'ONEMLI', 1, 2, 'WhatsApp', 'h', 'Outlook', '')"
        )
        assertEquals(1L, long("SELECT COUNT(*) FROM order_pairs"))
    }

    @Test
    fun migrationEskiKaydiKorurYeniAlanlarBos() {
        val r = rows(db, "SELECT `key`, appLabel, title, suzgecCategory, reason, dismissedAt, dismissMethod FROM notifications WHERE `key` = 'eski'")
        assertEquals(listOf(listOf("eski", "WhatsApp", "Annem", "ONEMLI", "Birebir", null, null)), r)
        assertEquals(1L, countInBox("ONEMLI")) // eski kayıt kutuda görünür
    }

    @Test
    fun sayimlarYalnizKutudakileriGorur() {
        insert("NORMAL"); insert("NORMAL"); insert("NORMAL", dismissedAt = 5)
        insert("ONEMSIZ", dismissedAt = 5)
        insert("SILINENLER"); insert("SILINENLER", dismissedAt = 5)
        assertEquals(2L, countInBox("NORMAL"))
        assertEquals(0L, countInBox("ONEMSIZ"))
        assertEquals(3L, long(BoxSql.BOX.replace("SELECT *", "SELECT COUNT(*)"))) // 2 Normal + eski Önemli
        assertEquals(1L, long(BoxSql.DELETED.replace("SELECT *", "SELECT COUNT(*)").substringBefore(" ORDER BY")))
        // Haftalık özet ve CSV bütün kayıtları görür (kutudan çıkanlar dahil).
        assertEquals(7L, long("SELECT COUNT(*) FROM notifications"))
    }

    @Test
    fun kaldirVeGeriAl() {
        insert("NORMAL"); insert("ONEMSIZ")
        db.execSQL(BoxSql.DISMISS_IDS.replace(":time", "7").replace(":method", "'${Dismiss.CLEAR_ALL}'").replace(":ids", "100, 101"))
        assertEquals(0L, countInBox("NORMAL") + countInBox("ONEMSIZ"))
        assertEquals(listOf(listOf("7", "tumunu_temizle")), rows(db, "SELECT dismissedAt, dismissMethod FROM notifications WHERE id = 100"))
        // Kayıt silinmedi
        assertEquals(1L, long("SELECT COUNT(*) FROM notifications WHERE id = 100"))
        db.execSQL(BoxSql.UNDISMISS_IDS.replace(":ids", "100, 101"))
        assertEquals(2L, countInBox("NORMAL") + countInBox("ONEMSIZ"))
        assertEquals(listOf(listOf<String?>(null, null)), rows(db, "SELECT dismissedAt, dismissMethod FROM notifications WHERE id = 100"))
    }

    @Test
    fun silinenler7GunTemizligi() {
        val now = 100L * 24 * 3600 * 1000
        val day = 24L * 3600 * 1000
        insert("SILINENLER", postTime = now - 8 * day)  // 100: eski → çıkar
        insert("SILINENLER", postTime = now - 6 * day)  // 101: kalır
        insert("NORMAL", postTime = now - 30 * day)     // 102: kutu kategorisi, dokunulmaz
        db.execSQL(BoxSql.AUTO_CLEAN_DELETED.replace(":time", "$now").replace(":before", "${now - Dismiss.DELETED_KEEP_MS}"))
        assertEquals(
            listOf(listOf("100", "otomatik"), listOf("101", null), listOf("102", null)),
            rows(db, "SELECT id, dismissMethod FROM notifications WHERE id >= 100 ORDER BY id"),
        )
    }

    @Test
    fun sistemPanelindenKaldirmaYalnizKutuKategorilerinde() {
        insert("KRITIK", key = "kritik"); insert("SILINENLER", key = "sil")
        for (k in listOf("kritik", "sil")) {
            db.execSQL(BoxSql.DISMISS_FROM_PANEL.replace(":time", "9").replace(":key", "'$k'"))
        }
        assertEquals(listOf(listOf("sistem_paneli")), rows(db, "SELECT dismissMethod FROM notifications WHERE `key` = 'kritik'"))
        assertEquals(listOf(listOf<String?>(null)), rows(db, "SELECT dismissMethod FROM notifications WHERE `key` = 'sil'"))
    }

    @Test
    fun suzgecinKendiIptaliSayilmaz() {
        assertFalse(Dismiss.isUserPanelRemoval(NotificationListenerService.REASON_LISTENER_CANCEL))
        assertFalse(Dismiss.isUserPanelRemoval(NotificationListenerService.REASON_APP_CANCEL))
        assertFalse(Dismiss.isUserPanelRemoval(NotificationListenerService.REASON_APP_CANCEL_ALL))
        assertTrue(Dismiss.isUserPanelRemoval(NotificationListenerService.REASON_CLICK))
        assertTrue(Dismiss.isUserPanelRemoval(NotificationListenerService.REASON_CANCEL))
        assertTrue(Dismiss.isUserPanelRemoval(NotificationListenerService.REASON_CANCEL_ALL))
    }

    @Test
    fun kaldirmaDuzeltmeSayilmaz() {
        insert("NORMAL")
        db.execSQL(BoxSql.DISMISS_IDS.replace(":time", "7").replace(":method", "'${Dismiss.SWIPED}'").replace(":ids", "100"))
        assertNull(rows(db, "SELECT id FROM corrections").firstOrNull())
    }
}

package com.suzgec.app.data

import android.service.notification.NotificationListenerService
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.execSQL

/**
 * Kutudan çıkma (CLAUDE.md §1, §4). Kutudan çıkmak kaydı silmek değildir: `dismissedAt` dolar,
 * kutu ve teslim sayıları yalnız `dismissedAt IS NULL` kayıtları görür; haftalık özet ve CSV hepsini.
 * Kutudan çıkarmak bir kategori düzeltmesi değildir: kişiselleştirmeye (§7 δ) girmez, yalnız kayda geçer.
 */
object Dismiss {
    /** Kutudaki bildirime dokunup uygulamasını açtı. */
    const val OPENED = "acildi"
    /** Satırı sağa/sola kaydırdı. */
    const val SWIPED = "kaydirildi"
    /** Sekmenin "Tümünü temizle" düğmesi. */
    const val CLEAR_ALL = "tumunu_temizle"
    /** Silinenler'de 7 günden eski (uygulama açılışında ve teslim alarmında). */
    const val AUTO = "otomatik"
    /** Kritik/Önemli bildirimi telefonun panelinde açtı ya da kaydırdı. */
    const val SYSTEM_PANEL = "sistem_paneli"

    /** Silinenler kutusunda kalma süresi. */
    const val DELETED_KEEP_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Panelden kalkma nedeni kullanıcının kendi eylemi mi (açtı / kaydırdı / tümünü temizledi)?
     * `REASON_LISTENER_CANCEL` (Süzgeç'in kendi `cancelNotification`'ı) ve uygulamanın kendi iptalleri sayılmaz;
     * yoksa Normal/Önemsiz bildirimler kutuya düşer düşmez kutudan da çıkar.
     */
    fun isUserPanelRemoval(reason: Int): Boolean = when (reason) {
        NotificationListenerService.REASON_CLICK,
        NotificationListenerService.REASON_CANCEL,
        NotificationListenerService.REASON_CANCEL_ALL -> true
        else -> false
    }
}

/** Kutu sorguları; birim testi aynı SQL'i çalıştırır (BoxSqlTest). */
object BoxSql {
    const val BOX_CATEGORIES = "('KRITIK','ONEMLI','NORMAL','ONEMSIZ')"

    const val BOX = "SELECT * FROM notifications WHERE suzgecCategory IN $BOX_CATEGORIES AND dismissedAt IS NULL"

    /** Eski kaydı arşivle (Upsert.shouldArchive): satır olduğu gibi kalır, yalnız key'i değişir. */
    const val RENAME_KEY = "UPDATE notifications SET `key` = :newKey WHERE id = :id"
    const val DELETED = "SELECT * FROM notifications WHERE suzgecCategory = 'SILINENLER' AND dismissedAt IS NULL ORDER BY postTime DESC"

    /** Teslim özeti: kutuda (kutudan çıkmamış) kaç bildirim var. */
    const val COUNT_IN_BOX = "SELECT COUNT(*) FROM notifications WHERE suzgecCategory = :category AND dismissedAt IS NULL"

    const val DISMISS_IDS = "UPDATE notifications SET dismissedAt = :time, dismissMethod = :method WHERE id IN (:ids) AND dismissedAt IS NULL"
    const val UNDISMISS_IDS = "UPDATE notifications SET dismissedAt = NULL, dismissMethod = NULL WHERE id IN (:ids)"

    /** Telefonun panelinde açılan/kaydırılan bildirim kutudan da çıkar. */
    const val DISMISS_FROM_PANEL = "UPDATE notifications SET dismissedAt = :time, dismissMethod = '${Dismiss.SYSTEM_PANEL}' " +
        "WHERE `key` = :key AND dismissedAt IS NULL AND suzgecCategory IN $BOX_CATEGORIES"

    /** Silinenler'de [before]'dan eski kayıtlar kendiliğinden kutudan çıkar. */
    const val AUTO_CLEAN_DELETED = "UPDATE notifications SET dismissedAt = :time, dismissMethod = '${Dismiss.AUTO}' " +
        "WHERE suzgecCategory = 'SILINENLER' AND dismissedAt IS NULL AND postTime < :before"
}

/** v5 → v6: kutudan çıkma alanları. Yazılı migration: M51'deki gerçek kullanım verisi korunur. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    val sql = listOf(
        "ALTER TABLE `notifications` ADD COLUMN `dismissedAt` INTEGER",
        "ALTER TABLE `notifications` ADD COLUMN `dismissMethod` TEXT",
    )

    override fun migrate(db: SupportSQLiteDatabase) = sql.forEach { db.execSQL(it) }

    override fun migrate(connection: SQLiteConnection) = sql.forEach { connection.execSQL(it) }
}

/** v6 → v7 (25 Kasım): sürükleyerek sıralama. Yalnız ekleyici: yeni tablo + boş bırakılabilir sütun. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    val sql = listOf(
        "ALTER TABLE `notifications` ADD COLUMN `manualRank` REAL",
        "CREATE TABLE IF NOT EXISTS `order_pairs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, " +
            "`category` TEXT NOT NULL, `aboveId` INTEGER NOT NULL, `belowId` INTEGER NOT NULL, `aboveApp` TEXT NOT NULL, " +
            "`aboveSender` TEXT NOT NULL, `belowApp` TEXT NOT NULL, `belowSender` TEXT NOT NULL)",
    )

    override fun migrate(db: SupportSQLiteDatabase) = sql.forEach { db.execSQL(it) }

    override fun migrate(connection: SQLiteConnection) = sql.forEach { connection.execSQL(it) }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_5_6, MIGRATION_6_7)

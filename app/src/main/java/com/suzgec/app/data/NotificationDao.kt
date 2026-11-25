package com.suzgec.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    /** Kayıt ekranı: geliş zamanına göre, en yeni üstte (5 sn beklenenler de doğru yerde durur). */
    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun observeAll(): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notifications WHERE `key` = :key LIMIT 1")
    suspend fun findByKey(key: String): NotificationRecord?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: NotificationRecord): Long

    @Update
    suspend fun update(record: NotificationRecord)

    /** Aynı key varsa satırı günceller, yoksa yeni satır ekler. */
    @Transaction
    suspend fun upsertByKey(incoming: NotificationRecord) {
        val existing = findByKey(incoming.key)
        when {
            existing == null -> insert(incoming)
            // Kategori değiştiyse eski satır arşivlenir, yeni satır açılır (kayıt kaybı olmaz).
            Upsert.shouldArchive(existing, incoming) -> {
                renameKey(existing.id, Upsert.archiveKey(existing))
                insert(incoming)
            }
            else -> update(Upsert.merge(existing, incoming))
        }
    }

    @Query(BoxSql.RENAME_KEY)
    suspend fun renameKey(id: Long, newKey: String)

    @Query("UPDATE notifications SET removed = 1, removedAt = :time, removalReason = :reason WHERE `key` = :key")
    suspend fun markRemoved(key: String, time: Long, reason: Int?)

    /** Kaydın "Neden burada?" satırını ve kuralını değiştir (ör. altı boşalan özet silindi). */
    @Query("UPDATE notifications SET reason = :reason, rule = :rule WHERE `key` = :key")
    suspend fun setDecisionText(key: String, reason: String, rule: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()

    // --- Kutu ve Silinenler ---

    /** Kutudaki dört kategori; sıralama Kotlin'de (kategori, puan, zaman). */
    @Query(BoxSql.BOX)
    fun observeBox(): Flow<List<NotificationRecord>>

    @Query(BoxSql.DELETED)
    fun observeDeleted(): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun byId(id: Long): NotificationRecord?

    /** Teslim özeti: kutuda duran (kutudan çıkmamış) Normal ve Önemsiz sayıları. */
    @Query(BoxSql.COUNT_IN_BOX)
    suspend fun countInBox(category: String): Int

    // --- Kutudan çıkma (kayıt silinmez; kişiselleştirmeye girmez) ---

    @Query(BoxSql.DISMISS_IDS)
    suspend fun dismiss(ids: List<Long>, time: Long, method: String): Int

    /** "Geri al": kutuya geri koy. */
    @Query(BoxSql.UNDISMISS_IDS)
    suspend fun undismiss(ids: List<Long>)

    @Query(BoxSql.DISMISS_FROM_PANEL)
    suspend fun dismissFromPanel(key: String, time: Long): Int

    @Query(BoxSql.AUTO_CLEAN_DELETED)
    suspend fun autoCleanDeleted(time: Long, before: Long): Int

    /** Silinenler'de 7 günden eskileri kutudan çıkar (uygulama açılışı ve teslim alarmı). */
    suspend fun cleanOldDeleted(now: Long): Int = autoCleanDeleted(now, now - Dismiss.DELETED_KEEP_MS)

    @Query("UPDATE notifications SET seenAt = :time WHERE seenAt IS NULL AND suzgecCategory IN ('KRITIK','ONEMLI','NORMAL','ONEMSIZ')")
    suspend fun markBoxSeen(time: Long)

    // Kategori değişince elle verilen sıra o kategoriye ait olduğu için silinir (manualRank).
    @Query("UPDATE notifications SET suzgecCategory = :category, restoredByUser = :restored, reason = :reason, seenAt = NULL, manualRank = NULL WHERE id = :id")
    suspend fun setCategory(id: Long, category: String, reason: String, restored: Boolean)

    @Insert
    suspend fun insertBoxOpen(open: BoxOpen)

    @Insert
    suspend fun insertCorrection(c: Correction)

    @Query("SELECT * FROM box_opens ORDER BY time DESC LIMIT 1")
    suspend fun lastBoxOpen(): BoxOpen?

    @Query("SELECT COUNT(*) FROM box_opens WHERE time >= :since")
    suspend fun countBoxOpens(since: Long): Int

    /** Demo öncesi öğrenilenleri sıfırlamak için (debug menüsü). */
    @Query("DELETE FROM corrections")
    suspend fun clearCorrections()

    /** Kişiselleştirme sayaçları için tüm düzeltmeler. */
    @Query("SELECT * FROM corrections")
    fun observeCorrections(): Flow<List<Correction>>

    /**
     * Kullanıcının kategori düzeltmesi (kutuda taşıma ya da Silinenler'den geri alma):
     * bildirimi taşı ve (gönderen, uygulama, tür) için düzeltmeyi kaydet (§7).
     */
    @Transaction
    suspend fun moveByUser(id: Long, to: String, toLabel: String, time: Long) {
        val r = byId(id) ?: return
        val from = r.suzgecCategory ?: return
        if (from == to) return
        val restored = from == "SILINENLER"
        val what = if (restored) Upsert.RESTORED else Upsert.MOVED
        setCategory(id, to, "$what → $toLabel (önceki: ${r.reason ?: "-"})", restored || r.restoredByUser)
        insertCorrection(
            Correction(
                time = time, senderHash = r.senderHash, packageName = r.packageName, appLabel = r.appLabel,
                contentType = r.contentType, fromCategory = from, toCategory = to,
            )
        )
    }

    /** Silinenler'den geri alma: Normal'e taşı. */
    suspend fun restoreFromDeleted(id: Long, time: Long) = moveByUser(id, "NORMAL", "Normal", time)

    // --- Sürükleyerek sıralama (v7; kategori kararına girmez) ---

    @Query("SELECT * FROM order_pairs ORDER BY time")
    fun observeOrderPairs(): Flow<List<OrderPair>>

    @Query("SELECT * FROM order_pairs ORDER BY time")
    suspend fun allOrderPairs(): List<OrderPair>

    @Insert
    suspend fun insertOrderPairs(pairs: List<OrderPair>)

    @Query("UPDATE notifications SET manualRank = :rank WHERE id = :id")
    suspend fun setManualRank(id: Long, rank: Double)

    /** Bir sürüklemenin sonucu tek işlemde: sıra değerleri + "A, B'nin üstünde" çiftleri. */
    @Transaction
    suspend fun applyReorder(ranks: Map<Long, Double>, pairs: List<OrderPair>) {
        for ((id, rank) in ranks) setManualRank(id, rank)
        if (pairs.isNotEmpty()) insertOrderPairs(pairs)
    }

    // --- Haftalık özet ve dışa aktarma ---

    /** [since]'ten beri her Süzgeç kategorisinde kaç bildirim var. */
    @Query("SELECT suzgecCategory AS category, COUNT(*) AS n FROM notifications WHERE postTime >= :since AND suzgecCategory IS NOT NULL GROUP BY suzgecCategory")
    suspend fun countByCategory(since: Long): List<CategoryCount>

    @Query("SELECT COUNT(*) FROM notifications WHERE postTime >= :since AND cancelledBySuzgec = 1")
    suspend fun countCancelledBySuzgec(since: Long): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE postTime >= :since AND suzgecCategory = 'SILINENLER' AND contentType = :type")
    suspend fun countDeletedOfType(since: Long, type: Int): Int

    @Query("SELECT COUNT(*) FROM box_opens WHERE time >= :since AND afterDelivery = 0")
    suspend fun countOpensOutsideDelivery(since: Long): Int

    @Query("SELECT * FROM notifications ORDER BY postTime")
    suspend fun allForExport(): List<NotificationRecord>

    // --- Örnek haftalık veri (yalnız debug) ---

    @Insert
    suspend fun insertAll(records: List<NotificationRecord>)

    @Insert
    suspend fun insertBoxOpens(opens: List<BoxOpen>)

    @Query("DELETE FROM notifications WHERE `key` LIKE 'ornek|%'")
    suspend fun deleteSampleRecords()

    @Query("DELETE FROM box_opens WHERE id < 0")
    suspend fun deleteSampleOpens()
}

data class CategoryCount(val category: String, val n: Int)

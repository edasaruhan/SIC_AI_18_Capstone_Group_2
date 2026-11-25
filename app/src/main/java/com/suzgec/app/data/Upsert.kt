package com.suzgec.app.data

/**
 * Aynı key ile gelen güncellemenin mevcut kayıtla birleştirilmesi (NotificationDao.upsertByKey).
 * Room'dan bağımsız, birim testli.
 */
object Upsert {
    /** NotificationDao.moveByUser'ın "Neden burada?" satırı önekleri. */
    const val MOVED = "Sen taşıdın"
    const val RESTORED = "Silinenler'den geri alındı"

    /** Arşiv satırının key eki: "<key>#arsiv-<firstSeen>". */
    const val ARCHIVE_MARK = "#arsiv-"

    /** Arşivlenebilen kategoriler (kutu + Silinenler); dokunulmaz/model bekliyor kayıtları bölünmez. */
    private val ARCHIVABLE = setOf("KRITIK", "ONEMLI", "NORMAL", "ONEMSIZ", "SILINENLER")

    /**
     * Kayıt kaybını önleme (25 Kasım, migration'sız): aynı key ile gelen gerçek bir güncellemede (postTime değişti)
     * yeni karar eski kaydın kategorisinden farklıysa eski satır arşivlenir ("<key>#arsiv-<firstSeen>") ve
     * asıl key ile yeni satır açılır. Ör. WhatsApp'ta aynı sohbetten önce kumar (Silinenler), sonra normal
     * mesaj (Önemli): Silinenler'deki kumar kaydı kalır. Elle taşınmış kayıt yeniden sınıflanmadığı için bölünmez.
     */
    fun shouldArchive(existing: NotificationRecord, incoming: NotificationRecord): Boolean =
        incoming.postTime != existing.postTime &&
            !isUserMoved(existing) &&
            existing.suzgecCategory in ARCHIVABLE &&
            incoming.suzgecCategory != existing.suzgecCategory

    fun archiveKey(existing: NotificationRecord): String = existing.key + ARCHIVE_MARK + existing.firstSeen

    /** Arşiv satırının asıl bildirim key'i (hedef sayfa önbelleği için). */
    fun baseKey(key: String): String = key.substringBefore(ARCHIVE_MARK)

    /** Kullanıcı bu bildirimi elle taşıdı ya da Silinenler'den geri aldı mı? */
    fun isUserMoved(r: NotificationRecord): Boolean =
        r.restoredByUser || r.reason?.startsWith(MOVED) == true || r.reason?.startsWith(RESTORED) == true

    fun merge(existing: NotificationRecord, incoming: NotificationRecord): NotificationRecord {
        // Dinleyici yeniden bağlanınca paneldeki bildirimler tekrar okunur;
        // postTime değişmediyse bu bir güncelleme değildir.
        val changed = incoming.postTime != existing.postTime
        val merged = incoming.copy(
            id = existing.id,
            firstSeen = existing.firstSeen,
            lastUpdated = if (changed) incoming.lastUpdated else existing.lastUpdated,
            updateCount = existing.updateCount + if (changed) 1 else 0,
            removed = false,
            removedAt = null,
            removalReason = null,
            // Kutudan çıkmış bir bildirim yeni içerikle güncellenirse kutuya geri döner;
            // yeniden bağlanmada aynı bildirim tekrar okunursa kutu dışında kalır.
            dismissedAt = if (changed) null else existing.dismissedAt,
            dismissMethod = if (changed) null else existing.dismissMethod,
            restoredByUser = existing.restoredByUser || incoming.restoredByUser,
            // Kullanıcının sürükleyerek verdiği sıra güncellemede korunur (kategori aynı kaldığı sürece).
            manualRank = existing.manualRank.takeIf { incoming.suzgecCategory == existing.suzgecCategory },
        )
        // Kullanıcının taşıdığı kayıt yeniden sınıflanmaz; yalnız metni güncellenir (24 Kasım LinkedIn hatası).
        if (!isUserMoved(existing)) return merged
        return merged.copy(
            suzgecCategory = existing.suzgecCategory,
            contentType = existing.contentType,
            reason = existing.reason,
            rule = existing.rule,
            score = existing.score,
        )
    }
}

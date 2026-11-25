package com.suzgec.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Yakalanan bir bildirimin kaydı. Aynı `key` tekrar gelirse yeni satır açılmaz,
 * bu satır güncellenir (`updateCount` artar).
 *
 * Not: Bildirimin hedef sayfası (contentIntent) Room'a yazılamaz; bellekte
 * [com.suzgec.app.listener.IntentCache] içinde tutulur.
 */
@Entity(
    tableName = "notifications",
    indices = [Index(value = ["key"], unique = true), Index("lastUpdated")],
)
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    /** Notification.category (ör. "msg", "call", "promo"). */
    val category: String?,
    val channelId: String?,
    val postTime: Long,
    val flags: Int,
    val isOngoing: Boolean,
    val isGroupSummary: Boolean,
    /** MessagingStyle'dan; bilinmiyorsa null. */
    val isGroupConversation: Boolean?,
    val hasContentIntent: Boolean,
    val firstSeen: Long,
    val lastUpdated: Long,
    val updateCount: Int = 0,
    /** Bildirim panelden kalktı mı (kullanıcı, uygulama ya da Süzgeç tarafından). */
    val removed: Boolean = false,
    val removedAt: Long? = null,
    val removalReason: Int? = null,
    /** Süzgeç kategorisi ([com.suzgec.app.rules.Category] adı). */
    val suzgecCategory: String? = null,
    /** İçerik türü (1–11); kural belirleyemediyse null. */
    val contentType: Int? = null,
    /** "Neden burada?" satırı. */
    val reason: String? = null,
    /** Karar veren kuralın kısa kodu (ör. "guvenlik", "kumar", "model"). */
    val rule: String? = null,
    /** Modelin beklenen önem puanı (1–4); kural kararlarında null. Kutu sıralamasında kullanılacak. */
    val score: Double? = null,
    /** Karardan önce beklenen süre (5 sn kuralı), ms. */
    val waitedMs: Long = 0,
    /** Süzgeç bu bildirimi panelden sildi mi? */
    val cancelledBySuzgec: Boolean = false,
    /** Kullanıcı kutuyu açıp bu bildirimi gördüğü an; teslim özeti yalnız görülmemişleri sayar. */
    val seenAt: Long? = null,
    /** Gönderenin SHA-256 karması (§7: düz metin saklanmaz); kişiselleştirme anahtarı. */
    val senderHash: String = "",
    /** Kullanıcı Silinenler'den geri aldı mı? */
    val restoredByUser: Boolean = false,
    /**
     * Kutudan çıktığı an; kayıt silinmez (haftalık özet ve CSV bütün kayıtları kullanır).
     * null = kutuda. Bkz. [Dismiss].
     */
    val dismissedAt: Long? = null,
    /** Kutudan nasıl çıktı: [Dismiss] sabitlerinden biri. */
    val dismissMethod: String? = null,
    /**
     * Kullanıcının sürükleyerek verdiği sıra değeri (kategori içinde, büyük olan üstte; v7, 25 Kasım).
     * null = elle yerleştirilmedi: sıra puanı + önem puanıyla yerleşir. Kategori kararını etkilemez.
     */
    val manualRank: Double? = null,
)

/** Kutu her açıldığında bir satır (CLAUDE.md §1: teslim saatleri dışındaki girişler sayılır). */
@Entity(tableName = "box_opens")
data class BoxOpen(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    /** Son teslimden sonraki 30 dk içinde mi açıldı (teslimle gelen giriş)? */
    val afterDelivery: Boolean,
)

/**
 * Sürükleyerek sıralamadan bir "A, B'nin üstünde" çifti (v7, 25 Kasım). Yalnız kategori içi sırayı
 * öğrenmek için (sıra puanı, model/Ordering.kt); kategori kararına ve δ kişiselleştirmesine girmez.
 */
@Entity(tableName = "order_pairs")
data class OrderPair(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val category: String,
    val aboveId: Long,
    val belowId: Long,
    val aboveApp: String,
    val aboveSender: String,
    val belowApp: String,
    val belowSender: String,
)

/** Kullanıcının kategori düzeltmesi; kişiselleştirme (27 Kasım) bu sayaçları kullanacak. */
@Entity(tableName = "corrections")
data class Correction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val senderHash: String = "",
    val packageName: String,
    val appLabel: String,
    val contentType: Int?,
    val fromCategory: String,
    val toCategory: String,
)

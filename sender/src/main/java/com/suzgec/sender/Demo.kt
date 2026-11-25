package com.suzgec.sender

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Bundle

/** Süzgeç debug derlemesinde uygulama adı olarak bu extra kullanılır. */
const val EXTRA_FAKE_APP = "suzgec.debug.fakeApp"

/** Süzgeç debug derlemesinde kural katmanı paket listelerini bununla dener (öncelikli uygulama, filtre kapsamı...). */
const val EXTRA_FAKE_PKG = "suzgec.debug.fakePkg"
private const val CHANNEL = "demo"

enum class Kind { PLAIN, ONE_TO_ONE, GROUP_CHAT, MEDIA, NAVIGATION, PROGRESS, SHORT_LIVED, GROUP_SUMMARY, TELEGRAM_CHANNEL }

/** docs/demo_senaryosu.md ile aynı sıra ve id'ler. */
data class Demo(
    val id: String,
    val app: String,
    val title: String,
    val text: String,
    val expected: String,
    val kind: Kind = Kind.PLAIN,
    val category: String? = null,
    /** Sohbet bildirimlerinde gönderen kişi. */
    val sender: String? = null,
    /** Taklit edilen paket adı (Süzgeç debug'da kural katmanında kullanır); null: com.suzgec.sender. */
    val pkg: String? = null,
)

private const val MESAJLAR = "com.samsung.android.messaging"

val DEMOS = listOf(
    Demo("banka", "Ziraat Mobil", "Hesap hareketi", "Hesabınızdan 1.250,00 TL para çekildi.", "Kritik"),
    Demo("otp", "Mesajlar", "ZIRAAT", "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın.", "Kritik"),
    Demo("anne", "WhatsApp", "Annem", "Akşam yemeğe geliyor musun?", "Önemli", Kind.ONE_TO_ONE, sender = "Annem"),
    Demo("grup", "WhatsApp", "Sınıf 12-B", "Yarın forma giyiyor muyuz?", "Normal", Kind.GROUP_CHAT, sender = "Can"),
    Demo("sinav", "OBS", "Not açıklandı", "Veri Madenciliği vize notunuz açıklandı.", "Önemli"),
    Demo("toplanti", "Takvim", "Proje toplantısı", "15 dakika sonra (10:00)", "Önemli", category = Notification.CATEGORY_EVENT),
    Demo("kargo", "Trendyol", "Siparişin yolda", "Siparişin kargoya verildi, yarın kapında.", "Normal", pkg = "trendyol.com"),
    Demo("kampanya", "Hepsiburada", "Sepette %40 indirim!", "Fırsatı kaçırma, sadece bugün.", "Önemsiz", category = Notification.CATEGORY_PROMO, pkg = "com.pozitron.hepsiburada"),
    Demo("kumar_sms", "Mesajlar", "", "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx", "Silinenler"),
    Demo("sosyal", "Instagram", "Instagram", "ayse_k gönderini beğendi.", "Önemsiz", category = Notification.CATEGORY_SOCIAL, pkg = "com.instagram.android"),
    Demo("oyun", "Bahçe Macerası", "Canların doldu!", "Hemen geri dön, bahçen seni bekliyor.", "Önemsiz"),
    Demo("guncelleme", "Yazılım güncelleme", "Güncelleme hazır", "Yeni yazılım güncellemesi hazır.", "Önemsiz", category = Notification.CATEGORY_RECOMMENDATION),
    Demo("muzik", "Müzik", "Örnek Şarkı", "Örnek Sanatçı", "Dokunulmaz", Kind.MEDIA),
    Demo("navigasyon", "Haritalar", "200 m sonra sağa dönün", "Atatürk Cd. · 12 dk", "Dokunulmaz", Kind.NAVIGATION, Notification.CATEGORY_NAVIGATION),
    Demo("indirme", "Dosyalar", "Ders notları indiriliyor", "%40", "Dokunulmaz", Kind.PROGRESS, Notification.CATEGORY_PROGRESS),
    Demo("kisa", "Ekran görüntüsü", "Ekran görüntüsü kaydedildi", "Görmek için dokun", "Kayıt yok", Kind.SHORT_LIVED),
    Demo("bahis", "Mesajlar", "", "Canlı bahiste yüksek oran! Maç başladı, kuponunu yap.", "Silinenler"),
    Demo("bonus_kart", "Bonus", "Bonus", "Bonus kartınla market alışverişine 50 TL bonus kazan!", "Önemsiz (kumar değil)"),
    Demo("giris", "Garanti BBVA", "Güvenlik", "Hesabınıza yeni bir cihazdan giriş yapıldı.", "Kritik"),
    Demo("grup_ozet", "WhatsApp", "2 yeni mesaj", "Aile grubu", "Özet: Dokunulmaz, mesajlar: Normal", Kind.GROUP_SUMMARY),
    Demo("kurum_kampanya_sms", "Mesajlar", "TURKCELL", "Size özel 20 GB sadece 99 TL! Katılmak için EVET yazıp 2222'ye gönderin.", "Önemsiz", Kind.ONE_TO_ONE, sender = "TURKCELL"),
    Demo("banka_sms_birebir", "Mesajlar", "AKBANK", "Akbank giriş şifreniz: 482913. Kimseyle paylaşmayın.", "Kritik", Kind.ONE_TO_ONE, sender = "AKBANK"),
    Demo("aldatici_kumar", "Mesajlar", "", "Tebrikler! Çarkı çevir, anında 1.000 TL kazan: goo.su/k1", "Silinenler"),
    // 25 (sosyal filtre): aynı uygulamanın birebir DM'i filtre açıkken de Önemli kalmalı.
    Demo("sosyal_dm", "Instagram", "ayse_k", "Yarın derse geliyor musun? Notları getirir misin?", "Önemli (sosyal filtre açıkken de)", Kind.ONE_TO_ONE, sender = "ayse_k", pkg = "com.instagram.android"),
    // 25 Kasım: gerçek kullanımdan çıkan kurallar (docs/demo_senaryosu.md 27–32)
    Demo("grup_toplanti", "WhatsApp", "Proje Ekibi", "Yarın 10:00'da toplantı var, herkes gelsin", "Önemli (grup anahtar kelimesi)", Kind.GROUP_CHAT, sender = "Ayşe", pkg = "com.whatsapp"),
    Demo("oncelikli_kumar", "Mesajlar", "Babam", "Kazanmak için hemen gazino.com adresine gir", "Silinenler (Mesajlar öncelikli olsa da)", Kind.ONE_TO_ONE, sender = "Babam", pkg = MESAJLAR),
    Demo("telegram_kanal", "Telegram", "Kurs Etkinlik Kanalı", "Kasım indiriminde son gün! Bütün eğitimlerde %50 indirim, fırsatı kaçırma!", "Önemsiz (kanal birebir sayılmaz)", Kind.TELEGRAM_CHANNEL, sender = "Kurs Etkinlik Kanalı", pkg = "org.telegram.messenger"),
    Demo("haber", "Bundle", "Son Dakika", "Merkez Bankası faiz kararını açıkladı", "Önemsiz (haber)", pkg = "com.dwarfplanet.bundle"),
    Demo("linkedin_eposta", "Outlook", "LinkedIn İş İlanı Uyarıları", "Bir teknoloji şirketi ağ güvenliği uzmanı arıyor", "Önemsiz (LinkedIn e-postası)", pkg = "com.microsoft.office.outlook"),
    Demo("ytu_rezervasyon", "Mesajlar", "Y.T.U", "Yarın 10:00 - 12:00, B BLOK çalışma salonu, 3. sandalyede rezervasyonunuz gerçekleşmiştir. QR kodunuz: https://ktprezervasyon.yildiz.edu.tr/url/?u=DEMO", "Önemli (YTÜ rezervasyonu)", Kind.ONE_TO_ONE, sender = "Y.T.U", pkg = MESAJLAR),
    Demo("toplu_kutlama", "Mesajlar", "BELEDIYE", "Eğitim emekçilerimizin özel gününü kutluyorum. Belediye Başkanlığı", "Önemsiz (toplu kutlama)", Kind.ONE_TO_ONE, sender = "BELEDIYE", pkg = MESAJLAR),
)

private var mediaSession: MediaSession? = null

fun ensureChannel(ctx: Context) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    if (nm.getNotificationChannel(CHANNEL) == null) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Demo bildirimleri", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
}

fun send(ctx: Context, d: Demo) {
    ensureChannel(ctx)
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val notifId = DEMOS.indexOf(d) + 1
    val open = PendingIntent.getActivity(
        ctx, notifId,
        Intent(ctx, MainActivity::class.java).putExtra("from", d.id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    fun base() = Notification.Builder(ctx, CHANNEL)
        .setSmallIcon(android.R.drawable.sym_action_chat)
        .setContentIntent(open)
        .setAutoCancel(true)
        .addExtras(Bundle().apply {
            putString(EXTRA_FAKE_APP, d.app)
            d.pkg?.let { putString(EXTRA_FAKE_PKG, it) }
        })
        .also { b -> d.category?.let { b.setCategory(it) } }

    val b = base()
    when (d.kind) {
        Kind.PLAIN, Kind.SHORT_LIVED -> {
            if (d.title.isNotEmpty()) b.setContentTitle(d.title)
            b.setContentText(d.text).setStyle(Notification.BigTextStyle().bigText(d.text))
            if (d.kind == Kind.SHORT_LIVED) b.setTimeoutAfter(3000)
        }
        Kind.ONE_TO_ONE, Kind.GROUP_CHAT -> {
            val me = Person.Builder().setName("Ben").build()
            val from = Person.Builder().setName(d.sender ?: d.title).build()
            val style = Notification.MessagingStyle(me)
                .addMessage(d.text, System.currentTimeMillis(), from)
                .setGroupConversation(d.kind == Kind.GROUP_CHAT)
            if (d.kind == Kind.GROUP_CHAT) style.setConversationTitle(d.title)
            b.setStyle(style).setCategory(Notification.CATEGORY_MESSAGE)
        }
        Kind.TELEGRAM_CHANNEL -> {
            // Telegram kanal gönderisi: birebir görünür ama yanıt eylemi yok, kısayol kimliği negatif diyalog.
            val me = Person.Builder().setName("Ben").build()
            val from = Person.Builder().setName(d.sender ?: d.title).build()
            val style = Notification.MessagingStyle(me)
                .addMessage(d.text, System.currentTimeMillis(), from)
                .setGroupConversation(false)
            b.setStyle(style).setCategory(Notification.CATEGORY_MESSAGE).setShortcutId("ndid_-1001234567890")
        }
        Kind.MEDIA -> {
            val session = mediaSession ?: MediaSession(ctx.applicationContext, "suzgec-demo").also { mediaSession = it }
            b.setContentTitle(d.title).setContentText(d.text)
                .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken))
        }
        Kind.NAVIGATION -> b.setContentTitle(d.title).setContentText(d.text).setOngoing(true)
        Kind.PROGRESS -> b.setContentTitle(d.title).setContentText(d.text).setOngoing(true).setProgress(100, 40, false)
        Kind.GROUP_SUMMARY -> {
            // Alt bildirimler grup sohbeti mesajıdır (beklenen: Normal); özet dokunulmaz.
            val group = "aile"
            val me = Person.Builder().setName("Ben").build()
            listOf("Babam" to "Akşam geç kalacağım", "Kardeşim" to "Tamam").forEachIndexed { i, (who, msg) ->
                val style = Notification.MessagingStyle(me)
                    .setConversationTitle("Aile")
                    .setGroupConversation(true)
                    .addMessage(msg, System.currentTimeMillis(), Person.Builder().setName(who).build())
                nm.notify(
                    100 + i,
                    base().setStyle(style).setCategory(Notification.CATEGORY_MESSAGE).setGroup(group).build(),
                )
            }
            b.setContentTitle(d.title).setContentText(d.text).setGroup(group).setGroupSummary(true)
        }
    }
    nm.notify(notifId, b.build())
}

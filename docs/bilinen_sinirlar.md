# Bilinen sınırlar (Süzgeç 1.0)

Süzgeç'in bilerek çözülmemiş ya da Android'in izin vermediği durumlar. Her madde kısaca ne olduğunu, nedenini ve ne yapılabileceğini anlatır. Son güncelleme: 24 Kasım 2026, v0.9.3.

## Android'in sınırları

1. **Başka uygulamanın bildirim içeriği değiştirilemez.** Süzgeç bir bildirimi ya panelde bırakır ya da tamamen kaldırır. WhatsApp'ın "3 sohbetten 4 mesaj" gibi grup özetinden tek bir satır silinemez.
2. **Özet silinince altındaki bildirimler de gider.** Android, bir grup özeti kaldırılınca altındaki sohbet bildirimlerini de kaldırır (`REASON_GROUP_SUMMARY_CANCELED`; 24 Kasım 21:30'da görüldü). Bu yüzden Süzgeç özeti yalnız altı boşsa siler. Altta Önemli bir bildirim varken özetteki kumar ya da Normal satırları kalabilir; grup kapalıyken önizlemede görünebilirler.
3. **Sessize alınmış WhatsApp gruplarının bazı mesajları Süzgeç'e hiç gelmez.** WhatsApp bu gruplarda çoğu zaman ayrı bir bildirim göndermez, yalnız özeti günceller (24 Kasım 21:26 ve 21:33'te görüldü). Bu mesajlar sınıflanmaz, kutuya ve kayda girmez; WhatsApp'ta okunmamış olarak kalır. Özet silinince panelden de kalkarlar.
4. **Rahatsız Etmeyin açıkken zil modu doğrudan okunamaz.** Bu sırada Android zil modunu hep "sessiz" bildirir. Süzgeç gerçek modu sistemin yapışkan yayınından alır; yayın yoksa (telefon yeniden başladıysa) son kaydettiği modu kullanır.
5. **Samsung arka plandaki uygulamaları kapatabilir.** Süzgeç'in pil ayarı "Kısıtlanmamış" olmalı ve "Arka plan kullanım sınırları" listesinde yer almamalı; kurulum sihirbazı bunu söyler. M51'de 20 saat kesintisiz çalıştı, ama bu ayarlar bozulursa bildirimler süzülmez.
6. **Android 15'ten önce tek kullanımlık kodlar gizlenmez.** "Gizli içerik → Kritik" kuralı M51'de (Android 12) hiç tetiklenmez. Kodları güvenlik kuralı yakalar.

## Sınıflandırma

7. **Model en çok grup mesajıyla birebir mesajı karıştırır.** Bildirim bu bilgiyi taşıyorsa (MessagingStyle) kural katmanı karar verir; taşımıyorsa model tahmin eder.
8. **Emin olmadığı model kararları en fazla Normal olur.** Tek istisna güvenlik/para türü; o Önemli olur. Belirsiz bir okul/iş bildirimi Normal'de kalabilir. Puanı zaten yüksek olan Önemli kalır: 23 Kasım'da bir Outlook sistem bildirimi böyle Önemli oldu.
9. **Uygulama listeleri elle tutulur.** Haber, alışveriş, sosyal medya, e-posta ve Telegram paketleri `rules.json`'da yazılı. Listede olmayan bir alışveriş uygulamasının kampanyası alışveriş filtresiyle silinmez, Önemsiz'e düşer.
10. **Telegram kanal işareti gerçek bir kanalda tam doğrulanmadı.** Kısayol (`ndid_-…`) ve "Yanıtla düğmesi yok" işaretleri Telegram'ın kaynak kodu bilgisine dayanıyor. Gerçek bir kanal gönderisinde logcat'teki "Telegram işaretleri" satırına bakılmalı.
11. **Kullanıcı adı varsayılan olarak "Süleyman".** Grup mesajlarında bu ad aranır. Başka biri kullanacaksa adı Ayarlar'dan değiştirmeli.
12. **Kişiselleştirme basit (v1).** Kullanıcının kategori düzeltmeleri (gönderen, uygulama, içerik türü) sayılıp puana eklenir; düzeltme azken etkisi küçüktür. Kaydırarak kaldırma ve sürükleme düzeltme sayılmaz.

## Zamanlama

13. **5 saniye kuralı.** Türü anlaşılamayan bildirimler 5 sn bekletilir; bu sürede kalkan bildirim hiç kaydedilmez. Güvenlik kodu ve kumar bekletilmez. Bekleme boyunca işlemci kısa bir süre uyanık tutulur, bu yüzden ekran kapalıyken de 5 sn sürer (önceden 9–15 sn sürüyordu).
14. **Teslim saati 10 dakika oynayabilir.** Teslim, Android'in pil dostu alarmıyla 10 dakikalık bir pencerede gelir: 10:00 teslimi 10:00 ile 10:10 arasında.
15. **Aynı sohbetten 2 dakika içinde gelen yeni mesaj tekrar titretmez.** Kritik bildirimler bundan muaf, her zaman titretir.

## Kayıtlar ve veri

16. **Bir sohbet çoğu zaman tek kayıttır.** WhatsApp her sohbet için tek bir bildirim anahtarı kullanır. Kategori aynı kaldıkça yeni mesaj eski kaydın metnini ezer. Kategori değişirse eski kayıt arşivlenip korunur; ör. önce kumar sonra normal mesaj gelirse kumar kaydı Silinenler'de kalır. 24 Kasım 21:29'daki kumar kaydı bu düzeltmeden önce ezildi.
17. **Kutuya giriş sayımı yaklaşıktır.** Uygulamanın ekrana her gelişi giriş sayılır; 5 dakika içindeki tekrarlar tek giriş sayılır.
18. **Veritabanı eski sürüme indirilemez.** v0.9.2 veritabanı şemasını v7'ye yükseltti; v0.9.1 ve öncesinin APK'sı bu veritabanını açamaz. Geri dönmek için Süzgeç kaldırılıp eski sürüm temiz kurulmalı ve `docs/veri/m51_20261124_v7oncesi/` yedeği geri konmalı.
19. **CSV'de bildirim metni yoktur.** Dışa aktarma bilerek yalnız uygulama, kategori, kural, neden ve zamanları yazar. Nedendeki tırnaklı kelimeler "…" olarak, gönderen karmasının yalnız ilk 12 karakteri yazılır.

## Sıralama

20. **Sürükleme yalnız tek kategori sekmelerinde yapılır.** "Tümü" ve Silinenler'de sürükleme yoktur; kategoriler arası taşıma "Taşı…" ile yapılır. "Taşı…" ile kategori değişince elle verilen sıra sıfırlanır.
21. **Sıra puanı kategoriyi değiştirmez.** Sürüklemelerden öğrenilen sıra puanı yalnız aynı kategori içindeki sırayı etkiler; bildirimin kategorisi ve titreşimi değişmez.

## Demo ve sürüm

22. **Demo uygulama taklidi yalnız debug derlemesinde çalışır.** Demo Gönderici'nin "WhatsApp", "Mesajlar" gibi uygulama adı ve paket taklidi yalnız debug Süzgeç'te geçerli. İmzalı v1.0 (release) derlemesinde demo bildirimleri "Süzgeç Demo Gönderici" adıyla görünür; öncelikli uygulama, filtre kapsamı ve haber/e-posta kuralları da onlara uygulanmaz. Demo videosunda hangisinin kullanılacağına 25 Kasım'da karar verilecek.
23. **Örnek haftalık veri gerçek cihazda kapalı.** Geliştirici menüsündeki bu seçenek gerçek veriyle karışmasın diye yalnız emülatörde açılır.
24. **M51 kişisel telefondur.** Süzgeç başka uygulamaların verisine hiç dokunmaz; yalnız bildirimi panelden kaldırır ve kendi kayıtlarını tutar (CLAUDE.md §0).

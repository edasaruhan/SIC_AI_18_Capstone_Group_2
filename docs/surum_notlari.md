# Sürüm notları — Süzgeç 0.1 → 1.0

Geliştirme geçmişinden derlenmiştir (23–25 Kasım 2026). Gerçek kullanım denemesinden gelen düzeltmeler genel ifadeyle yazılmıştır.

## 0.1 — ilk çalışan sürüm (23 Kasım)
- Proje iskeleti: `NotificationListenerService` ile bildirim yakalama, Room kaydı ve bildirim kaydı ekranı.
- Kural katmanı: dokunulmayan bildirimler (arama, alarm, medya, kalıcı, sistem), türü belli olmayanlar için 5 sn bekleme, Normal/Önemsiz/Silinenler'in panelden kaldırılması; demo bildirimleri gönderen `:sender` modülü.
- Metin modeli (TF-IDF + lojistik regresyon, 11 içerik türü) JSON'dan Kotlin'de hesaplanıyor; Python ile eşlik testi; kurum göndericisi kuralı.
- Rahatsız Etmeyin öncelik modu (aramalar, tekrar arayanlar, alarmlar, medya serbest), Kritik/Önemli sesini Süzgeç'in kendisinin çalması, günde 3 sessiz teslim ve kategori sekmeli kutu.
- Ayarlar, kurulum sihirbazı, kişiselleştirme v1 (kategori taşımalarından δ düzeltmesi), model v3 (kumar / kart kampanyası ayrımı), haftalık özet ve CSV dışa aktarma.
- Uyarı kullanıcının zil moduna uyar (sesli / titreşim / sessiz); kutudan çıkma (açma, kaydırma, "Tümünü temizle", Geri al), Room v6 yazılı migration; emin olunamayan bildirim en fazla Normal'e yükseltilir.

## 0.9 — gerçek kullanım düzeltmeleri (24 Kasım)
- Kural sırası: güvenlik kodu ve kumar, kullanıcının öncelikli uygulama seçiminden önce çalışır.
- "Panelde bırak" seçeneği; alışveriş ve sosyal medya filtreleri yalnız ilgili uygulama listelerine uygulanır.
- Yeni kurallar: haber uygulamaları, e-postada mülakat daveti ve iş ilanı platformu göndericileri, toplu kutlama mesajları, gruplarda anahtar kelime ve kullanıcının adı, kurum rezervasyon bildirimleri, mesajlaşma kanalı gönderileri.
- Altı boşalan grup özeti de silinir; kategori değişen güncellemede eski kayıt arşivlenir; ekran kapalıyken 5 sn bekleme kısa bir uyanık kalma kilidiyle gerçekten 5 sn sürer.
- Kullanıcının taşıdığı kaydın güncellemesi yeniden sınıflanmaz; aynı sohbetten 2 dk içinde ikinci uyarı yok; kutuya giriş sayımı.
- Başka uygulamaların verisine dokunmama kuralı ve buna dair teknik kilit yazılı hâle getirildi; bilinen sınırlar belgesi.

## 0.9.1 (24 Kasım)
- Güvenlik ve kumar kontrolü 5 sn beklemeden önce yapılır: kumar mesajı panelde hiç görünmez, doğrulama kodu beklemeden Kritik olur.

## 0.9.2 (24 Kasım)
- Kutuda sürükleyerek sıralama (tek kategori sekmelerinde), "A, B'nin üstünde" çiftlerinden öğrenilen ayrı bir sıra puanı; sıra puanı kategori kararını etkilemez.
- Room şema v7 (yalnız ekleyici yazılı migration).

## 0.9.3 (24 Kasım)
- Gizli geliştirici menüsü (teslimi şimdi gönder, örnek haftalık veri yalnız emülatörde, düzeltmeleri sıfırla, sihirbazı yeniden aç).
- "Verileri dışa aktar" (bildirim metni ve başlığı içermeyen CSV); CSV başlığındaki eksik sütunlar düzeltildi; uygulama simgeleri.

## 1.0 — kod dondurma (25 Kasım)
- Sıralama tutamacından uzun basmadan anında sürükleme, 48 dp dokunma alanı ve kısa titreşim; satır menüsünde "Yukarı / Aşağı taşı" yedek yolu.
- İmzalı release APK (hata ayıklanamaz; demo uygulama adı taklidi yalnız debug'da).
- Son demo turu: emülatörde 31/31 senaryo satırı beklenen kategoride; Android 12 emülatöründe de 31/31.
- Depodaki test metinleri sahte örneklerle değiştirildi; gizlilik taraması.

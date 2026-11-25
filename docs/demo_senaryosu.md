# Demo senaryosu (20 bildirim + 25 Kasım ekleri)

Her kilometre taşında bu listeyle kontrol edilir (CLAUDE.md §9). Bildirimleri `:sender` uygulaması gönderir; her satırın `id`'si sender'daki düğmeyle ve adb komutuyla aynıdır:

```
adb shell am broadcast -f 32 -n com.suzgec.sender/.SendReceiver --es id <id>
```

**Karar veren** sütunu: *kural* = kural katmanı (24 Kasım), *model* = metin modeli + önem puanı (25 Kasım). Model satırlarında "Neden:" satırı modelin tahminini gösterir (ör. "Model: kampanya, %96; puan 1,04").

Aynı liste birim testte de var: `app/src/test/.../model/DemoScenarioTest.kt` (Android'siz, kural + model uçtan uca).

| # | id | Uygulama (taklit) | Bildirim | Biçim | Beklenen | Karar veren |
|---|---|---|---|---|---|---|
| 1 | banka | Ziraat Mobil | "Hesabınızdan 1.250,00 TL para çekildi." | düz | Kritik | kural (güvenlik) |
| 2 | otp | Mesajlar | "Doğrulama kodunuz: 482913. Kimseyle paylaşmayın." | düz | Kritik | kural (güvenlik) |
| 3 | anne | WhatsApp | Annem: "Akşam yemeğe geliyor musun?" | MessagingStyle, birebir | Önemli | kural (birebir sohbet) |
| 4 | grup | WhatsApp | Sınıf 12-B / Can: "Yarın forma giyiyor muyuz?" | MessagingStyle, grup | Normal | kural (grup sohbeti) |
| 5 | sinav | OBS | "Veri Madenciliği vize notunuz açıklandı." | düz | Önemli | model |
| 6 | toplanti | Takvim | "Proje toplantısı 15 dakika sonra (10:00)" | kategori `event` | Önemli | model |
| 7 | kargo | Trendyol | "Siparişin kargoya verildi, yarın kapında." | düz | Normal | model |
| 8 | kampanya | Hepsiburada | "Sepette %40 indirim! Fırsatı kaçırma." | kategori `promo` | Önemsiz | model |
| 9 | kumar_sms | Mesajlar | "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx" | düz | Silinenler | kural (kumar) |
| 10 | sosyal | Instagram | "ayse_k gönderini beğendi." | kategori `social` | Önemsiz | model |
| 11 | oyun | Bahçe Macerası | "Canların doldu! Hemen geri dön." | düz | Önemsiz | model |
| 12 | guncelleme | Yazılım güncelleme | "Yeni yazılım güncellemesi hazır." | kategori `recommendation` | Önemsiz | model |
| 13 | muzik | Müzik | "Çalıyor: Örnek Şarkı" | MediaStyle + medya oturumu | Dokunulmaz | kural (medya) |
| 14 | navigasyon | Haritalar | "200 m sonra sağa dönün" | kalıcı, kategori `navigation` | Dokunulmaz | kural (kalıcı) |
| 15 | indirme | Dosyalar | "Ders notları indiriliyor %40" | kalıcı, ilerleme | Dokunulmaz | kural (kalıcı) |
| 16 | kisa | Ekran görüntüsü | "Ekran görüntüsü kaydedildi" | `timeoutAfter` 3 sn | Kayıt yok (5 sn içinde kalkar) | kural (5 sn) |
| 17 | bahis | Mesajlar | "Canlı bahiste yüksek oran! Maç başladı, kuponunu yap." | düz | Silinenler | kural (kumar) |
| 18 | bonus_kart | Bonus | "Bonus kartınla market alışverişine 50 TL bonus kazan!" | düz | Önemsiz (kumar **değil**) | model |
| 19 | giris | Garanti BBVA | "Hesabınıza yeni bir cihazdan giriş yapıldı." | düz | Kritik | kural (güvenlik) |
| 20 | grup_ozet | WhatsApp | 2 grup sohbeti mesajı ("Aile") + grup özeti (`FLAG_GROUP_SUMMARY`) | MessagingStyle grup + özet | Özet: Dokunulmaz · mesajlar: Normal; mesajlar panelden silinince altı boşalan özet de silinir (`ozet_silindi`, 25 Kasım) | kural (grup özeti / grup sohbeti) |
| 21 | kurum_kampanya_sms | Mesajlar | TURKCELL: "Size özel 20 GB sadece 99 TL! Katılmak için EVET yazıp 2222'ye gönderin." | MessagingStyle, birebir | Önemsiz | model (kurum göndericisine birebir sohbet kuralı uygulanmaz) |
| 22 | banka_sms_birebir | Mesajlar | AKBANK: "Akbank giriş şifreniz: 482913. Kimseyle paylaşmayın." | MessagingStyle, birebir | Kritik | kural (güvenlik, birebir sohbetten önce) |
| 23 | aldatici_kumar | Mesajlar | "Tebrikler! Çarkı çevir, anında 1.000 TL kazan: goo.su/k1" | düz | Silinenler | model (sözlükte açık kelime yok) |

### Gerçek kullanımdan çıkan kurallar (25 Kasım)

Sender bu satırlarda gerçek paket adını taklit eder (`suzgec.debug.fakePkg`, yalnız debug). Bu yüzden öncelikli uygulama ve filtre kapsamı gerçek uygulamadaki gibi çalışır. Beklenenler hem varsayılan ayarlarla hem M51 ayarlarıyla (Mesajlar öncelikli Önemli, alışveriş ve sosyal filtre açık) aynıdır. `DemoScenarioTest` iki durumu da dener.

| # | id | Uygulama (taklit) | Bildirim | Biçim | Beklenen | Karar veren |
|---|---|---|---|---|---|---|
| 27 | grup_toplanti | WhatsApp | Proje Ekibi / Ayşe: "Yarın 10:00'da toplantı var, herkes gelsin" | MessagingStyle, grup | Önemli | kural (grup anahtar kelimesi "toplanti") |
| 28 | oncelikli_kumar | Mesajlar | Babam: "Kazanmak için hemen gazino.com adresine gir" | MessagingStyle, birebir | Silinenler | kural (kumar: "gazino" + kazan/.com; öncelikli uygulamadan önce) |
| 29 | telegram_kanal | Telegram | Kurs Etkinlik Kanalı: "Kasım indiriminde son gün! … %50 indirim" | MessagingStyle birebir, yanıt eylemi yok, kısayol `ndid_-100…` | Önemsiz | model (Telegram kanalı birebir sayılmaz) |
| 30 | haber | Bundle | Son Dakika: "Merkez Bankası faiz kararını açıkladı" | düz | Önemsiz | kural (haber uygulaması) |
| 31 | linkedin_eposta | Outlook | LinkedIn İş İlanı Uyarıları: "Bir teknoloji şirketi ağ güvenliği uzmanı arıyor" | düz | Önemsiz | kural (e-posta, gönderen LinkedIn) |
| 33 | ytu_rezervasyon | Mesajlar | Y.T.U: "… 3. sandalyede rezervasyonunuz gerçekleşmiştir. QR kodunuz: https://ktprezervasyon.yildiz.edu.tr/…" | MessagingStyle, birebir | Önemli (kısa titreşim; Panelde bırak ya da öncelik ayarından bağımsız) | kural (YTÜ rezervasyonu) |
| 32 | toplu_kutlama | Mesajlar | BELEDIYE: "Eğitim emekçilerimizin özel gününü kutluyorum." | MessagingStyle, birebir | Önemsiz | kural (toplu kutlama: büyük harfli gönderen + "kutluyorum") |

"Panelde bırak" (Ayarlar'da uygulama başına) demo gönderiyle denenmez. Gerçek uygulamada seçilir; o uygulamanın bildirimi panelde kalır, ses çalmaz, Bildirim kaydında "Dokunulmaz [panelde_birak]" görünür.

### Filtre örnekleri (Ayarlar → Görmek istemediğin içerikler)

25 Kasım'dan beri alışveriş filtresi yalnız alışveriş uygulamalarına (Trendyol, Hepsiburada…), sosyal medya filtresi yalnız Instagram, TikTok, X, Facebook ve Snapchat'e uygulanır (`rules.json`). Başka uygulamadaki kampanya Önemsiz'e düşer, silinmez.

| # | Ön koşul | Gönder (id) | Beklenen | Karar veren |
|---|---|---|---|---|
| 24a | Alışveriş filtresi **açık** | kampanya (8) | Silinenler | model (kampanya) + alışveriş filtresi |
| 24b | Alışveriş filtresi **açık** | kargo (7) | Normal (silinmez) | model (işlem bilgisi) |
| 25a | Sosyal medya filtresi **açık** | sosyal (10) | Silinenler | model (sosyal etkileşim) + sosyal filtre |
| 25b | Sosyal medya filtresi **açık** | sosyal_dm — Instagram, ayse_k (MessagingStyle birebir) | Önemli (silinmez) | kural (birebir sohbet) |

Filtreler kapalıyken 8 ve 10 yine Önemsiz olur (tablonun üst kısmı). Demo bitince filtreleri eski hâline getir (alışveriş ve sosyal kapalı, kumar açık).

### Kişiselleştirme örneği (isteğe bağlı)
26: kargo (7) bildirimini kutuda iki kez "Taşı… → Önemli" yap; üçüncü kargo bildirimi doğrudan **Önemli** gelir. "Neden burada?" satırında "senin düzeltmen +0,50 → 2,51" görünür (delta = 2 / (2 + 0 + 2) = 0,5).

## Gerçek sistem testleri (sender göndermez)

| Test | Nasıl | Beklenen |
|---|---|---|
| Gelen arama | Emülatör: `adb emu gsm call 05551234567` · M51: başka telefondan ara | Dokunulmaz, çalar |
| Alarm | Saat uygulamasında 1 dk sonraya alarm | Dokunulmaz, çalar |
| Acil durum uyarısı | M51: Ayarlar → Güvenlik ve acil durum → Kablosuz acil durum uyarıları (test mesajı varsa) | Dokunulmaz |

## Notlar
- Taklit uygulama adı (`suzgec.debug.fakeApp`) yalnız Süzgeç'in **debug** derlemesinde kullanılır; gerçek paket adı her zaman `com.suzgec.sender`.
- 18 numara bilerek eklendi: ml/README.md'deki bulguya göre "Bonus" kart kampanyaları kumar sanılmamalı. Model v2 bu bildirime "kumar, %71" diyordu; v3 (23 Kasım) "kampanya, %68" diyor. `rules.json` → `kumar.model_istisna` ("bonus kart" vb.) ek güvence olarak duruyor: model kumar dese bile bildirim silinmez, önem puanıyla Önemsiz olur.
- Kişiselleştirme denendikten sonra demo öncesi menü → "Test: öğrenilen düzeltmeleri sıfırla" (yalnız debug), yoksa düzeltilen bildirimler (ör. kargo) beklenenden farklı kategoriye düşer.
- Kurum göndericisi: MessagingStyle birebir sohbette gönderen küçük harf içermiyor ve en az 2 harf (ya da harf + rakam: B001) taşıyorsa, telefon numarası ya da rehber kaydı değilse kurum sayılır; birebir sohbet kuralı atlanır, karar modele kalır.

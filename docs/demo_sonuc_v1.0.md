# Demo sonuçları · Süzgeç 1.0

Tarih: 2026-11-25 00:28 · Cihaz: Android Studio emülatörü (Medium Phone, API 37) · Derleme: debug (demo uygulama/paket taklidi yalnız debug'da çalışır) ·
Ayarlar: varsayılan (kumar filtresi açık, öncelikli uygulama yok).
Yöntem: Demo Gönderici'nin bütün satırları sırayla `adb shell am broadcast … --es id <id>` ile gönderildi; karar ve uyarı
Süzgeç'in logcat satırlarından okundu. Demo metinleri sahtedir (gerçek kullanıcı verisi yok).

**Sonuç: 31 / 31 satır beklenen kategoride.**

| # | id | Uygulama (taklit) | Beklenen | Süzgeç'in kararı [kural] | Uyarı | |
|---|---|---|---|---|---|---|
| 1 | banka | Ziraat Mobil | Kritik | Kritik [guvenlik] | Kritik, zil modu normal (sistem) → ses 3500 ms + titreşim | ✅ |
| 2 | otp | Mesajlar | Kritik | Kritik [guvenlik] | Kritik, zil modu normal (sistem) → ses 3500 ms + titreşim | ✅ |
| 3 | anne | WhatsApp | Önemli | Önemli [birebir_sohbet] | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| 4 | grup | WhatsApp | Normal | Normal [grup_sohbeti] | — | ✅ |
| 5 | sinav | OBS | Önemli | Önemli [model] | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| 6 | toplanti | Takvim | Önemli | Önemli [model] | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| 7 | kargo | Trendyol | Normal | Normal [model] | — | ✅ |
| 8 | kampanya | Hepsiburada | Önemsiz | Önemsiz [model] | — | ✅ |
| 9 | kumar_sms | Mesajlar | Silinenler | Silinenler [kumar] | — | ✅ |
| 10 | sosyal | Instagram | Önemsiz | Önemsiz [model] | — | ✅ |
| 11 | oyun | Bahçe Macerası | Önemsiz | Önemsiz [model] | — | ✅ |
| 12 | guncelleme | Yazılım güncelleme | Önemsiz | Önemsiz [model] | — | ✅ |
| 13 | muzik | Müzik | Dokunulmaz | Dokunulmaz [dokunma] | — | ✅ |
| 14 | navigasyon | Haritalar | Dokunulmaz | Dokunulmaz [dokunma] | — | ✅ |
| 15 | indirme | Dosyalar | Dokunulmaz | Dokunulmaz [dokunma] | — | ✅ |
| 16 | kisa | Ekran görüntüsü | Kayıt yok | Kayıt yok [5 sn] | — | ✅ |
| 17 | bahis | Mesajlar | Silinenler | Silinenler [kumar] | — | ✅ |
| 18 | bonus_kart | Bonus | Önemsiz (kumar değil) | Önemsiz [model] | — | ✅ |
| 19 | giris | Garanti BBVA | Kritik | Kritik [guvenlik] | Kritik, zil modu normal (sistem) → ses 3500 ms + titreşim | ✅ |
| 20 | grup_ozet | WhatsApp | Özet: Dokunulmaz, mesajlar: Normal | Normal [grup_sohbeti] (+2) | — | ✅ |
| 21 | kurum_kampanya_sms | Mesajlar | Önemsiz | Önemsiz [model] | — | ✅ |
| 22 | banka_sms_birebir | Mesajlar | Kritik | Kritik [guvenlik] | Kritik, zil modu normal (sistem) → ses 3500 ms + titreşim | ✅ |
| 23 | aldatici_kumar | Mesajlar | Silinenler | Silinenler [model] | — | ✅ |
| 24 | sosyal_dm | Instagram | Önemli (sosyal filtre açıkken de) | Önemli [birebir_sohbet] | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| 25 | grup_toplanti | WhatsApp | Önemli (grup anahtar kelimesi) | Önemli [grup_anahtar] | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| 26 | oncelikli_kumar | Mesajlar | Silinenler (Mesajlar öncelikli olsa da) | Silinenler [kumar] | — | ✅ |
| 27 | telegram_kanal | Telegram | Önemsiz (kanal birebir sayılmaz) | Önemsiz [model] | — | ✅ |
| 28 | haber | Bundle | Önemsiz (haber) | Önemsiz [haber] | — | ✅ |
| 29 | linkedin_eposta | Outlook | Önemsiz (LinkedIn e-postası) | Önemsiz [eposta_linkedin] | — | ✅ |
| 30 | ytu_rezervasyon | Mesajlar | Önemli (YTÜ rezervasyonu) | Önemli [rezervasyon] | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| 31 | toplu_kutlama | Mesajlar | Önemsiz (toplu kutlama) | Önemsiz [toplu_kutlama] | — | ✅ |

Notlar:
- `otp` satırından sonra görünen ek "Dokunulmaz" kararı, Android'in demo göndericinin bildirimlerini kendiliğinden gruplamasıyla oluşan sistem özetidir (beklenen).
- `grup_ozet`: iki grup mesajı Normal, grup özeti Dokunulmaz; mesajlar panelden silinince altı boşalan özet de silinir.
- Uyarı sütunu: emülatörde zil modu normal olduğu için Kritik ≈ 3,5 sn ses + titreşim, Önemli ≈ 1 sn ses + titreşim.
- Filtre örnekleri (24a/b, 25a/b) ayar değiştirmeyi gerektirdiği için bu turda yok; birim testlerinde (`FiltersAndPersonalizationTest`) doğrulanıyor.

## M51 kısa kontrol (Galaxy M51, Android 12, debug derlemesi, kullanıcının kendi ayarları)

| id | Beklenen | Süzgeç'in kararı [kural] | Uyarı | |
|---|---|---|---|---|
| otp | Kritik | Kritik [guvenlik] | Kritik, zil modu titreşim (sistem) → yalnız titreşim | ✅ |
| anne | Önemli | Önemli [birebir_sohbet] | Önemli, zil modu titreşim (sistem) → yalnız titreşim | ✅ |
| kumar_sms | Silinenler | Silinenler [kumar] | — | ✅ |

M51 titreşim modunda: Kritik ve Önemli yalnız titreşti (ses yok), kumar sessizce Silinenler'e gitti.

## Release v1.0 kontrolü (emülatör, imzalı release APK)

Release derlemesinde demo uygulama adı/paket taklidi çalışmaz (yalnız debug); bildirimler "Süzgeç Demo Gönderici" adıyla görünür. Kararlar metin ve bildirim biçimine göre yine doğru.

| id | Beklenen | Süzgeç'in kararı [kural] | Görünen uygulama | Uyarı | |
|---|---|---|---|---|---|
| otp | Kritik | Kritik [guvenlik] | Süzgeç Demo Gönderici | Kritik, zil modu normal (sistem) → ses 3500 ms + titreşim | ✅ |
| anne | Önemli | Önemli [birebir_sohbet] | Süzgeç Demo Gönderici | Önemli, zil modu normal (sistem) → ses 1000 ms + titreşim | ✅ |
| kumar_sms | Silinenler | Silinenler [kumar] | Süzgeç Demo Gönderici | — | ✅ |

Açılış hatasız (kurulum sihirbazı → boş kutu, "Bildirim Kutunuz Temiz"); `dumpsys package` bayrakları: DEBUGGABLE yok.

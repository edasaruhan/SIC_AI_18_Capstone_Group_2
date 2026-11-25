# Emülatöre özel "uygulamanın içinden de sil" demosu (dal: `emulator-silme-demo`)

Tarih: 25 Kasım 2026. **Yalnız bu dalda; main'e merge edilmez.** v1.0 (main, `v1.0` etiketi, release APK, M51 kurulumu) değişmedi.

## Ne yapar?
Kumar kararıyla Silinenler'e giden bildirim, **yalnız Demo Gönderici'nin (`com.suzgec.sender`)** bildirimiyse ve
üzerinde **"Sil"** başlıklı eylem varsa, Süzgeç o eylemi **bir kez** tetikler. Demo Gönderici yalnız **kendi sahte gelen
kutusundan** o kaydı siler. Süzgeç kayda "· Uygulamadan da silindi (demo)" ekler; Silinenler'deki "Neden burada?"
satırında görünür. Kumar dışı hiçbir karar tetiklemez.

## Üç kilit (CLAUDE.md §0 teknik kilit)
| Kilit | Nasıl |
|---|---|
| 1. Hedef | `InAppDeletePolicy.ALLOWED_PACKAGES = setOf("com.suzgec.sender")` (tek eleman, testle sabit); yalnız başlığı tam "Sil" olan eylem |
| 2. Ayrı varyant | `productFlavors` boyutu `dagitim`: **standart** (varsayılan; M51 ve release) ve **emulatorDemo** (`applicationIdSuffix .emulatordemo`). Tetikleyen kod yalnız `app/src/emulatorDemo/`. `app/src/main`'de yalnız boş `InAppDeleteHook` arayüzü; `app/src/standart`'ta işlem yapmayan nesne |
| 3. Çalışma zamanı | `DeviceKind.isEmulator()` değilse hiçbir şey yapılmaz, logcat'e "gerçek cihaz: devre dışı"; Ayarlar'da varsayılanı **kapalı** "Uygulamadan da sil (deneme)" anahtarı (yalnız emulatorDemo'da görünür) |

## Değişen / eklenen dosyalar
- `app/build.gradle.kts` — iki flavor
- `app/src/main/.../listener/InAppDeleteHook.kt` — boş arayüz (yeni)
- `app/src/main/.../listener/SuzgecListener.kt` — karar uygulandıktan sonra `InAppDelete.hook.onDecided(...)` (standart'ta boş)
- `app/src/main/.../ui/SettingsScreen.kt` — `hasSettings` doğruysa anahtar bölümü (standart'ta satır eklenmez)
- `app/src/standart/.../listener/InAppDelete.kt` — işlem yapmayan kanca
- `app/src/emulatorDemo/.../listener/InAppDelete.kt`, `emulatordemo/InAppDeletePolicy.kt`, `emulatordemo/EmulatorInAppDelete.kt`, `res/values/strings.xml`
- `app/src/testEmulatorDemo/.../InAppDeletePolicyTest.kt` — 5 test
- `sender/`: `FakeInbox.kt` (sahte kutu + dışa kapalı `FakeDeleteReceiver`), `Demo.kt` ("Sil" eylemi, `FLAG_IMMUTABLE`), `MainActivity.kt` ("Sahte Mesajlar: N" ekranı), `SendReceiver.kt` (`sahte_bosalt`), manifest

## Test sonuçları
| Görev | Sonuç |
|---|---|
| `:app:testStandartDebugUnitTest` | 103 test, 103 geçti (v1.0 ile aynı) |
| `:app:testEmulatorDemoDebugUnitTest` | 108 test (103 + 5 yeni), 108 geçti |

## Emülatör (API 37, emulatorDemo debug + Demo Gönderici debug)
| Anahtar | Gönderilen | Süzgeç kararı | Demo kancası | Sahte kutu |
|---|---|---|---|---|
| kapalı | kumar_sms | Silinenler [kumar] | KAPALI | kalır |
| kapalı | anne | Önemli [birebir_sohbet] | KAPALI | kalır |
| kapalı | bonus_kart | Önemsiz [model] | KAPALI | kalır |
| açık | kumar_sms | Silinenler [kumar] | **uygulamadan_silindi** | **silindi** |
| açık | anne | Önemli [birebir_sohbet] | KUMAR_DEGIL | kalır |
| açık | bonus_kart | Önemsiz [model] | KUMAR_DEGIL | kalır |
| açık | bahis | Silinenler [kumar] | **uygulamadan_silindi** | **silindi** |
| açık | aldatici_kumar | Silinenler [model, kumar %98] | **uygulamadan_silindi** | **silindi** |
| açık | kurum_kampanya_sms | Önemsiz [model] | KUMAR_DEGIL | kalır |

## Ayrım kanıtı
- `apkanalyzer dex packages`: `app-standart-debug.apk` ve `app-standart-release-unsigned.apk` (yalnız inceleme için imzasız derlendi) içinde `emulatordemo` / `InAppDeletePolicy` / `EmulatorInAppDelete` **0 satır**; `app-emulatorDemo-debug.apk` içinde 85 satır. v1.0 teslim APK'sında 0.
- CLAUDE.md §0 grep'i `app/src/main` ve `app/src/standart` üzerinde **boş**; `actionIntent` yalnız `app/src/emulatorDemo` içinde.
- Hiçbir APK'da `BIND_ACCESSIBILITY_SERVICE` yok.

Demo videosu: [Türkçe, 2:53](https://youtu.be/Eu482-Sd8X8?t=173) · [English, 2:53](https://youtu.be/rKRlmqLBZEY?t=173)

# Deneysel silme demosunun kaynak kodu

Bu klasör, Süzgeç 1.0'ın (deponun kökündeki kod) üzerine eklenen **emülatör silme demosunun** kodudur. Geliştirme sırasında ayrı bir dalda (`emulator-silme-demo`) tutuldu ve 1.0'a **bilerek birleştirilmedi**. Deponun kökündeki kod Süzgeç 1.0'dır ve bu özelliği içermez.

Ayrıntılı tasarım ve test sonuçları: [`docs/emulator_silme_demo.md`](docs/emulator_silme_demo.md)

## Nasıl çalışıyor? (kısaca)

Silme, Android'in bildirimlerdeki eylem düğmeleriyle yapılır. Demo Gönderici, her bildirimine bir **"Sil"** düğmesi koyar. Süzgeç bir bildirimi kumar diye Silinenler'e gönderdiğinde, şu dört şart aynı anda sağlanırsa o düğmeye **bir kez** basar:

1. Bildirim **yalnız** Demo Gönderici'den gelmişse (`com.suzgec.sender`; izin listesinde tek paket var ve bu bir testle sabitlendi),
2. karar **kumar** ise (Silinenler + içerik türü 8),
3. cihaz **emülatör** ise (gerçek telefonda kod hiçbir şey yapmaz, yalnız "gerçek cihaz: devre dışı" diye log yazar),
4. kullanıcı Ayarlar'daki **"Uygulamadan da sil (deneme)"** anahtarını açmışsa (varsayılan kapalı).

Kod yalnız `emulatorDemo` derleme varyantında var (`app/src/emulatorDemo/`). Varsayılan `standart` varyantta (Süzgeç 1.0 APK'sı) bu sınıflar hiç bulunmaz; `app/src/main`'de yalnız boş bir arayüz (`InAppDeleteHook`) kalır.

## Klasördeki dosyalar

| Dosya | Durum | Ne yapar |
|---|---|---|
| `app/build.gradle.kts` | değişti | `standart` ve `emulatorDemo` derleme varyantları |
| `app/src/main/.../listener/InAppDeleteHook.kt` | yeni | Boş kanca arayüzü |
| `app/src/main/.../listener/SuzgecListener.kt` | değişti | Karar verildikten sonra kancayı çağırır (standart'ta boş) |
| `app/src/main/.../ui/SettingsScreen.kt` | değişti | Ayarlar anahtarı (yalnız emulatorDemo'da görünür) |
| `app/src/standart/.../listener/InAppDelete.kt` | yeni | Hiçbir şey yapmayan kanca |
| `app/src/emulatorDemo/.../listener/InAppDelete.kt` | yeni | Demo kancasını bağlar |
| `app/src/emulatorDemo/.../emulatordemo/InAppDeletePolicy.kt` | yeni | Dört şartı uygulayan saf karar kuralı |
| `app/src/emulatorDemo/.../emulatordemo/EmulatorInAppDelete.kt` | yeni | "Sil" eylemini tetikler, kayda not düşer |
| `app/src/emulatorDemo/res/values/strings.xml` | yeni | Uygulama adı: "Süzgeç (emülatör demo)" |
| `app/src/testEmulatorDemo/.../InAppDeletePolicyTest.kt` | yeni | 5 birim testi |
| `sender/...` | 1 yeni, 4 değişti | Sahte gelen kutusu (`FakeInbox.kt`), "Sil" eylemi, "Sahte Mesajlar" ekranı |
| `emulator-silme-demo.patch` | — | Hepsi tek yamada (16 dosya, +552 / −19 satır) |

## Kendin derlemek istersen

Deponun kökünde:

```
git apply release/deneysel_emulator_silme/kaynak/emulator-silme-demo.patch
gradlew :app:assembleEmulatorDemoDebug :sender:assembleDebug
gradlew :app:testEmulatorDemoDebugUnitTest      # 108 test (103 + 5 yeni)
gradlew :app:testStandartDebugUnitTest          # 103 test, Süzgeç 1.0 ile aynı
```

Yama, deponun kökündeki Süzgeç 1.0 koduna temiz uygulanır (kontrol edildi). Hazır APK'lar bir üst klasörde.

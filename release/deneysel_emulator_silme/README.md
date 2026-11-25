# ⚠️ Deneysel: mesajı uygulamanın içinden silme (yalnız emülatör demosu)

> **Bu klasördeki APK'lar Süzgeç 1.0 değildir.** Kullanmak için hazır sürüm [`release/Suzgec-1.0.apk`](../Suzgec-1.0.apk). Buradaki derleme yalnız bir özelliği göstermek için hazırlandı ve **yalnız Android emülatöründe** denendi.

## Ne gösteriyor?

Süzgeç 1.0 kumar bildirimini panelden kaldırır ve Silinenler kutusuna koyar; mesajın kendisi SMS ya da e-posta uygulamasında kalır. Bu deneysel derleme bir adım daha ileri gider: kullanıcı ayarlardan **açıkça izin verirse**, kumar bildirimi Silinenler'e gidince mesaj, onu gönderen uygulamanın gelen kutusundan da silinir.

Gerçek SMS ve e-posta uygulamaları başka uygulamalara mesaj silme izni vermez. Bu yüzden demo, kendi sahte gelen kutusu olan **Süzgeç Demo Gönderici** uygulamasıyla çalışır.

Demo videosundaki gösterim: [Türkçe, 2:53](https://youtu.be/Eu482-Sd8X8?t=173) · [English, 2:53](https://youtu.be/rKRlmqLBZEY?t=173)

## Güvenlik

- Süzgeç'in bu derlemesinde **SMS okuma/yazma izni yoktur**. Yalnız Demo Gönderici'nin sahte kutusundaki mesajları silebilir; başka hiçbir uygulamaya dokunmaz.
- **Gerçek telefonda silme hiç çalışmaz:** kod önce cihazın emülatör olup olmadığına bakar; değilse hiçbir şey yapmaz.
- Silme **varsayılan olarak kapalıdır**. Ayarlar'daki "Uygulamadan da sil (deneme)" anahtarı açılmadıkça hiçbir şey silinmez.
- Paket adı farklıdır (`com.suzgec.app.emulatordemo`, uygulama adı "Süzgeç (emülatör demo)"); Süzgeç 1.0'ın üzerine yazmaz.
- İkisi de **debug derlemesidir** (imzalı yayın sürümü değil). Bu nedenle gerçek telefon yerine Android Studio emülatörü önerilir.

## Nasıl çalışıyor?

Demo Gönderici her bildirimine bir **"Sil"** düğmesi koyar. Süzgeç bir bildirimi kumar diye Silinenler'e gönderdiğinde, bildirim Demo Gönderici'den geldiyse, cihaz emülatörse ve anahtar açıksa o düğmeye bir kez basar. Yani silme, Android'in bildirim eylem düğmeleri üzerinden yapılır. Kaynak kod ve ayrıntılar: [`kaynak/`](kaynak/README.md)

## Dosyalar

| Dosya | Açıklama |
|---|---|
| `Suzgec-emulator-silme-demo.apk` | Süzgeç 1.0 + "Uygulamadan da sil (deneme)" seçeneği (17,4 MB) |
| `Suzgec-Demo-Gonderici-sahte-kutu.zip` | İçinde `Suzgec-Demo-Gonderici-sahte-kutu.apk`: demo bildirimlerini gönderen ve sahte gelen kutusu tutan uygulama. GitHub'ın web yükleme sınırı (25 MB) yüzünden zip içinde. |
| [`kaynak/`](kaynak/README.md) | Bu özelliğin kaynak kodu (değişen 16 dosya ve tek yama dosyası) |

SHA-256 (APK'lar):
- `Suzgec-emulator-silme-demo.apk`: `1a55a1f0f8a06b8dbdd5d5f18e0388a51e4d12e5e08b05ee0909e07bc1e967cc`
- `Suzgec-Demo-Gonderici-sahte-kutu.apk`: `e25548a0e761d3b4e2a27b4519c53843c43b4819430bd0a358204e078dafc396`

## Emülatörde denemek

1. Android Studio'da bir emülatör açın (Android 9 / API 28 ve üstü; API 37'de denendi).
2. Zip'i açın; iki APK'yı emülatör penceresine sürükleyip bırakın (ya da `adb install <dosya>.apk`).
3. **Süzgeç (emülatör demo)** uygulamasını açıp kurulum sihirbazını tamamlayın.
4. Ayarlar'da **"Uygulamadan da sil (deneme)"** anahtarını açın.
5. **Süzgeç Demo Gönderici** ile demo bildirimlerini gönderin. Kumar mesajları Süzgeç'in Silinenler kutusuna gider ve Demo Gönderici'nin "Sahte Mesajlar" listesinden de kalkar; aile mesajı ve kart kampanyası gibi kumar olmayan mesajlar yerinde kalır.

Emülatör denemesinde anahtar kapalıyken hiçbir mesaj silinmedi; açıkken yalnız üç kumar mesajı silindi, kumar olmayan üç kontrol mesajı kaldı. Bu derlemenin birim testleri 108/108 geçti (standart derleme 103/103).

---

## English summary

**Experimental, emulator-only.** These APKs are not Süzgeç 1.0 (use [`release/Suzgec-1.0.apk`](../Suzgec-1.0.apk)). With the user's explicit opt-in ("Uygulamadan da sil (deneme)" in Settings, off by default), this build also deletes a gambling message from the sending app's inbox when it goes to the Deleted box. Real SMS/e-mail apps do not let third-party apps delete messages, so the demo works with its own fake inbox app, **Süzgeç Demo Gönderici**. The Süzgeç build has **no SMS permissions**, does nothing on a real (non-emulator) device and touches no other app; it works by pressing the "Sil" (Delete) action button on the demo sender's notification. It uses a separate package name (`com.suzgec.app.emulatordemo`). Source code: [`kaynak/`](kaynak/README.md). Both are debug builds, tested only on an Android emulator (API 37). The sender APK is zipped because of GitHub's 25 MB browser-upload limit. Demo: [video at 2:53](https://youtu.be/rKRlmqLBZEY?t=173).

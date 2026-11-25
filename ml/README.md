# ml/ — Süzgeç metin modeli (G1, 11 içerik türü)

## Veri
| Dosya | İçerik | Rol |
|---|---|---|
| `data/sentetik_v1.csv` | 5.415 sentetik bildirim, 11 tür × 38 şablon ailesi (`sentetik_uret.py`) | Eğitim |
| `data/elle_yazilmis_v1.csv` | 138 elle yazılmış, şablon dışı gerçekçi bildirim | Dış test (yazar yanlılığı var) |
| `data/sms_zayif_etiket.csv` | TurkishSMSCollection (4.733) + Uysal 2013 (772), tekilleştirilmiş, yeniden etiketli | Eğitim (TurkishSMS) / test (Uysal) |
| `data/egitim_birlesik_v2.csv` | Hepsi birlikte (10.643 satır) | Uygulamadaki son model |

**SMS'lerin yeniden etiketlenmesi (zayıf etiket):** spam → kampanya (7); kumar sözlüğüne uyanlar → kumar (8) (22 SMS). Normal → kişisel mesaj (2); banka/fatura/kargo kalıpları → güvenlik (1) / işlem (6). Kurum kalıbına uyan tüm normal SMS'ler elle kontrol edildi (`elle_kontrol=1`), 15 düzeltme yapıldı.
Komninos 2018/2019 metin içermediği için metin modeline girmez; kural katmanı (bayraklar, 5 sn) bu analizden gelir. UCI e-posta seti bildirime benzemediği için eğitimde kullanılmadı.

## Deney: SMS verisi neyi değiştirdi? (`deney_sms.py`, `out/deney_sms.json`)
| Model | Test | Kampanya duyarlılığı | Kampanya → kumar hatası | Güvenlik duyarlılığı |
|---|---|---|---|---|
| A: yalnız sentetik | Uysal SMS (772, eğitimde yok) | 0,69 | **54 / 364** | 0,89 |
| A: yalnız sentetik | TurkishSMS (4.733) | 0,67 | **611 / 2.499** | – |
| B: sentetik + TurkishSMS | Uysal SMS | 0,99 | **0** | 0,58 |
| A | Elle yazılmış (138) | 1,00 | 0 | 0,87 (makro F1 0,94) |
| B | Elle yazılmış (138) | 1,00 | 0 | 0,73 (makro F1 0,90) |

**Ana bulgu:** yalnız sentetikle eğitilen model gerçek SMS'lerde kampanyaların dörtte birini **kumar sanıyor** ("Bonus" kart adı, "TL kazan", spor sözcükleri). Bu, kumar filtresinin normal kampanyaları silmesi demek. Gerçek SMS eklenince bu hata sıfırlandı. Bedeli: güvenlik duyarlılığı düştü → aşağıdaki kural eklendi.

**Güvenlik kuralı** (`guvenlik_kurali.py`, modelden önce): elle 15/15, Uysal 17/19 yakalıyor; yanlış alarm 0/876 ve 4/4.733. Uyarı: kural bu setlere bakılarak yazıldı; gerçek bildirimlerde ayrıca ölçülmeli.

Diğer: aile-dışarıda (GroupKFold) makro F1 0,83 · eşlik farkı < 2e-7 · Python'da ~0,07 ms/örnek · model 3,86 MB (30.000 n-gram).

## Ön işleme v2 (Kotlin'de birebir aynı)
1. `uygulama | başlık | metin`
2. Türkçe küçük harf: önce `İ→i`, `I→ı`, sonra küçült
3. `URL_RE` → ` <url> `, `TEL_RE` → ` <tel> `, `NUM_RE` (`\d[\d.,]{3,}`) → ` <num> `
4. ASCII katlama: ş→s, ı→i, ğ→g, ü→u, ö→o, ç→c
5. Boşlukları tek boşluğa indir, kırp

## Çalıştırma
```
pip install -r requirements.txt pyarrow
python sentetik_uret.py --n 700
python deney_sms.py
python egit.py --data data/egitim_birlesik_v2.csv --out out
```
Çıktılar `out/` içinde; `model_tfidf_lr.json` ve `model_parity_cases.json` → `app/src/main/assets/`.

## v3 (23 Kasım) — kumar / kart kampanyası ayrımı
Uygulamadaki testte (docs/test_25kasim.md) model Bonus kart kampanyasını %71 kumar sandı, "hemen oyna", "kazı kazan" gibi kumar metinlerini kaçırdı. Sentetik veriye iki şablon ailesi eklendi (`sentetik_v2.csv`, 5.521 örnek): banka kartı puan/bonus kampanyaları (tür 7) ve hediye-bakiye / kazı kazan / freebet / kombine kupon / aviator gibi kumar kalıpları (tür 8).

Yeni zor test seti `data/zor_test_v1.csv` (20 elle yazılmış örnek, eğitimde yok; 10 kumar + 10 kart/uygulama kampanyası):
| Model | Kumar yakalanan | Kampanya → kumar hatası |
|---|---|---|
| v2 | 7 / 10 | 1 / 10 (Bonus kart, p=0,71) |
| **v3** | **10 / 10** | **0 / 10** (Bonus kart p=0,27) |

Uyarı: zor testi de şablonları yazan kişi yazdı; bazı kumar örnekleri yeni şablonlara benziyor. Gerçek telefonda ayrıca ölçülmeli.
Gerileme kontrolü (sentetik+TurkishSMS ile eğit → test): Uysal SMS kampanya duyarlılığı 0,986 → 0,989, kampanya→kumar 0 → 0; elle yazılmış set makro F1 0,897 → 0,903, kumar duyarlılığı 0,833 → 1,00, güvenlik 0,733 → 0,667 (güvenlik kuralı modelden önce çalıştığı için uygulamada etkisi yok).
Son model: `out_v3/` (sentetik_v2 + elle + tüm SMS = 11.164 satır), `model_version: 3`, `preprocess_version: 2` (ön işleme değişmedi). Aile-dışarıda makro F1 0,85.

## Modeli tek bildirimle denemek (`ornek_tahmin.py`)

- `python ornek_tahmin.py "Mesajlar" "" "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx"` → ön işlenmiş metin ve en olası 3 içerik türü (ör. `8 kumar / bahis 0.9875`).
- Uygulamanın kullandığı `../app/src/main/assets/model_tfidf_lr.json`'u okur (başka model: `--model yol`); yalnız standart kütüphane, numpy varsa onu kullanır.
- Ön işleme ve hesap `egit.py` (`on_isle`, `json_proba`) ve Kotlin `Preprocess` / `TextModel` ile birebir aynı: demo senaryosundaki 10 model metninde Kotlin ile aynı tür, olasılık farkı < 1e-15; 50 eşlik örneğinde ön işleme 50/50 aynı.
- Not: belgelerdeki metrikler (çapraz doğrulama, aile-dışarıda, elle yazılmış ve SMS testleri) **değerlendirme modellerinden** gelir; uygulamadaki son model bütün veriyle yeniden eğitildi, bu yüzden aynı verilerde ölçülürse daha iyi görünür.

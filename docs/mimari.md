# Süzgeç 1.0 — Mimari (geliştirici belgesi)

Bu belge kaynak koddan (`app/`, `sender/`, `ml/`) çıkarılmıştır. Paket kökü `com.suzgec.app`; Kotlin, Jetpack Compose, Material 3, Room, DataStore. `minSdk 26`, `compileSdk`/`targetSdk 37`.

## 1. Modüller

| Modül | İçerik |
|---|---|
| `:app` | Süzgeç uygulaması |
| `:sender` | Demo Gönderici: `docs/demo_senaryosu.md`'deki sahte bildirimleri gönderir (düğmeyle ya da `adb shell am broadcast -n com.suzgec.sender/.SendReceiver --es id <id>`). Teslim APK'sına girmez |
| `ml/` (Python) | Sentetik veri üretimi (`sentetik_uret.py`), eğitim ve JSON dışa aktarma (`egit.py`), güvenlik regex'i (`guvenlik_kurali.py`), BERT karşılaştırması (`bert/`) |

## 2. `:app` paketleri

| Paket | Sınıflar | Görev |
|---|---|---|
| (kök) | `SuzgecApp`, `MainActivity`, `DeviceKind` | Uygulama nesnesi: veritabanı, ayarlar, DND, ses, kural motoru ve metin modeli tembel (lazy) yüklenir; ayar akışları `StateFlow`. `DeviceKind` emülatör algılar (geliştirici menüsündeki örnek veri yalnız emülatörde) |
| `listener` | `SuzgecListener`, `IntentCache`, `EarlyGate`, `SummaryCleanup` | `NotificationListenerService`: bildirimi yakalar, karar verir, uygular. `IntentCache` bildirimin `contentIntent`'ini anahtara göre bellekte tutar (Room'a yazılamaz) |
| `rules` | `RuleEngine`, `Rules`, `Preprocess`, `Category`, `ContentType`, `Decision`, `NotificationFacts`, `SenderHeuristics` | Kural katmanı ve ön işleme; `assets/rules.json` ayrıştırılır |
| `model` | `TextModel`, `CategoryMapper`, `Personalization`, `Ordering` | Metin modeli çıkarımı, olasılık → kategori, kişisel düzeltme, kutu sırası |
| `data` | `NotificationRecord`, `BoxOpen`, `Correction`, `OrderPair`, `NotificationDao`, `SuzgecDatabase`, `Upsert`, `Dismiss`, `BoxSql`, `BoxOpenCount` | Room veritabanı, güncelleme kuralları, kutudan çıkma |
| `dnd` | `DndController` | Rahatsız Etmeyin öncelik modu; önceki filtre/politika yedeklenir ve duraklatmada geri yüklenir |
| `sound` | `AlertPlayer`, `AlertThrottle`, `Ringer` | Kritik/Önemli uyarısını Süzgeç kendisi çalar (`USAGE_ALARM`); zil moduna uyar; aynı sohbette 2 dk sessizlik |
| `delivery` | `Delivery`, `DeliveryReceiver`, `RescheduleReceiver` | Günde 3 sessiz teslim özeti (`AlarmManager.setWindow`, 10 dk pencere); açılış, saat ve saat dilimi değişiminde yeniden kurulur |
| `settings` | `SuzgecSettings` | DataStore: teslim saatleri, filtreler, öncelikli uygulamalar, kullanıcı adı, DND yedeği |
| `stats` | `Weekly` | Haftalık özet sayıları ve CSV dışa aktarma |
| `ui` | `BoxScreen`, `DeletedScreen`, `SettingsScreen`, `WizardScreen`, `WeeklyScreen`, `LogScreen`, `PermissionScreen` | Compose ekranları |

## 3. Karar akışı (12 adım)

`SuzgecListener.onNotificationPosted` → `RuleEngine.preFilter` → (`earlyDecision`) → (5 sn bekleme) → `RuleEngine.classify` → `apply`. İlk eşleşen adım karar verir.

| # | Adım | Kod | Sonuç |
|---|---|---|---|
| 1 | **Dokunma**: kalıcı, ön plan servisi, grup özeti, `rules.json` kategorileri (arama, alarm, navigasyon, ilerleme, sistem…), medya oturumu, korunan paketler (sistem, telefon, saat), acil durum yayını | `Rules.untouchedReason` | Dokunulmaz (panelde kalır, kayda geçer) |
| 2 | **Erken karar** (beklemeden): içeriği gizlenmiş tek kullanımlık kod, güvenlik regex'i (`SEC` var, `NEG` yok) | `RuleEngine.earlyDecision` | Kritik |
| 3 | **Erken karar**: kumar sözlüğü (filtre açıksa) | `earlyDecision` | Silinenler (panelde hiç görünmez) |
| 4 | **5 sn bekleme**: `timeoutAfter` ≤ eşik ya da kategorisi olmayan bildirim; bu sürede kalkarsa yok sayılır. Bekleme boyunca kısa `PARTIAL_WAKE_LOCK` | `PreFilter.Wait`, `SuzgecListener.handleUnsafe` | — |
| 5 | **Rezervasyon** kalıbı (gönderen/alan adı + "rezervasyon") | `Rules.reservationMatch` | Önemli |
| 6 | **Panelde bırak** seçilen uygulama | `RuleSettings.panelApps` | Dokunulmaz, ses yok |
| 7 | **Toplu kutlama**: kurum göndericisi (`SenderHeuristics`) + kutlama ifadesi | `Rules.celebrationMatch` | Önemsiz |
| 8 | **Öncelikli uygulama**: kullanıcının seçtiği Kritik/Önemli; model kumar ≥ 0,5 → Silinenler, kampanya (`NEG` ya da model tür 7 ≥ eşik) → Önemsiz; kişiden gelen sohbet mesajına bu kontrol uygulanmaz | `classify`, `campaignOf` | Kritik/Önemli/Önemsiz/Silinenler |
| 9 | **Haber uygulaması** (liste ya da `ApplicationInfo.CATEGORY_NEWS`) | `Rules.isNewsApp` | Önemsiz |
| 10 | **E-posta**: mülakat/görüşme daveti → Önemli; iş ilanı platformu göndericileri → Önemsiz | `emailImportant`, `emailLowSender` | Önemli / Önemsiz |
| 11 | **Sohbet** (`MessagingStyle`): grup + anahtar kelime ya da kullanıcının adı → Önemli; diğer grup → Normal; birebir → Önemli. Kurum göndericisi ve Telegram kanal gönderisi modele bırakılır | `groupKeyword`, `telegramChannelSign`, `messageDecision` | Önemli / Normal |
| 12 | **Metin modeli + eşleme**: filtre (kumar/alışveriş/sosyal, ≥ 0,5) → Silinenler; değilse beklenen önem puanı + kişisel δ; emin değilse (< 0,5) bir üst kategori, en fazla Normal (güvenlik ≥ 0,30 ilk ikideyse Önemli) | `CategoryMapper.decide`, `unsureRaise` | Herhangi bir kategori |

**Uygulama (`apply`)**: kayıt `upsertByKey` ile yazılır (aynı `key` güncellenir; kategori değişen güncellemede eski satır arşivlenir; kullanıcının taşıdığı kayıt yeniden sınıflanmaz — `Upsert`). Normal/Önemsiz/Silinenler `cancelNotification` ile panelden kaldırılır; altı boşalan grup özeti de silinir (`SummaryCleanup`). Kritik/Önemli için `AlertPlayer` çalar (Kritik ≈ 3,5 sn, Önemli ≈ 1 sn; aynı sohbetten 2 dk içinde ikinci uyarı yok, Kritik hariç).

`onNotificationRemoved`: kullanıcının panelde açtığı/kaydırdığı Kritik/Önemli kayıt kutudan da çıkar (`sistem_paneli`); Süzgeç'in kendi iptali (`REASON_LISTENER_CANCEL`) yok sayılır.

## 4. Veri modeli (Room, şema v7)

Şemalar `app/schemas/com.suzgec.app.data.SuzgecDatabase/{5,6,7}.json`. Yazılı migration'lar: `MIGRATION_5_6` (`dismissedAt`, `dismissMethod`), `MIGRATION_6_7` (`manualRank`, `order_pairs`). Yıkıcı migration yok.

| Tablo | Satır | Önemli sütunlar |
|---|---|---|
| `notifications` | Her bildirim (`key` tekil) | `packageName`, `appLabel`, `title`, `text`, `bigText`, `category`, `channelId`, `postTime`, `flags`, `isGroupConversation`, `suzgecCategory`, `contentType` (1–11), `reason` ("Neden burada?"), `rule`, `score` (1–4), `waitedMs`, `cancelledBySuzgec`, `seenAt`, `senderHash` (SHA-256), `restoredByUser`, `dismissedAt`/`dismissMethod` (kutudan çıkış: `acildi`, `kaydirildi`, `tumunu_temizle`, `otomatik`, `sistem_paneli`), `manualRank` |
| `box_opens` | Kutu her açıldığında | `time`, `afterDelivery` (5 dk'dan sık girişler sayılmaz: `BoxOpenCount`) |
| `corrections` | Kullanıcının kategori taşıması | `senderHash`, `packageName`, `appLabel`, `contentType`, `fromCategory`, `toCategory` |
| `order_pairs` | Sürüklemede "A, B'nin üstünde" çifti | `category`, `aboveId`/`belowId`, `aboveApp`/`aboveSender`, `belowApp`/`belowSender` |

Kayıt silinmez; kutu sorguları `dismissedAt IS NULL` olanları gösterir (`BoxSql`). Silinenler'de 7 günden eski kayıtlar kendiliğinden kutudan çıkar. Haftalık özet ve CSV bütün kayıtları kullanır; CSV bildirim metni ve başlığı içermez.

## 5. Metin modeli ve Kotlin çıkarımı

- **Dosya:** `app/src/main/assets/model_tfidf_lr.json` (3,9 MB): `vocab` (30.000 karakter n-gramı → indeks), `idf`, `coef` (11 × 30.000), `intercept`, `classes` (1–11), `class_names`, `ngram_min`=2, `ngram_max`=5, `preprocess_version`=2. `ml/egit.py` üretir.
- **Girdi:** `uygulama | başlık | metin`. **Ön işleme** (`Preprocess.onIsle`, Python'daki `on_isle` ile birebir): Türkçe küçük harf, bağlantı → `<url>`, telefon → `<tel>`, 4+ basamak → `<num>`, ASCII katlama (ş→s, ı→i, ğ→g, ü→u, ö→o, ç→c), boşluk sadeleştirme.
- **Çıkarım** (`TextModel.predictPreprocessed`): scikit-learn `char_wb` mantığıyla her kelime `" " + w + " "` sarılır, 2–5 uzunluğunda n-gramlar sayılır → `1 + ln(tf)` × `idf` → L2 normalize → `coef·x + b` → softmax. Kod noktası bazında çalışır (emoji Python'la aynı sayılır).
- **Eşlik:** `model_parity_cases.json` (50 örnek, Python olasılıkları) ile her sınıfta fark < 1e-4 (`TextModelParityTest`).
- **İçerik türleri ve varsayılan kategori** (`CategoryMapper.defaultCategory`): 1 güvenlik/para → Kritik; 2 kişisel mesaj, 4 okul/iş/resmi, 5 zaman hassas → Önemli; 3 grup, 6 işlem bilgisi → Normal; 7 kampanya, 8 kumar, 9 sosyal, 10 geri çağırma/oyun, 11 sistem/diğer → Önemsiz (kumar filtreyle Silinenler).

## 6. Kişiselleştirme

**Kategori düzeltmesi (δ)** — `Personalization`. Anahtar: (gönderen karması, uygulama etiketi, içerik türü). Kullanıcının "Taşı…" ile yaptığı her yukarı/aşağı taşıma `corrections`'a yazılır:

δ = η · (n_yukarı − n_aşağı) / (n_yukarı + n_aşağı + k), k = 2, η = 1 (bir kategori basamağı)

δ, modelin beklenen önem puanına (Kritik 4, Önemli 3, Normal 2, Önemsiz 1) eklenir ve puan [1, 4]'e kırpılır; birebir/grup mesajı kararlarında da kategorinin ağırlığına eklenir. Örnek: iki kez yukarı → δ = 0,5. Kaydırma ve "Tümünü temizle" düzeltme sayılmaz.

**Sıra puanı** — `Ordering`. Anahtar: (uygulama etiketi, gönderen karması). Sürüklemede kaydın üstünden geçtiği her kayıtla bir `order_pairs` çifti yazılır:

δ_sıra = 0,5 · (n_yukarı − n_aşağı) / (n_yukarı + n_aşağı + 2)

Kutu sırası: kategori → sıra değeri (elle yerleştirilmişse `manualRank`, değilse önem puanı + δ_sıra) → zaman. Sıra puanı **yalnız kategori içi sırayı** etkiler; kategori kararına, sese ve δ'ya girmez (`RuleEngine` bu sınıfı görmez).

## 7. Testler (`gradlew :app:testDebugUnitTest`, 103 birim testi, Android'siz JVM)

| Test sınıfı | Test | Neyi doğrular |
|---|---|---|
| `rules.RuleEngineTest` | 13 | Kural sırası, dokunma/bekleme ön süzgeci, güvenlik ve kumar sözlüğü, sohbet kuralları |
| `model.RealUseRulesTest` | 15 | Gerçek kullanımdan türetilen kurallar (haber, e-posta, toplu kutlama, rezervasyon, Telegram kanalı, öncelikli uygulamada kampanya) — sahte metinlerle |
| `model.DemoScenarioTest` | 5 | Demo senaryosunun 31 satırı, kural + model uçtan uca, iki ayar profiliyle |
| `model.EarlyDecisionTest` | 4 | Güvenlik ve kumarın 5 sn beklemeden önce karar vermesi; diğerlerinde davranışın değişmemesi |
| `model.FiltersAndPersonalizationTest` | 10 | Alışveriş/sosyal filtrelerin uygulama listesiyle sınırı; δ formülü ve kategori kayması |
| `model.UnsureRaiseTest` | 9 | Belirsizlik yükseltmesi (en fazla Normal; güvenlik ≥ 0,30 → Önemli) |
| `model.OrderingTest` | 7 | Sürükleme çiftleri, `manualRank`, sıra puanının kategori kararını etkilememesi |
| `model.TextModelParityTest` | 3 | Kotlin ↔ Python olasılık eşliği (< 1e-4) |
| `rules.PreprocessParityTest` | 3 | Kotlin ↔ Python ön işleme eşliği |
| `model.RealDataReplayTest` | 1 | Yerel gerçek kullanım yedeği varsa çevrimdışı yeniden sınıflama; bu depoda veri olmadığı için **atlanır** |
| `data.MigrationAndBoxSqlTest` | 10 | 5→6→7 migration'ları gerçek SQLite'ta (dışa aktarılmış şemalardan), kutu/Silinenler sorguları, kutudan çıkma |
| `data.UpsertThrottleOpensTest` | 5 | Taşınmış kaydın korunması, aynı sohbet sessizliği, kutuya giriş sayımı |
| `listener.SummaryCleanupTest` | 4 | Altı boşalan grup özetinin silinmesi (altta Önemli/bekleyen varsa dokunmama) |
| `sound.AlertPlanTest` | 4 | Zil moduna göre ses/titreşim planı |
| `sound.RingerTest` | 4 | Zil modu kaynağı (sistem yayını → AudioManager → son kayıt) |
| `delivery.DeliveryTimeTest` | 2 | Sonraki teslim zamanının hesaplanması |
| `stats.CsvHeaderTest` | 2 | CSV başlığı ve satırlarının sütun sayısı |
| `DeviceKindTest` | 2 | Emülatör algılama (gerçek cihaz parmak izi emülatör sayılmaz) |

Testler `app/src/main/assets/` dosyalarını doğrudan okur; yeni model gelince `gradlew :app:testDebugUnitTest --rerun-tasks`.

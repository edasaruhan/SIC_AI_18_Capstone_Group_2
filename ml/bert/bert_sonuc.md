# turkish-mini-bert deneyi — sonuç (23 Kasım 2026, Colab T4 GPU)

Aynı eğitim verisi (sentetik_v1 5.415 + TurkishSMSCollection 4.733 = 10.148) ve aynı testler; tek koşu (seed 26).
BERT: ytu-ce-cosmos/turkish-mini-bert-uncased (11,6M parametre), 4 epok, lr 5e-5, batch 64, max 64 token, sınıf ağırlıklı kayıp. Eğitim ~18 sn (T4). ASCII katlama yok (BERT kendi Türkçe sözlüğüyle çalışıyor).
Not: Colab'daki transformers sürümü Trainer'ın `warmup_ratio` argümanını kabul etmediği için eğitim döngüsü elle yazıldı (aynı ayarlar).

| Model | Test | Makro F1 | Kampanya duyarlılığı | Kampanya→kumar | Güvenlik duyarlılığı | Kumar duyarlılığı | Kişisel→silinen |
|---|---|---|---|---|---|---|---|
| TF-IDF + LR | Uysal SMS (772, eğitimde yok) | **0,530** | 0,986 | 0 | **0,579** | – | 8 |
| turkish-mini-bert | Uysal SMS | 0,339 | 0,992 | 1 | 0,053 | – | 9 |
| TF-IDF + LR | Elle yazılmış (138) | 0,897 | **1,000** | 0 | 0,733 | 0,833 | 0 |
| turkish-mini-bert | Elle yazılmış | **0,917** | 0,667 | 1 | **0,933** | **1,000** | 0 |

| | turkish-mini-bert | TF-IDF + LR |
|---|---|---|
| Tek bildirim, CPU tek çekirdek (Colab) | 8,29 ms | 1,61 ms (uygulamada Kotlin: 0,045 ms) |
| Boyut | 46,2 MB | 3,86 MB |

## Yorum
- Bildirim biçimindeki temiz metinde (elle yazılmış set) BERT biraz daha iyi: makro F1 0,92 vs 0,90; güvenlik ve kumar duyarlılığı daha yüksek. Ama kampanyaların üçte birini başka türlere kaydırdı ve 1 kampanyayı kumar sandı.
- Eğitimde hiç görülmeyen gerçek, eski, çoğu Türkçe karaktersiz yazılmış SMS'lerde (Uysal 2013) BERT belirgin şekilde geride: güvenlik SMS'lerinin yalnız %5'ini tanıdı. Olası neden: BERT'in alt-kelime sözlüğü "sifreniz", "hesabiniza" gibi ASCII yazımları iyi temsil etmiyor; TF-IDF'te ön işleme (ASCII katlama) bu farkı kapatıyor.
- Telefonda BERT 46 MB ve ~5 kat yavaş (yine de 50 ms sınırının altında).
- **Karar:** Uygulamada TF-IDF + LR (v3) kalıyor; turkish-mini-bert derin öğrenme karşılaştırması olarak 5. ödevde raporlanıyor. Kritik güvenlik bildirimlerinde iki model de kural katmanının arkasında çalışıyor (güvenlik kuralı ikisinden önce).
- Sınırlılık: tek koşu, küçük test setleri; BERT'e ASCII-katlanmış veri ile ek eğitim ve v3 verisiyle yeniden koşu ileride denenebilir.

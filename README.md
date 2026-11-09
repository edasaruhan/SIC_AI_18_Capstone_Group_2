# Süzgeç · Grup 2

**Samsung Innovation Campus (SIC) AI 18 & Yeşilay — Capstone Projesi**
*Dijital bağımlılık / Digital addiction*

---

## Türkçe

**Süzgeç**, telefonu sürekli "Rahatsız Etmeyin" modunda tutan ve gelen her bildirimi gönderen, uygulama, içerik ve saate göre beş kategoriye ayıran bir Android uygulamasıdır. Kritik ve Önemli bildirimler sesli olarak hemen gelir; Normal ve Önemsiz bildirimler kullanıcının seçtiği saatlerde günde 3 kez özet halinde sunulur; kullanıcının seçtiği bağımlılık türüyle ilgili bildirimler (kumar, alışveriş kampanyaları, sosyal medya) silinir.

**Amaç:** FoMO'yu artırmadan telefon kontrol etme ve kilit açma sayısını azaltmak; sesli/titreşimli uyarıları en az %50 azaltırken Kritik bildirimlerin en az %95'ini kaçırmadan iletmek.

**Hedef kitle:** 18 yaş üstü, üst model olmayan telefon kullanan Android kullanıcıları; öncelikle üniversite öğrencileri.

**Yaklaşım:** Cihaz üzerinde çalışan hibrit sistem — kural katmanı + küçük Türkçe BERT (turkish-mini-bert) metin modeli + TF-IDF ve lojistik regresyon taban/yedek modeli + kullanıcı düzeltmelerinden öğrenen kişiselleştirme.

## English

**Süzgeç** (Turkish for "filter") is an Android app that keeps the phone in Do Not Disturb mode and sorts every notification into five categories based on its sender, app, content and time. Critical and Important notifications arrive immediately with sound; Normal and Unimportant ones are delivered as a summary three times a day at times the user chooses; notifications related to the addiction type selected by the user (gambling, shopping promotions, social media) are deleted.

**Goal:** Reduce phone checks and unlocks without increasing FoMO; cut sound/vibration alerts by at least 50% while delivering at least 95% of Critical notifications.

**Target users:** Android users aged 18+ with non-flagship phones, primarily university students.

**Approach:** On-device hybrid system — rule layer + small Turkish BERT text model (turkish-mini-bert) + TF-IDF and logistic regression baseline/fallback + personalization that learns from the user's corrections.

---

## Teslim dosyaları / Deliverables

| # | Ödev / Assignment | English | Türkçe |
|---|---|---|---|
| 1 | Idea Proposal / Fikir Önerisi | [PDF](deliverables/01_Idea_Proposal/Suzgec_Group2_Idea_Proposal_EN.pdf) · [Word](deliverables/01_Idea_Proposal/Suzgec_Group2_Idea_Proposal_EN.docx) | [PDF](deliverables/01_Idea_Proposal/Suzgec_Grup2_Fikir_Onerisi_TR.pdf) · [Word](deliverables/01_Idea_Proposal/Suzgec_Grup2_Fikir_Onerisi_TR.docx) |
| 2 | Literature, Data & Technology Review / Literatür, Veri ve Teknoloji İncelemesi | [PDF](deliverables/02_Literature_Data_Technology_Review/Suzgec_Group2_Literature_Data_Technology_Review_EN.pdf) · [Word](deliverables/02_Literature_Data_Technology_Review/Suzgec_Group2_Literature_Data_Technology_Review_EN.docx) | [PDF](deliverables/02_Literature_Data_Technology_Review/Suzgec_Grup2_Literatur_Veri_Teknoloji_Incelemesi_TR.pdf) · [Word](deliverables/02_Literature_Data_Technology_Review/Suzgec_Grup2_Literatur_Veri_Teknoloji_Incelemesi_TR.docx) |
| 3 | Concept Note & Implementation Plan / Kavram Notu ve Uygulama Planı | [PDF](deliverables/03_Concept_Note_Implementation_Plan/Suzgec_Grup2_Concept_Note_Implementation_Plan_EN.pdf) · [Word](deliverables/03_Concept_Note_Implementation_Plan/Suzgec_Grup2_Concept_Note_Implementation_Plan_EN.docx) | [PDF](deliverables/03_Concept_Note_Implementation_Plan/Suzgec_Grup2_Concept_Note_ve_Uygulama_Plani_TR.pdf) · [Word](deliverables/03_Concept_Note_Implementation_Plan/Suzgec_Grup2_Concept_Note_ve_Uygulama_Plani_TR.docx) |

## Ekip / Team

| Üye / Member |
|---|
| Süleyman Yalım |
| Sinem Can |
| Nimet Nisa Silav |
| Mehmet Ali Kuruçay |
| Muhammed Hamza Orak |

## Depo yapısı / Repository layout

```
.
├── README.md
└── deliverables/
    ├── 01_Idea_Proposal/                       # 1. ödev (EN + TR, PDF + Word)
    ├── 02_Literature_Data_Technology_Review/   # 2. ödev (EN + TR, PDF + Word)
    └── 03_Concept_Note_Implementation_Plan/    # 3. ödev (EN + TR, PDF + Word)
```

## Veri ve gizlilik / Data and privacy

Ham veri setleri bu depoya eklenmemiştir; kullanılan veri setleri, kaynakları ve lisansları 2. ödevin Part 2 bölümünde listelenmiştir.
Raw datasets are not included in this repository; the datasets used, their sources and licences are listed in Part 2 of the second deliverable.

## Ana kaynak / Main source

Fitz, N., Kushlev, K., Jagannathan, R., Lewis, T., Paliwal, D., & Ariely, D. (2019). Batching smartphone notifications can improve well-being. *Computers in Human Behavior, 101*, 84–94. https://doi.org/10.1016/j.chb.2019.07.016

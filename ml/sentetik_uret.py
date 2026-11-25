"""Süzgeç — sentetik Türkçe bildirim veri seti üreticisi (v1).

11 içerik türü için şablon aileleri. Her örnek: app, title, text, label, family.
`family` (şablon ailesi) eğitim/test bölmesinde sızıntıyı önlemek için kullanılır:
aynı aileden örnekler hem eğitimde hem testte olmaz.

Kullanım: python sentetik_uret.py --n 600 --out data/sentetik_v1.csv
"""
import argparse
import csv
import random

ISIMLER = ["Ayşe", "Mehmet", "Zeynep", "Ali", "Elif", "Can", "Merve", "Burak", "Selin", "Emre",
           "Annem", "Babam", "Abim", "Ablam", "Deniz", "Ece", "Kerem", "Hande", "Oğuz", "Nisa"]
GRUPLAR = ["Sınıf Grubu", "EE 4. Sınıf", "Aile", "Kuzenler", "Proje Ekibi", "Halı Saha", "Yurt 3. Kat",
           "SIC Grup 2", "Lab Grubu", "Mezunlar"]
BANKALAR = ["Ziraat Mobil", "İşCep", "Garanti BBVA", "Akbank", "Yapı Kredi", "QNB", "VakıfBank", "Enpara"]
MAGAZALAR = ["Trendyol", "Hepsiburada", "Amazon", "n11", "Getir", "Yemeksepeti", "Migros", "A101", "LC Waikiki"]
KARGOLAR = ["Yurtiçi Kargo", "Aras Kargo", "MNG Kargo", "PTT Kargo", "Sürat Kargo", "HepsiJET", "Trendyol Express"]
SOSYAL = ["Instagram", "TikTok", "X", "Facebook", "Snapchat", "YouTube", "LinkedIn", "Twitch"]
MESAJ = ["WhatsApp", "Telegram", "Mesajlar", "Messenger", "Signal"]
OKUL = ["OBS", "YTÜ Online", "Microsoft Teams", "e-Devlet", "Outlook", "Gmail", "MHRS", "Moodle"]
OYUNLAR = ["Candy Crush", "Clash of Clans", "PUBG Mobile", "Brawl Stars", "Subway Surfers", "Duolingo",
           "Royal Match", "Pokémon GO"]
SISTEM = ["Android Sistemi", "Google Play Store", "Ayarlar", "Samsung Cloud", "Galaxy Store", "Pil", "Cihaz bakımı"]
KUMAR_SMS = ["Mesajlar", "Telegram", "WhatsApp", "Chrome"]
TAKVIM = ["Google Takvim", "Samsung Takvim", "Outlook", "Pegasus", "THY", "Obilet", "Google Keep"]
DERSLER = ["Güç Sistemleri Analizi", "Enerji Dağıtımı", "Elektrik Makinaları", "Sosyal Politika",
           "Elektrik Makinalarının Kontrolü", "Mühendislik Tasarımı", "Sinyaller", "Devre Teorisi"]
URUNLER = ["kulaklık", "spor ayakkabı", "mont", "telefon kılıfı", "kahve makinesi", "laptop çantası", "parfüm", "saat"]
KUMAR_SITE = ["betxx", "kazanbet", "sekabet", "jojobet", "casinomaxi", "tipobet", "matadorbet", "superbetin"]


def para():
    return random.choice(["150", "250", "1.250", "2.500", "75,90", "3.400", "18.750", "499,99", "12.000", "60"])


def kod():
    return str(random.randint(100000, 999999))


def link():
    return random.choice(["bit.ly/3xK9", "t.ly/aB7", "cutt.ly/kzn", "https://tinyurl.com/y8b2", "goo.su/Kd8",
                          "https://kisa.link/bns", "bit.ly/bonus26"])


def saat():
    return f"{random.randint(8, 22):02d}:{random.choice(['00', '15', '30', '45'])}"


def kart():
    return str(random.randint(1000, 9999))


# Her aile: (aile_adı, uygulama_üretici, başlık_üretici, metin_üretici)
R = random.choice
AILELER = {
    1: [  # Güvenlik ve para hareketi
        ("otp_kod", lambda: R(BANKALAR + ["Mesajlar"]), lambda: R(["Doğrulama kodu", "Tek kullanımlık şifre", "Güvenlik kodu", ""]),
         lambda: R([f"{kod()} doğrulama kodunuzdur. Kimseyle paylaşmayınız.",
                    f"İşleminizi onaylamak için tek kullanımlık şifreniz: {kod()}",
                    f"{R(MAGAZALAR)} ödemeniz için doğrulama kodu {kod()}. Bu kodu banka çalışanları dahil kimseye vermeyin."])),
        ("para_cekme", lambda: R(BANKALAR), lambda: R(["Hesap hareketi", "Para çekme", "Harcama bildirimi"]),
         lambda: R([f"Hesabınızdan {para()} TL çekilmiştir.",
                    f"****{kart()} ile biten kartınızdan {R(MAGAZALAR)} işyerinde {para()} TL harcama yapıldı.",
                    f"Hesabınıza {R(ISIMLER)} tarafından {para()} TL EFT gönderildi."])),
        ("giris_uyari", lambda: R(BANKALAR + ["Google", "Instagram", "e-Devlet", "Outlook"]), lambda: R(["Yeni giriş", "Güvenlik uyarısı", "Şüpheli işlem"]),
         lambda: R([f"Hesabınıza yeni bir cihazdan giriş yapıldı. Siz değilseniz hemen şifrenizi değiştirin.",
                    f"Saat {saat()} itibarıyla internet bankacılığına giriş yapılmıştır.",
                    "Kartınızla şüpheli bir işlem denendi ve engellendi. Lütfen bizi arayın.",
                    "Şifreniz başarıyla değiştirildi. Bu işlemi siz yapmadıysanız bize ulaşın."])),
        ("odeme", lambda: R(BANKALAR + ["Papara", "ininal"]), lambda: R(["Ödeme alındı", "Fatura ödemesi", "Otomatik ödeme"]),
         lambda: R([f"{para()} TL kredi kartı borç ödemeniz alındı.",
                    f"Son ödeme tarihi yarın olan {para()} TL kart borcunuz bulunmaktadır.",
                    f"Otomatik ödeme talimatınızla {para()} TL elektrik faturanız ödendi."])),
    ],
    2: [  # Kişisel mesaj
        ("kisa_mesaj", lambda: R(MESAJ), lambda: R(ISIMLER),
         lambda: R(["Akşam ara beni", "Neredesin?", "Eve gelirken ekmek al", "Müsait olunca döner misin", "Tamamdır görüşürüz",
                    "Yarın sabah kaçta çıkıyoruz?", "Sınav nasıl geçti?", "Bugün yemeğe geliyor musun", "Acil dönüş yap lütfen",
                    "Doğum günün kutlu olsun 🎉", "Dosyayı attım bakar mısın", "Kapıdayım aşağı in"])),
        ("sesli_foto", lambda: R(MESAJ), lambda: R(ISIMLER),
         lambda: R(["🎤 Sesli mesaj (0:23)", "📷 Fotoğraf", "📎 Belge: rapor.pdf", "📍 Konum paylaştı", "🎥 Video", "Çıkartma"])),
        ("cagri_cevapsiz", lambda: R(MESAJ), lambda: R(ISIMLER),
         lambda: R(["Cevapsız sesli arama", "Cevapsız görüntülü arama", "Seni aradı"])),
        ("uzun_mesaj", lambda: R(MESAJ), lambda: R(ISIMLER),
         lambda: R([f"Yarın {saat()}'de kütüphanede buluşalım mı, {R(DERSLER)} için birlikte çalışırız",
                    "Hocanın attığı maili gördün mü, ödev teslimi ertelenmiş galiba",
                    "Annemler hafta sonu bize geliyor, sen de gel istersen",
                    f"{R(ISIMLER)} ile konuştum, proje sunumunu perşembe yapacağız"])),
    ],
    3: [  # Grup mesajı
        ("grup_ozet", lambda: R(MESAJ), lambda: R(GRUPLAR),
         lambda: f"{random.randint(3, 120)} yeni mesaj"),
        ("grup_kisi", lambda: R(MESAJ), lambda: R(GRUPLAR),
         lambda: f"{R(ISIMLER)}: " + R(["Arkadaşlar yarın ders var mı?", "Notlar paylaşıldı mı", "Haha 😂", "Kim geliyor akşam",
                                       "Sunum dosyası gruba atıldı", "Bu hafta lab yok sanırım", "Günaydın herkese", "👍"])),
        ("grup_medya", lambda: R(MESAJ), lambda: R(GRUPLAR),
         lambda: f"{R(ISIMLER)}: " + R(["📷 Fotoğraf", "🎤 Sesli mesaj", "📎 notlar.pdf", "GIF", "🎥 Video"])),
    ],
    4: [  # Okul / iş / resmi kurum
        ("not_aciklama", lambda: R(OKUL), lambda: R(["Not açıklandı", "Sınav sonucu", "Duyuru"]),
         lambda: R([f"{R(DERSLER)} dersi vize notunuz sisteme girilmiştir.",
                    f"{R(DERSLER)} final sınav sonuçları açıklandı.",
                    f"{R(DERSLER)} dersi için yeni bir duyuru yayınlandı."])),
        ("mail_hoca", lambda: R(["Outlook", "Gmail"]), lambda: R(["Dr. Öğr. Üyesi", "Prof. Dr.", "Bölüm Sekreterliği", "Öğrenci İşleri"]) + " " + R(ISIMLER[:10]),
         lambda: R([f"{R(DERSLER)} ödev teslim tarihi hakkında", "Staj belgelerinizi en geç cuma gününe kadar teslim ediniz",
                    "Yarınki ders çevrim içi yapılacaktır", "Proje raporu geri bildirimi", "Bitirme projesi danışman ataması"])),
        ("edevlet", lambda: R(["e-Devlet", "MHRS", "e-Nabız", "PTT", "GİB"]), lambda: R(["Bilgilendirme", "Randevu", "Tebligat"]),
         lambda: R([f"MHRS randevunuz {random.randint(1, 28)} Kasım saat {saat()} olarak oluşturulmuştur.",
                    "Adınıza yeni bir elektronik tebligat bulunmaktadır.",
                    "Başvurunuz sonuçlandı. Detaylar için e-Devlet'e giriş yapınız.",
                    "e-Reçeteniz düzenlenmiştir."])),
        ("is_teams", lambda: R(["Microsoft Teams", "Slack", "Outlook", "Zoom"]), lambda: R(ISIMLER + ["Toplantı"]),
         lambda: R(["Sizi bir toplantıya davet etti", "Bir dosyada sizden bahsetti", "Kanalda sizi etiketledi", "Görev size atandı"])),
    ],
    5: [  # Zaman hassas hatırlatma
        ("takvim", lambda: R(TAKVIM), lambda: R(["Toplantı", "Ders", "Etkinlik", "Hatırlatma"]),
         lambda: R([f"{random.choice([5, 10, 15, 30])} dk sonra: {R(DERSLER)}",
                    f"Bugün {saat()}: proje toplantısı", f"Yarın {saat()}: dişçi randevusu",
                    f"{saat()} SIC mentor görüşmesi başlıyor"])),
        ("ucus", lambda: R(["THY", "Pegasus", "AJet", "Obilet", "Biletall"]), lambda: R(["Uçuş bilgisi", "Sefer hatırlatma", "Check-in"]),
         lambda: R([f"Uçuşunuz için online check-in açıldı. Kalkış {saat()}.",
                    f"Otobüs seferiniz {saat()}'de kalkıyor, peron 23.",
                    "Uçuşunuzun kapı numarası değişti: B12", "Uçuşunuz 40 dakika rötarlıdır."])),
        ("not_hatirlatici", lambda: R(["Google Keep", "Samsung Hatırlatıcı", "Todoist", "Google Görevler"]), lambda: R(["Hatırlatıcı", "Görev"]),
         lambda: R(["İlaç saati", "Fatura son gün bugün", "Ödev teslimi bugün 23:59", "Annemi ara", "Kira yatır"])),
    ],
    6: [  # İşlem bilgilendirmesi
        ("siparis", lambda: R(MAGAZALAR), lambda: R(["Siparişiniz", "Sipariş durumu", "Teslimat"]),
         lambda: R(["Siparişiniz hazırlanıyor.", "Siparişiniz kargoya verildi.", "Siparişiniz teslim edildi. Afiyet olsun!",
                    "Kuryeniz yola çıktı, tahmini varış 15 dk.", "İade talebiniz onaylandı.", "Siparişiniz dağıtıma çıktı."])),
        ("kargo", lambda: R(KARGOLAR + ["Mesajlar"]), lambda: R(["Kargo", "Gönderi bilgisi", ""]),
         lambda: R([f"{random.randint(100000000, 999999999)} takip numaralı gönderiniz dağıtıma çıkmıştır.",
                    f"Gönderiniz {R(['Esenler', 'Beşiktaş', 'Kadıköy', 'Bağcılar'])} şubesine ulaştı.",
                    "Gönderiniz teslim edilmiştir. Bizi tercih ettiğiniz için teşekkürler."])),
        ("fatura", lambda: R(["Turkcell", "Vodafone", "Türk Telekom", "İGDAŞ", "CK Boğaziçi", "İSKİ", "Superonline"]),
         lambda: R(["Faturanız", "Fatura bilgisi"]),
         lambda: R([f"{R(['Kasım', 'Ekim', 'Aralık'])} ayı faturanız {para()} TL'dir. Son ödeme: {random.randint(1, 28)} Aralık.",
                    "Faturanız ödenmiştir, teşekkür ederiz.", f"Paketinizin %80'ini kullandınız."])),
    ],
    7: [  # Kampanya / indirim / reklam
        ("kart_puan", lambda: R(["Garanti BBVA","Yapı Kredi","Akbank","İşCep","QNB","Mesajlar","Bonus","Maximum","Paraf","Bankkart"]),
         lambda: R(["Kampanya","Size özel","Fırsat","",R(["Bonus","World","Axess","Maximum","Paraf","Bankkart","Wings"])]),
         lambda: R([f"{R(['Bonus','World','Axess','Maximum','Paraf','Bankkart'])} kartınla {R(['market','akaryakıt','giyim','elektronik','restoran'])} harcamana {random.choice([50,100,150,250])} TL {R(['bonus','puan','chip-para','maxipuan','worldpuan'])} kazan!",
                    f"Kartınla {para()} TL ve üzeri alışverişe {random.choice([3,6,9])} taksit + {random.choice([50,100])} TL bonus hediye.",
                    f"{R(['Bonus','Axess','World'])} Flaş: bu hafta sonu kartınla yapacağın harcamalara ekstra puan kazan.",
                    f"Kampanyaya katılmak için KATIL yazıp {random.choice([4442,3340,5300])}'e gönder, {para()} TL harcamana {random.choice([100,200])} TL bonus kazan.",
                    f"Kredi kartı başvurunu bugün yap, ilk alışverişine {random.choice([200,300,500])} TL bonus hediye!"])),
        ("indirim", lambda: R(MAGAZALAR), lambda: R(["Kaçırma!", "Sana özel 🎁", "Son gün!", "Fırsat", "🔥 Büyük indirim"]),
         lambda: R([f"Sepetindeki {R(URUNLER)} %{random.choice([20, 30, 40, 50, 70])} indirimde!",
                    f"Efsane Kasım başladı! Binlerce üründe %{random.choice([50, 60, 70])}'e varan indirim.",
                    f"Sadece bugün {para()} TL ve üzeri alışverişe kargo bedava.",
                    f"Favorilerindeki {R(URUNLER)} fiyatı düştü, stoklar tükenmeden al."])),
        ("kupon", lambda: R(MAGAZALAR), lambda: R(["Kupon", "Hediye", "İndirim kodu"]),
         lambda: R([f"{random.choice([50, 100, 150])} TL indirim kuponun tanımlandı, {random.randint(2, 7)} gün geçerli.",
                    f"KASIM{random.randint(10, 99)} koduyla ek %10 indirim!", "Sana özel kupon seni bekliyor 🎁"])),
        ("banka_kampanya", lambda: R(BANKALAR), lambda: R(["Kampanya", "Fırsat", "Size özel"]),
         lambda: R([f"Kartınızla yapacağınız {para()} TL harcamaya {random.choice([100, 250, 500])} TL puan!",
                    "İhtiyaç kredisinde faizsiz 3 ay fırsatı sizi bekliyor.",
                    f"Kredi kartınıza {para()} TL'ye varan chip-para kazanma fırsatı."])),
        ("operator_reklam", lambda: R(["Turkcell", "Vodafone", "Türk Telekom", "Mesajlar"]), lambda: R(["Kampanya", ""]),
         lambda: R([f"Size özel {random.choice([10, 20, 40])} GB internet sadece {para()} TL! Hemen katılmak için EVET yazıp gönderin.",
                    "Yeni iPhone 0 faiz 12 taksit fırsatıyla mağazalarımızda.", "B001 SMS ret için RET yazıp 3000'e gönderin."])),
    ],
    8: [  # Kumar / bahis tanıtımı
        ("hediye_oyna", lambda: R(KUMAR_SMS), lambda: R(["", "Hediye", "Tebrikler"]),
         lambda: R([f"Hesabına {para()} TL hediye tanımlandı, hemen oyna: {link()}",
                    f"Yatırımsız {random.choice([50,100,250])} TL freebet hesabında! Hemen kullan {link()}",
                    f"Kazı kazan oyna, {para()} TL senin olabilir! {link()}",
                    f"Aviator'da çarpan yükseliyor, şimdi gir kazan {link()}",
                    f"Kombine kuponun hazır: 3 maç, {random.choice([7,12,15])}.40 oran. Hemen oyna {link()}",
                    f"Günün banko maçı: üst 2.5, oran {random.choice([1,2])}.{random.randint(50,95)}. Kanala katıl {link()}",
                    f"Spin çarkı seni bekliyor, her gün ücretsiz çevir ve kazan {link()}",
                    f"Yeni giriş adresimiz: {link()} Üyelere özel {para()} TL kayıp iadesi."])),
        ("bonus_sms", lambda: R(KUMAR_SMS), lambda: R(["", "Bonus", "Kazan"]),
         lambda: R([f"Hoş geldin bonusu {para()} TL! Hemen üye ol: {link()}",
                    f"%{random.choice([100, 150, 200])} yatırım bonusu seni bekliyor {link()}",
                    f"Çevrimsiz {random.choice([50, 100, 250])} free spin hesabına tanımlandı! {link()}",
                    f"{R(KUMAR_SITE)} yeni adresi: {link()} Kesintisiz giriş için tıkla.",
                    f"Deneme bonusu veren site! Yatırımsız {para()} TL {link()}"])),
        ("bahis_oran", lambda: R(KUMAR_SMS + ["Chrome"]), lambda: R(["Canlı bahis", "Yüksek oran", "Maç günü"]),
         lambda: R([f"Derbi maçına özel 5.00 oran! Kuponunu şimdi yap {link()}",
                    f"Canlı bahiste kayıp bonusu %{random.choice([20, 25, 30])} {link()}",
                    "Bu akşamki maça dev oranlar, kazanmak çok kolay!", f"Kombine kuponuna sigorta! {link()}"])),
        ("casino", lambda: R(KUMAR_SMS + ["Chrome", "Facebook"]), lambda: R(["Casino", "Jackpot", "🎰"]),
         lambda: R([f"Slot oyunlarında {para()} TL jackpot seni bekliyor! {link()}",
                    "Canlı casino masalarında anında çekim, hemen kazan!",
                    f"Rulet ve blackjack'te %{random.choice([10, 15, 20])} kayıp iadesi {link()}",
                    "Sweet Bonanza'da büyük kazanç fırsatı, şimdi dene!"])),
        ("sans_oyun", lambda: R(["Mesajlar", "Chrome", "Facebook", "Instagram"]), lambda: R(["Şansını dene", "Büyük ikramiye"]),
         lambda: R(["Çarkı çevir, anında para kazan! Para çekme limiti yok.", f"Tebrikler! {para()} TL kazandınız, almak için {link()}",
                    "Kazı kazan oyununda büyük ikramiye bu gece!"])),
    ],
    9: [  # Sosyal etkileşim
        ("begeni", lambda: R(SOSYAL), lambda: R(["", R(SOSYAL)]),
         lambda: R([f"{R(ISIMLER).lower()}_{random.randint(1, 99)} gönderini beğendi.",
                    f"{R(ISIMLER).lower()}{random.randint(1, 99)} ve {random.randint(2, 40)} diğer kişi fotoğrafını beğendi.",
                    f"{R(ISIMLER).lower()}.{R(['tr', 'x', 'ofc'])} seni takip etmeye başladı.",
                    f"{R(ISIMLER).lower()} hikayende senden bahsetti."])),
        ("canli_oneri", lambda: R(SOSYAL), lambda: R(["", "Önerilen"]),
         lambda: R([f"{R(ISIMLER).lower()}_{random.randint(1, 99)} canlı yayında: şimdi katıl!",
                    "Tanıyor olabileceğin kişiler", "Bugün kaçırdığın gönderiler var", "Senin için yeni Reels videoları",
                    f"{R(ISIMLER)} yeni bir video paylaştı", "Popüler bir gönderi: 1,2 Mn görüntülenme"])),
        ("yorum", lambda: R(SOSYAL), lambda: R(["", "Yorum"]),
         lambda: R([f"{R(ISIMLER).lower()}{random.randint(1, 99)} gönderine yorum yaptı: \"çok iyi 🔥\"",
                    f"{R(ISIMLER).lower()} yorumuna yanıt verdi", f"{random.randint(2, 30)} yeni bildirim"])),
    ],
    10: [  # Uygulama geri çağırma / oyun
        ("geri_cagir", lambda: R(OYUNLAR + MAGAZALAR[:3]), lambda: R(["Seni özledik!", "Geri dön 💙", "Neredesin?"]),
         lambda: R(["Uzun zamandır görüşemedik, seni bekliyoruz!", "Hesabında seni bekleyen sürprizler var.",
                    "Bir süredir uğramadın, kaçırdıkların var."])),
        ("oyun_enerji", lambda: R(OYUNLAR), lambda: R(["Enerjin doldu!", "Ödülün hazır", "Yeni etkinlik"]),
         lambda: R(["Canların yenilendi, oyuna devam et!", "Günlük ödülün seni bekliyor 🎁", "Sandığın açılmaya hazır!",
                    "Yeni sezon başladı, hemen katıl!", "Klanın saldırı altında!"])),
        ("seri", lambda: R(["Duolingo", "Snapchat", "Headspace", "Strava"]), lambda: R(["Serin tehlikede!", "Hatırlatma"]),
         lambda: R([f"{random.randint(3, 200)} günlük serini kaybetme!", "Bugünkü dersini henüz yapmadın 🦉",
                    "Arkadaşların seni geçti!"])),
    ],
    11: [  # Sistem ve diğer
        ("guncelleme", lambda: R(SISTEM), lambda: R(["Güncelleme", "Yazılım güncellemesi", ""]),
         lambda: R([f"{random.randint(2, 15)} uygulama güncellendi.", "Yazılım güncellemesi indirildi, yüklemek için dokunun.",
                    "Sistem güncellemesi mevcut."])),
        ("pil_depo", lambda: R(SISTEM), lambda: R(["Pil", "Depolama", "Cihaz bakımı"]),
         lambda: R([f"Pil seviyesi %{random.randint(5, 20)}. Güç tasarrufu modunu açın.", "Depolama alanı azalıyor.",
                    "Şarj tamamlandı.", "Cihaz optimize edildi."])),
        ("yedek_bilgi", lambda: R(SISTEM + ["Google Fotoğraflar", "Google Drive"]), lambda: R(["Yedekleme", "Bilgi", ""]),
         lambda: R(["Fotoğraflarınız yedeklendi.", "Ekran görüntüsü kaydedildi.", "Wi-Fi ağına bağlanıldı.",
                    "Bir yıl önce bugün: anılarınıza göz atın", "Hava durumu: yarın yağmurlu, 12°C"])),
    ],
}


def uret(n_per_class: int, seed: int = 26):
    random.seed(seed)
    rows = []
    for label, families in AILELER.items():
        for i in range(n_per_class):
            fam, app_f, title_f, text_f = families[i % len(families)]
            rows.append({"app": app_f(), "title": title_f(), "text": text_f(), "label": label, "family": f"{label}_{fam}"})
    # tekrarları kaldır
    seen, out = set(), []
    for r in rows:
        k = (r["app"], r["title"], r["text"])
        if k not in seen:
            seen.add(k)
            out.append(r)
    random.shuffle(out)
    return out


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=600)
    ap.add_argument("--out", default="data/sentetik_v2.csv")
    a = ap.parse_args()
    rows = uret(a.n)
    import os
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    with open(a.out, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=["app", "title", "text", "label", "family"])
        w.writeheader()
        w.writerows(rows)
    print(f"{len(rows)} örnek yazıldı -> {a.out}")

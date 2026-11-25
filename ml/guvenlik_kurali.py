"""Kural katmanı: güvenlik / para hareketi (tür 1) — modelden önce çalışır.
Ön işlenmiş (egit.on_isle) metin üzerinde uygulanır. Kotlin'e birebir taşınacak."""
import re

SEC = re.compile(
    r"((dogrulama|onay|guvenlik|tek kullanimlik|giris)\s*(kod|sifre)"
    r"|(kod|sifre)(u|si|niz|nuz|unuz|iniz)?\s*[:=]?\s*(<num>|\d{4,8})(?!\d)"
    r"|<num>\s*(dogrulama|onay)"
    r"|hesab\w*\s*(dan|den|a|e)?\s*.{0,25}(cekil|yatiril|gonderil|gonderdi|tahsil)"
    r"|tahsil edil|harcama yapildi|islem(i|iniz)? (yapildi|gerceklesmedi|engellendi)"
    r"|(yeni|farkli) (bir )?cihaz\w*.{0,15}(giris|oturum)|(giris|oturum) (yapildi|yapilmistir|acildi)"
    r"|sifre\w* (degistirildi|blokelen|sifirla)|guvenlik (bilgi|uyari)|numaraniz degistirildi"
    r"|limit\w* dol|ekstre borcu|asgari (odeme|borc)|toplam borcu"
    r"|<num> [^.|]{0,30}(sifre|kod)\w*dir|kimseyle paylasma)")  # 25 Kasım: internet sağlayıcısı şifre SMS'i "<num> ... sifrenizdir"
NEG = re.compile(r"(indirim|kampanya|firsat|hediye|kazan|taksit|bonus|ozel|promosyon|cekilis|kredi(nizi)? (al|teklif))")


def guvenlik_mi(on_islenmis: str) -> bool:
    return bool(SEC.search(on_islenmis)) and not NEG.search(on_islenmis)

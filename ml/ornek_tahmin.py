"""Süzgeç metin modelini (uygulamadaki JSON) tek bir bildirim üzerinde dener.

Kullanım:
    python ornek_tahmin.py "uygulama" "başlık" "metin"
    python ornek_tahmin.py "Mesajlar" "" "Çevrimsiz %100 deneme bonusu! Hemen yatır, kazan: bit.ly/xx"

Model: ../app/src/main/assets/model_tfidf_lr.json (başka dosya için --model).
Ön işleme ve hesap, egit.py (on_isle, json_proba) ve Kotlin'deki Preprocess / TextModel ile birebir aynıdır:
Türkçe küçük harf → <url>/<tel>/<num> maskeleri → ASCII katlama → char_wb 2–5 n-gram → 1 + ln(tf) × idf
→ L2 normalize → coef·x + b → softmax. Yalnız standart kütüphane; numpy varsa onu kullanır.
"""
import argparse
import json
import math
import re
import sys
from pathlib import Path

try:
    import numpy as np
except ImportError:  # numpy yoksa saf Python
    np = None

PREPROCESS_VERSION = 2
FOLD = str.maketrans("şığüöç", "siguoc")
URL_RE = re.compile(r"(https?://\S+|www\.\S+|\b[a-z0-9-]+\.(?:ly|com|link|su|me|net|org|tr|io|co)(?:/\S*)?)")
TEL_RE = re.compile(r"(\+?90[\s-]?)?0?5\d{2}[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}")
NUM_RE = re.compile(r"\d[\d.,]{3,}")
WS_RE = re.compile(r"\s+")
TUR_ADI = {1: "güvenlik / para hareketi", 2: "kişisel mesaj", 3: "grup mesajı", 4: "okul / iş / resmi kurum",
           5: "zaman hassas hatırlatma", 6: "işlem bilgilendirmesi", 7: "kampanya / reklam", 8: "kumar / bahis",
           9: "sosyal etkileşim", 10: "geri çağırma / oyun", 11: "sistem ve diğer"}


def tr_lower(s):
    return s.replace("İ", "i").replace("I", "ı").lower()


def on_isle(app, title, text):
    s = f"{app or ''} | {title or ''} | {text or ''}"
    s = tr_lower(s)
    s = URL_RE.sub(" <url> ", s)
    s = TEL_RE.sub(" <tel> ", s)
    s = NUM_RE.sub(" <num> ", s)
    s = s.translate(FOLD)
    return WS_RE.sub(" ", s).strip()


def char_wb_ngrams(text, nmin, nmax):
    """scikit-learn _char_wb_ngrams ile aynı."""
    out = []
    for w in text.split():
        w = " " + w + " "
        wl = len(w)
        for n in range(nmin, nmax + 1):
            off = 0
            out.append(w[off:off + n])
            while off + n < wl:
                off += 1
                out.append(w[off:off + n])
            if off == 0:
                break
    return out


def yukle(path):
    m = json.loads(Path(path).read_text(encoding="utf-8"))
    if m["preprocess_version"] != PREPROCESS_VERSION:
        raise SystemExit(f"Model ön işleme sürümü {m['preprocess_version']}, betik {PREPROCESS_VERSION}")
    return m


def olasilik(m, pre):
    tf = {}
    for g in char_wb_ngrams(pre, m["ngram_min"], m["ngram_max"]):
        j = m["vocab"].get(g)
        if j is not None:
            tf[j] = tf.get(j, 0) + 1
    x = {j: (1.0 + math.log(c)) * m["idf"][j] for j, c in tf.items()}
    nrm = math.sqrt(sum(v * v for v in x.values())) or 1.0
    if np is not None:
        idx = np.fromiter(x.keys(), dtype=np.int64, count=len(x))
        val = np.fromiter(x.values(), dtype=np.float64, count=len(x)) / nrm
        z = np.array([float(np.dot(np.asarray(row)[idx], val)) if len(x) else 0.0 for row in m["coef"]]) + np.asarray(m["intercept"])
        e = np.exp(z - z.max())
        return [float(v) for v in e / e.sum()]
    z = [sum(row[j] * v / nrm for j, v in x.items()) + b for row, b in zip(m["coef"], m["intercept"])]
    mx = max(z)
    e = [math.exp(v - mx) for v in z]
    s = sum(e)
    return [v / s for v in e]


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser(description="Süzgeç metin modeliyle tek bildirim tahmini")
    ap.add_argument("uygulama")
    ap.add_argument("baslik")
    ap.add_argument("metin")
    ap.add_argument("--model", default=str(Path(__file__).resolve().parent.parent / "app/src/main/assets/model_tfidf_lr.json"))
    a = ap.parse_args()
    m = yukle(a.model)
    pre = on_isle(a.uygulama, a.baslik, a.metin)
    p = olasilik(m, pre)
    print(f"Ön işlenmiş: {pre}")
    for k in sorted(range(len(p)), key=lambda i: -p[i])[:3]:
        t = m["classes"][k]
        print(f"  {t:>2}  {TUR_ADI.get(t, t):<26} {p[k]:.4f}")


if __name__ == "__main__":
    main()

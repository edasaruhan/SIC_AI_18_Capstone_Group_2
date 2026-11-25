"""Süzgeç — G1 metin modeli: TF-IDF (char_wb 2–5) + lojistik regresyon.

1) Ön işleme (Kotlin tarafında birebir aynısı uygulanmalı, bkz. on_isle)
2) Değerlendirme: (a) rastgele tabakalı 5 katlı ÇD, (b) şablon ailesi dışarıda bırakmalı
   GroupKFold (modelin hiç görmediği kalıplara genelleşmesini ölçer)
3) Tüm veriyle son eğitim → model_tfidf_lr.json + model_parity_cases.json

Kullanım: python egit.py --data data/sentetik_v1.csv --out out/
"""
import argparse
import json
import os
import re
import time

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, f1_score
from sklearn.model_selection import GroupKFold, StratifiedKFold
from sklearn.pipeline import make_pipeline

PREPROCESS_VERSION = 2
# v2: Türkçe karakterler ASCII'ye katlanır (ş→s, ı→i, ğ→g, ü→u, ö→o, ç→c); eski SMS'ler ASCII yazılmış
FOLD = str.maketrans("şığüöç", "siguoc")
TURLER = {1: "guvenlik_para", 2: "kisisel_mesaj", 3: "grup_mesaji", 4: "okul_is_resmi", 5: "zaman_hassas",
          6: "islem_bilgi", 7: "kampanya", 8: "kumar", 9: "sosyal_etkilesim", 10: "geri_cagirma", 11: "sistem_diger"}

URL_RE = re.compile(r"(https?://\S+|www\.\S+|\b[a-z0-9-]+\.(?:ly|com|link|su|me|net|org|tr|io|co)(?:/\S*)?)")
TEL_RE = re.compile(r"(\+?90[\s-]?)?0?5\d{2}[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}")
NUM_RE = re.compile(r"\d[\d.,]{3,}")
WS_RE = re.compile(r"\s+")


def tr_lower(s: str) -> str:
    return s.replace("İ", "i").replace("I", "ı").lower()


def on_isle(app: str, title: str, text: str) -> str:
    s = f"{app or ''} | {title or ''} | {text or ''}"
    s = tr_lower(s)
    s = URL_RE.sub(" <url> ", s)
    s = TEL_RE.sub(" <tel> ", s)
    s = NUM_RE.sub(" <num> ", s)
    s = s.translate(FOLD)
    return WS_RE.sub(" ", s).strip()


def model_kur():
    return make_pipeline(
        TfidfVectorizer(analyzer="char_wb", ngram_range=(2, 5), sublinear_tf=True, lowercase=False,
                        min_df=2, max_features=30000, dtype=np.float64),
        LogisticRegression(C=4.0, max_iter=3000, class_weight="balanced"),
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="data/sentetik_v1.csv")
    ap.add_argument("--out", default="out")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    d = pd.read_csv(a.data, keep_default_na=False)
    X = [on_isle(r.app, r.title, r.text) for r in d.itertuples()]
    y = d.label.values
    labels = sorted(TURLER)
    names = [TURLER[k] for k in labels]
    rapor = {}

    # (a) rastgele tabakalı ÇD
    skf = StratifiedKFold(5, shuffle=True, random_state=26)
    pred = np.zeros_like(y)
    for tr, te in skf.split(X, y):
        m = model_kur().fit([X[i] for i in tr], y[tr])
        pred[te] = m.predict([X[i] for i in te])
    rapor["rastgele_5kat_makroF1"] = round(f1_score(y, pred, average="macro"), 4)

    # (b) şablon ailesi dışarıda (görülmemiş kalıp)
    gkf = GroupKFold(5)
    pred_g = np.zeros_like(y)
    for tr, te in gkf.split(X, y, d.family.values):
        m = model_kur().fit([X[i] for i in tr], y[tr])
        pred_g[te] = m.predict([X[i] for i in te])
    rapor["aile_disarida_makroF1"] = round(f1_score(y, pred_g, average="macro"), 4)
    rapor["aile_disarida_sinif_raporu"] = classification_report(
        y, pred_g, labels=labels, target_names=names, output_dict=True, zero_division=0)
    rapor["aile_disarida_karisiklik"] = confusion_matrix(y, pred_g, labels=labels).tolist()
    print(classification_report(y, pred_g, labels=labels, target_names=names, zero_division=0))

    # son model
    m = model_kur().fit(X, y)
    vec, lr = m.steps[0][1], m.steps[1][1]
    t0 = time.perf_counter()
    m.predict_proba(X[:500])
    rapor["python_ms_per_ornek"] = round((time.perf_counter() - t0) / 500 * 1000, 3)

    model_json = {
        "preprocess_version": PREPROCESS_VERSION,
        "analyzer": "char_wb", "ngram_min": 2, "ngram_max": 5, "sublinear_tf": True, "norm": "l2",
        "classes": [int(c) for c in lr.classes_],
        "class_names": [TURLER[int(c)] for c in lr.classes_],
        "vocab": {k: int(v) for k, v in vec.vocabulary_.items()},
        "idf": [round(float(v), 6) for v in vec.idf_],
        "coef": [[round(float(v), 6) for v in row] for row in lr.coef_],
        "intercept": [round(float(v), 6) for v in lr.intercept_],
    }
    p = os.path.join(a.out, "model_tfidf_lr.json")
    with open(p, "w", encoding="utf-8") as f:
        json.dump(model_json, f, ensure_ascii=False, separators=(",", ":"))
    rapor["model_json_mb"] = round(os.path.getsize(p) / 1e6, 2)
    rapor["vocab_boyutu"] = len(vec.vocabulary_)

    # eşlik testi örnekleri (yuvarlanmış ağırlıklarla yeniden hesap — Kotlin ile aynı)
    rng = np.random.default_rng(26)
    idx = rng.choice(len(d), 50, replace=False)
    cases = []
    for i in idx:
        r = d.iloc[i]
        cases.append({"app": r.app, "title": r.title, "text": r.text,
                      "preprocessed": X[i], "proba": json_proba(model_json, X[i])})
    with open(os.path.join(a.out, "model_parity_cases.json"), "w", encoding="utf-8") as f:
        json.dump(cases, f, ensure_ascii=False, indent=1)

    with open(os.path.join(a.out, "rapor.json"), "w", encoding="utf-8") as f:
        json.dump(rapor, f, ensure_ascii=False, indent=1)
    print({k: v for k, v in rapor.items() if "rapor" not in k and "karisiklik" not in k})


def char_wb_ngrams(text, nmin, nmax):
    """scikit-learn _char_wb_ngrams ile aynı mantık."""
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


def json_proba(mj, pre):
    """JSON ağırlıklarından olasılık — Kotlin uygulamasının referansı."""
    tf = {}
    for g in char_wb_ngrams(pre, mj["ngram_min"], mj["ngram_max"]):
        j = mj["vocab"].get(g)
        if j is not None:
            tf[j] = tf.get(j, 0) + 1
    x = {j: (1 + np.log(c)) * mj["idf"][j] for j, c in tf.items()}
    nrm = np.sqrt(sum(v * v for v in x.values())) or 1.0
    z = [sum(row[j] * v / nrm for j, v in x.items()) + b for row, b in zip(mj["coef"], mj["intercept"])]
    z = np.array(z)
    e = np.exp(z - z.max())
    return [round(float(v), 8) for v in e / e.sum()]


if __name__ == "__main__":
    main()

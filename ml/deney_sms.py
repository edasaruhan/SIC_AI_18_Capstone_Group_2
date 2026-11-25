"""Deney: sentetik model vs. sentetik+gerçek SMS modeli. Test: Uysal 2013 (eğitimde hiç yok) + elle yazılmış set."""
import json
import pandas as pd
from sklearn.metrics import f1_score, recall_score, precision_score, confusion_matrix
import sys
from egit import on_isle, model_kur  # v2 ön işleme (ASCII katlama dahil)

import os
SYN = os.environ.get("SYN", "data/sentetik_v2.csv")
syn_only = pd.read_csv(SYN, keep_default_na=False)
elle = pd.read_csv("data/elle_yazilmis_v1.csv", keep_default_na=False)
sms = pd.read_csv("data/sms_zayif_etiket.csv", keep_default_na=False)
sms_tr, sms_te = sms[sms.src == "TurkishSMSCollection"], sms[sms.src == "Uysal2013"]

def X(df, app=None):
    return [on_isle(app or r.app, "" if app else r.title, r.text) for r in df.itertuples()]

def olc(m, df, app=None):
    p = m.predict(X(df, app)); y = df.label.values
    return {"n": len(y), "makroF1": round(f1_score(y, p, average="macro"), 3),
            "kumar_duyarlilik": round(recall_score(y, p, labels=[8], average="macro", zero_division=0), 3) if (y == 8).any() else None,
            "kampanya_duyarlilik": round(recall_score(y, p, labels=[7], average="macro", zero_division=0), 3),
            "guvenlik_duyarlilik": round(recall_score(y, p, labels=[1], average="macro", zero_division=0), 3) if (y == 1).any() else None,
            "kampanya->kumar_hata": int(((y == 7) & (p == 8)).sum()),
            "kisisel->silinen(7/8)": int(((y == 2) & ((p == 7) | (p == 8))).sum())}

sonuc = {}
mA = model_kur().fit(X(syn_only), syn_only.label)
sonuc["A_sentetik"] = {"Uysal_SMS": olc(mA, sms_te, "Mesajlar"), "TurkishSMS": olc(mA, sms_tr, "Mesajlar"), "elle": olc(mA, elle)}
trB = pd.concat([syn_only[["app", "title", "text", "label"]],
                 sms_tr.assign(app="Mesajlar", title="")[["app", "title", "text", "label"]]])
mB = model_kur().fit(X(trB), trB.label)
sonuc["B_sentetik+TurkishSMS"] = {"Uysal_SMS": olc(mB, sms_te, "Mesajlar"), "elle": olc(mB, elle)}
print(json.dumps(sonuc, ensure_ascii=False, indent=1))
json.dump(sonuc, open("out/deney_sms_" + os.path.basename(SYN).replace(".csv", "") + ".json", "w"), ensure_ascii=False, indent=1)

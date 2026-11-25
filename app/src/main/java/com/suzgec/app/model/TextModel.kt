package com.suzgec.app.model

import com.suzgec.app.rules.Preprocess
import org.json.JSONObject
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Modelin bir bildirim için tahmini. */
data class Prediction(
    /** İçerik türü numaraları (1–11), [proba] ile aynı sırada. */
    val classes: IntArray,
    val proba: DoubleArray,
) {
    val bestIndex: Int get() = proba.indices.maxBy { proba[it] }
    val bestType: Int get() = classes[bestIndex]
    val bestProba: Double get() = proba[bestIndex]
    fun probaOf(type: Int): Double = proba[classes.indexOf(type)]
}

/**
 * G1 metin modeli: TF-IDF (char_wb 2–5, sublinear tf, L2) + çok sınıflı lojistik regresyon.
 * Ağırlıklar ml/egit.py'nin ürettiği model_tfidf_lr.json'dan okunur; hesap, egit.py'deki
 * `json_proba` ile birebir aynıdır (eşlik testi: TextModelParityTest).
 */
class TextModel private constructor(
    private val vocab: HashMap<String, Int>,
    private val idf: DoubleArray,
    /** coef[sınıf][özellik] */
    private val coef: Array<DoubleArray>,
    private val intercept: DoubleArray,
    val classes: IntArray,
    val classNames: List<String>,
    private val ngramMin: Int,
    private val ngramMax: Int,
) {
    /** Ham bildirim alanlarından tahmin. */
    fun predict(app: String?, title: String?, text: String?): Prediction =
        predictPreprocessed(Preprocess.onIsle(app, title, text))

    /** Ön işlenmiş metinden tahmin. */
    fun predictPreprocessed(pre: String): Prediction {
        // tf sayımı
        val tf = HashMap<Int, Int>()
        forEachCharWbNgram(pre) { g ->
            vocab[g]?.let { j -> tf[j] = (tf[j] ?: 0) + 1 }
        }
        // sublinear tf × idf, L2 normu
        val idx = IntArray(tf.size)
        val v = DoubleArray(tf.size)
        var k = 0
        var sq = 0.0
        for ((j, c) in tf) {
            val x = (1.0 + ln(c.toDouble())) * idf[j]
            idx[k] = j; v[k] = x; sq += x * x; k++
        }
        val norm = sqrt(sq).let { if (it == 0.0) 1.0 else it }
        // doğrusal skor + softmax
        val z = DoubleArray(classes.size) { c ->
            val row = coef[c]
            var s = 0.0
            for (i in idx.indices) s += row[idx[i]] * v[i] / norm
            s + intercept[c]
        }
        val m = z.max()
        val e = DoubleArray(z.size) { exp(z[it] - m) }
        val sum = e.sum()
        return Prediction(classes, DoubleArray(e.size) { e[it] / sum })
    }

    /**
     * scikit-learn `_char_wb_ngrams`: her kelime " " + w + " " ile sarılır, 2..5 uzunluğunda
     * kaydırılır; kelime n'den kısaysa o kelime için daha uzun n'ler denenmez.
     * Python dizeleri kod noktasıyla saydığı için emoji gibi karakterler de kod noktası olarak işlenir.
     */
    private inline fun forEachCharWbNgram(text: String, emit: (String) -> Unit) {
        for (word in text.split(' ', '\t', '\n', '\r', '\u000B', '\u000C')) {
            if (word.isEmpty()) continue
            val w = " $word "
            val cps = w.codePoints().toArray()
            val wl = cps.size
            for (n in ngramMin..ngramMax) {
                var off = 0
                emit(String(cps, off, minOf(n, wl - off)))
                while (off + n < wl) {
                    off++
                    emit(String(cps, off, n))
                }
                if (off == 0) break
            }
        }
    }

    companion object {
        fun parse(json: String): TextModel {
            val o = JSONObject(json)
            require(o.getInt("preprocess_version") == Preprocess.VERSION) {
                "Model ön işleme sürümü ${o.getInt("preprocess_version")}, uygulama ${Preprocess.VERSION}"
            }
            val vj = o.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vj.length() * 2)
            for (key in vj.keys()) vocab[key] = vj.getInt(key)
            val idfJ = o.getJSONArray("idf")
            val idf = DoubleArray(idfJ.length()) { idfJ.getDouble(it) }
            val cj = o.getJSONArray("coef")
            val coef = Array(cj.length()) { c ->
                val row = cj.getJSONArray(c)
                DoubleArray(row.length()) { row.getDouble(it) }
            }
            val ij = o.getJSONArray("intercept")
            val clj = o.getJSONArray("classes")
            val nj = o.getJSONArray("class_names")
            return TextModel(
                vocab = vocab,
                idf = idf,
                coef = coef,
                intercept = DoubleArray(ij.length()) { ij.getDouble(it) },
                classes = IntArray(clj.length()) { clj.getInt(it) },
                classNames = List(nj.length()) { nj.getString(it) },
                ngramMin = o.getInt("ngram_min"),
                ngramMax = o.getInt("ngram_max"),
            )
        }
    }
}

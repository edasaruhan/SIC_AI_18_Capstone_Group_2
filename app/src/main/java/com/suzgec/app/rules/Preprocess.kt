package com.suzgec.app.rules

import java.util.Locale
import java.util.regex.PatternSyntaxException

/**
 * Python 3 `re` gibi Unicode'a duyarlı regex. JVM'de (birim testler) bunun için `(?U)` gerekir;
 * Android'in ICU motoru `(?U)`'yu tanımaz ama \w, \d, \b'yi zaten Unicode'a göre yorumlar.
 */
internal fun uRegex(pattern: String): Regex = try {
    Regex("(?U)$pattern")
} catch (e: PatternSyntaxException) {
    Regex(pattern)
}

/**
 * Ön işleme v2 — ml/egit.py `on_isle` ile birebir aynı olmalı.
 * (?U): Python 3'teki gibi \w, \d, \s, \b Unicode'a duyarlı.
 */
object Preprocess {
    const val VERSION = 2

    private val URL_RE = uRegex("""(https?://\S+|www\.\S+|\b[a-z0-9-]+\.(?:ly|com|link|su|me|net|org|tr|io|co)(?:/\S*)?)""")
    private val TEL_RE = uRegex("""(\+?90[\s-]?)?0?5\d{2}[\s-]?\d{3}[\s-]?\d{2}[\s-]?\d{2}""")
    private val NUM_RE = uRegex("""\d[\d.,]{3,}""")
    private val WS_RE = uRegex("""\s+""")

    fun trLower(s: String): String = s.replace("İ", "i").replace("I", "ı").lowercase(Locale.ROOT)

    /** ş→s, ı→i, ğ→g, ü→u, ö→o, ç→c */
    fun fold(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(
            when (c) {
                'ş' -> 's'; 'ı' -> 'i'; 'ğ' -> 'g'; 'ü' -> 'u'; 'ö' -> 'o'; 'ç' -> 'c'
                else -> c
            }
        )
        return sb.toString()
    }

    /** Modelin ve güvenlik kuralının girdisi: `uygulama | başlık | metin`. */
    fun onIsle(app: String?, title: String?, text: String?): String {
        var s = "${app ?: ""} | ${title ?: ""} | ${text ?: ""}"
        s = trLower(s)
        s = URL_RE.replace(s, " <url> ")
        s = TEL_RE.replace(s, " <tel> ")
        s = NUM_RE.replace(s, " <num> ")
        s = fold(s)
        return WS_RE.replace(s, " ").trim()
    }

    /**
     * Kumar sözlüğü için normalizasyon: küçük harf + ASCII katlama + basit "leet"
     * çözme (b0nus → bonus, ca$ino → casino). Bağlantılar korunur (kısa link kuralı için).
     * Bir rakam/simge yalnız solunda harf varsa ve sağında harf ya da kelime sonu varsa çevrilir;
     * böylece "bets10", "%100", "m51" bozulmaz.
     */
    fun forGambling(app: String?, title: String?, text: String?): String {
        val s = fold(trLower("${app ?: ""} | ${title ?: ""} | ${text ?: ""}"))
        val out = StringBuilder(s.length)
        for (i in s.indices) {
            val c = s[i]
            val mapped = LEET[c]
            if (mapped != null) {
                val prevLetter = i > 0 && s[i - 1].isLetter()
                val next = s.getOrNull(i + 1)
                val nextOk = next == null || next.isLetter() || !next.isLetterOrDigit()
                if (prevLetter && nextOk) { out.append(mapped); continue }
            }
            out.append(c)
        }
        return WS_RE.replace(out, " ").trim()
    }

    private val LEET = mapOf('0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't', '@' to 'a', '$' to 's')
}

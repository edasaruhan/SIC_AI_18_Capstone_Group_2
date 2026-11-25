package com.suzgec.app.rules

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Kotlin ön işlemesi, Python'un (ml/egit.py on_isle) ürettiği metinle birebir aynı olmalı. */
class PreprocessParityTest {

    @Test
    fun ellıOrnekPythonIleAyni() {
        val cases = JSONArray(File("src/main/assets/model_parity_cases.json").readText())
        var checked = 0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val got = Preprocess.onIsle(c.getString("app"), c.getString("title"), c.getString("text"))
            assertEquals("örnek $i: ${c.getString("text")}", c.getString("preprocessed"), got)
            checked++
        }
        assertEquals(50, checked)
    }

    @Test
    fun turkceKucukHarfVeKatlama() {
        assertEquals("istanbul | isik | <num> tl", Preprocess.onIsle("İstanbul", "IŞIK", "1.250 TL"))
        assertEquals("a | | <tel> <url>", Preprocess.onIsle("A", "", "0532 123 45 67 https://x.co/a"))
    }

    @Test
    fun kumarNormalizasyonuLeetCozer() {
        assertEquals("| | bonus casino bets10 %100 m51", Preprocess.forGambling("", "", "B0NUS CA\$INO Bets10 %100 M51"))
    }
}

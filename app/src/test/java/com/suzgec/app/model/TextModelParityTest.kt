package com.suzgec.app.model

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Eşlik testi (CLAUDE.md §6): Kotlin olasılıkları, Python'un model_parity_cases.json'a yazdığı
 * olasılıklardan her sınıfta 1e-4'ten az farklı olmalı.
 */
class TextModelParityTest {

    companion object {
        lateinit var model: TextModel
        lateinit var cases: JSONArray

        @BeforeClass @JvmStatic
        fun load() {
            model = TextModel.parse(File("src/main/assets/model_tfidf_lr.json").readText())
            cases = JSONArray(File("src/main/assets/model_parity_cases.json").readText())
        }
    }

    @Test
    fun elliOrnekteSinifBasinaFarkBinde1denAz() {
        var maxDiff = 0.0
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            // Uçtan uca: ham alanlar → Kotlin ön işleme → model
            val p = model.predict(c.getString("app"), c.getString("title"), c.getString("text"))
            val exp = c.getJSONArray("proba")
            assertEquals(exp.length(), p.proba.size)
            for (k in 0 until exp.length()) {
                val d = abs(exp.getDouble(k) - p.proba[k])
                maxDiff = maxOf(maxDiff, d)
                assertTrue("örnek $i sınıf ${k + 1}: fark $d (${c.getString("text")})", d < 1e-4)
            }
        }
        println("Eşlik testi: 50 örnek, en büyük fark = $maxDiff")
    }

    @Test
    fun bildirimBasinaHiz() {
        val texts = (0 until cases.length()).map { cases.getJSONObject(it).getString("preprocessed") }
        repeat(3) { texts.forEach { model.predictPreprocessed(it) } } // ısınma
        val t0 = System.nanoTime()
        repeat(10) { texts.forEach { model.predictPreprocessed(it) } }
        val ms = (System.nanoTime() - t0) / 1e6 / (10 * texts.size)
        println("Model: bildirim başına %.3f ms".format(ms))
        assertTrue("bildirim başına $ms ms", ms < 50)
    }

    @Test
    fun siniflarVeAdlar() {
        assertEquals((1..11).toList(), model.classes.toList())
        assertEquals("kampanya", model.classNames[6])
    }
}

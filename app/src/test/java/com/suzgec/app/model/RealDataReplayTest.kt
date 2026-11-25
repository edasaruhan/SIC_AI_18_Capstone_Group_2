package com.suzgec.app.model

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.suzgec.app.countCorrections
import com.suzgec.app.data.Correction
import com.suzgec.app.rules.Category
import com.suzgec.app.rules.NotificationFacts
import com.suzgec.app.rules.RuleEngine
import com.suzgec.app.rules.RuleSettings
import com.suzgec.app.rules.Rules
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M51 gerçek kullanım yedeğini (docs/veri/, .gitignore'da) yeni kurallarla çevrimdışı yeniden sınıflandırır
 * ve "önce → sonra" tablosunu aynı klasöre yeniden_siniflama.md olarak yazar. Yedek yoksa atlanır.
 * Demo gönderici ve dokunulmaz kayıtlar hariç (dokunulmaz kuralı değişmedi).
 *
 * Çalıştırma: gradlew :app:testDebugUnitTest --tests "*RealDataReplayTest" -Dsuzgec.veri=<klasör adı>
 */
class RealDataReplayTest {

    private val dir = File("../docs/veri").listFiles()
        ?.filter { File(it, "suzgec.db").exists() && File(it, "ayarlar.preferences_pb").exists() }
        ?.maxByOrNull { it.name }

    private fun rows(c: SQLiteConnection, sql: String): List<Map<String, String?>> = c.prepare(sql).use { st ->
        buildList {
            while (st.step()) add((0 until st.getColumnCount()).associate { st.getColumnName(it) to if (st.isNull(it)) null else st.getText(it) })
        }
    }

    /** DataStore dosyasından "oncelikli_uygulamalar" ve filtreler (protobuf, basit okuma). */
    private fun settingsFromBackup(d: File): RuleSettings {
        val raw = String(File(d, "ayarlar.preferences_pb").readBytes(), Charsets.ISO_8859_1)
        val apps = Regex("""([a-z0-9_.]+)=(KRITIK|ONEMLI|PANELDE)""").findAll(raw).associate { it.groupValues[1] to it.groupValues[2] }
        fun flag(k: String, def: Boolean): Boolean {
            val i = raw.indexOf(k); if (i < 0) return def
            return raw[i + k.length + 3].code == 1
        }
        return RuleSettings(
            gamblingFilter = flag("kumar_filtresi", true),
            shoppingFilter = flag("alisveris_filtresi", false),
            socialFilter = flag("sosyal_filtresi", false),
            priorityApps = apps.filterValues { it != "PANELDE" }.mapValues { Category.valueOf(it.value) },
            panelApps = apps.filterValues { it == "PANELDE" }.keys,
            userName = "Süleyman",
        )
    }

    @Test
    fun yenidenSiniflandir() {
        assumeTrue("docs/veri yedeği yok", dir != null)
        val d = dir!!
        val engine = RuleEngine(
            Rules.parse(File("src/main/assets/rules.json").readText()),
            TextModel.parse(File("src/main/assets/model_tfidf_lr.json").readText()),
        )
        val conn = BundledSQLiteDriver().open(File(d, "suzgec.db").path)
        val corr = rows(conn, "SELECT * FROM corrections WHERE packageName != 'com.suzgec.sender'").map {
            Correction(
                time = it["time"]!!.toLong(), senderHash = it["senderHash"] ?: "", packageName = it["packageName"]!!,
                appLabel = it["appLabel"]!!, contentType = it["contentType"]?.toInt(),
                fromCategory = it["fromCategory"]!!, toCategory = it["toCategory"]!!,
            )
        }
        val now = settingsFromBackup(d).copy(personal = countCorrections(corr))
        // Kullanıcının bugün yapacağı ayar: bu uygulamalar "Panelde bırak".
        val planned = setOf(
            "com.google.android.inputmethod.latin", "com.sec.android.app.camera", "com.sec.android.app.popupcalculator",
            "com.sec.android.app.myfiles", "com.google.android.apps.bard", "com.sec.android.app.shealth",
        )
        val after = now.copy(priorityApps = now.priorityApps - planned, panelApps = now.panelApps + planned)

        val recs = rows(
            conn,
            "SELECT * FROM notifications WHERE packageName != 'com.suzgec.sender' AND `key` NOT LIKE 'ornek|%' " +
                "AND suzgecCategory != 'DOKUNMA' ORDER BY postTime",
        )
        conn.close()
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale.ROOT)
        val out = StringBuilder()
        out.appendLine("# Yeni kurallarla yeniden sınıflama · ${d.name}\n")
        out.appendLine("Demo ve dokunulmaz kayıtlar hariç ${recs.size} kayıt. Ayarlar yedekten (öncelikli uygulamalar, filtreler, düzeltmeler); " +
            "\"Panelde bırak\" planı: Gboard, Kamera, Hesap makinesi, Dosyalarım, Gemini, Samsung Health. " +
            "Telegram kayıtlarında yanıt eylemi/kısayol saklanmadığı için kanal olduğu varsayıldı (†).\n")
        out.appendLine("| # | Zaman | Uygulama | Başlık | Önce | Sonra | Sonraki neden |")
        out.appendLine("|---|---|---|---|---|---|---|")
        var changed = 0
        for (r in recs) {
            val pkg = r["packageName"]!!
            val telegram = pkg.startsWith("org.telegram")
            val f = NotificationFacts(
                pkg, r["appLabel"]!!, r["title"], r["text"], r["bigText"], r["category"],
                r["isOngoing"] == "1", (r["flags"]!!.toInt() and 0x40) != 0, r["isGroupSummary"] == "1", false, 0,
                r["isGroupConversation"]?.let { it == "1" }, null,
                channelId = r["channelId"], hasReplyAction = if (telegram) false else null,
            )
            val dec = engine.classify(f, after)
            val before = Category.valueOf(r["suzgecCategory"]!!)
            val mark = if (dec.category != before) "**" else ""
            if (dec.category != before) changed++
            fun short(s: String?, n: Int) = (s ?: "").replace(Regex("\\s+"), " ").replace("|", "/").let { if (it.length > n) it.take(n - 1) + "…" else it }
            out.appendLine(
                "| ${r["id"]} | ${fmt.format(Date(r["postTime"]!!.toLong()))} | ${r["appLabel"]}${if (telegram) " †" else ""} | ${short(r["title"], 34)} | " +
                    "${before.label} [${r["rule"]}] | $mark${dec.category.label}$mark [${dec.rule}] | ${short(dec.reason, 90)} |",
            )
        }
        out.appendLine("\nDeğişen: $changed / ${recs.size}")
        File(d, "yeniden_siniflama.md").writeText(out.toString())
        println(out)
    }
}

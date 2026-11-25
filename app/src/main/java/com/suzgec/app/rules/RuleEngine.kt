package com.suzgec.app.rules

import com.suzgec.app.model.CategoryMapper
import com.suzgec.app.model.Counts
import com.suzgec.app.model.PersonalKey
import com.suzgec.app.model.Personalization
import com.suzgec.app.model.Prediction
import com.suzgec.app.model.TextModel
import org.json.JSONArray
import org.json.JSONObject

/** Süzgeç'in beş kategorisi + kural katmanının iki ara durumu. */
enum class Category(val label: String) {
    KRITIK("Kritik"),
    ONEMLI("Önemli"),
    NORMAL("Normal"),
    ONEMSIZ("Önemsiz"),
    SILINENLER("Silinenler"),

    /** Hiç dokunulmaz, kutuya da alınmaz (servis, arama, alarm, medya...). */
    DOKUNMA("Dokunulmaz"),

    /** Kural karar veremedi; metin modeli (25 Kasım) karar verecek. Şimdilik panelde kalır. */
    MODEL_BEKLIYOR("Model bekliyor");

    /** Bu kategorideki bildirim panelden silinir mi? */
    val removeFromPanel: Boolean get() = this == NORMAL || this == ONEMSIZ || this == SILINENLER
}

/** İçerik türleri (CLAUDE.md §6). */
object ContentType {
    const val GUVENLIK = 1
    const val KISISEL_MESAJ = 2
    const val GRUP_MESAJI = 3
    const val KAMPANYA = 7
    const val KUMAR = 8
    const val SOSYAL = 9
}

/** Kural katmanının bildirimden ihtiyaç duyduğu her şey (Android'den bağımsız, test edilebilir). */
data class NotificationFacts(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    /** Notification.category, ör. "call", "msg"; yoksa null. */
    val category: String?,
    val isOngoing: Boolean,
    val isForegroundService: Boolean,
    val isGroupSummary: Boolean,
    val hasMediaSession: Boolean,
    /** Notification.timeoutAfter (ms); 0 = yok. */
    val timeoutAfterMs: Long,
    /** MessagingStyle'dan; bilinmiyorsa null. */
    val isGroupConversation: Boolean?,
    /** Sohbet bildiriminde son mesajı gönderen (MessagingStyle kişisi); yoksa null. */
    val sender: String? = null,
    /** Gönderen rehberdeki bir kişi mi (Person.uri bir rehber kaydı). */
    val senderIsContact: Boolean = false,
    /** Notification.channelId (Telegram kanal işareti için). */
    val channelId: String? = null,
    /** Notification.shortcutId (Telegram: "ndid_<diyalog>"). */
    val shortcutId: String? = null,
    /** Bildirimde yanıt (RemoteInput) eylemi var mı; bilinmiyorsa null. */
    val hasReplyAction: Boolean? = null,
    /** ApplicationInfo.category (CATEGORY_NEWS = 5); bilinmiyorsa -1. */
    val appCategory: Int = -1,
) {
    /** Sınıflandırmada kullanılan gövde: uzun metin varsa o. */
    val body: String? get() = bigText?.takeIf { it.isNotBlank() } ?: text
}

/** Kullanıcı ayarları + kişisel düzeltme sayaçları (DataStore ve Room'dan, SuzgecApp.ruleSettings). */
data class RuleSettings(
    /** Kumar filtresi (varsayılan açık): sözlük ya da model "kumar" → Silinenler. */
    val gamblingFilter: Boolean = true,
    /** Alışveriş filtresi: yalnız kampanya/indirim (tür 7) Silinenler'e; sipariş/kargo (tür 6) kalır. */
    val shoppingFilter: Boolean = false,
    /** Sosyal medya filtresi: sosyal etkileşim (tür 9) Silinenler'e; mesajlar kalır. */
    val socialFilter: Boolean = false,
    /** Paket adı → KRITIK ya da ONEMLI. */
    val priorityApps: Map<String, Category> = emptyMap(),
    /** "Panelde bırak" seçilen uygulamalar: panelden silinmez, ses çalınmaz, yalnız kayda geçer. */
    val panelApps: Set<String> = emptySet(),
    /** Kullanıcının adı: grup mesajında geçerse Önemli (boşsa aranmaz). */
    val userName: String = "",
    /** Kişiselleştirme v1 sayaçları (§7). */
    val personal: Map<PersonalKey, Counts> = emptyMap(),
) {
    fun personalDelta(key: PersonalKey): Double = personal[key]?.delta ?: 0.0
}

sealed interface PreFilter {
    data class Untouched(val reason: String) : PreFilter
    /** Kategori kararından önce [delayMs] bekle. */
    data class Wait(val delayMs: Long, val reason: String) : PreFilter
    data object Proceed : PreFilter
}

data class Decision(
    val category: Category,
    val contentType: Int?,
    /** "Neden burada?" satırı. */
    val reason: String,
    /** Hangi kural karar verdi (kısa kod, kayıt ve test için). */
    val rule: String,
    /** Modelin beklenen önem puanı (1–4); kural kararlarında null. */
    val score: Double? = null,
)

/**
 * Gönderen bir kurum mu? SMS'lerde kurumlar harf ağırlıklı gönderen kimliği kullanır
 * (AKBANK, TURKCELL, B001). Küçük harf içeren adlar (Annem, Ayşe Yılmaz) ve telefon
 * numaraları kişi sayılır. Rehberdeki kayıtlar her zaman kişidir.
 */
object SenderHeuristics {
    private val PHONE = Regex("""^[+\d\s().-]{7,}$""")

    /** Samsung Mesajlar adları yön işaretleriyle sarar ("\u2068BELEDIYE\u2069"). */
    fun clean(name: String?): String = name.orEmpty().filterNot {
        it in '\u2066'..'\u2069' || it == '\u200e' || it == '\u200f' || it == '\u202a' || it == '\u202c'
    }.trim()

    fun isInstitution(name: String?, isContact: Boolean): Boolean {
        if (isContact) return false
        val n = clean(name)
        if (n.isEmpty() || PHONE.matches(n)) return false
        if (n.any { it.isLowerCase() }) return false
        val letters = n.count { it.isLetter() }
        val digits = n.count { it.isDigit() }
        return letters >= 2 || (letters >= 1 && digits >= 1)
    }
}

/**
 * Kural katmanı (CLAUDE.md §5). Sırayla uygulanır, ilk eşleşen karar verir.
 * Kural eşleşmezse [model] karar verir (§6–7); model yüklenmemişse "Model bekliyor".
 */
class RuleEngine(private val rules: Rules, private val modelProvider: () -> TextModel? = { null }) {

    constructor(rules: Rules, model: TextModel?) : this(rules, { model })

    /** 1. ve 2. adım: dokunulmaz mı, beklenecek mi? */
    fun preFilter(f: NotificationFacts): PreFilter {
        rules.untouchedReason(f)?.let { return PreFilter.Untouched(it) }
        if (f.timeoutAfterMs in 1..rules.timeoutThresholdMs) {
            return PreFilter.Wait(rules.waitMs, "kısa ömürlü (timeoutAfter ${f.timeoutAfterMs} ms)")
        }
        if (f.category == null) {
            return PreFilter.Wait(rules.waitMs, "türü bayraklardan anlaşılamadı")
        }
        return PreFilter.Proceed
    }

    /**
     * 3.–6. adım (CLAUDE.md §5). [preFilter] Untouched dönmediyse çağrılır. Sıra:
     * gizli kod / güvenlik → kumar → YTÜ rezervasyonu → Panelde bırak → toplu kutlama → öncelikli uygulama (kampanya geçmez)
     * → haber → e-posta (mülakat → Önemli; LinkedIn → Önemsiz) → sohbet (grup anahtar kelimesi / birebir; Telegram kanalı hariç) → model.
     */
    fun classify(f: NotificationFacts, settings: RuleSettings = RuleSettings()): Decision {
        // 1–3. Gizli kod, güvenlik, kumar (5 sn beklemeden önce de çalışır: earlyDecision).
        earlyDecision(f, settings)?.let { return it }
        val pre = Preprocess.onIsle(f.appLabel, f.title, f.body)
        val folded = Preprocess.forGambling(f.appLabel, f.title, f.body)
        // 4. YTÜ rezervasyonu kaçmasın → Önemli (Panelde bırak, kutlama, öncelikli uygulama ve kampanyadan önce).
        rules.reservationMatch(f)?.let { why ->
            return Decision(Category.ONEMLI, 5, "Rezervasyon: $why → Önemli", "rezervasyon")
        }
        // 5. Panelde bırak: panelden silinmez, ses yok, kayda geçer.
        if (f.packageName in settings.panelApps) {
            return Decision(Category.DOKUNMA, null, "Panelde bırak: ${f.appLabel}", "panelde_birak")
        }
        val who = f.sender ?: f.title
        val institution = SenderHeuristics.isInstitution(who, f.senderIsContact)
        // 5. Toplu kutlama mesajı (kurum/toplu gönderici + kutlama ifadesi)
        if (institution) {
            rules.celebrationMatch(folded)?.let { hit ->
                return Decision(
                    Category.ONEMSIZ, null, "Toplu kutlama mesajı: ${SenderHeuristics.clean(who)}, \"$hit\"", "toplu_kutlama",
                )
            }
        }
        // 6. Öncelikli uygulama; kampanya içeriği geçmez (kişiden gelen sohbet mesajı hariç).
        settings.priorityApps[f.packageName]?.let { level ->
            val personChat = f.isGroupConversation != null && !institution
            if (!personChat) {
                val p = modelProvider()?.predictPreprocessed(pre)
                // Kurumdan gelen, sözlükte açık kelimesi olmayan kumar (demo 23) öncelikli uygulamada da silinir.
                if (p != null && settings.gamblingFilter && p.bestType == ContentType.KUMAR &&
                    p.bestProba >= CategoryMapper.FILTER_MIN && rules.modelGamblingException(folded) == null
                ) {
                    return Decision(
                        Category.SILINENLER, ContentType.KUMAR,
                        "Öncelikli uygulama ${f.appLabel}, ama model: kumar ${pct(p.bestProba)} → kumar filtresi", "oncelikli_kumar",
                    )
                }
                campaignOf(pre, p)?.let { why ->
                    return Decision(
                        Category.ONEMSIZ, ContentType.KAMPANYA,
                        "Öncelikli uygulama ${f.appLabel}, ama kampanya ($why) → Önemsiz", "oncelikli_kampanya",
                    )
                }
            }
            return Decision(level, null, "Öncelikli uygulama: ${f.appLabel} → ${level.label}", "oncelikli_uygulama")
        }
        // 7. Haber uygulaması
        if (rules.isNewsApp(f)) {
            return Decision(Category.ONEMSIZ, null, "Haber uygulaması: ${f.appLabel}", "haber")
        }
        // 8a. E-posta: mülakat / görüşme daveti → Önemli
        rules.emailImportant(f)?.let { hit ->
            return Decision(Category.ONEMLI, 4, "E-posta, \"$hit\" geçiyor → Önemli", "eposta_mulakat")
        }
        // 8b. E-posta: LinkedIn / Youthall gönderenleri
        rules.emailLowSender(f)?.let { hit ->
            return Decision(Category.ONEMSIZ, null, "E-posta, gönderen \"$hit\" → Önemsiz", "eposta_linkedin")
        }
        // 9. MessagingStyle grup/birebir bilgisi modelden kesindir (CLAUDE.md §6).
        // Kurum göndericisinden gelen "birebir" SMS'ler ve Telegram kanal gönderileri kişisel mesaj değildir.
        // Tür 2 (birebir) ve 3 (grup) "düzeltmeyle değişir" (§6): kişisel düzeltme uygulanır.
        var note: String? = null
        when (f.isGroupConversation) {
            true -> {
                rules.groupKeyword(f.body, settings.userName)?.let { hit ->
                    return Decision(
                        Category.ONEMLI, ContentType.GRUP_MESAJI, "Grup sohbeti, \"$hit\" geçiyor → Önemli", "grup_anahtar",
                    )
                }
                return messageDecision(f, ContentType.GRUP_MESAJI, "Grup sohbeti mesajı", "grup_sohbeti", settings)
            }
            false -> {
                val channel = rules.telegramChannelSign(f)
                when {
                    institution -> note = "kurum göndericisi: ${SenderHeuristics.clean(who)}"
                    channel != null -> note = "Telegram kanalı: $channel"
                    else -> return messageDecision(f, ContentType.KISISEL_MESAJ, "Birebir sohbet mesajı", "birebir_sohbet", settings)
                }
            }
            null -> {}
        }
        // 10. Metin modeli + kategori eşlemesi (§6–7)
        val m = modelProvider()
            ?: return Decision(Category.MODEL_BEKLIYOR, null, "Kural eşleşmedi; metin modeli yüklenmedi", "model_yok")
        val exception = rules.modelGamblingException(folded)
        val p = m.predictPreprocessed(pre)
        val delta = settings.personalDelta(personalKey(f, p.bestType))
        return CategoryMapper.decide(
            p, settings, note, gamblingException = exception, personalDelta = delta,
            shoppingApp = rules.isShoppingApp(f.packageName), socialApp = rules.isSocialApp(f.packageName),
        )
    }

    /**
     * classify'ın ilk üç adımı: gizli tek kullanımlık kod / güvenlik regex'i → Kritik, kumar sözlüğü → Silinenler.
     * Dinleyici bunu 5 sn beklemeden ÖNCE de çalıştırır (25 Kasım): kumar panelde hiç görünmesin, kod beklemeden
     * Kritik olsun. Eşleşmezse null; bekleme ve sonraki adımlar değişmez (classify bu fonksiyonla başlar).
     */
    fun earlyDecision(f: NotificationFacts, settings: RuleSettings = RuleSettings()): Decision? {
        // 1. İçeriği gizlenmiş tek kullanımlık kod
        if (rules.isHiddenContent(f)) {
            return Decision(Category.KRITIK, ContentType.GUVENLIK, "İçeriği gizlenmiş tek kullanımlık kod", "gizli_icerik")
        }
        // 2. Güvenlik / para hareketi regex'i (ml/guvenlik_kurali.py); öncelikli uygulamadan önce.
        if (rules.isSecurity(Preprocess.onIsle(f.appLabel, f.title, f.body))) {
            return Decision(Category.KRITIK, ContentType.GUVENLIK, "Güvenlik kuralı: kod, giriş ya da para hareketi", "guvenlik")
        }
        // 3. Kumar sözlüğü; öncelikli uygulamadan önce (Babam'dan gelen gazino SMS'i).
        if (settings.gamblingFilter) {
            rules.gamblingMatch(Preprocess.forGambling(f.appLabel, f.title, f.body))?.let { hit ->
                return Decision(Category.SILINENLER, ContentType.KUMAR, "Kumar filtresi: \"$hit\"", "kumar")
            }
        }
        return null
    }

    /** Öncelikli uygulamada kampanya mı? Güvenlik kuralının NEG ifadesi ya da model tür 7 (≥ eşik). */
    private fun campaignOf(pre: String, p: Prediction?): String? {
        rules.negMatch(pre)?.let { return "\"$it\"" }
        if (p != null && p.bestType == ContentType.KAMPANYA && p.bestProba >= rules.priorityCampaignMin) {
            return "model kampanya ${pct(p.bestProba)}"
        }
        return null
    }

    private fun pct(x: Double) = "%" + String.format(java.util.Locale.ROOT, "%.0f", x * 100)

    /** Düzeltme anahtarı: (gönderen karması, uygulama, içerik türü). */
    fun personalKey(f: NotificationFacts, type: Int) =
        PersonalKey(Personalization.senderHash(f.sender), f.appLabel, type)

    private fun messageDecision(
        f: NotificationFacts, type: Int, label: String, rule: String, settings: RuleSettings,
    ): Decision {
        val base = CategoryMapper.weight(CategoryMapper.defaultCategory(type)).toDouble()
        val delta = settings.personalDelta(personalKey(f, type))
        if (delta == 0.0) return Decision(CategoryMapper.defaultCategory(type), type, label, rule, base)
        val score = (base + delta).coerceIn(1.0, 4.0)
        val cat = CategoryMapper.fromScore(score)
        return Decision(cat, type, "$label; ${CategoryMapper.personalText(delta)}", rule, score)
    }
}

/** rules.json'un ayrıştırılmış hâli. */
class Rules(
    private val untouchedCategories: Set<String>,
    private val untouchedPackages: Set<String>,
    private val untouchedPackageSubstrings: List<String>,
    val waitMs: Long,
    val timeoutThresholdMs: Long,
    private val hiddenPhrases: List<String>,
    private val sec: Regex,
    private val neg: Regex,
    private val gamblingStrong: List<Regex>,
    private val gamblingSites: List<Regex>,
    private val gamblingTogether: List<List<Regex>>,
    private val modelGamblingExceptions: List<Regex> = emptyList(),
    private val celebration: List<Regex> = emptyList(),
    private val reservationSender: Regex? = null,
    private val reservationWord: Regex? = null,
    private val reservationDomain: Regex? = null,
    private val reservationWith: Regex? = null,
    val priorityCampaignMin: Double = 0.5,
    private val newsPackages: Set<String> = emptySet(),
    private val emailPackages: Set<String> = emptySet(),
    private val emailLowSenders: List<Regex> = emptyList(),
    private val emailImportantWords: List<Regex> = emptyList(),
    private val shoppingPackages: Set<String> = emptySet(),
    private val socialPackages: Set<String> = emptySet(),
    private val groupKeywords: List<Regex> = emptyList(),
    private val telegramPackages: Set<String> = emptySet(),
    private val telegramChannelPart: String = "_channels_",
    private val telegramShortcutPrefix: String = "ndid_-",
) {
    /** YTÜ rezervasyonu (rules.json `rezervasyon`): eşleşmenin açıklaması; değilse null. */
    fun reservationMatch(f: NotificationFacts): String? {
        val word = reservationWord ?: return null
        val text = Preprocess.forGambling(null, f.title, f.body)
        val who = Preprocess.fold(Preprocess.trLower(SenderHeuristics.clean(f.sender) + " | " + SenderHeuristics.clean(f.title)))
        val hasWord = word.containsMatchIn(text)
        reservationSender?.find(who)?.let { if (hasWord) return "gönderen \"${it.value}\" + \"rezervasyon\"" }
        reservationDomain?.find(text)?.let { return "\"${it.value}\"" }
        if (hasWord) reservationWith?.find(text)?.let { return "\"rezervasyon\" + \"${it.value}\"" }
        return null
    }

    fun celebrationMatch(folded: String): String? = celebration.firstNotNullOfOrNull { it.find(folded)?.value }

    fun negMatch(preprocessed: String): String? = neg.find(preprocessed)?.value

    fun isNewsApp(f: NotificationFacts): Boolean =
        f.packageName in newsPackages || f.appCategory == CATEGORY_NEWS

    /** E-posta uygulamasında başlık (gönderen) LinkedIn/Youthall ise eşleşen ifade. */
    fun emailLowSender(f: NotificationFacts): String? {
        if (f.packageName !in emailPackages) return null
        val t = Preprocess.fold(Preprocess.trLower(SenderHeuristics.clean(f.title)))
        return emailLowSenders.firstNotNullOfOrNull { it.find(t)?.value }
    }

    /** E-posta uygulamasında başlık ya da metinde mülakat/görüşme daveti ifadesi; yoksa null. */
    fun emailImportant(f: NotificationFacts): String? {
        if (f.packageName !in emailPackages) return null
        val t = Preprocess.forGambling(null, SenderHeuristics.clean(f.title), f.body)
        return emailImportantWords.firstNotNullOfOrNull { it.find(t)?.value }
    }

    fun isShoppingApp(pkg: String) = pkg in shoppingPackages
    fun isSocialApp(pkg: String) = pkg in socialPackages

    /** Grup mesajı metninde acil/toplantı/sınav... ya da kullanıcının adı (WhatsApp @bahsetme dahil). */
    fun groupKeyword(body: String?, userName: String): String? {
        val t = Preprocess.fold(Preprocess.trLower(body.orEmpty()))
        groupKeywords.firstNotNullOfOrNull { it.find(t)?.value }?.let { return it }
        val name = Preprocess.fold(Preprocess.trLower(userName.trim()))
        if (name.length < 2) return null
        return uRegex("(?<![a-z0-9])@?" + Regex.escape(name) + "(?![a-z0-9])").find(t)?.value
    }

    /** Telegram'da birebir görünen bildirim aslında kanal gönderisi mi? İşareti döndürür; değilse null. */
    fun telegramChannelSign(f: NotificationFacts): String? {
        if (f.packageName !in telegramPackages || f.isGroupConversation != false) return null
        return when {
            f.channelId?.contains(telegramChannelPart) == true -> "bildirim kanalı ${f.channelId}"
            f.shortcutId?.startsWith(telegramShortcutPrefix) == true -> "kısayol ${f.shortcutId}"
            f.hasReplyAction == false -> "yanıt eylemi yok"
            else -> null
        }
    }

    /** Modelin "kumar" kararını silmeye çevirmeyi engelleyen ifade (Bonus kart vb.); yoksa null. */
    fun modelGamblingException(normalized: String): String? =
        modelGamblingExceptions.firstNotNullOfOrNull { it.find(normalized)?.value }

    fun untouchedReason(f: NotificationFacts): String? = when {
        f.isOngoing -> "kalıcı bildirim"
        f.isForegroundService -> "ön plan servisi"
        f.isGroupSummary -> "grup özeti"
        f.category != null && f.category in untouchedCategories -> "kategori: ${f.category}"
        f.hasMediaSession -> "medya oturumu"
        f.packageName in untouchedPackages -> "korunan uygulama"
        untouchedPackageSubstrings.any { f.packageName.contains(it) } -> "acil durum uyarısı"
        else -> null
    }

    fun isHiddenContent(f: NotificationFacts): Boolean {
        val t = Preprocess.trLower(listOfNotNull(f.title, f.body).joinToString(" "))
        return hiddenPhrases.any { t.contains(it) }
    }

    fun isSecurity(preprocessed: String): Boolean =
        sec.containsMatchIn(preprocessed) && !neg.containsMatchIn(preprocessed)

    /** Eşleşen ifadeyi döndürür (Neden burada? satırı için); yoksa null. */
    fun gamblingMatch(normalized: String): String? {
        for (r in gamblingStrong) r.find(normalized)?.let { return it.value }
        for (r in gamblingSites) r.find(normalized)?.let { return it.value }
        for (group in gamblingTogether) {
            val hits = group.map { it.find(normalized) ?: return@map null }
            if (hits.all { it != null }) return hits.joinToString(" + ") { it!!.value.trim() }
        }
        return null
    }

    companion object {
        /** ApplicationInfo.CATEGORY_NEWS */
        const val CATEGORY_NEWS = 5

        private fun rx(p: String) = uRegex(p)

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

        fun parse(json: String): Rules {
            val o = JSONObject(json)
            val d = o.getJSONObject("dokunma")
            val b = o.getJSONObject("bekleme")
            val g = o.getJSONObject("guvenlik")
            val k = o.getJSONObject("kumar")
            val together = k.getJSONArray("birlikte")
            return Rules(
                untouchedCategories = d.getJSONArray("kategoriler").strings().toSet(),
                untouchedPackages = d.getJSONArray("paketler").strings().toSet(),
                untouchedPackageSubstrings = d.getJSONArray("paket_icerir").strings(),
                waitMs = b.getLong("sure_ms"),
                timeoutThresholdMs = b.getLong("timeout_esik_ms"),
                hiddenPhrases = o.getJSONObject("gizli_icerik").getJSONArray("ifadeler").strings()
                    .map { Preprocess.trLower(it) },
                sec = rx(g.getString("sec")),
                neg = rx(g.getString("neg")),
                gamblingStrong = k.getJSONArray("guclu").strings().map(::rx),
                gamblingSites = k.getJSONArray("siteler").strings().map { rx("\\b${Regex.escape(it)}\\b") },
                gamblingTogether = (0 until together.length()).map { i -> together.getJSONArray(i).strings().map(::rx) },
                modelGamblingExceptions = k.optJSONObject("model_istisna")?.getJSONArray("ifadeler")?.strings()
                    ?.map(::rx).orEmpty(),
                reservationSender = o.optJSONObject("rezervasyon")?.getString("gonderen")?.let(::rx),
                reservationWord = o.optJSONObject("rezervasyon")?.getString("kelime")?.let(::rx),
                reservationDomain = o.optJSONObject("rezervasyon")?.getString("alan_adi")?.let(::rx),
                reservationWith = o.optJSONObject("rezervasyon")?.getString("yaninda")?.let(::rx),
                celebration = o.optJSONObject("kutlama")?.getJSONArray("ifadeler")?.strings()?.map(::rx).orEmpty(),
                priorityCampaignMin = o.optJSONObject("oncelikli_kampanya")?.optDouble("model_esik", 0.5) ?: 0.5,
                newsPackages = o.optJSONObject("haber")?.getJSONArray("paketler")?.strings()?.toSet().orEmpty(),
                emailPackages = o.optJSONObject("eposta")?.getJSONArray("paketler")?.strings()?.toSet().orEmpty(),
                emailLowSenders = o.optJSONObject("eposta")?.getJSONArray("onemsiz_gonderen")?.strings()?.map(::rx).orEmpty(),
                emailImportantWords = o.optJSONObject("eposta")?.optJSONArray("onemli_ifadeler")?.strings()?.map(::rx).orEmpty(),
                shoppingPackages = o.optJSONObject("alisveris")?.getJSONArray("paketler")?.strings()?.toSet().orEmpty(),
                socialPackages = o.optJSONObject("sosyal")?.getJSONArray("paketler")?.strings()?.toSet().orEmpty(),
                groupKeywords = o.optJSONObject("grup_anahtar")?.getJSONArray("ifadeler")?.strings()?.map(::rx).orEmpty(),
                telegramPackages = o.optJSONObject("telegram")?.getJSONArray("paketler")?.strings()?.toSet().orEmpty(),
                telegramChannelPart = o.optJSONObject("telegram")?.optString("kanal_icerir", "_channels_") ?: "_channels_",
                telegramShortcutPrefix = o.optJSONObject("telegram")?.optString("kisayol_kanal_onek", "ndid_-") ?: "ndid_-",
            )
        }
    }
}

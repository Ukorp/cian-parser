package ru.ukorp.parser.cian

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.springframework.stereotype.Component

private val CAPTCHA_MARKERS = listOf("adfstat.yandex.ru", "Вы не робот", "SmartCaptcha", "showCaptcha")
private const val DESCRIPTION_MAX_LENGTH = 300
private val OFFER_ID = Regex("""/(\d+)/""")

/**
 * Parses cian.ru search-results cards using stable data-* attributes (data-testid, data-name,
 * data-mark) as anchors rather than the CSS classes cian ships (e.g. "x31de4314--_416c6--card"),
 * since those class names carry a per-build content hash and change on every cian.ru frontend
 * deploy — the data-* attributes are cian's own component/test hooks and are far more stable.
 */
@Component
class CianHtmlParser {

    fun parse(document: Document): List<CianOffer> {
        val html = document.outerHtml()
        if (CAPTCHA_MARKERS.any { html.contains(it, ignoreCase = true) }) {
            throw CianBlockedException("cian.ru returned an anti-bot challenge page instead of search results")
        }

        return document.select("div[data-testid=offer-card]").mapNotNull(::toOffer)
    }

    private fun toOffer(card: Element): CianOffer? {
        val url = card.selectFirst("a[data-name=TitleComponent]")
            ?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val id = OFFER_ID.find(url)?.groupValues?.get(1) ?: return null

        val specialGeo = card.selectFirst("div[data-name=SpecialGeo]")
        val metroStation = specialGeo?.selectFirst("a")?.text()?.takeIf { it.isNotBlank() }
        val metroRemoteness = specialGeo?.children()?.lastOrNull()
            ?.takeIf { it.tagName() == "div" }
            ?.text()
            ?.takeIf { it.isNotBlank() }

        return CianOffer(
            id = id,
            title = card.selectFirst("[data-mark=OfferTitle]")?.text().orEmpty(),
            subtitle = card.selectFirst("[data-mark=OfferSubtitle]")?.text().orEmpty(),
            price = card.selectFirst("[data-mark=MainPrice]")?.text().orEmpty(),
            metroStation = metroStation,
            metroRemoteness = metroRemoteness,
            description = truncate(card.selectFirst("div[data-name=Description]")?.text().orEmpty()),
            url = url,
            photos = CianImageExtractor.extractBestImages(card.outerHtml(), limit = 5),
        )
    }

    private fun truncate(text: String, maxLength: Int = DESCRIPTION_MAX_LENGTH): String =
        if (text.length <= maxLength) text else text.take(maxLength).trimEnd() + "…"
}

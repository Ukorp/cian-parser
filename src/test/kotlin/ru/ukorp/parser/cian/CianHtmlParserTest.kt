package ru.ukorp.parser.cian

import org.jsoup.Jsoup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CianHtmlParserTest {

    private val parser = CianHtmlParser()

    @Test
    fun `parses an offer card using data attributes`() {
        val document = Jsoup.parse(fixture("cian-search-sample.html"), "https://www.cian.ru/")

        val offers = parser.parse(document)

        assertEquals(1, offers.size)
        val offer = offers[0]
        assertEquals("314252217", offer.id)
        assertTrue(offer.url.startsWith("https://www.cian.ru/rent/flat/314252217/"))
        assertEquals("Шикарная трешка в Neva Towers", offer.title)
        assertEquals("3-комн. апартаменты, 135 м², 69/79 этаж", offer.subtitle)
        assertEquals("750 000 ₽/мес.", offer.price)
        assertEquals("Москва-Сити", offer.metroStation)
        assertEquals("2 минуты пешком", offer.metroRemoteness)
        assertTrue(offer.description.startsWith("АРЕНДА АПАРТАМЕНТОВ"))
    }

    @Test
    fun `picks the highest-resolution photo per image and skips thumbnail duplicates`() {
        val document = Jsoup.parse(fixture("cian-search-sample.html"), "https://www.cian.ru/")

        val photos = parser.parse(document).single().photos

        assertEquals(
            listOf(
                "https://images.cdn-cian.ru/images/kvartira-moskva-1y-krasnogvardeyskiy-proezd-2413802068-4.jpg",
                "https://images.cdn-cian.ru/images/kvartira-moskva-1y-krasnogvardeyskiy-proezd-2413802076-4.jpg",
                "https://images.cdn-cian.ru/images/kvartira-moskva-1y-krasnogvardeyskiy-proezd-2413802072-4.jpg",
                "https://images.cdn-cian.ru/images/kvartira-moskva-1y-krasnogvardeyskiy-proezd-2413802082-4.jpg",
                "https://images.cdn-cian.ru/images/kvartira-moskva-1y-krasnogvardeyskiy-proezd-2413802112-4.jpg",
            ),
            photos,
        )
    }

    @Test
    fun `throws when page is an anti-bot challenge`() {
        val document = Jsoup.parse(fixture("cian-search-captcha.html"), "https://www.cian.ru/")

        assertFailsWith<CianBlockedException> { parser.parse(document) }
    }

    @Test
    fun `returns empty list when the page has no offer cards`() {
        val document = Jsoup.parse(fixture("cian-search-empty.html"), "https://www.cian.ru/")

        assertTrue(parser.parse(document).isEmpty())
    }

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "Missing fixture $name" }
            .bufferedReader(Charsets.UTF_8)
            .readText()
}

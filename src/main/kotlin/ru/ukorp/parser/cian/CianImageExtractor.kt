package ru.ukorp.parser.cian

private val CIAN_IMAGE_URL = Regex("""https://images\.cdn-cian\.ru/images/[\w-]+-\d+\.jpg""")
private val SIZE_SUFFIX = Regex("""-(\d+)\.jpg$""")

/**
 * Cian serves the same photo at several resolutions from the same CDN, differing only by a
 * trailing "-<size>.jpg" suffix (bigger number = bigger image) — e.g. on a search-results card,
 * the small "Thumbnails" strip reuses "-2.jpg" versions of photos the main gallery already shows
 * as "-4.jpg". Scanning raw HTML for every occurrence and keeping only the largest suffix per
 * photo automatically prefers the better copy, and works the same way whether it's run against a
 * search-card fragment or a full offer detail page, without depending on either page's exact DOM
 * structure — only on cian's CDN URL naming convention.
 */
object CianImageExtractor {

    fun extractBestImages(html: String, limit: Int = 5): List<String> {
        val bestBySlug = LinkedHashMap<String, Pair<String, Int>>()
        for (match in CIAN_IMAGE_URL.findAll(html)) {
            val url = match.value
            val size = SIZE_SUFFIX.find(url)?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val slug = url.removeSuffix("-$size.jpg")
            val current = bestBySlug[slug]
            if (current == null || size > current.second) {
                bestBySlug[slug] = url to size
            }
        }
        return bestBySlug.values.map { it.first }.take(limit)
    }
}

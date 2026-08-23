package ru.ukorp.parser.cian

data class CianOffer(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val metroStation: String?,
    val metroRemoteness: String?,
    val description: String,
    val url: String,
    val photos: List<String>,
)

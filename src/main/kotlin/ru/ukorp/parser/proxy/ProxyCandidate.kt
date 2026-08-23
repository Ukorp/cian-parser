package ru.ukorp.parser.proxy

data class ProxyCandidate(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"
}

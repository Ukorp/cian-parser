package ru.ukorp.parser.proxy

import java.net.InetSocketAddress
import java.net.Proxy

data class ProxyCandidate(val host: String, val port: Int) {
    override fun toString(): String = "$host:$port"

    val httpProxy: Proxy get() =
        Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
}

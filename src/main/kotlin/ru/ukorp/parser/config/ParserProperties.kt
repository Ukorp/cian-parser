package ru.ukorp.parser.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

class ParserProperties {

    var pollInterval: Duration = Duration.ofMinutes(5)
    var searchUrls: List<String> = emptyList()
    var telegram: Telegram = Telegram()
    var proxy: Proxy = Proxy()

    class Telegram {
        var botToken: String = ""
        var chatId: String = ""
    }

    class Proxy {
        var sourceUrl: String = ""
        var refreshInterval: Duration = Duration.ofMinutes(30)
    }
}

@Configuration
class ParserConfig {

    @Bean
    @ConfigurationProperties(prefix = "parser")
    fun parserProperties(): ParserProperties = ParserProperties()
}

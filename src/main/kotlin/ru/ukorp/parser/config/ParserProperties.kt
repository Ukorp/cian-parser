package ru.ukorp.parser.config

import com.pengrad.telegrambot.TelegramBot
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

class ParserProperties {

    var pollInterval: Duration = Duration.ofMinutes(5)
    var telegram: Telegram = Telegram()
    var proxy: Proxy = Proxy()
    var state: State = State()

    class Telegram {
        var botToken: String = ""
    }

    class Proxy {
        var sourceUrl: String = ""
        var refreshInterval: Duration = Duration.ofMinutes(30)
    }

    class State {
        var snapshotInterval: Duration = Duration.ofSeconds(30)
    }
}

@Configuration
class ParserConfig {

    @Bean
    @ConfigurationProperties(prefix = "parser")
    fun parserProperties(): ParserProperties = ParserProperties()

    @Bean
    fun telegramBot(): TelegramBot = TelegramBot(parserProperties().telegram.botToken)
}

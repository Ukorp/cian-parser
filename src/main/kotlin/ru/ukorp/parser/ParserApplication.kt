package ru.ukorp.parser

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class ParserApplication

fun main(args: Array<String>) {
    runApplication<ParserApplication>(*args)
}

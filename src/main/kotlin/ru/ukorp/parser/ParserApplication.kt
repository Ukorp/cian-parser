package ru.ukorp.parser

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.io.File

@SpringBootApplication
@EnableScheduling
class ParserApplication

fun main(args: Array<String>) {
    // Spring's DataSource auto-config connects to the SQLite file before any of our
    // beans run, and the sqlite-jdbc driver refuses to create a missing parent directory.
    val dbPath = System.getenv("PARSER_STATE_DB_PATH") ?: "./data/parser-state.db"
    File(dbPath).absoluteFile.parentFile?.mkdirs()

    runApplication<ParserApplication>(*args)
}

package io.github.alcindogandhi.champs

import kotlinx.serialization.json.Json

/** Respostas reais da ESPN gravadas em `src/test/resources/espn` (ver `scripts/`). */
object Fixtures {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    fun read(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("espn/$name")) { "fixture $name não encontrada" }.readText()

    inline fun <reified T> decode(name: String): T = json.decodeFromString(read(name))
}

package io.github.alcindogandhi.champs.domain

/**
 * Distribui os titulares em linhas do campo (goleiro, defesa, ..., ataque) a partir do esquema tático.
 *
 * A ESPN informa o esquema ("4-3-3") e a sigla da posição de cada jogador em português (G, ZC, LE, VOL, MC,
 * ME, A...). Os jogadores são ordenados pela profundidade da posição e, em caso de empate, pelo
 * `formationPlace`; depois são fatiados conforme o esquema. Dentro de cada linha, a ordem é da esquerda
 * para a direita (posições terminadas em E, depois C, depois D).
 */
object LineupLayout {

    /** Linhas do campo, do goleiro ao ataque; vazio se não der para montar. */
    fun lines(formation: String?, starters: List<LineupPlayer>): List<List<LineupPlayer>> {
        if (starters.size != 11) return emptyList()
        val shape = formation?.split('-')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        if (shape.isEmpty() || shape.sum() != 10) return emptyList()
        val sorted = starters.sortedWith(compareBy<LineupPlayer> { depth(it) }.thenBy { it.formationPlace })
        val goalkeeper = sorted.first()
        val outfield = sorted.drop(1)
        val result = mutableListOf(listOf(goalkeeper))
        var index = 0
        for (count in shape) {
            result += outfield.subList(index, index + count).sortedBy { side(it.position) }
            index += count
        }
        return result
    }

    private fun depth(p: LineupPlayer): Int {
        val pos = p.position.uppercase()
        return when {
            pos == "G" || pos == "GK" || pos == "GOL" -> 0
            pos.startsWith("Z") || pos.startsWith("L") || pos.endsWith("B") -> 1
            pos.startsWith("V") || pos == "DM" -> 2
            pos.startsWith("M") || pos.startsWith("AM") -> 3
            // Centroavante é o mais avançado; pontas e segundos atacantes ficam uma linha atrás.
            pos == "A" || pos == "CA" || pos == "CF" || pos == "ST" -> 5
            pos.startsWith("A") || pos.startsWith("F") || pos.endsWith("W") -> 4
            else -> 3
        }
    }

    /** -1 esquerda, 0 centro, 1 direita. */
    private fun side(position: String): Int {
        val pos = position.uppercase()
        return when {
            pos.endsWith("E") || pos.endsWith("-L") || pos in setOf("LB", "LM", "LW", "LWB") -> -1
            pos.endsWith("D") || pos.endsWith("-R") || pos in setOf("RB", "RM", "RW", "RWB") -> 1
            else -> 0
        }
    }
}

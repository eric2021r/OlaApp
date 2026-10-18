package com.example.olaapp.coliseu

/**
 * As classes de lutador do coliseu.
 *
 * O dano de cada uma é definido em relação ao cavaleiro (referência = 20):
 *  - Cavaleiro: espada, dano normal (1x).
 *  - Lutador: machado de lâmina dupla com cabo meio longo, dano 2x o cavaleiro.
 *  - Assassino: lança adagas (ataque à distância) mais rápido que os outros, dano 1,5x o cavaleiro.
 *
 * O nível (1..20) aumenta o dano: cada nível soma +10% ao dano base.
 */
enum class Personagem(
    val id: String,
    val nome: String,
    val danoBase: Float,
    val duracaoAtaque: Float,
    val golpeInicio: Float,
    val golpeFim: Float,
    val alcance: Float,
    val arremessa: Boolean,
    val cor: Int
) {
    CAVALEIRO(
        "cavaleiro", "Cavaleiro", 20f, 0.45f, 0.15f, 0.27f, 2.3f, false, 0xFFB3262B.toInt()
    ),
    LUTADOR(
        "lutador", "Lutador", 40f, 0.62f, 0.22f, 0.36f, 2.7f, false, 0xFFB3722B.toInt()
    ),
    ASSASSINO(
        "assassino", "Assassino", 30f, 0.30f, 0.10f, 0.18f, 2.0f, true, 0xFF8E5FC0.toInt()
    );

    /** Dano de um golpe no nível dado (1..20). Cada nível soma +10% ao dano base. */
    fun danoNoNivel(nivel: Int): Float {
        val n = nivel.coerceIn(1, NIVEL_MAX)
        return danoBase * (1f + 0.1f * (n - 1))
    }

    companion object {
        /** Nível máximo de cada lutador. */
        const val NIVEL_MAX = 20

        /** Devolve o lutador com o id dado, ou o [CAVALEIRO] se não encontrar. */
        fun porId(id: String?): Personagem =
            entries.firstOrNull { it.id == id } ?: CAVALEIRO
    }
}

package com.example.olaapp

import android.content.Context
import com.example.olaapp.coliseu.Personagem

/**
 * Guarda o que o jogador conquistou: as moedas e o nível de cada lutador.
 * Fica no mesmo SharedPreferences "olaapp" usado pelo recorde do coliseu.
 */
class Progresso(context: Context) {

    private val prefs = context.getSharedPreferences("olaapp", Context.MODE_PRIVATE)

    /** Moedas disponíveis para subir de nível. */
    var moedas: Int
        get() = prefs.getInt(CHAVE_MOEDAS, 0)
        set(valor) = prefs.edit().putInt(CHAVE_MOEDAS, valor.coerceAtLeast(0)).apply()

    /** Nível atual do lutador (sempre entre 1 e [Personagem.NIVEL_MAX]). */
    fun nivel(p: Personagem): Int =
        prefs.getInt(chaveNivel(p), 1).coerceIn(1, Personagem.NIVEL_MAX)

    fun definirNivel(p: Personagem, nivel: Int) {
        prefs.edit().putInt(chaveNivel(p), nivel.coerceIn(1, Personagem.NIVEL_MAX)).apply()
    }

    /**
     * Quanto custa o próximo nível. O preço dobra no começo (1, 2, 4, 8, 16, 32), mas para
     * no teto de [CUSTO_MAX] moedas. Assim dá para chegar ao nível 20 jogando poucas partidas.
     * Devolve 0 quando o lutador já está no nível máximo.
     */
    fun custoProximoNivel(p: Personagem): Int {
        val nivel = nivel(p)
        if (nivel >= Personagem.NIVEL_MAX) return 0
        return custoParaSubir(nivel)
    }

    /** Sobe o lutador um nível, gastando as moedas. Devolve false se não deu (sem moedas ou no máximo). */
    fun subirNivel(p: Personagem): Boolean {
        val custo = custoProximoNivel(p)
        if (custo <= 0) return false
        val atual = moedas
        if (atual < custo) return false
        moedas = atual - custo
        definirNivel(p, nivel(p) + 1)
        return true
    }

    companion object {
        private const val CHAVE_MOEDAS = "moedas"

        fun chaveNivel(p: Personagem) = "nivel_${p.id}"

        /** Teto do preço de um nível, para o jogo ficar alcançável. */
        const val CUSTO_MAX = 50

        /** Preço para passar do nível [nivelAtual] para o seguinte: 1, 2, 4, 8, 16, ... até o teto. */
        fun custoParaSubir(nivelAtual: Int): Int = (1 shl (nivelAtual - 1)).coerceAtMost(CUSTO_MAX)
    }
}

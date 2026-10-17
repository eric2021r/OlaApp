package com.example.olaapp

import java.math.BigDecimal
import java.math.MathContext

/**
 * Lógica da calculadora, sem nenhuma dependência do Android.
 * Usa BigDecimal para evitar erros como 0,1 + 0,2 = 0,30000000000000004.
 */
class CalculadoraMotor {

    private var entrada = "0"          // número que está sendo digitado / resultado atual
    private var acumulado: BigDecimal? = null
    private var operador: Char? = null // '+', '-', '×' ou '÷'
    private var novoNumero = true      // o próximo dígito começa um número novo
    private var erro = false
    private var expressao = ""

    /** Texto grande do visor (número atual ou resultado). */
    val visor: String
        get() = if (erro) MENSAGEM_ERRO else entrada.replace('.', ',')

    /** Texto pequeno acima do visor (a conta em andamento). */
    val historico: String
        get() = expressao

    fun digito(d: Char) {
        if (erro) limpar()
        when {
            novoNumero -> { entrada = d.toString(); novoNumero = false }
            entrada == "0" -> entrada = d.toString()
            entrada == "-0" -> entrada = "-$d"
            entrada.count { it.isDigit() } < MAX_DIGITOS -> entrada += d
        }
    }

    fun virgula() {
        if (erro) limpar()
        when {
            novoNumero -> { entrada = "0."; novoNumero = false }
            !entrada.contains('.') -> entrada += "."
        }
    }

    fun operador(op: Char) {
        if (erro) limpar()
        if (operador != null && !novoNumero) {
            aplicar() // encadeia: 2 + 3 × ... calcula 2 + 3 primeiro
            if (erro) return
        } else if (operador == null) {
            acumulado = valor()
        }
        operador = op
        novoNumero = true
        expressao = "${formatar(acumulado!!)} $op"
    }

    fun igual() {
        if (erro) return
        val op = operador ?: return
        val a = acumulado ?: return
        val b = valor()
        val texto = "${formatar(a)} $op ${formatar(b)} ="
        aplicar()
        if (!erro) expressao = texto
        operador = null
        novoNumero = true
    }

    fun porcentagem() {
        if (erro) return
        entrada = formatar(valor().divide(CEM, CONTEXTO))
        novoNumero = true
    }

    fun inverterSinal() {
        if (erro) return
        if (entrada == "0" || (novoNumero && operador != null)) {
            // Começa um número negativo: ± e depois 4 resulta em -4
            // (também depois de um operador: 5 + ± 4 = 1).
            entrada = "-0"
            novoNumero = false
            return
        }
        entrada = if (entrada.startsWith("-")) entrada.drop(1) else "-$entrada"
    }

    fun apagar() {
        if (erro) { limpar(); return }
        if (novoNumero) return
        entrada = entrada.dropLast(1)
        if (entrada.isEmpty() || entrada == "-") entrada = "0"
    }

    fun limpar() {
        entrada = "0"
        acumulado = null
        operador = null
        novoNumero = true
        erro = false
        expressao = ""
    }

    // --- internos ---

    private fun valor(): BigDecimal = BigDecimal(entrada.removeSuffix("."))

    private fun aplicar() {
        val a = acumulado ?: return
        val b = valor()
        val resultado = when (operador) {
            '+' -> a.add(b)
            '-' -> a.subtract(b)
            '×' -> a.multiply(b)
            '÷' -> {
                if (b.signum() == 0) {
                    erro = true
                    expressao = ""
                    return
                }
                a.divide(b, CONTEXTO)
            }
            else -> return
        }.round(CONTEXTO)
        acumulado = resultado
        entrada = formatar(resultado)
    }

    private fun formatar(n: BigDecimal): String {
        var s = if (n.signum() == 0) BigDecimal.ZERO else n.stripTrailingZeros()
        if (s.scale() < 0) s = s.setScale(0)
        return s.toPlainString()
    }

    companion object {
        const val MENSAGEM_ERRO = "Não dá para dividir por zero"
        private const val MAX_DIGITOS = 15
        private val CONTEXTO = MathContext(15)
        private val CEM = BigDecimal(100)
    }
}

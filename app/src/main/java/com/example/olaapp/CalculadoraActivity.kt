package com.example.olaapp

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class CalculadoraActivity : Activity() {

    private val motor = CalculadoraMotor()
    private lateinit var visor: TextView
    private lateinit var historico: TextView

    private val dp: Float by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Som.iniciar(this)
        val pad = (16 * dp).toInt()

        historico = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.END
            maxLines = 1
        }

        visor = TextView(this).apply {
            textSize = 48f
            setTextColor(Color.parseColor("#1B1B1B"))
            gravity = Gravity.END
            maxLines = 1
        }

        val areaVisor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(pad, pad, pad, pad)
            addView(historico)
            addView(visor)
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Visor ocupa o espaço de cima; teclado, a parte de baixo.
            addView(areaVisor, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f))

            val teclado = LinearLayout(this@CalculadoraActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad / 2, 0, pad / 2, pad / 2)
            }
            linhas().forEach { linha -> teclado.addView(criarLinha(linha), pesoLinha()) }
            addView(teclado, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 5f))
        }

        // Evita que o teclado fique embaixo da barra de navegação (Android 15+).
        raiz.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                insets.systemWindowInsetLeft,
                0,
                insets.systemWindowInsetRight,
                insets.systemWindowInsetBottom
            )
            insets
        }

        setContentView(raiz)
        atualizar()
    }

    // Cada tecla: texto, tipo (cor) e ação.
    private enum class Tipo { NUMERO, FUNCAO, OPERADOR, IGUAL }

    private class Tecla(val texto: String, val tipo: Tipo, val acao: () -> Unit)

    private fun linhas(): List<List<Tecla>> = listOf(
        listOf(
            Tecla("C", Tipo.FUNCAO) { motor.limpar() },
            Tecla("⌫", Tipo.FUNCAO) { motor.apagar() },
            Tecla("%", Tipo.FUNCAO) { motor.porcentagem() },
            Tecla("÷", Tipo.OPERADOR) { motor.operador('÷') }
        ),
        listOf(
            numero('7'), numero('8'), numero('9'),
            Tecla("×", Tipo.OPERADOR) { motor.operador('×') }
        ),
        listOf(
            numero('4'), numero('5'), numero('6'),
            Tecla("−", Tipo.OPERADOR) { motor.operador('-') }
        ),
        listOf(
            numero('1'), numero('2'), numero('3'),
            Tecla("+", Tipo.OPERADOR) { motor.operador('+') }
        ),
        listOf(
            Tecla("±", Tipo.NUMERO) { motor.inverterSinal() },
            numero('0'),
            Tecla(",", Tipo.NUMERO) { motor.virgula() },
            Tecla("=", Tipo.IGUAL) { motor.igual() }
        )
    )

    private fun numero(d: Char) = Tecla(d.toString(), Tipo.NUMERO) { motor.digito(d) }

    private fun pesoLinha() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun criarLinha(teclas: List<Tecla>): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            teclas.forEach { addView(criarBotao(it), pesoBotao()) }
        }

    private fun pesoBotao() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        .apply {
            val m = (4 * dp).toInt()
            setMargins(m, m, m, m)
        }

    private fun criarBotao(tecla: Tecla): Button {
        val (fundo, texto) = when (tecla.tipo) {
            Tipo.NUMERO -> "#F0F0F0" to "#1B1B1B"
            Tipo.FUNCAO -> "#DADADA" to "#1B1B1B"
            Tipo.OPERADOR -> "#FFE0B2" to "#E65100"
            Tipo.IGUAL -> "#F57C00" to "#FFFFFF"
        }
        val forma = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20 * dp
            setColor(Color.parseColor(fundo))
        }
        return Button(this).apply {
            this.text = tecla.texto
            textSize = 24f
            isAllCaps = false
            setTextColor(Color.parseColor(texto))
            stateListAnimator = null // sem sombra, visual mais plano
            background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#33000000")), forma, null)
            setOnClickListener {
                tecla.acao()
                Som.tocar(Som.Efeito.CLIQUE, 0.7f)
                atualizar()
            }
        }
    }

    private fun atualizar() {
        val texto = motor.visor
        visor.text = texto
        // Reduz a fonte quando o número é grande, para caber na tela.
        visor.textSize = when {
            texto.length > 18 -> 24f
            texto.length > 12 -> 32f
            else -> 48f
        }
        historico.text = motor.historico
    }
}

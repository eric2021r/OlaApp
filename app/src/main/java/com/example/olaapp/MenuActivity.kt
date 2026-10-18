package com.example.olaapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.olaapp.coliseu.Personagem

/**
 * Menu mostrado antes do jogo começar.
 * Aqui o jogador escolhe o lutador e gasta moedas para subir o nível de cada um (até 20).
 */
class MenuActivity : Activity() {

    private lateinit var progresso: Progresso
    private var selecionado = Personagem.CAVALEIRO

    private lateinit var textoMoedas: TextView
    private val cartoes = LinkedHashMap<Personagem, Cartao>()

    private class Cartao(
        val raiz: LinearLayout,
        val titulo: TextView,
        val detalhe: TextView,
        val botaoSubir: Button,
        val botaoEscolher: Button
    )

    private val dp get() = resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        progresso = Progresso(this)
        selecionado = Personagem.porId(intent.getStringExtra(EXTRA_SELECIONADO))

        val pad = (20 * dp).toInt()

        val coluna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#16110D"))
        }

        coluna.addView(TextView(this).apply {
            text = "COLISEU DO ERIC"
            textSize = 30f
            setTextColor(Color.parseColor("#FFD54F"))
            gravity = Gravity.CENTER
        })
        coluna.addView(TextView(this).apply {
            text = "Escolha o lutador e gaste moedas para evoluir"
            textSize = 14f
            setTextColor(Color.parseColor("#CFC3B4"))
            gravity = Gravity.CENTER
        })

        textoMoedas = TextView(this).apply {
            textSize = 22f
            setTextColor(Color.parseColor("#FFD54F"))
            gravity = Gravity.CENTER
        }
        coluna.addView(textoMoedas, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (10 * dp).toInt()
            bottomMargin = (10 * dp).toInt()
        })

        for (p in Personagem.entries) {
            val cartao = criarCartao(p)
            cartoes[p] = cartao
            coluna.addView(cartao.raiz, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() })
        }

        coluna.addView(Button(this).apply {
            text = "JOGAR"
            textSize = 22f
            setOnClickListener { jogar() }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (8 * dp).toInt() })

        setContentView(ScrollView(this).apply { addView(coluna) })
    }

    override fun onResume() {
        super.onResume()
        atualizar()
    }

    private fun criarCartao(p: Personagem): Cartao {
        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }
        val titulo = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        val detalhe = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#CFC3B4"))
        }
        val linha = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val botaoSubir = Button(this).apply {
            textSize = 13f
            setOnClickListener { if (progresso.subirNivel(p)) atualizar() }
        }
        val botaoEscolher = Button(this).apply {
            textSize = 13f
            setOnClickListener {
                selecionado = p
                atualizar()
            }
        }
        linha.addView(botaoSubir, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        linha.addView(botaoEscolher, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        raiz.addView(titulo)
        raiz.addView(detalhe)
        raiz.addView(linha)
        return Cartao(raiz, titulo, detalhe, botaoSubir, botaoEscolher)
    }

    private fun atualizar() {
        textoMoedas.text = "Moedas: ${progresso.moedas}"
        for ((p, c) in cartoes) {
            val nivel = progresso.nivel(p)
            val dano = p.danoNoNivel(nivel)
            c.titulo.text = "${p.nome}  •  Nível $nivel"
            c.detalhe.text = detalheDe(p, dano)

            val custo = progresso.custoProximoNivel(p)
            if (custo <= 0) {
                c.botaoSubir.text = "Nível máximo"
                c.botaoSubir.isEnabled = false
            } else {
                c.botaoSubir.text = "Subir nível • $custo moedas"
                c.botaoSubir.isEnabled = progresso.moedas >= custo
            }

            val escolhido = selecionado == p
            c.botaoEscolher.text = if (escolhido) "SELECIONADO" else "Escolher"
            c.botaoEscolher.isEnabled = !escolhido
            c.raiz.background = fundo(p, escolhido)
        }
    }

    private fun detalheDe(p: Personagem, dano: Float): String {
        val arma = when (p) {
            Personagem.CAVALEIRO -> "espada"
            Personagem.LUTADOR -> "machado de lâmina dupla (cabo meio longo)"
            Personagem.ASSASSINO -> "lança adagas (ataque à distância)"
        }
        return "Arma: $arma\nDano por golpe: ${dano.toInt()}"
    }

    private fun fundo(p: Personagem, escolhido: Boolean): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 18f * dp
        setColor(Color.parseColor(if (escolhido) "#33261A" else "#221A13"))
        setStroke(
            ((if (escolhido) 4f else 1.5f) * dp).toInt(),
            if (escolhido) p.cor else Color.parseColor("#3A2E24")
        )
    }

    private fun jogar() {
        startActivity(
            Intent(this, ColiseuActivity::class.java)
                .putExtra(ColiseuActivity.EXTRA_PERSONAGEM, selecionado.id)
                .putExtra(ColiseuActivity.EXTRA_NIVEL, progresso.nivel(selecionado))
        )
    }

    companion object {
        private const val EXTRA_SELECIONADO = "selecionado"
    }
}

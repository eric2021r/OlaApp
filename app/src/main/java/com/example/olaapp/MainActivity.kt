package com.example.olaapp

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private var cliques = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()

        val titulo = TextView(this).apply {
            text = "Olá, mundo! 👋"
            textSize = 28f
            setTextColor(Color.parseColor("#1B1B1B"))
            gravity = Gravity.CENTER
        }

        val info = TextView(this).apply {
            text = "Android ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"}"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setPadding(0, pad / 3, 0, pad)
        }

        val contador = TextView(this).apply {
            text = "Cliques: 0"
            textSize = 20f
            setTextColor(Color.parseColor("#1B1B1B"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, pad / 2)
        }

        val botao = Button(this).apply {
            text = "Clique aqui"
            setOnClickListener {
                cliques++
                contador.text = "Cliques: $cliques"
            }
        }

        val botaoCalculadora = Button(this).apply {
            text = "Abrir calculadora"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, CalculadoraActivity::class.java))
            }
        }

        val raiz = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.WHITE)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(titulo)
            addView(info)
            addView(contador)
            addView(botao)
            addView(botaoCalculadora)
        }

        setContentView(raiz)
    }
}

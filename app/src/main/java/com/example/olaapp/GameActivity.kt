package com.example.olaapp

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import com.example.olaapp.game.GameView

class GameActivity : Activity() {

    private lateinit var jogo: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("olaapp", MODE_PRIVATE)
        jogo = GameView(this, prefs.getInt(CHAVE_RECORDE, 0)) { novo ->
            prefs.edit().putInt(CHAVE_RECORDE, novo).apply()
        }
        jogo.keepScreenOn = true
        setContentView(jogo)

        // Usa a área do "notch" também, para o jogo ocupar a tela toda.
        if (Build.VERSION.SDK_INT >= 28) {
            val atributos = window.attributes
            atributos.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = atributos
        }
    }

    override fun onResume() {
        super.onResume()
        esconderBarras()
        jogo.retomar()
    }

    override fun onPause() {
        jogo.pausar()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) esconderBarras()
    }

    @Suppress("DEPRECATION")
    private fun esconderBarras() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    companion object {
        private const val CHAVE_RECORDE = "recorde_jogo"
    }
}

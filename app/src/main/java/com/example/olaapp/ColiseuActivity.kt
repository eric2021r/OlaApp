package com.example.olaapp

import android.app.Activity
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.example.olaapp.coliseu.ColiseuHud
import com.example.olaapp.coliseu.ColiseuRenderer
import com.example.olaapp.coliseu.ColiseuSessao
import com.example.olaapp.coliseu.EfeitoColiseu
import com.example.olaapp.coliseu.Personagem

/** "Coliseu do Eric": o jogo 3D. O 3D fica no GLSurfaceView e a interface (vida, botões, menus) por cima. */
class ColiseuActivity : Activity() {

    private lateinit var tela3d: GLSurfaceView
    private lateinit var sessao: ColiseuSessao
    private var personagem = Personagem.CAVALEIRO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Som.iniciar(this)
        personagem = Personagem.porId(intent.getStringExtra(EXTRA_PERSONAGEM))
        val nivel = intent.getIntExtra(EXTRA_NIVEL, 1)
        sessao = ColiseuSessao(personagem, nivel, aoSom = ::somDoColiseu)

        val prefs = getSharedPreferences("olaapp", MODE_PRIVATE)
        val progresso = Progresso(this)

        tela3d = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(ColiseuRenderer(sessao))
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }

        val interface2d = ColiseuHud(
            this, sessao, personagem, nivel,
            prefs.getInt(CHAVE_ROUND, 0), prefs.getInt(CHAVE_PONTOS, 0),
            aoNovoRecorde = { round, pontos ->
                prefs.edit().putInt(CHAVE_ROUND, round).putInt(CHAVE_PONTOS, pontos).apply()
            },
            aoGanharMoedas = { moedas -> progresso.moedas = progresso.moedas + moedas }
        )

        val raiz = FrameLayout(this)
        raiz.addView(tela3d, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        raiz.addView(interface2d, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        raiz.keepScreenOn = true
        setContentView(raiz)

        // usa também a área do "notch", para o jogo ocupar a tela toda
        if (Build.VERSION.SDK_INT >= 28) {
            val atributos = window.attributes
            atributos.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = atributos
        }
    }

    override fun onResume() {
        super.onResume()
        Som.retomar()
        tela3d.onResume()
        esconderBarras()
    }

    override fun onPause() {
        sessao.pausado = true   // se saiu do app no meio da partida, volta pausado
        sessao.stickX = 0f
        sessao.stickY = 0f
        Som.pausar()
        tela3d.onPause()
        super.onPause()
    }

    /** Traduz o aviso do motor no efeito sonoro certo (a arma depende do lutador). */
    private fun somDoColiseu(e: EfeitoColiseu) {
        when (e) {
            EfeitoColiseu.COMECO -> Som.tocar(Som.Efeito.ROUND, 0.9f)
            EfeitoColiseu.ROUND -> Som.tocar(Som.Efeito.VITORIA, 0.9f)
            EfeitoColiseu.ROLAR -> Som.tocar(Som.Efeito.ROLAR, 0.9f, 0.08f)
            EfeitoColiseu.ATAQUE -> when (personagem) {
                Personagem.CAVALEIRO -> Som.tocar(Som.Efeito.ESPADA, 0.9f, 0.08f)
                Personagem.LUTADOR -> Som.tocar(Som.Efeito.MACHADO, 1f, 0.07f)
                Personagem.ASSASSINO -> Som.tocar(Som.Efeito.ADAGA, 0.9f, 0.08f)
            }
            EfeitoColiseu.IMPACTO -> Som.tocar(Som.Efeito.IMPACTO, 1f, 0.10f)
            EfeitoColiseu.IMPACTO_FORTE -> Som.tocar(Som.Efeito.IMPACTO_FORTE, 1f, 0.06f)
            EfeitoColiseu.MORRE -> Som.tocar(Som.Efeito.GOBLIN_MORRE, 0.9f, 0.12f)
            EfeitoColiseu.GRITA -> Som.tocar(Som.Efeito.GOBLIN_GRITA, 0.8f, 0.15f)
            EfeitoColiseu.ROAR -> Som.tocar(Som.Efeito.ROAR, 1f)
            EfeitoColiseu.DOR -> Som.tocar(Som.Efeito.DOR, 1f, 0.08f)
            EfeitoColiseu.DERROTA -> Som.tocar(Som.Efeito.DERROTA, 1f)
        }
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
        const val EXTRA_PERSONAGEM = "personagem"
        const val EXTRA_NIVEL = "nivel"

        private const val CHAVE_ROUND = "coliseu_recorde_round"
        private const val CHAVE_PONTOS = "coliseu_recorde_pontos"
    }
}

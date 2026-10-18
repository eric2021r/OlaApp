package com.example.olaapp.coliseu

import kotlin.math.cos
import kotlin.math.sin

/**
 * Foto do estado do jogo, tirada na thread do desenho e lida pela tela de interface (HUD).
 * Assim a interface nunca mexe nas listas do motor enquanto ele as altera.
 */
class Foto(
    val iniciado: Boolean,
    val fase: Fase,
    val round: Int,
    val hp: Float,
    val pontos: Int,
    val restantes: Int,
    val tFase: Float,
    val tFim: Float,
    val morto: Boolean,
    val abates: Int
) {
    companion object {
        val VAZIA = Foto(false, Fase.INTRO, 1, ColiseuEngine.JOGADOR_HP, 0, 0, 0f, 0f, false, 0)
    }
}

/**
 * Liga o motor do jogo aos controles e à tela. Não usa nada do Android.
 * A thread do desenho chama [atualizar] a cada quadro; a interface só escreve nos campos de entrada.
 */
class ColiseuSessao {

    @Volatile var engine = ColiseuEngine()

    // câmera (a interface gira, o desenho lê)
    @Volatile var camYaw = 0f
    @Volatile var camPitch = Camara.PITCH_INICIAL

    // direção do dedo no joystick: x para a direita, y para a frente (-1..1)
    @Volatile var stickX = 0f
    @Volatile var stickY = 0f

    @Volatile var pausado = false

    @Volatile var foto: Foto = Foto.VAZIA
        private set

    /** Chamado pela thread do desenho. */
    fun atualizar(dt: Float) {
        val e = engine
        if (!e.iniciado) {
            camYaw += dt * 0.22f           // na tela de título a câmera gira devagar em volta da arena
        } else {
            val mundo = mundoDoStick(stickX, stickY, camYaw)
            e.mover(mundo[0], mundo[1])
        }
        e.update(if (pausado) 0f else dt)
        foto = Foto(
            e.iniciado, e.fase, e.round, e.hp, e.pontos, e.goblinsRestantes,
            e.tFase, e.tFim, e.morto, e.abates
        )
    }

    /** Começa uma partida nova (também usado para "jogar de novo"). */
    fun novoJogo() {
        val e = ColiseuEngine()
        e.comecar()
        engine = e
        camYaw = 0f
        camPitch = Camara.PITCH_INICIAL
        pausado = false
    }

    fun girarCamera(dYaw: Float, dPitch: Float) {
        camYaw += dYaw
        camPitch = (camPitch + dPitch).coerceIn(Camara.PITCH_MIN, Camara.PITCH_MAX)
    }

    companion object {
        /**
         * Transforma o joystick (frente / direita da TELA) em direção no mundo.
         * A câmera fica em (sen yaw, cos yaw) em volta do jogador, então "frente" é o oposto disso.
         */
        fun mundoDoStick(sx: Float, sy: Float, yaw: Float): FloatArray {
            val s = sin(yaw)
            val c = cos(yaw)
            return floatArrayOf(c * sx - s * sy, -s * sx - c * sy)
        }
    }
}

package com.example.olaapp.coliseu

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** A luz do jogo: uma luz principal (sol) e uma luz fraca do lado oposto. O desenho do OpenGL usa estes mesmos números. */
object Luz {
    val PRINCIPAL = normalizar(0.45f, 0.80f, 0.40f)
    val FRACA = normalizar(-0.50f, 0.30f, -0.45f)
    const val AMBIENTE = 0.42f
    const val FORCA_PRINCIPAL = 0.70f
    const val FORCA_FRACA = 0.18f

    /** Brilho (0..~1,3) de uma superfície com a normal dada. */
    fun brilho(nx: Float, ny: Float, nz: Float): Float {
        val a = max(0f, nx * PRINCIPAL[0] + ny * PRINCIPAL[1] + nz * PRINCIPAL[2])
        val b = max(0f, nx * FRACA[0] + ny * FRACA[1] + nz * FRACA[2])
        return AMBIENTE + FORCA_PRINCIPAL * a + FORCA_FRACA * b
    }

    private fun normalizar(x: Float, y: Float, z: Float): FloatArray {
        val l = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / l, y / l, z / l)
    }
}

/** Câmera em terceira pessoa: fica atrás do jogador, mais alta, e gira em volta dele. */
object Camara {
    const val DISTANCIA = 7.0f
    const val ALTURA_ALVO = 1.35f
    const val FOV_GRAUS = 55f
    const val PERTO = 0.6f
    const val LONGE = 110f
    const val PITCH_MIN = 0.18f
    const val PITCH_MAX = 1.05f
    const val PITCH_INICIAL = 0.42f

    /** Raio máximo onde a câmera pode ficar (não atravessa a parede da arena). */
    private const val RAIO_MAX = 17.0f

    /** Distância horizontal mínima entre a câmera e o jogador (ela fica acima do muro, que tem 3,8 m). */
    private const val AFASTAR_MIN = 1.0f

    class Pose(val olhoX: Float, val olhoY: Float, val olhoZ: Float, val vp: FloatArray)

    /** yaw = 0 coloca a câmera em +z olhando para -z; aumentar gira a câmera em volta do jogador. */
    fun calcular(alvoX: Float, alvoZ: Float, yaw: Float, pitch: Float, aspecto: Float): Pose {
        val ty = ALTURA_ALVO
        val hx = sin(yaw)   // direção horizontal do jogador para a câmera
        val hz = cos(yaw)
        // deslocamento horizontal desejado; se a câmera passaria do muro, ela anda só até o raio máximo
        // e SOBE (mantendo a distância), em vez de entrar na parede e nas arquibancadas
        var s = DISTANCIA * cos(pitch)
        val b = alvoX * hx + alvoZ * hz
        val c = alvoX * alvoX + alvoZ * alvoZ - RAIO_MAX * RAIO_MAX
        val disc = b * b - c
        val sMax = if (disc >= 0f) -b + sqrt(disc) else 0f
        // nunca fica exatamente em cima do jogador (olhar reto para baixo estraga a matriz da câmera)
        if (s > sMax) s = max(AFASTAR_MIN, sMax)
        val altura = sqrt(max(0f, DISTANCIA * DISTANCIA - s * s))
        val ex = alvoX + hx * s
        val ey = max(1.0f, ty + altura)
        val ez = alvoZ + hz * s
        val view = Math3D.lookAt(ex, ey, ez, alvoX, ty, alvoZ, 0f, 1f, 0f)
        val proj = Math3D.perspective((FOV_GRAUS * PI / 180.0).toFloat(), aspecto, PERTO, LONGE)
        return Pose(ex, ey, ez, Math3D.mul(proj, view))
    }
}

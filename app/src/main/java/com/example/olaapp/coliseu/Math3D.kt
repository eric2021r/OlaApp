package com.example.olaapp.coliseu

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Matemática 3D sem Android (por isso dá para testar no computador).
 * Matrizes 4x4 em FloatArray(16), na ordem "coluna por coluna" (como o OpenGL espera):
 * o elemento da linha r e coluna c fica em [c * 4 + r].
 * Mundo: x para a direita, y para cima, z vindo na direção de quem olha.
 */
object Math3D {

    fun identity() = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    )

    /** a × b (aplica b primeiro e depois a). */
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
                out[c * 4 + r] = s
            }
        }
        return out
    }

    fun translate(x: Float, y: Float, z: Float): FloatArray {
        val m = identity()
        m[12] = x; m[13] = y; m[14] = z
        return m
    }

    fun scale(x: Float, y: Float, z: Float): FloatArray {
        val m = identity()
        m[0] = x; m[5] = y; m[10] = z
        return m
    }

    fun rotX(a: Float): FloatArray {
        val c = cos(a); val s = sin(a)
        val m = identity()
        m[5] = c; m[6] = s
        m[9] = -s; m[10] = c
        return m
    }

    /** Gira em volta do eixo vertical: o eixo +z vira (sin a, 0, cos a). */
    fun rotY(a: Float): FloatArray {
        val c = cos(a); val s = sin(a)
        val m = identity()
        m[0] = c; m[2] = -s
        m[8] = s; m[10] = c
        return m
    }

    fun rotZ(a: Float): FloatArray {
        val c = cos(a); val s = sin(a)
        val m = identity()
        m[0] = c; m[1] = s
        m[4] = -s; m[5] = c
        return m
    }

    fun perspective(fovyRad: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(fovyRad / 2f)
        val m = FloatArray(16)
        m[0] = f / aspect
        m[5] = f
        m[10] = (far + near) / (near - far)
        m[11] = -1f
        m[14] = 2f * far * near / (near - far)
        return m
    }

    fun lookAt(
        ex: Float, ey: Float, ez: Float,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float
    ): FloatArray {
        var fx = cx - ex; var fy = cy - ey; var fz = cz - ez
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        // s = f × up
        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        var sl = sqrt(sx * sx + sy * sy + sz * sz)
        if (sl < 1e-4f) {
            // olhando exatamente para cima ou para baixo: "cima" e a direção do olhar são paralelos,
            // então usa -z como referência (o que está à frente, em -z, continua indo para cima na tela)
            sx = -fy
            sy = fx
            sz = 0f
            sl = sqrt(sx * sx + sy * sy + sz * sz)
            if (sl < 1e-6f) { sx = 1f; sy = 0f; sz = 0f; sl = 1f }
        }
        sx /= sl; sy /= sl; sz /= sl
        // u = s × f
        val tx = sy * fz - sz * fy
        val ty = sz * fx - sx * fz
        val tz = sx * fy - sy * fx
        val m = identity()
        m[0] = sx; m[4] = sy; m[8] = sz
        m[1] = tx; m[5] = ty; m[9] = tz
        m[2] = -fx; m[6] = -fy; m[10] = -fz
        m[12] = -(sx * ex + sy * ey + sz * ez)
        m[13] = -(tx * ex + ty * ey + tz * ez)
        m[14] = fx * ex + fy * ey + fz * ez
        return m
    }

    /** Transforma um ponto (x, y, z, 1) pela matriz e devolve (x, y, z, w). */
    fun transform(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray = FloatArray(4)): FloatArray {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14]
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15]
        return out
    }

    /**
     * Matriz para girar as normais (3x3, também por colunas): é a inversa transposta
     * da parte 3x3 da matriz do modelo. Sem isso, objetos esticados ficam com a luz errada.
     */
    fun normalMatrix(m: FloatArray): FloatArray {
        fun a(r: Int, c: Int) = m[c * 4 + r]
        // cofatores
        fun cof(r: Int, c: Int): Float {
            val r1 = (r + 1) % 3; val r2 = (r + 2) % 3
            val c1 = (c + 1) % 3; val c2 = (c + 2) % 3
            return a(r1, c1) * a(r2, c2) - a(r1, c2) * a(r2, c1)
        }
        val det = a(0, 0) * cof(0, 0) + a(0, 1) * cof(0, 1) + a(0, 2) * cof(0, 2)
        val out = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        if (abs(det) < 1e-9f) return out
        val inv = 1f / det
        for (r in 0 until 3) for (c in 0 until 3) out[c * 3 + r] = cof(r, c) * inv
        return out
    }
}

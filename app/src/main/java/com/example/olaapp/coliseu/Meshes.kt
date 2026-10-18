package com.example.olaapp.coliseu

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Uma malha de triângulos. Cada vértice tem 9 números:
 * posição (x, y, z), normal (x, y, z) e cor (r, g, b). Sem índices: 3 vértices por triângulo.
 * Todos os triângulos ficam em sentido anti-horário visto de fora.
 */
class Mesh(val data: FloatArray) {
    val vertexCount: Int get() = data.size / FLOATS

    companion object {
        const val FLOATS = 9
    }
}

/** Uma peça para desenhar: a malha, onde ela fica no mundo e a cor que multiplica a cor da malha. */
class DrawItem(
    val mesh: Mesh,
    val model: FloatArray,
    val r: Float, val g: Float, val b: Float
)

class MeshBuilder {
    private var buf = FloatArray(4096)
    private var n = 0

    private fun put(v: Float) {
        if (n == buf.size) buf = buf.copyOf(buf.size * 2)
        buf[n++] = v
    }

    private fun vertex(
        x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float
    ) {
        put(x); put(y); put(z); put(nx); put(ny); put(nz); put(r); put(g); put(b)
    }

    /** Triângulo com a normal calculada a partir da ordem dos pontos (anti-horário = para fora). */
    fun tri(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l < 1e-9f) return // triângulo sem área
        nx /= l; ny /= l; nz /= l
        vertex(ax, ay, az, nx, ny, nz, r, g, b)
        vertex(bx, by, bz, nx, ny, nz, r, g, b)
        vertex(cx, cy, cz, nx, ny, nz, r, g, b)
    }

    fun quad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f
    ) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, r, g, b)
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, r, g, b)
    }

    /** Copia uma malha para cá, já movida/girada/esticada por [model] e pintada com a cor dada. */
    fun append(mesh: Mesh, model: FloatArray, r: Float, g: Float, b: Float) {
        val nm = Math3D.normalMatrix(model)
        val d = mesh.data
        val p = FloatArray(4)
        var i = 0
        while (i < d.size) {
            Math3D.transform(model, d[i], d[i + 1], d[i + 2], p)
            val nx = nm[0] * d[i + 3] + nm[3] * d[i + 4] + nm[6] * d[i + 5]
            val ny = nm[1] * d[i + 3] + nm[4] * d[i + 4] + nm[7] * d[i + 5]
            val nz = nm[2] * d[i + 3] + nm[5] * d[i + 4] + nm[8] * d[i + 5]
            val l = sqrt(nx * nx + ny * ny + nz * nz).let { if (it < 1e-9f) 1f else it }
            vertex(p[0], p[1], p[2], nx / l, ny / l, nz / l, d[i + 6] * r, d[i + 7] * g, d[i + 8] * b)
            i += Mesh.FLOATS
        }
    }

    fun build() = Mesh(buf.copyOf(n))
}

/** As formas básicas, todas com tamanho 1 e centradas na origem. São esticadas e pintadas na hora de desenhar. */
object Meshes {

    /** Cubo de lado 1. */
    val CUBE: Mesh by lazy { cube() }

    /** Cilindro de diâmetro 1 e altura 1 (eixo vertical). */
    val CYLINDER: Mesh by lazy { cylinder(10) }

    /** Cone de diâmetro 1 e altura 1, com a ponta para cima. */
    val CONE: Mesh by lazy { cone(8) }

    /** Pirâmide de base quadrada. */
    val PYRAMID: Mesh by lazy { cone(4) }

    /** Esfera de diâmetro 1, com poucas faces (visual "low-poly"). */
    val SPHERE: Mesh by lazy { sphere(5, 8) }

    fun cube(): Mesh {
        val b = MeshBuilder()
        // cada face: normal n e dois eixos u, v com u × v = n
        val faces = arrayOf(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
            floatArrayOf(0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f),
            floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, -1f, 0f, 1f, 0f, 1f, 0f, 0f)
        )
        for (f in faces) {
            val cx = f[0] * 0.5f; val cy = f[1] * 0.5f; val cz = f[2] * 0.5f
            val ux = f[3] * 0.5f; val uy = f[4] * 0.5f; val uz = f[5] * 0.5f
            val vx = f[6] * 0.5f; val vy = f[7] * 0.5f; val vz = f[8] * 0.5f
            b.quad(
                cx - ux - vx, cy - uy - vy, cz - uz - vz,
                cx + ux - vx, cy + uy - vy, cz + uz - vz,
                cx + ux + vx, cy + uy + vy, cz + uz + vz,
                cx - ux + vx, cy - uy + vy, cz - uz + vz
            )
        }
        return b.build()
    }

    fun cylinder(seg: Int): Mesh {
        val b = MeshBuilder()
        for (i in 0 until seg) {
            val a0 = (2 * PI * i / seg).toFloat()
            val a1 = (2 * PI * (i + 1) / seg).toFloat()
            val x0 = 0.5f * cos(a0); val z0 = 0.5f * sin(a0)
            val x1 = 0.5f * cos(a1); val z1 = 0.5f * sin(a1)
            b.quad(x0, -0.5f, z0, x0, 0.5f, z0, x1, 0.5f, z1, x1, -0.5f, z1) // lado
            b.tri(0f, 0.5f, 0f, x1, 0.5f, z1, x0, 0.5f, z0)                   // tampa de cima
            b.tri(0f, -0.5f, 0f, x0, -0.5f, z0, x1, -0.5f, z1)                // tampa de baixo
        }
        return b.build()
    }

    fun cone(seg: Int): Mesh {
        val b = MeshBuilder()
        for (i in 0 until seg) {
            val a0 = (2 * PI * i / seg).toFloat()
            val a1 = (2 * PI * (i + 1) / seg).toFloat()
            val x0 = 0.5f * cos(a0); val z0 = 0.5f * sin(a0)
            val x1 = 0.5f * cos(a1); val z1 = 0.5f * sin(a1)
            b.tri(x0, -0.5f, z0, 0f, 0.5f, 0f, x1, -0.5f, z1)    // lado
            b.tri(0f, -0.5f, 0f, x0, -0.5f, z0, x1, -0.5f, z1)   // base
        }
        return b.build()
    }

    fun sphere(lat: Int, lon: Int): Mesh {
        val b = MeshBuilder()
        fun px(i: Int, j: Int) = 0.5f * sin(PI * i / lat).toFloat() * cos(2 * PI * j / lon).toFloat()
        fun py(i: Int) = 0.5f * cos(PI * i / lat).toFloat()
        fun pz(i: Int, j: Int) = 0.5f * sin(PI * i / lat).toFloat() * sin(2 * PI * j / lon).toFloat()
        for (i in 0 until lat) {
            for (j in 0 until lon) {
                b.quad(
                    px(i, j), py(i), pz(i, j),
                    px(i, j + 1), py(i), pz(i, j + 1),
                    px(i + 1, j + 1), py(i + 1), pz(i + 1, j + 1),
                    px(i + 1, j), py(i + 1), pz(i + 1, j)
                )
            }
        }
        return b.build()
    }
}

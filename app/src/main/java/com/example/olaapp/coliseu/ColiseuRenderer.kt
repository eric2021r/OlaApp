package com.example.olaapp.coliseu

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Desenha a cena com OpenGL ES 2.0. Os números da luz vêm do objeto [Luz], os mesmos usados
 * no teste do computador, então o visual no celular é o mesmo das imagens de conferência.
 * Roda na thread do GLSurfaceView e também avança o jogo (sessao.atualizar).
 */
class ColiseuRenderer(private val sessao: ColiseuSessao) : GLSurfaceView.Renderer {

    private val cena = SceneBuilder()
    private val itens = ArrayList<DrawItem>()
    private val buffers = HashMap<Mesh, Int>()

    private var programa = 0
    private var aPos = 0
    private var aNormal = 0
    private var aColor = 0
    private var uVP = 0
    private var uModel = 0
    private var uNormal = 0
    private var uTint = 0
    private var uEye = 0

    private var aspecto = 16f / 9f
    private var ultimo = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // o contexto foi (re)criado: os buffers antigos não existem mais
        buffers.clear()
        programa = criarPrograma(VERTEX, FRAGMENT)
        aPos = GLES20.glGetAttribLocation(programa, "aPos")
        aNormal = GLES20.glGetAttribLocation(programa, "aNormal")
        aColor = GLES20.glGetAttribLocation(programa, "aColor")
        uVP = GLES20.glGetUniformLocation(programa, "uVP")
        uModel = GLES20.glGetUniformLocation(programa, "uModel")
        uNormal = GLES20.glGetUniformLocation(programa, "uNormal")
        uTint = GLES20.glGetUniformLocation(programa, "uTint")
        uEye = GLES20.glGetUniformLocation(programa, "uEye")
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glClearColor(0.45f, 0.68f, 0.92f, 1f)
        ultimo = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspecto = width.toFloat() / maxOf(1, height).toFloat()
    }

    override fun onDrawFrame(gl: GL10?) {
        val agora = System.nanoTime()
        val dt = if (ultimo == 0L) 0f else ((agora - ultimo) / 1_000_000_000f).coerceIn(0f, 0.1f)
        ultimo = agora

        sessao.atualizar(dt)
        val e = sessao.engine
        cena.montar(e, itens)
        val pose = Camara.calcular(e.px, e.pz, sessao.camYaw, sessao.camPitch, aspecto)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(programa)
        GLES20.glUniformMatrix4fv(uVP, 1, false, pose.vp, 0)
        GLES20.glUniform3f(uEye, pose.olhoX, pose.olhoY, pose.olhoZ)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glEnableVertexAttribArray(aColor)
        for (item in itens) desenhar(item)
    }

    private fun desenhar(item: DrawItem) {
        val vbo = buffers.getOrPut(item.mesh) { enviar(item.mesh) }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        val passo = Mesh.FLOATS * 4
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, passo, 0)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, passo, 12)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, passo, 24)
        GLES20.glUniformMatrix4fv(uModel, 1, false, item.model, 0)
        GLES20.glUniformMatrix3fv(uNormal, 1, false, Math3D.normalMatrix(item.model), 0)
        GLES20.glUniform3f(uTint, item.r, item.g, item.b)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, item.mesh.vertexCount)
    }

    /** Copia os vértices da malha para a memória da placa de vídeo (uma vez só por malha). */
    private fun enviar(mesh: Mesh): Int {
        val bytes = ByteBuffer.allocateDirect(mesh.data.size * 4).order(ByteOrder.nativeOrder())
        val fb = bytes.asFloatBuffer()
        fb.put(mesh.data)
        fb.position(0)
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mesh.data.size * 4, fb, GLES20.GL_STATIC_DRAW)
        return ids[0]
    }

    private fun criarPrograma(vs: String, fs: String): Int {
        val v = compilar(GLES20.GL_VERTEX_SHADER, vs)
        val f = compilar(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Erro ao ligar o programa de desenho: " + GLES20.glGetProgramInfoLog(p))
        return p
    }

    private fun compilar(tipo: Int, fonte: String): Int {
        val s = GLES20.glCreateShader(tipo)
        GLES20.glShaderSource(s, fonte)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Erro no shader: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    companion object {
        private fun n(v: Float) = String.format(Locale.US, "%.5f", v)
        private fun vec(a: FloatArray) = "vec3(${n(a[0])}, ${n(a[1])}, ${n(a[2])})"

        private val VERTEX = """
            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            uniform mat4 uVP;
            uniform mat4 uModel;
            uniform mat3 uNormal;
            uniform vec3 uTint;
            uniform vec3 uEye;
            varying vec3 vCor;
            void main() {
                vec4 w = uModel * vec4(aPos, 1.0);
                gl_Position = uVP * w;
                vec3 nn = normalize(uNormal * aNormal);
                float a = max(dot(nn, ${vec(Luz.PRINCIPAL)}), 0.0);
                float b = max(dot(nn, ${vec(Luz.FRACA)}), 0.0);
                float luz = ${n(Luz.AMBIENTE)} + ${n(Luz.FORCA_PRINCIPAL)} * a + ${n(Luz.FORCA_FRACA)} * b;
                vec3 cor = aColor * uTint * luz;
                float neblina = clamp((distance(w.xyz, uEye) - 45.0) / 50.0, 0.0, 1.0);
                vCor = mix(cor, vec3(0.45, 0.68, 0.92), neblina);
            }
        """.trimIndent()

        private val FRAGMENT = """
            precision mediump float;
            varying vec3 vCor;
            void main() {
                gl_FragColor = vec4(vCor, 1.0);
            }
        """.trimIndent()
    }
}

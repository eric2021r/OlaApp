package com.example.olaapp.game

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Desenha o jogo (tudo em pixel art, ampliado sem suavizar) e recebe os toques.
 * A lógica do jogo fica no GameEngine; aqui só tem tela, controles e menus.
 */
class GameView(
    context: Context,
    private var recorde: Int,
    private val aoMudarRecorde: (Int) -> Unit
) : View(context), Choreographer.FrameCallback {

    private enum class Estado { INICIO, JOGANDO, PAUSADO, FIM }

    private class Botao {
        var cx = 0f
        var cy = 0f
        var r = 0f
        fun contem(x: Float, y: Float) = hypot(x - cx, y - cy) <= r * 1.3f
    }

    private val arte = PixelArt()
    private val dp = resources.displayMetrics.density

    private var estado = Estado.INICIO
    private var engine: GameEngine? = null
    private var escala = 1f              // pixels da tela por pixel do desenho
    private var larguraLogica = 256f
    private var ativo = false
    private var ultimoFrame = 0L
    private var tempoTela = 0f
    private var tempoFim = 0f
    private var novoRecorde = false

    private val btnEsq = Botao()
    private val btnDir = Botao()
    private val btnPulo = Botao()
    private val btnAtaque = Botao()

    // ---- tintas (criadas uma vez só)
    private val pPixel = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
    private val pFlash = Paint(pPixel).apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP) }
    private val pAlfa = Paint(pPixel)
    private val pCor = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val pLinha = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val pTexto = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val caminho = Path()
    private val dst = Rect()

    private val coresCeu = intArrayOf(
        Color.parseColor("#5BB8F0"), Color.parseColor("#6EC3F4"), Color.parseColor("#82CEF7"),
        Color.parseColor("#99D8F9"), Color.parseColor("#B2E2FB"), Color.parseColor("#CBEBFC")
    )
    private val corMontanha = Color.parseColor("#8FA9C8")
    private val corColina = Color.parseColor("#5DAE5A")
    private val corNuvem = Color.argb(225, 255, 255, 255)
    private val corSol = Color.parseColor("#FFE680")

    // ============================================================ ciclo de vida

    fun retomar() {
        if (ativo) return
        ativo = true
        ultimoFrame = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pausar() {
        ativo = false
        Choreographer.getInstance().removeFrameCallback(this)
        if (estado == Estado.JOGANDO) {
            estado = Estado.PAUSADO
            soltarControles()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        escala = h / GameEngine.VIEW_H
        larguraLogica = w / escala

        val r = 38 * dp
        val m = 20 * dp
        val y = h - m - r
        btnEsq.apply { cx = m + r; cy = y; this.r = r }
        btnDir.apply { cx = m + 3 * r + 14 * dp; cy = y; this.r = r }
        btnAtaque.apply { cx = w - m - r; cy = y; this.r = r }
        btnPulo.apply { cx = w - m - 3 * r - 14 * dp; cy = y; this.r = r }

        novoEngine()
    }

    private fun novoEngine() {
        engine = GameEngine(System.nanoTime(), larguraLogica)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!ativo) return
        val dt = if (ultimoFrame == 0L) 0f
        else ((frameTimeNanos - ultimoFrame) / 1_000_000_000f).coerceIn(0f, 0.1f)
        ultimoFrame = frameTimeNanos
        tempoTela += dt

        val e = engine
        if (e != null) {
            when (estado) {
                Estado.JOGANDO -> {
                    e.update(dt)
                    if (e.gameOver) terminar(e)
                }
                Estado.FIM -> {
                    e.update(dt) // só para as partículas terminarem
                    tempoFim += dt
                }
                else -> Unit
            }
        }
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun terminar(e: GameEngine) {
        estado = Estado.FIM
        tempoFim = 0f
        soltarControles()
        novoRecorde = e.score > recorde
        if (novoRecorde) {
            recorde = e.score
            aoMudarRecorde(recorde)
        }
    }

    private fun soltarControles() {
        engine?.let { it.inLeft = false; it.inRight = false; it.jumpHeld = false }
    }

    // ============================================================ toques

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val toqueNovo = ev.actionMasked == MotionEvent.ACTION_DOWN
        when (estado) {
            Estado.INICIO -> if (toqueNovo) { novoEngine(); estado = Estado.JOGANDO }
            Estado.PAUSADO -> if (toqueNovo) estado = Estado.JOGANDO
            Estado.FIM -> if (toqueNovo && tempoFim > 0.8f) { novoEngine(); estado = Estado.JOGANDO }
            Estado.JOGANDO -> controles(ev)
        }
        return true
    }

    private fun controles(ev: MotionEvent) {
        val e = engine ?: return
        val acao = ev.actionMasked
        var esq = false
        var dir = false
        var pulo = false
        var pulouAgora = false
        val terminando = acao == MotionEvent.ACTION_UP || acao == MotionEvent.ACTION_CANCEL
        if (!terminando) {
            for (i in 0 until ev.pointerCount) {
                if (acao == MotionEvent.ACTION_POINTER_UP && i == ev.actionIndex) continue
                val x = ev.getX(i)
                val y = ev.getY(i)
                val novo = (acao == MotionEvent.ACTION_DOWN || acao == MotionEvent.ACTION_POINTER_DOWN) &&
                        i == ev.actionIndex
                if (btnEsq.contem(x, y)) esq = true else if (btnDir.contem(x, y)) dir = true
                if (btnPulo.contem(x, y)) {
                    pulo = true
                    if (novo) pulouAgora = true
                }
                if (novo && btnAtaque.contem(x, y)) e.pressAttack()
            }
        }
        e.inLeft = esq
        e.inRight = dir
        e.jumpHeld = pulo
        if (pulouAgora) e.pressJump()
    }

    // ============================================================ desenho

    override fun onDraw(canvas: Canvas) {
        val e = engine ?: return
        val cam = e.camX

        desenharFundo(canvas, cam)
        desenharMundo(canvas, e, cam)
        desenharHud(canvas, e)
        if (estado == Estado.JOGANDO || estado == Estado.PAUSADO) desenharControles(canvas, e)

        when (estado) {
            Estado.INICIO -> telaInicio(canvas)
            Estado.PAUSADO -> telaPausa(canvas)
            Estado.FIM -> telaFim(canvas, e)
            Estado.JOGANDO -> Unit
        }
    }

    // ---- ferramentas de desenho

    private fun sprite(
        c: Canvas, bmp: Bitmap, x: Float, y: Float, cam: Float,
        espelhar: Boolean = false, tinta: Paint = pPixel
    ) {
        val l = ((x - cam) * escala).roundToInt()
        val t = (y * escala).roundToInt()
        val r = ((x - cam + bmp.width) * escala).roundToInt()
        val b = ((y + bmp.height) * escala).roundToInt()
        dst.set(l, t, r, b)
        if (espelhar) {
            c.save()
            c.scale(-1f, 1f, (l + r) / 2f, 0f)
            c.drawBitmap(bmp, null, dst, tinta)
            c.restore()
        } else {
            c.drawBitmap(bmp, null, dst, tinta)
        }
    }

    /** Retângulo em coordenadas do desenho, com as duas bordas arredondadas (sem frestas). */
    private fun retangulo(c: Canvas, x: Float, y: Float, w: Float, h: Float, cor: Int) {
        pCor.color = cor
        c.drawRect(
            (x * escala).roundToInt().toFloat(), (y * escala).roundToInt().toFloat(),
            ((x + w) * escala).roundToInt().toFloat(), ((y + h) * escala).roundToInt().toFloat(), pCor
        )
    }

    private fun texto(
        c: Canvas, s: String, x: Float, y: Float, tamanho: Float,
        alinhamento: Paint.Align, cor: Int = Color.WHITE
    ) {
        pTexto.textSize = tamanho
        pTexto.textAlign = alinhamento
        val sombra = max(2f, tamanho * 0.07f)
        pTexto.color = Color.argb(170, 0, 0, 0)
        c.drawText(s, x + sombra, y + sombra, pTexto)
        pTexto.color = cor
        c.drawText(s, x, y, pTexto)
    }

    // ---- fundo em camadas (parallax)

    private fun desenharFundo(c: Canvas, cam: Float) {
        val faixa = height / coresCeu.size.toFloat()
        for (i in coresCeu.indices) {
            pCor.color = coresCeu[i]
            c.drawRect(0f, i * faixa, width.toFloat(), (i + 1) * faixa + 1f, pCor)
        }

        retangulo(c, larguraLogica - 52f, 14f, 18f, 18f, corSol)

        // nuvens
        val volta = larguraLogica + 80f
        for (k in 0 until 6) {
            val base = k * 97f + (k * 37 % 41)
            val x = ((base - cam * 0.12f) % volta + volta) % volta - 40f
            val y = 10f + (k * 23 % 38)
            retangulo(c, x, y + 4f, 30f, 6f, corNuvem)
            retangulo(c, x + 5f, y, 18f, 5f, corNuvem)
        }

        camada(c, cam * 0.25f, corMontanha, 118f) { wx ->
            34f + 22f * abs(sin(wx * 0.011f)) + 10f * sin(wx * 0.029f + 1.3f)
        }
        camada(c, cam * 0.5f, corColina, 124f) { wx ->
            24f + 12f * sin(wx * 0.021f + 0.5f) + 6f * sin(wx * 0.057f)
        }
    }

    private inline fun camada(c: Canvas, deslocamento: Float, cor: Int, base: Float, altura: (Float) -> Float) {
        val passo = 4
        val primeira = floor(deslocamento / passo).toInt()
        val n = (larguraLogica / passo).toInt() + 2
        for (i in primeira until primeira + n) {
            val wx = (i * passo).toFloat()
            val h = altura(wx).toInt().toFloat()
            retangulo(c, wx - deslocamento, base - h, passo.toFloat(), h + 40f, cor)
        }
    }

    // ---- mundo

    private fun desenharMundo(c: Canvas, e: GameEngine, cam: Float) {
        val t = GameEngine.TILE
        val primeira = max(0, floor(cam / t).toInt())
        val ultima = floor((cam + larguraLogica) / t).toInt()
        for (col in primeira..ultima) {
            val g = e.groundRow(col)
            if (g < 0) continue
            sprite(c, arte.grass, col * t, g * t, cam)
            for (linha in g + 1 until GameEngine.ROWS) sprite(c, arte.dirt, col * t, linha * t, cam)
        }

        for (p in e.platforms) {
            if (p.x + p.w < cam || p.x > cam + larguraLogica) continue
            for (i in 0 until (p.w / t).toInt()) sprite(c, arte.platform, p.x + i * t, p.y, cam)
        }

        for (p in e.pickups) {
            sprite(c, arte.heart, p.x, p.y + sin(e.time * 4f) * 2f, cam)
        }

        for (m in e.monsters) {
            if (m.x + 16f < cam || m.x > cam + larguraLogica) continue
            val bmp = when (m.type) {
                MonsterType.SLIME -> if ((m.animT * 3f).toInt() % 2 == 0) arte.slimeA else arte.slimeB
                MonsterType.SKELETON ->
                    if (abs(m.vx) > 5f && (m.animT * 5f).toInt() % 2 == 0) arte.skelStep else arte.skelIdle
                MonsterType.BAT -> if ((m.animT * 9f).toInt() % 2 == 0) arte.batUp else arte.batDown
            }
            sprite(c, bmp, m.x, m.y, cam, espelhar = m.facing < 0, tinta = if (m.hitFlash > 0f) pFlash else pPixel)
        }

        desenharHeroi(c, e, cam)

        for (p in e.particles) {
            retangulo(c, p.x - cam, p.y, 2f, 2f, p.color)
        }
    }

    private fun desenharHeroi(c: Canvas, e: GameEngine, cam: Float) {
        val h = e.hero
        val piscando = e.invuln > 0f && (e.invuln * 16f).toInt() % 2 == 0
        if (piscando) return

        val frame = when {
            !h.onGround -> arte.heroJump
            abs(h.vx) > 12f -> if ((e.time * 10f).toInt() % 2 == 0) arte.heroStepA else arte.heroStepB
            else -> arte.heroIdle
        }
        sprite(c, frame, h.x, h.y, cam, espelhar = h.facing < 0)

        // efeito do golpe
        val t = e.attackT
        if (t >= GameEngine.HIT_START - 0.02f) {
            val progresso = ((t - 0.06f) / 0.18f).coerceIn(0f, 1f)
            pAlfa.alpha = (255 * (1f - progresso)).toInt()
            val x = if (h.facing > 0) h.x + 8f else h.x - 8f
            sprite(c, arte.slash, x, h.y - 1f, cam, espelhar = h.facing < 0, tinta = pAlfa)
        }

        // espada: gira em volta da mão
        val angulo = when {
            t < 0f -> 28f
            t < 0.06f -> lerp(28f, -70f, t / 0.06f)
            t < 0.20f -> lerp(-70f, 120f, (t - 0.06f) / 0.14f)
            else -> lerp(120f, 28f, ((t - 0.20f) / 0.08f).coerceIn(0f, 1f))
        }
        val maoX = h.x + if (h.facing > 0) 12f else 4f
        val maoY = h.y + 10f
        c.save()
        c.translate((maoX - cam) * escala, maoY * escala)
        c.scale(h.facing.toFloat(), 1f)
        c.rotate(angulo)
        dst.set(
            (-3f * escala).roundToInt(), (-14.5f * escala).roundToInt(),
            (3f * escala).roundToInt(), (2.5f * escala).roundToInt()
        )
        c.drawBitmap(arte.sword, null, dst, pPixel)
        c.restore()
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    // ---- interface

    private fun desenharHud(c: Canvas, e: GameEngine) {
        for (i in 0 until GameEngine.MAX_HP) {
            sprite(c, if (i < e.hp) arte.heart else arte.heartEmpty, 6f + i * 11f, 6f, 0f)
        }
        val direita = width - 8f * escala
        texto(c, "Pontos ${e.score}", direita, 11f * escala, 6.5f * escala, Paint.Align.RIGHT)
        texto(c, "Recorde $recorde", direita, 18.5f * escala, 4.5f * escala, Paint.Align.RIGHT, Color.parseColor("#FFE680"))
    }

    private fun desenharControles(c: Canvas, e: GameEngine) {
        circulo(c, btnEsq, e.inLeft)
        circulo(c, btnDir, e.inRight)
        circulo(c, btnPulo, e.jumpHeld)
        circulo(c, btnAtaque, e.attackT >= 0f)

        triangulo(c, btnEsq.cx, btnEsq.cy, btnEsq.r * 0.55f, 180f)
        triangulo(c, btnDir.cx, btnDir.cy, btnDir.r * 0.55f, 0f)
        triangulo(c, btnPulo.cx, btnPulo.cy, btnPulo.r * 0.55f, 270f)

        // ícone de espada no botão de ataque
        val s = btnAtaque.r * 1.5f / 17f
        c.save()
        c.translate(btnAtaque.cx, btnAtaque.cy)
        c.rotate(45f)
        dst.set((-3f * s).roundToInt(), (-8.5f * s).roundToInt(), (3f * s).roundToInt(), (8.5f * s).roundToInt())
        c.drawBitmap(arte.sword, null, dst, pPixel)
        c.restore()
    }

    private fun circulo(c: Canvas, b: Botao, pressionado: Boolean) {
        pCor.color = Color.argb(if (pressionado) 120 else 60, 255, 255, 255)
        c.drawCircle(b.cx, b.cy, b.r, pCor)
        pLinha.color = Color.argb(150, 255, 255, 255)
        pLinha.strokeWidth = 2f * dp
        c.drawCircle(b.cx, b.cy, b.r, pLinha)
    }

    /** Triângulo apontando para a direita (0°), esquerda (180°) ou cima (270°). */
    private fun triangulo(c: Canvas, cx: Float, cy: Float, s: Float, graus: Float) {
        c.save()
        c.translate(cx, cy)
        c.rotate(graus)
        caminho.reset()
        caminho.moveTo(s, 0f)
        caminho.lineTo(-s * 0.7f, -s * 0.9f)
        caminho.lineTo(-s * 0.7f, s * 0.9f)
        caminho.close()
        pCor.color = Color.argb(200, 255, 255, 255)
        c.drawPath(caminho, pCor)
        c.restore()
        pCor.style = Paint.Style.FILL
    }

    // ---- telas de menu

    private fun escurecer(c: Canvas) {
        pCor.color = Color.argb(140, 0, 0, 0)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pCor)
    }

    private fun piscar() = (tempoTela * 2f).toInt() % 2 == 0

    private fun telaInicio(c: Canvas) {
        escurecer(c)
        val cx = width / 2f
        texto(c, "AVENTURA", cx, height * 0.30f, 14f * escala, Paint.Align.CENTER)
        texto(c, "DA ESPADA", cx, height * 0.30f + 14f * escala, 14f * escala, Paint.Align.CENTER, Color.parseColor("#FFE680"))
        if (piscar()) texto(c, "Toque para começar", cx, height * 0.68f, 6.5f * escala, Paint.Align.CENTER)
        texto(c, "Recorde: $recorde", cx, height * 0.78f, 5f * escala, Paint.Align.CENTER, Color.parseColor("#FFE680"))
        texto(c, "Use os botões: andar, pular e atacar", cx, height * 0.88f, 4.2f * escala, Paint.Align.CENTER, Color.parseColor("#DDDDDD"))
    }

    private fun telaPausa(c: Canvas) {
        escurecer(c)
        val cx = width / 2f
        texto(c, "PAUSADO", cx, height * 0.45f, 12f * escala, Paint.Align.CENTER)
        if (piscar()) texto(c, "Toque para continuar", cx, height * 0.62f, 6f * escala, Paint.Align.CENTER)
    }

    private fun telaFim(c: Canvas, e: GameEngine) {
        escurecer(c)
        val cx = width / 2f
        texto(c, "FIM DE JOGO", cx, height * 0.27f, 12f * escala, Paint.Align.CENTER, Color.parseColor("#FF6B5E"))
        texto(c, "Pontos: ${e.score}", cx, height * 0.43f, 8f * escala, Paint.Align.CENTER)
        texto(c, "${e.distance} m percorridos  -  ${e.kills} monstros", cx, height * 0.53f, 4.6f * escala, Paint.Align.CENTER, Color.parseColor("#DDDDDD"))
        if (novoRecorde) {
            texto(c, "NOVO RECORDE!", cx, height * 0.65f, 7f * escala, Paint.Align.CENTER, Color.parseColor("#FFE680"))
        } else {
            texto(c, "Recorde: $recorde", cx, height * 0.65f, 5.5f * escala, Paint.Align.CENTER, Color.parseColor("#FFE680"))
        }
        if (tempoFim > 0.8f && piscar()) {
            texto(c, "Toque para jogar de novo", cx, height * 0.82f, 6f * escala, Paint.Align.CENTER)
        }
    }
}

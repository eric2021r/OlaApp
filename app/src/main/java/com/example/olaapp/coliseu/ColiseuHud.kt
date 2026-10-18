package com.example.olaapp.coliseu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Camada 2D por cima do 3D: vida, round, pontos, joystick, botões e menus. Também recebe os toques.
 * Só lê a "foto" do jogo (nunca as listas do motor) para não atrapalhar a thread do desenho.
 */
class ColiseuHud(
    context: Context,
    private val sessao: ColiseuSessao,
    private var recRound: Int,
    private var recPontos: Int,
    private val aoNovoRecorde: (Int, Int) -> Unit
) : View(context) {

    private class Botao {
        var cx = 0f
        var cy = 0f
        var r = 0f
        fun contem(x: Float, y: Float) = hypot(x - cx, y - cy) <= r * 1.25f
    }

    private val dp = resources.displayMetrics.density
    private val btnAtaque = Botao()
    private val btnRolar = Botao()
    private val btnPausa = Botao()

    private val pCor = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLinha = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pTexto = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
    private val caminho = Path()
    private val retang = RectF()

    // toques
    private var idStick = -1
    private var baseX = 0f
    private var baseY = 0f
    private var knobX = 0f
    private var knobY = 0f
    private var idCamera = -1
    private var camX = 0f
    private var camY = 0f
    private var idAtaque = -1
    private var idRolar = -1

    private var registrado = false
    private var novoRecorde = false
    private var tempoTela = 0f
    private var ultimo = 0L

    private val raioStick get() = 56f * dp

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val m = 26f * dp
        btnAtaque.apply { r = 46f * dp; cx = w - m - r; cy = h - m - r }
        btnRolar.apply { r = 34f * dp; cx = btnAtaque.cx - btnAtaque.r - r - 14f * dp; cy = h - m - r - 4f * dp }
        btnPausa.apply { r = 17f * dp; cx = w - 14f * dp - r; cy = 14f * dp + r }
    }

    // ============================================================ toques

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val foto = sessao.foto
        val acao = ev.actionMasked
        val novo = acao == MotionEvent.ACTION_DOWN
        when {
            !foto.iniciado -> { if (novo) sessao.novoJogo() }
            foto.fase == Fase.FIM -> { if (novo && foto.tFim > 1.5f) sessao.novoJogo() }
            sessao.pausado -> { if (novo) sessao.pausado = false }
            else -> controles(ev, acao)
        }
        return true
    }

    private fun controles(ev: MotionEvent, acao: Int) {
        when (acao) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                tocou(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) moveu(ev.getPointerId(i), ev.getX(i), ev.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> soltou(ev.getPointerId(ev.actionIndex))
            MotionEvent.ACTION_CANCEL -> {
                soltou(idStick); soltou(idCamera); soltou(idAtaque); soltou(idRolar)
            }
        }
    }

    private fun tocou(id: Int, x: Float, y: Float) {
        when {
            btnAtaque.contem(x, y) -> { idAtaque = id; sessao.engine.atacar() }
            btnRolar.contem(x, y) -> { idRolar = id; sessao.engine.rolar() }
            btnPausa.contem(x, y) -> sessao.pausado = true
            x < width * 0.5f && idStick == -1 -> {
                idStick = id
                baseX = x; baseY = y; knobX = x; knobY = y
                atualizarStick()
            }
            idCamera == -1 -> { idCamera = id; camX = x; camY = y }
        }
    }

    private fun moveu(id: Int, x: Float, y: Float) {
        if (id == idStick) {
            var dx = x - baseX
            var dy = y - baseY
            val d = hypot(dx, dy)
            if (d > raioStick) { dx *= raioStick / d; dy *= raioStick / d }
            knobX = baseX + dx; knobY = baseY + dy
            atualizarStick()
        } else if (id == idCamera) {
            // arrastar para a direita gira a visão para a direita; para cima, olha para cima
            sessao.girarCamera(-(x - camX) * 0.0058f, (y - camY) * 0.0036f)
            camX = x; camY = y
        }
    }

    private fun soltou(id: Int) {
        if (id == -1) return
        if (id == idStick) { idStick = -1; sessao.stickX = 0f; sessao.stickY = 0f }
        if (id == idCamera) idCamera = -1
        if (id == idAtaque) idAtaque = -1
        if (id == idRolar) idRolar = -1
    }

    private fun atualizarStick() {
        var sx = (knobX - baseX) / raioStick
        var sy = -(knobY - baseY) / raioStick
        if (hypot(sx, sy) < 0.12f) { sx = 0f; sy = 0f }
        sessao.stickX = sx
        sessao.stickY = sy
    }

    // ============================================================ desenho

    override fun onDraw(c: Canvas) {
        val agora = System.nanoTime()
        if (ultimo != 0L) tempoTela += ((agora - ultimo) / 1_000_000_000f).coerceIn(0f, 0.1f)
        ultimo = agora

        val f = sessao.foto
        if (f.fase == Fase.FIM && f.iniciado) {
            if (!registrado) {
                registrado = true
                novoRecorde = f.round > recRound || f.pontos > recPontos
                if (novoRecorde) {
                    recRound = max(recRound, f.round)
                    recPontos = max(recPontos, f.pontos)
                    aoNovoRecorde(recRound, recPontos)
                }
            }
        } else registrado = false

        if (!f.iniciado) {
            telaTitulo(c)
        } else {
            sangue(c, f)
            barraVida(c, f)
            placar(c, f)
            avisos(c, f)
            if (f.fase != Fase.FIM) controlesNaTela(c)
            if (f.fase == Fase.FIM) telaFim(c, f)
            else if (sessao.pausado) telaPausa(c)
        }
        postInvalidateOnAnimation()
    }

    private fun texto(c: Canvas, s: String, x: Float, y: Float, tam: Float, alin: Paint.Align, cor: Int = Color.WHITE, alfa: Int = 255) {
        pTexto.textSize = tam
        pTexto.textAlign = alin
        val sombra = max(2f, tam * 0.07f)
        pTexto.color = Color.argb(alfa * 170 / 255, 0, 0, 0)
        c.drawText(s, x + sombra, y + sombra, pTexto)
        pTexto.color = (cor and 0x00FFFFFF) or (alfa shl 24)
        c.drawText(s, x, y, pTexto)
    }

    private fun piscar() = (tempoTela * 2f).toInt() % 2 == 0

    /** Borda vermelha pulsando quando a vida está baixa. */
    private fun sangue(c: Canvas, f: Foto) {
        if (f.hp > 30f || f.morto) return
        val forca = (1f - f.hp / 30f) * (0.5f + 0.5f * sin(tempoTela * 6f))
        pCor.style = Paint.Style.FILL
        pCor.color = Color.argb((forca * 70f).toInt(), 200, 0, 0)
        val b = 40f * dp
        c.drawRect(0f, 0f, width.toFloat(), b, pCor)
        c.drawRect(0f, height - b, width.toFloat(), height.toFloat(), pCor)
        c.drawRect(0f, 0f, b, height.toFloat(), pCor)
        c.drawRect(width - b, 0f, width.toFloat(), height.toFloat(), pCor)
    }

    private fun barraVida(c: Canvas, f: Foto) {
        val x = 16f * dp
        val y = 14f * dp
        val w = 200f * dp
        val h = 18f * dp
        pCor.style = Paint.Style.FILL
        pCor.color = Color.argb(170, 20, 12, 12)
        retang.set(x - 3f * dp, y - 3f * dp, x + w + 3f * dp, y + h + 3f * dp)
        c.drawRoundRect(retang, 8f * dp, 8f * dp, pCor)
        val frac = (f.hp / ColiseuEngine.JOGADOR_HP).coerceIn(0f, 1f)
        pCor.color = when {
            frac > 0.5f -> Color.rgb(70, 190, 80)
            frac > 0.25f -> Color.rgb(235, 190, 50)
            else -> Color.rgb(215, 55, 45)
        }
        retang.set(x, y, x + w * frac, y + h)
        if (frac > 0f) c.drawRoundRect(retang, 6f * dp, 6f * dp, pCor)
        texto(c, "${f.hp.toInt()} / ${ColiseuEngine.JOGADOR_HP.toInt()}", x + w / 2f, y + h - 3f * dp, 13f * dp, Paint.Align.CENTER)
    }

    private fun placar(c: Canvas, f: Foto) {
        val meio = width / 2f
        texto(c, "ROUND ${f.round}", meio, 30f * dp, 22f * dp, Paint.Align.CENTER, Color.rgb(255, 224, 130))
        val restantes = if (f.fase == Fase.LUTA || f.fase == Fase.INTRO) "Goblins: ${f.restantes}" else ""
        if (restantes.isNotEmpty()) texto(c, restantes, meio, 50f * dp, 14f * dp, Paint.Align.CENTER)
        val dir = btnPausa.cx - btnPausa.r - 12f * dp
        texto(c, "${f.pontos} pontos", dir, 30f * dp, 18f * dp, Paint.Align.RIGHT)
        texto(c, "Recorde: round $recRound · $recPontos", dir, 48f * dp, 12f * dp, Paint.Align.RIGHT, Color.rgb(255, 224, 130))
        // botão de pausa
        pCor.style = Paint.Style.FILL
        pCor.color = Color.argb(110, 0, 0, 0)
        c.drawCircle(btnPausa.cx, btnPausa.cy, btnPausa.r, pCor)
        pCor.color = Color.WHITE
        val bw = 4f * dp
        val bh = 12f * dp
        c.drawRect(btnPausa.cx - 5.5f * dp, btnPausa.cy - bh / 2f, btnPausa.cx - 5.5f * dp + bw, btnPausa.cy + bh / 2f, pCor)
        c.drawRect(btnPausa.cx + 1.5f * dp, btnPausa.cy - bh / 2f, btnPausa.cx + 1.5f * dp + bw, btnPausa.cy + bh / 2f, pCor)
    }

    private fun avisos(c: Canvas, f: Foto) {
        val cx = width / 2f
        val cy = height * 0.36f
        when (f.fase) {
            Fase.INTRO -> {
                val a = (f.tFase / 0.6f).coerceIn(0f, 1f)
                val alfa = (255 * a).toInt()
                texto(c, "ROUND ${f.round}", cx, cy, 58f * dp, Paint.Align.CENTER, Color.rgb(255, 224, 130), alfa)
                if (f.round % 5 == 0) {
                    texto(c, "O CHEFE GOBLIN CHEGOU!", cx, cy + 40f * dp, 22f * dp, Paint.Align.CENTER, Color.rgb(255, 110, 90), alfa)
                } else {
                    texto(c, "Derrote os goblins", cx, cy + 38f * dp, 22f * dp, Paint.Align.CENTER, Color.WHITE, alfa)
                }
            }
            Fase.VENCEU -> {
                texto(c, "ROUND COMPLETO!", cx, cy, 42f * dp, Paint.Align.CENTER, Color.rgb(140, 235, 120))
                texto(c, "+30 de vida", cx, cy + 34f * dp, 20f * dp, Paint.Align.CENTER)
            }
            else -> Unit
        }
    }

    // ---- controles

    private fun controlesNaTela(c: Canvas) {
        // joystick: aparece onde o dedo toca; parado mostra uma dica fraca
        val bx = if (idStick != -1) baseX else 24f * dp + raioStick + 10f * dp
        val by = if (idStick != -1) baseY else height - 24f * dp - raioStick - 6f * dp
        val kx = if (idStick != -1) knobX else bx
        val ky = if (idStick != -1) knobY else by
        val ativo = idStick != -1
        pCor.style = Paint.Style.FILL
        pCor.color = Color.argb(if (ativo) 70 else 35, 255, 255, 255)
        c.drawCircle(bx, by, raioStick, pCor)
        pLinha.color = Color.argb(if (ativo) 170 else 90, 255, 255, 255)
        pLinha.strokeWidth = 2f * dp
        c.drawCircle(bx, by, raioStick, pLinha)
        pCor.color = Color.argb(if (ativo) 200 else 100, 255, 255, 255)
        c.drawCircle(kx, ky, 22f * dp, pCor)

        botao(c, btnAtaque, Color.rgb(214, 80, 45), idAtaque != -1)
        botao(c, btnRolar, Color.rgb(60, 120, 205), idRolar != -1)
        iconeEspada(c, btnAtaque)
        iconeRolar(c, btnRolar)
        texto(c, "ATACAR", btnAtaque.cx, btnAtaque.cy + btnAtaque.r + 15f * dp, 11f * dp, Paint.Align.CENTER, Color.WHITE, 200)
        texto(c, "ROLAR", btnRolar.cx, btnRolar.cy + btnRolar.r + 15f * dp, 11f * dp, Paint.Align.CENTER, Color.WHITE, 200)
    }

    private fun botao(c: Canvas, b: Botao, cor: Int, apertado: Boolean) {
        pCor.style = Paint.Style.FILL
        pCor.color = Color.argb(if (apertado) 235 else 170, Color.red(cor), Color.green(cor), Color.blue(cor))
        c.drawCircle(b.cx, b.cy, if (apertado) b.r * 0.93f else b.r, pCor)
        pLinha.color = Color.argb(200, 255, 255, 255)
        pLinha.strokeWidth = 2.5f * dp
        c.drawCircle(b.cx, b.cy, b.r, pLinha)
    }

    private fun iconeEspada(c: Canvas, b: Botao) {
        val s = b.r / 46f
        c.save()
        c.translate(b.cx, b.cy)
        c.rotate(45f)
        pCor.style = Paint.Style.FILL
        pCor.color = Color.rgb(235, 240, 246)
        retang.set(-4f * s, -30f * s, 4f * s, 12f * s)           // lâmina
        c.drawRect(retang, pCor)
        caminho.reset()                                            // ponta
        caminho.moveTo(-4f * s, -30f * s); caminho.lineTo(0f, -38f * s); caminho.lineTo(4f * s, -30f * s); caminho.close()
        c.drawPath(caminho, pCor)
        pCor.color = Color.rgb(240, 190, 60)
        retang.set(-12f * s, 12f * s, 12f * s, 17f * s)          // guarda
        c.drawRect(retang, pCor)
        pCor.color = Color.rgb(110, 70, 40)
        retang.set(-3f * s, 17f * s, 3f * s, 30f * s)            // cabo
        c.drawRect(retang, pCor)
        c.restore()
    }

    private fun iconeRolar(c: Canvas, b: Botao) {
        val r = b.r * 0.46f
        pLinha.color = Color.WHITE
        pLinha.strokeWidth = 4.5f * dp
        retang.set(b.cx - r, b.cy - r, b.cx + r, b.cy + r)
        c.drawArc(retang, 40f, 270f, false, pLinha)
        // ponta da seta no fim do arco (sentido horário)
        val fim = (310.0 * PI / 180.0)
        val px = b.cx + r * cos(fim).toFloat()
        val py = b.cy + r * sin(fim).toFloat()
        c.save()
        c.translate(px, py)
        c.rotate(310f + 90f)
        caminho.reset()
        val t = 8f * dp
        caminho.moveTo(t, 0f); caminho.lineTo(-t * 0.6f, -t * 0.8f); caminho.lineTo(-t * 0.6f, t * 0.8f); caminho.close()
        pCor.style = Paint.Style.FILL
        pCor.color = Color.WHITE
        c.drawPath(caminho, pCor)
        c.restore()
    }

    // ---- telas

    private fun escurecer(c: Canvas, alfa: Int) {
        pCor.style = Paint.Style.FILL
        pCor.color = Color.argb(alfa, 10, 6, 4)
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pCor)
    }

    private fun telaTitulo(c: Canvas) {
        escurecer(c, 90)
        val cx = width / 2f
        texto(c, "COLISEU", cx, height * 0.34f, 64f * dp, Paint.Align.CENTER, Color.rgb(255, 224, 130))
        texto(c, "DO ERIC", cx, height * 0.34f + 56f * dp, 56f * dp, Paint.Align.CENTER)
        texto(c, "Sobreviva às rodadas de goblins", cx, height * 0.62f, 20f * dp, Paint.Align.CENTER, Color.rgb(235, 225, 205))
        if (piscar()) texto(c, "Toque para começar", cx, height * 0.74f, 26f * dp, Paint.Align.CENTER)
        if (recRound > 0) {
            texto(c, "Recorde: round $recRound · $recPontos pontos", cx, height * 0.84f, 15f * dp, Paint.Align.CENTER, Color.rgb(255, 224, 130))
        }
        texto(c, "Esquerda: andar   Direita: atacar e rolar   Arraste: girar a câmera", cx, height * 0.93f, 13f * dp, Paint.Align.CENTER, Color.rgb(220, 220, 220))
    }

    private fun telaPausa(c: Canvas) {
        escurecer(c, 140)
        texto(c, "PAUSADO", width / 2f, height * 0.46f, 52f * dp, Paint.Align.CENTER)
        if (piscar()) texto(c, "Toque para continuar", width / 2f, height * 0.62f, 22f * dp, Paint.Align.CENTER)
    }

    private fun telaFim(c: Canvas, f: Foto) {
        escurecer(c, min(170, (f.tFim * 200f).toInt()))
        if (f.tFim < 0.6f) return
        val cx = width / 2f
        texto(c, "VOCÊ CAIU NO COLISEU", cx, height * 0.27f, 40f * dp, Paint.Align.CENTER, Color.rgb(255, 105, 90))
        texto(c, "Round ${f.round}  ·  ${f.pontos} pontos", cx, height * 0.43f, 30f * dp, Paint.Align.CENTER)
        texto(c, "${f.abates} goblins derrotados", cx, height * 0.53f, 20f * dp, Paint.Align.CENTER, Color.rgb(225, 225, 225))
        if (novoRecorde) {
            texto(c, "NOVO RECORDE!", cx, height * 0.66f, 30f * dp, Paint.Align.CENTER, Color.rgb(255, 224, 130))
        } else {
            texto(c, "Recorde: round $recRound · $recPontos pontos", cx, height * 0.66f, 18f * dp, Paint.Align.CENTER, Color.rgb(255, 224, 130))
        }
        if (f.tFim > 1.5f && piscar()) texto(c, "Toque para jogar de novo", cx, height * 0.82f, 24f * dp, Paint.Align.CENTER)
    }
}

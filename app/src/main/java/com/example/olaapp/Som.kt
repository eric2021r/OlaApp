package com.example.olaapp

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Efeitos sonoros do app.
 *
 * Não existe nenhum arquivo de áudio no projeto: cada som é uma continha (ondas, ruído
 * e envelope) calculada na hora, gravada como WAV na pasta de cache do aparelho e tocada
 * com [SoundPool], que deixa vários sons soarem ao mesmo tempo (importante numa briga).
 *
 * A primeira chamada a [iniciar] monta tudo numa thread separada, então a tela abre na hora.
 * O botão de mudo grava a escolha no mesmo SharedPreferences "olaapp" do resto do app.
 */
object Som {

    // ============================================================ efeitos

    /** Todos os efeitos do app. O [ganho] é o quanto o som sai alto depois de normalizado. */
    enum class Efeito(val ganho: Float) {
        CLIQUE(0.55f),          // botão de menu
        COMPRA(0.60f),          // subiu de nível
        ERRO(0.55f),            // não deu (faltou moeda)
        MOEDA(0.55f),           // ganhou moedas
        PULO(0.55f),            // pulo (jogo 2D)
        PICKUP(0.55f),          // pegou item (jogo 2D)

        ESPADA(0.45f),          // giro da espada
        MACHADO(0.55f),         // giro do machado (mais pesado)
        ADAGA(0.45f),           // arremesso da adaga
        ROLAR(0.40f),           // rolamento / desvio

        IMPACTO(0.70f),         // golpe acertou um bicho
        IMPACTO_FORTE(0.60f),   // pancada pesada (chefe)
        DOR(0.65f),             // o jogador levou dano
        GOBLIN_MORRE(0.60f),    // bicho morreu
        GOBLIN_GRITA(0.35f),    // bicho levantando o porrete
        ROAR(0.70f),            // o chefe chegou

        ROUND(0.60f),           // começou a rodada
        VITORIA(0.60f),         // venceu a rodada
        DERROTA(0.65f)          // morreu
    }

    // ============================================================ estado

    private const val CHAVE = "som_ligado"
    private const val VERSAO = "1"      // mude para regravar os sons depois de mexer neles

    private var iniciado = false
    private var prefs: SharedPreferences? = null

    @Volatile private var pool: SoundPool? = null
    @Volatile private var ids: IntArray? = null

    /** Som ligado? É lido do aparelho quando [iniciar] é chamado. */
    var ligado: Boolean = true
        private set

    /** Prepara os sons. Pode ser chamado por toda tela: só faz o serviço na primeira vez. */
    fun iniciar(ctx: Context) {
        if (iniciado) return
        iniciado = true
        val app = ctx.applicationContext
        val p = app.getSharedPreferences("olaapp", Context.MODE_PRIVATE)
        prefs = p
        ligado = p.getBoolean(CHAVE, true)

        Thread {
            val sp = SoundPool.Builder()
                .setMaxStreams(12)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()
            val pasta = File(app.cacheDir, "sons")
            if (!pasta.exists()) pasta.mkdirs()
            val carregados = IntArray(Efeito.entries.size)
            for (e in Efeito.entries) {
                val arquivo = File(pasta, "s${VERSAO}_${e.name.lowercase()}.wav")
                try {
                    if (!arquivo.exists()) arquivo.writeBytes(wav(gerar(e)))
                    carregados[e.ordinal] = sp.load(arquivo.absolutePath, 1)
                } catch (_: Exception) {
                    // se não deu para gravar, este som simplesmente não toca
                }
            }
            pool = sp
            ids = carregados
        }.start()
    }

    /** Toca um efeito. [volume] de 0 a 1; [variar] sorteia o tom para não ficar repetitivo. */
    fun tocar(e: Efeito, volume: Float = 1f, variar: Float = 0f) {
        if (!ligado) return
        val sp = pool ?: return
        val carregados = ids ?: return
        val id = carregados[e.ordinal]
        if (id == 0) return
        val taxa = if (variar > 0f) 1f + (Random.nextFloat() * 2f - 1f) * variar else 1f
        sp.play(id, volume, volume, 1, 0, taxa)
    }

    /** Liga/desliga o som e guarda a escolha no aparelho. */
    fun definirLigado(valor: Boolean) {
        ligado = valor
        prefs?.edit()?.putBoolean(CHAVE, valor)?.apply()
        if (!valor) pool?.autoPause()
    }

    /** Texto pronto para o botão de som das telas de menu. */
    fun rotulo(): String = if (ligado) "Som: ligado 🔊" else "Som: desligado 🔇"

    /** Chamado quando a tela sai de frente: para o que ainda está tocando. */
    fun pausar() {
        pool?.autoPause()
    }

    fun retomar() {
        if (ligado) pool?.autoResume()
    }

    // ============================================================ receitas

    /** A "partitura" de cada efeito. Tudo aqui é matemática: nenhum arquivo de som. */
    private fun gerar(e: Efeito): FloatArray {
        val rng = Random(e.ordinal * 977 + 13)   // sempre igual, para o som ser o mesmo toda vez
        val m: MixerSom = when (e) {
            Efeito.CLIQUE ->
                MixerSom(dur(0.07f)).apply { tom(0f, 0.06f, 1400f, 950f, 0.7f, 30f, 1) }

            Efeito.COMPRA ->
                MixerSom(dur(0.62f)).apply { arpejo(floatArrayOf(523f, 659f, 784f, 1047f), 0.085f, 0.5f) }

            Efeito.ERRO ->
                MixerSom(dur(0.33f)).apply {
                    tom(0f, 0.14f, 240f, 210f, 0.55f, 9f, 1)
                    tom(0.15f, 0.16f, 180f, 150f, 0.55f, 11f, 1)
                }

            Efeito.MOEDA ->
                MixerSom(dur(0.38f)).apply {
                    tom(0f, 0.08f, 988f, 988f, 0.5f, 16f, 2)
                    tom(0.07f, 0.28f, 1319f, 1319f, 0.5f, 9f, 2)
                }

            Efeito.PULO ->
                MixerSom(dur(0.2f)).apply { tom(0f, 0.16f, 320f, 780f, 0.6f, 14f, 1) }

            Efeito.PICKUP ->
                MixerSom(dur(0.3f)).apply {
                    tom(0f, 0.07f, 1319f, 1319f, 0.45f, 16f, 2)
                    tom(0.06f, 0.2f, 1760f, 1760f, 0.45f, 10f, 2)
                }

            Efeito.ESPADA ->
                MixerSom(dur(0.22f)).apply {
                    ruido(0f, 0.2f, 0.7f, 7f, 900f, 3600f, true, rng)
                    tom(0.02f, 0.16f, 2600f, 900f, 0.18f, 12f, 0)
                }

            Efeito.MACHADO ->
                MixerSom(dur(0.4f)).apply {
                    ruido(0f, 0.36f, 0.8f, 4.5f, 500f, 2000f, true, rng)
                    tom(0.03f, 0.3f, 900f, 260f, 0.3f, 7f, 0)
                }

            Efeito.ADAGA ->
                MixerSom(dur(0.18f)).apply { ruido(0f, 0.16f, 0.65f, 9f, 1200f, 4200f, true, rng) }

            Efeito.ROLAR ->
                MixerSom(dur(0.32f)).apply { ruido(0f, 0.3f, 0.6f, 8f, 700f, 2600f, false, rng) }

            Efeito.IMPACTO ->
                MixerSom(dur(0.22f)).apply {
                    tom(0f, 0.18f, 190f, 60f, 0.9f, 11f, 0)
                    ruido(0f, 0.1f, 0.5f, 22f, 2500f, 700f, false, rng)
                }

            Efeito.IMPACTO_FORTE ->
                MixerSom(dur(0.55f)).apply {
                    tom(0f, 0.5f, 420f, 340f, 0.4f, 4.5f, 0)
                    tom(0f, 0.5f, 1130f, 1080f, 0.3f, 5.5f, 0)
                    tom(0f, 0.45f, 1870f, 1800f, 0.22f, 7f, 0)
                    ruido(0f, 0.08f, 0.5f, 26f, 4000f, 1500f, true, rng)
                }

            Efeito.DOR ->
                MixerSom(dur(0.4f)).apply {
                    tom(0f, 0.34f, 430f, 150f, 0.55f, 6f, 2)
                    ruido(0f, 0.12f, 0.3f, 16f, 1800f, 800f, false, rng)
                }

            Efeito.GOBLIN_MORRE ->
                MixerSom(dur(0.6f)).apply {
                    tom(0f, 0.55f, 330f, 90f, 0.5f, 4.5f, 2)
                    ruido(0f, 0.2f, 0.35f, 9f, 1500f, 500f, false, rng)
                }

            Efeito.GOBLIN_GRITA ->
                MixerSom(dur(0.34f)).apply {
                    tom(0f, 0.3f, 200f, 420f, 0.45f, 5f, 2)
                    ruido(0f, 0.14f, 0.25f, 12f, 900f, 2000f, false, rng)
                }

            Efeito.ROAR ->
                MixerSom(dur(1.1f)).apply {
                    tom(0f, 1f, 120f, 70f, 0.7f, 2.2f, 2)
                    tom(0f, 1f, 180f, 105f, 0.4f, 2.4f, 1)
                    ruido(0f, 0.5f, 0.3f, 4f, 700f, 300f, false, rng)
                }

            Efeito.ROUND ->
                MixerSom(dur(0.6f)).apply { arpejo(floatArrayOf(659f, 880f, 1109f), 0.11f, 0.45f) }

            Efeito.VITORIA ->
                MixerSom(dur(1.2f)).apply {
                    arpejo(floatArrayOf(523f, 659f, 784f, 1047f), 0.13f, 0.45f)
                    tom(0.52f, 0.6f, 1047f, 1047f, 0.3f, 3f, 2)
                    tom(0.52f, 0.6f, 1319f, 1319f, 0.25f, 3f, 2)
                    tom(0.52f, 0.6f, 1568f, 1568f, 0.22f, 3f, 2)
                }

            Efeito.DERROTA ->
                MixerSom(dur(1.3f)).apply {
                    tom(0f, 0.35f, 392f, 392f, 0.4f, 5f, 2)
                    tom(0.3f, 0.35f, 311f, 311f, 0.4f, 5f, 2)
                    tom(0.6f, 0.7f, 233f, 220f, 0.45f, 2.5f, 2)
                }
        }
        m.normalizar(e.ganho)
        return m.a
    }
}

// ============================================================ geração das ondas

private const val TAXA = 22050                  // amostras por segundo
private val PI_SOM = PI.toFloat()
private const val ATAQUE = 300f                 // entra em 1/300 s, para não estalar

private fun dur(seg: Float) = (TAXA * seg).toInt()

/** Uma onda: 0 seno, 1 quadrada, 2 serra, 3 triângulo. [fase] é o quanto já andou (em voltas). */
private fun onda(tipo: Int, fase: Float): Float {
    val f = fase % 1f
    return when (tipo) {
        1 -> if (f < 0.5f) 1f else -1f
        2 -> 2f * f - 1f
        3 -> if (f < 0.5f) 4f * f - 1f else 3f - 4f * f
        else -> sin(2f * PI_SOM * f)
    }
}

/** Junta as ondas num vetor de amostras (soma tudo, então dá para sobrepor sons). */
private class MixerSom(val n: Int) {
    val a = FloatArray(n)

    /** Nota que desliza de [f0] para [f1] durante [seg] segundos. */
    fun tom(t0: Float, seg: Float, f0: Float, f1: Float, vol: Float, decai: Float, tipo: Int = 0) {
        val i0 = (t0 * TAXA).toInt()
        val m = (seg * TAXA).toInt()
        if (m <= 0) return
        var fase = 0f
        for (i in 0 until m) {
            val k = i0 + i
            if (k >= a.size) break
            if (k >= 0) {
                val t = i.toFloat() / TAXA
                fase += (f0 + (f1 - f0) * (i.toFloat() / m)) / TAXA
                val entra = if (t * ATAQUE > 1f) 1f else t * ATAQUE
                a[k] += vol * exp(-decai * t) * entra * onda(tipo, fase)
            }
        }
    }

    /**
     * Chiado de vento/golpe: ruído passando por um filtro cuja frequência de corte
     * anda de [corte0] para [corte1]. Com [agudo] = true o filtro é passa-alta.
     */
    fun ruido(
        t0: Float, seg: Float, vol: Float, decai: Float,
        corte0: Float, corte1: Float, agudo: Boolean, rng: Random
    ) {
        val i0 = (t0 * TAXA).toInt()
        val m = (seg * TAXA).toInt()
        if (m <= 0) return
        var y = 0f
        for (i in 0 until m) {
            val k = i0 + i
            if (k >= a.size) break
            if (k >= 0) {
                val t = i.toFloat() / TAXA
                val corte = corte0 + (corte1 - corte0) * (i.toFloat() / m)
                val alfa = (1f - exp(-2f * PI_SOM * corte / TAXA)).coerceIn(0f, 1f)
                val x = rng.nextFloat() * 2f - 1f
                y += alfa * (x - y)
                val entra = if (t * ATAQUE > 1f) 1f else t * ATAQUE
                a[k] += vol * exp(-decai * t) * entra * (if (agudo) x - y else y)
            }
        }
    }

    /** Sequência de notas, uma atrás da outra. */
    fun arpejo(notas: FloatArray, passo: Float, vol: Float, tipo: Int = 2, decai: Float = 6f) {
        for (i in notas.indices) tom(i * passo, passo * 3.2f, notas[i], notas[i], vol, decai, tipo)
    }

    /** Deixa o som no volume pedido (para todos ficarem parecidos). */
    fun normalizar(ganho: Float) {
        var pico = 0f
        for (v in a) {
            val x = if (v < 0f) -v else v
            if (x > pico) pico = x
        }
        if (pico < 1e-4f) return
        val k = ganho / pico
        for (i in a.indices) a[i] *= k
    }
}

/** Monta o arquivo WAV (16 bits, mono) a partir das amostras. */
private fun wav(a: FloatArray): ByteArray {
    val n = a.size
    val b = ByteArray(44 + n * 2)
    fun i32(p: Int, v: Int) {
        b[p] = (v and 255).toByte()
        b[p + 1] = ((v shr 8) and 255).toByte()
        b[p + 2] = ((v shr 16) and 255).toByte()
        b[p + 3] = ((v shr 24) and 255).toByte()
    }
    fun i16(p: Int, v: Int) {
        b[p] = (v and 255).toByte()
        b[p + 1] = ((v shr 8) and 255).toByte()
    }
    fun txt(p: Int, s: String) {
        for (i in s.indices) b[p + i] = s[i].code.toByte()
    }
    txt(0, "RIFF"); i32(4, 36 + n * 2); txt(8, "WAVE")
    txt(12, "fmt "); i32(16, 16); i16(20, 1); i16(22, 1)
    i32(24, TAXA); i32(28, TAXA * 2); i16(32, 2); i16(34, 16)
    txt(36, "data"); i32(40, n * 2)
    for (i in 0 until n) i16(44 + i * 2, (a[i].coerceIn(-1f, 1f) * 32000f).toInt())
    return b
}

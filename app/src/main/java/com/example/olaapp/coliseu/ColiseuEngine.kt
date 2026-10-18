package com.example.olaapp.coliseu

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Motor do "Coliseu do Eric": não usa nada do Android, então dá para testar no computador.
 * Unidades em metros. A arena é um círculo de raio ARENA_R com o centro em (0, 0).
 * Direção em que alguém olha = ângulo "face": a frente é (sen face, cos face) no plano x/z.
 */

enum class Fase { INTRO, LUTA, VENCEU, FIM }

enum class GoblinEstado { PERSEGUIR, RONDAR, PREPARAR, GOLPE, RECUPERAR }

class Goblin(
    val chefe: Boolean,
    var x: Float, var z: Float,
    val maxHp: Float,
    val velocidade: Float,
    val preparo: Float,   // tempo levantando o porrete antes de bater
    val dano: Float,
    val alcance: Float
) {
    var hp = maxHp
    val raio = if (chefe) 0.85f else 0.5f
    val escala = if (chefe) 1.55f else 1f
    var face = 0f
    var estado = GoblinEstado.PERSEGUIR
    var tEstado = 0f
    var kx = 0f
    var kz = 0f
    var andar = 0f          // fase da animação de andar
    var andarFracao = 0f    // 0 parado .. 1 andando
    var brilho = 0f         // fica branco por um instante quando leva golpe
    var atordoado = 0f
    var morrendo = -1f      // >= 0 enquanto cai
    var ultimoGolpe = -1
    var lado = 1f           // para que lado ele rodeia o jogador
    val vivo get() = morrendo < 0f
    val recuperar get() = if (chefe) 0.85f else 0.65f
}

class Particula(
    var x: Float, var y: Float, var z: Float,
    var vx: Float, var vy: Float, var vz: Float,
    var vida: Float, val vidaMax: Float,
    val r: Float, val g: Float, val b: Float, val tamanho: Float
)

private class Chegada(var t: Float, val chefe: Boolean, val portao: Int)

class ColiseuEngine(seed: Long = System.nanoTime()) {

    companion object {
        const val ARENA_R = 18f
        const val STEP = 1f / 60f

        const val JOGADOR_VEL = 5.6f
        const val JOGADOR_HP = 100f
        const val JOGADOR_RAIO = 0.45f

        const val ATAQUE_DUR = 0.45f
        const val GOLPE_INICIO = 0.15f
        const val GOLPE_FIM = 0.27f
        const val ESPADA_ALCANCE = 2.3f
        const val ESPADA_DANO = 20f

        const val ROLAR_DUR = 0.42f
        const val ROLAR_INVULNERAVEL = 0.34f
        const val ROLAR_VEL = 9.5f
        const val ROLAR_RECARGA = 0.9f

        const val INTRO_T = 2.4f
        const val VENCEU_T = 2.6f
        const val CURA_ENTRE_ROUNDS = 30f

        const val PORTAO_R = 16.2f
        val PORTOES = floatArrayOf(
            (PI / 4).toFloat(), (3 * PI / 4).toFloat(), (5 * PI / 4).toFloat(), (7 * PI / 4).toFloat()
        )
    }

    private val rng = Random(seed)
    private var acc = 0f

    // ---- estado geral
    var time = 0f; private set
    var fase = Fase.INTRO; private set
    var round = 1; private set
    var tFase = INTRO_T; private set          // tempo restante da fase INTRO / VENCEU
    var tFim = 0f; private set                // tempo desde o fim do jogo
    var pontos = 0; private set
    var abates = 0; private set
    var iniciado = false; private set

    val goblins = ArrayList<Goblin>()
    val particulas = ArrayList<Particula>()
    private val chegadas = ArrayList<Chegada>()

    // ---- jogador
    var px = 0f; private set
    var pz = 6f; private set
    var face = PI.toFloat(); private set      // começa olhando para -z
    var hp = JOGADOR_HP; private set
    var morto = false; private set
    var tMorto = 0f; private set
    var ataqueT = -1f; private set            // >= 0 durante o ataque
    var golpeId = 0; private set
    var rolarT = -1f; private set             // >= 0 durante o rolamento
    var invulneravel = 0f; private set
    var flashDano = 0f; private set
    var andarFracao = 0f; private set
    var andarFase = 0f; private set
    private var kx = 0f
    private var kz = 0f
    private var rolarDx = 0f
    private var rolarDz = 0f
    private var rolarRecarga = 0f
    private var bufAtaque = 0f
    private var bufRolar = 0f

    // ---- entrada (no plano do mundo, tamanho máximo 1)
    private var movX = 0f
    private var movZ = 0f

    val frenteX get() = sin(face)
    val frenteZ get() = cos(face)
    val goblinsRestantes get() = goblins.count { it.vivo } + chegadas.size
    val rolando get() = rolarT in 0f..ROLAR_DUR

    // ============================================================ entrada

    fun mover(x: Float, z: Float) {
        val l = hypot(x, z)
        if (l > 1f) { movX = x / l; movZ = z / l } else { movX = x; movZ = z }
    }

    fun atacar() { bufAtaque = 0.22f }

    fun rolar() { bufRolar = 0.22f }

    fun comecar() {
        if (iniciado) return
        iniciado = true
        fase = Fase.INTRO
        tFase = INTRO_T
    }

    /** Usado só nos testes: começa direto no round pedido. */
    internal fun forcarRound(n: Int) {
        round = n
        goblins.clear()
        chegadas.clear()
        iniciado = true
        fase = Fase.INTRO
        tFase = INTRO_T
    }

    /** Só deixa o relógio andar (animações do cenário na tela de título). */
    fun parado(dt: Float) { time += dt }

    // ============================================================ laço

    fun update(dt: Float) {
        acc += min(dt, 0.1f)
        while (acc >= STEP) {
            if (iniciado) passo(STEP) else time += STEP
            acc -= STEP
        }
    }

    private fun passo(dt: Float) {
        time += dt
        atualizarParticulas(dt)
        if (flashDano > 0f) flashDano -= dt
        if (invulneravel > 0f) invulneravel -= dt
        if (rolarRecarga > 0f) rolarRecarga -= dt

        when (fase) {
            Fase.INTRO -> {
                atualizarJogador(dt)
                tFase -= dt
                if (tFase <= 0f) comecarLuta()
            }
            Fase.LUTA -> {
                chegarGoblins(dt)
                atualizarJogador(dt)
                atualizarGoblins(dt)
                colisoes()
                if (chegadas.isEmpty() && goblins.none { it.vivo }) venceuRound()
            }
            Fase.VENCEU -> {
                atualizarJogador(dt)
                atualizarGoblins(dt)
                tFase -= dt
                if (tFase <= 0f) {
                    round++
                    fase = Fase.INTRO
                    tFase = INTRO_T
                }
            }
            Fase.FIM -> {
                tFim += dt
                tMorto += dt
                for (g in goblins) if (!g.vivo) g.morrendo += dt
            }
        }
    }

    // ============================================================ rounds

    private fun comecarLuta() {
        fase = Fase.LUTA
        val n = min(2 + round, 10)
        for (i in 0 until n) chegadas.add(Chegada(0.4f + i * 0.9f, false, (i + round) % PORTOES.size))
        if (round % 5 == 0) chegadas.add(Chegada(3f, true, round % PORTOES.size))
    }

    private fun venceuRound() {
        fase = Fase.VENCEU
        tFase = VENCEU_T
        pontos += 200 * round
        hp = min(JOGADOR_HP, hp + CURA_ENTRE_ROUNDS)
    }

    private fun chegarGoblins(dt: Float) {
        val it = chegadas.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.t -= dt
            if (c.t > 0f) continue
            it.remove()
            criarGoblin(c)
        }
    }

    private fun criarGoblin(c: Chegada) {
        val ang = PORTOES[c.portao]
        val x = cos(ang) * PORTAO_R
        val z = sin(ang) * PORTAO_R
        val r = round
        val hpBase = min(30f + 5f * (r - 1), 70f)
        val vel = min(2.8f + 0.15f * (r - 1), 4.4f)
        val preparo = max(0.35f, 0.60f - 0.025f * (r - 1))
        val dano = min(10f + r / 3f, 18f)
        val g = if (c.chefe) {
            Goblin(true, x, z, hpBase * 4f, vel * 0.8f, 0.8f, 25f, 2.9f)
        } else {
            Goblin(false, x, z, hpBase, vel, preparo, dano, 2.1f)
        }
        g.face = atan2(-x, -z)
        g.lado = if (rng.nextBoolean()) 1f else -1f
        goblins.add(g)
    }

    // ============================================================ jogador

    private fun atualizarJogador(dt: Float) {
        if (morto) return
        if (bufAtaque > 0f) bufAtaque -= dt
        if (bufRolar > 0f) bufRolar -= dt

        // rolamento
        if (bufRolar > 0f && rolarT < 0f && rolarRecarga <= 0f) {
            val l = hypot(movX, movZ)
            if (l > 0.1f) { rolarDx = movX / l; rolarDz = movZ / l } else { rolarDx = frenteX; rolarDz = frenteZ }
            face = atan2(rolarDx, rolarDz)
            rolarT = 0f
            rolarRecarga = ROLAR_RECARGA
            ataqueT = -1f
            bufRolar = 0f
            bufAtaque = 0f
        }

        // ataque
        if (bufAtaque > 0f && ataqueT < 0f && rolarT < 0f) {
            ataqueT = 0f
            golpeId++
            bufAtaque = 0f
            mirarNoMaisPerto()
            kx += frenteX * 6f
            kz += frenteZ * 6f
        }

        var vx = 0f
        var vz = 0f
        var vel = 0f
        if (rolarT >= 0f) {
            rolarT += dt
            val k = 1f - 0.4f * (rolarT / ROLAR_DUR)
            vx = rolarDx * ROLAR_VEL * k
            vz = rolarDz * ROLAR_VEL * k
            vel = 1f
            if (rolarT >= ROLAR_DUR) rolarT = -1f
        } else {
            val mult = if (ataqueT >= 0f) 0.35f else 1f
            val l = hypot(movX, movZ)
            vx = movX * JOGADOR_VEL * mult
            vz = movZ * JOGADOR_VEL * mult
            vel = min(1f, l) * mult
            if (l > 0.1f && ataqueT < 0f) virarPara(atan2(movX, movZ), 16f * dt)
        }

        if (ataqueT >= 0f) {
            ataqueT += dt
            golpearComEspada()
            if (ataqueT >= ATAQUE_DUR) ataqueT = -1f
        }

        px += (vx + kx) * dt
        pz += (vz + kz) * dt
        val dec = max(0f, 1f - 9f * dt)
        kx *= dec; kz *= dec

        andarFracao += (vel - andarFracao) * min(1f, 12f * dt)
        andarFase += vel * dt * 9f
        limitarArena()
    }

    private fun limitarArena() {
        val lim = ARENA_R - 0.8f
        val r = hypot(px, pz)
        if (r > lim) { px *= lim / r; pz *= lim / r }
    }

    private fun virarPara(alvo: Float, maximo: Float) {
        val d = difAngulo(face, alvo)
        face += d.coerceIn(-maximo, maximo)
    }

    private fun mirarNoMaisPerto() {
        var melhor: Goblin? = null
        var dist = 4.5f
        for (g in goblins) {
            if (!g.vivo) continue
            val d = hypot(g.x - px, g.z - pz)
            if (d < dist) { dist = d; melhor = g }
        }
        if (melhor != null) face = atan2(melhor.x - px, melhor.z - pz)
    }

    private fun golpearComEspada() {
        if (ataqueT < GOLPE_INICIO || ataqueT > GOLPE_FIM) return
        for (g in goblins) {
            if (!g.vivo || g.ultimoGolpe == golpeId) continue
            val dx = g.x - px
            val dz = g.z - pz
            val d = max(hypot(dx, dz), 0.001f)
            if (d > ESPADA_ALCANCE + g.raio) continue
            val alinhado = (frenteX * dx + frenteZ * dz) / d
            if (d > g.raio + 0.6f && alinhado < 0.26f) continue
            g.ultimoGolpe = golpeId
            levarGolpe(g, dx / d, dz / d)
        }
    }

    private fun levarGolpe(g: Goblin, dirX: Float, dirZ: Float) {
        g.hp -= ESPADA_DANO
        g.brilho = 0.16f
        g.kx = dirX * (if (g.chefe) 3.5f else 7f)
        g.kz = dirZ * (if (g.chefe) 3.5f else 7f)
        if (g.chefe) {
            g.atordoado = 0.1f
        } else {
            g.atordoado = 0.35f
            if (g.estado == GoblinEstado.PREPARAR || g.estado == GoblinEstado.GOLPE) {
                g.estado = GoblinEstado.PERSEGUIR
                g.tEstado = 0f
            }
        }
        explodir(g.x, 0.9f * g.escala, g.z, 7, 0.55f, 0.85f, 0.25f, 4f, 0.12f)
        if (g.hp <= 0f) {
            g.morrendo = 0f
            abates++
            pontos += if (g.chefe) 500 else 100
            explodir(g.x, 0.9f * g.escala, g.z, 16, 0.45f, 0.75f, 0.2f, 5.5f, 0.16f)
        }
    }

    /** Devolve false se o golpe foi evitado (rolamento ou proteção logo após levar dano). */
    private fun machucarJogador(dano: Float, deX: Float, deZ: Float): Boolean {
        if (morto || invulneravel > 0f || (rolarT >= 0f && rolarT < ROLAR_INVULNERAVEL)) return false
        hp -= dano
        invulneravel = 0.7f
        flashDano = 0.4f
        ataqueT = -1f
        val dx = px - deX
        val dz = pz - deZ
        val d = max(hypot(dx, dz), 0.001f)
        kx = dx / d * 7f
        kz = dz / d * 7f
        explodir(px, 1.2f, pz, 8, 0.85f, 0.15f, 0.15f, 4f, 0.12f)
        if (hp <= 0f) {
            hp = 0f
            morto = true
            tMorto = 0f
            tFim = 0f
            fase = Fase.FIM
            ataqueT = -1f
            rolarT = -1f
        }
        return true
    }

    // ============================================================ goblins

    private val maxAtacantes get() = if (round >= 6) 3 else 2

    private fun atualizarGoblins(dt: Float) {
        for (g in goblins) {
            if (!g.vivo) { g.morrendo += dt; continue }
            atualizarGoblin(g, dt)
        }
        goblins.removeAll { it.morrendo > 1.6f }
    }

    private fun atualizarGoblin(g: Goblin, dt: Float) {
        if (g.brilho > 0f) g.brilho -= dt
        g.x += g.kx * dt
        g.z += g.kz * dt
        val dec = max(0f, 1f - 9f * dt)
        g.kx *= dec; g.kz *= dec

        val dx = px - g.x
        val dz = pz - g.z
        val d = max(hypot(dx, dz), 0.001f)
        val paraX = dx / d
        val paraZ = dz / d

        val alvo = atan2(dx, dz)
        g.face += difAngulo(g.face, alvo).coerceIn(-8f * dt, 8f * dt)

        var andou = 0f
        if (g.atordoado > 0f) {
            g.atordoado -= dt
        } else {
            val atacantes = goblins.count {
                it.vivo && (it.estado == GoblinEstado.PREPARAR || it.estado == GoblinEstado.GOLPE)
            }
            when (g.estado) {
                GoblinEstado.PERSEGUIR -> {
                    if (!morto && d < g.alcance * 0.85f && atacantes < maxAtacantes) {
                        g.estado = GoblinEstado.PREPARAR; g.tEstado = 0f
                    } else if (!morto && d < 3.6f && atacantes >= maxAtacantes) {
                        g.estado = GoblinEstado.RONDAR; g.tEstado = 0f
                    } else if (!morto) {
                        g.x += paraX * g.velocidade * dt
                        g.z += paraZ * g.velocidade * dt
                        andou = 1f
                    }
                }
                GoblinEstado.RONDAR -> {
                    if (atacantes < maxAtacantes || morto) {
                        g.estado = GoblinEstado.PERSEGUIR
                    } else {
                        val radial = ((d - 3.0f) * 0.8f).coerceIn(-1f, 1f)
                        var mx = -paraZ * g.lado * 0.7f + paraX * radial
                        var mz = paraX * g.lado * 0.7f + paraZ * radial
                        val l = max(hypot(mx, mz), 0.001f)
                        mx /= l; mz /= l
                        g.x += mx * g.velocidade * 0.65f * dt
                        g.z += mz * g.velocidade * 0.65f * dt
                        andou = 0.65f
                    }
                }
                GoblinEstado.PREPARAR -> {
                    g.tEstado += dt
                    if (d > g.alcance * 0.6f) {
                        g.x += paraX * g.velocidade * 0.25f * dt
                        g.z += paraZ * g.velocidade * 0.25f * dt
                        andou = 0.25f
                    }
                    if (g.tEstado >= g.preparo) {
                        g.estado = GoblinEstado.GOLPE
                        g.tEstado = 0f
                        if (!morto && d < g.alcance) machucarJogador(g.dano, g.x, g.z)
                    }
                }
                GoblinEstado.GOLPE -> {
                    g.tEstado += dt
                    if (g.tEstado >= 0.2f) { g.estado = GoblinEstado.RECUPERAR; g.tEstado = 0f }
                }
                GoblinEstado.RECUPERAR -> {
                    g.tEstado += dt
                    if (g.tEstado >= g.recuperar) { g.estado = GoblinEstado.PERSEGUIR; g.tEstado = 0f }
                }
            }
        }
        g.andarFracao += (andou - g.andarFracao) * min(1f, 12f * dt)
        g.andar += andou * g.velocidade * dt * 3.2f
    }

    // ============================================================ colisões

    private fun colisoes() {
        val vivos = goblins.filter { it.vivo }
        // goblins entre si
        for (i in vivos.indices) {
            for (j in i + 1 until vivos.size) {
                val a = vivos[i]; val b = vivos[j]
                val dx = b.x - a.x; val dz = b.z - a.z
                val d = max(hypot(dx, dz), 0.001f)
                val min = (a.raio + b.raio) * 0.95f
                if (d < min) {
                    val p = (min - d) / 2f
                    a.x -= dx / d * p; a.z -= dz / d * p
                    b.x += dx / d * p; b.z += dz / d * p
                }
            }
        }
        // goblins contra o jogador (quem rola passa por baixo)
        if (!morto && rolarT < 0f) {
            for (g in vivos) {
                val dx = g.x - px; val dz = g.z - pz
                val d = max(hypot(dx, dz), 0.001f)
                val min = g.raio + JOGADOR_RAIO
                if (d < min) {
                    val p = min - d
                    g.x += dx / d * p * 0.7f; g.z += dz / d * p * 0.7f
                    px -= dx / d * p * 0.3f; pz -= dz / d * p * 0.3f
                }
            }
        }
        val lim = ARENA_R - 0.8f
        for (g in vivos) {
            val r = hypot(g.x, g.z)
            if (r > lim) { g.x *= lim / r; g.z *= lim / r }
        }
        limitarArena()
    }

    // ============================================================ partículas

    private fun explodir(
        x: Float, y: Float, z: Float, n: Int,
        r: Float, g: Float, b: Float, vel: Float, tam: Float
    ) {
        repeat(n) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val s = vel * (0.4f + rng.nextFloat() * 0.6f)
            particulas.add(
                Particula(
                    x, y, z, cos(a) * s, 2f + rng.nextFloat() * vel, sin(a) * s,
                    0.6f, 0.6f, r, g, b, tam
                )
            )
        }
    }

    private fun atualizarParticulas(dt: Float) {
        val it = particulas.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.vida -= dt
            if (p.vida <= 0f) { it.remove(); continue }
            p.vy -= 14f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.z += p.vz * dt
            if (p.y < p.tamanho / 2f) { p.y = p.tamanho / 2f; p.vy = 0f; p.vx *= 0.8f; p.vz *= 0.8f }
        }
    }

    // ============================================================ ferramentas

    private fun difAngulo(de: Float, para: Float): Float {
        var d = (para - de) % (2f * PI.toFloat())
        if (d > PI) d -= 2f * PI.toFloat()
        if (d < -PI) d += 2f * PI.toFloat()
        return d
    }
}

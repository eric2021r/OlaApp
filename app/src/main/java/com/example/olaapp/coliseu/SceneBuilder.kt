package com.example.olaapp.coliseu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/**
 * Monta a cena 3D: o coliseu (que nunca muda e fica numa malha só) e as peças que se mexem
 * (cavaleiro, goblins, chamas, partículas). Não usa OpenGL nem Android: só gera uma lista de
 * DrawItem, que o desenho do celular (e o teste no computador) percorre.
 */
class SceneBuilder {

    private val arena = DrawItem(construirArena(), Math3D.identity(), 1f, 1f, 1f)

    private var saida = ArrayList<DrawItem>()
    private var flash = 0f   // 0..1: mistura com branco (levou golpe)
    private var aviso = 0f   // 0..1: mistura com vermelho (goblin vai bater)
    private var dano = 0f    // 0..1: mistura com rosa (jogador machucado)

    fun montar(e: ColiseuEngine, destino: ArrayList<DrawItem>) {
        saida = destino
        destino.clear()
        destino.add(arena)
        chamas(e.time)
        jogador(e)
        for (g in e.goblins) goblin(g, e)
        adagasVoando(e)
        particulas(e)
    }

    // ============================================================ ferramentas

    private fun cor(hex: Int): FloatArray {
        var r = ((hex shr 16) and 255) / 255f
        var g = ((hex shr 8) and 255) / 255f
        var b = (hex and 255) / 255f
        if (flash > 0f) { r += (1f - r) * flash; g += (1f - g) * flash; b += (1f - b) * flash }
        if (aviso > 0f) { r += (1f - r) * aviso; g += (0.22f - g) * aviso; b += (0.18f - b) * aviso }
        if (dano > 0f) { r += (1f - r) * dano; g += (0.5f - g) * dano; b += (0.5f - b) * dano }
        return floatArrayOf(r, g, b)
    }

    private fun peca(mesh: Mesh, m: FloatArray, hex: Int) {
        val c = cor(hex)
        saida.add(DrawItem(mesh, m, c[0], c[1], c[2]))
    }

    /** Uma peça esticada, no ponto (tx, ty, tz) do sistema do "pai". Gira em z, depois y, depois x. */
    private fun caixa(
        pai: FloatArray, tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float, hex: Int,
        mesh: Mesh = Meshes.CUBE, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f
    ) {
        var m = Math3D.mul(pai, Math3D.translate(tx, ty, tz))
        if (rz != 0f) m = Math3D.mul(m, Math3D.rotZ(rz))
        if (ry != 0f) m = Math3D.mul(m, Math3D.rotY(ry))
        if (rx != 0f) m = Math3D.mul(m, Math3D.rotX(rx))
        m = Math3D.mul(m, Math3D.scale(sx, sy, sz))
        peca(mesh, m, hex)
    }

    /** Braço ou perna: gira no ponto (hx, hy, hz) e cresce para baixo. Devolve o sistema da junta, para pendurar coisas. */
    private fun membro(
        pai: FloatArray, hx: Float, hy: Float, hz: Float, ang: Float,
        comp: Float, larg: Float, prof: Float, hex: Int
    ): FloatArray {
        val base = Math3D.mul(Math3D.mul(pai, Math3D.translate(hx, hy, hz)), Math3D.rotX(ang))
        caixa(base, 0f, -comp / 2f, 0f, larg, comp, prof, hex)
        return base
    }

    private fun suave(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t.coerceIn(0f, 1f)

    private fun sombra(x: Float, z: Float, diametro: Float) {
        val m = Math3D.mul(Math3D.translate(x, 0.03f, z), Math3D.scale(diametro, 0.01f, diametro))
        val c = cor(0x6E5B3C)
        saida.add(DrawItem(Meshes.CYLINDER, m, c[0], c[1], c[2]))
    }

    // ============================================================ o jogador (lutador escolhido)

    private fun jogador(e: ColiseuEngine) {
        flash = 0f; aviso = 0f
        dano = if (e.invulneravel > 0f && ((e.time * 18f).toInt() % 2 == 0)) 0.55f else 0f

        val bob = abs(sin(e.andarFase)) * 0.06f * e.andarFracao
        var raiz = Math3D.mul(Math3D.translate(e.px, bob, e.pz), Math3D.rotY(e.face))
        if (e.morto) {
            val k = suave(e.tMorto / 0.5f)
            raiz = Math3D.mul(
                Math3D.mul(Math3D.translate(e.px, 0.28f * k, e.pz), Math3D.rotY(e.face)),
                Math3D.rotX(-k * 1.5f)
            )
        } else if (e.rolando) {
            val ang = (e.rolarT / ColiseuEngine.ROLAR_DUR) * 2f * PI.toFloat()
            raiz = Math3D.mul(
                Math3D.mul(Math3D.mul(Math3D.translate(e.px, 0.6f, e.pz), Math3D.rotY(e.face)), Math3D.rotX(ang)),
                Math3D.translate(0f, -0.6f, 0f)
            )
        }
        if (!e.morto && !e.rolando) sombra(e.px, e.pz, 0.95f) else if (e.rolando) sombra(e.px, e.pz, 0.8f)

        when (e.personagem) {
            Personagem.CAVALEIRO -> corpoCavaleiro(e, raiz)
            Personagem.LUTADOR -> corpoLutador(e, raiz)
            Personagem.ASSASSINO -> corpoAssassino(e, raiz)
        }
    }

    /** Ângulo do braço da arma: repouso, levanta antes do golpe, desce no golpe e volta ao repouso. */
    private fun anguloBraco(e: ColiseuEngine, repouso: Float, levantar: Float, golpe: Float): Float {
        val t = e.ataqueT
        return when {
            t < 0f -> repouso
            t < e.golpeInicio -> lerp(repouso, levantar, suave(t / e.golpeInicio))
            t < e.golpeFim -> lerp(levantar, golpe, (t - e.golpeInicio) / (e.golpeFim - e.golpeInicio))
            else -> lerp(golpe, repouso, suave((t - e.golpeFim) / (e.ataqueDur - e.golpeFim)))
        }
    }

    /** Cavaleiro: armadura de aço, elmo com crista, espada e escudo. */
    private fun corpoCavaleiro(e: ColiseuEngine, raiz: FloatArray) {
        val aco = 0x8E98A6
        val acoEscuro = 0x5C6573
        val couro = 0x4A3020
        val ouro = 0xE3B341
        val balanco = sin(e.andarFase) * 0.85f * e.andarFracao

        // pernas e botas
        for (lado in intArrayOf(-1, 1)) {
            val perna = membro(raiz, lado * 0.17f, 0.78f, 0f, balanco * lado, 0.78f, 0.24f, 0.26f, acoEscuro)
            caixa(perna, 0f, -0.72f, 0.05f, 0.27f, 0.16f, 0.4f, couro)
        }

        // corpo
        caixa(raiz, 0f, 1.12f, 0f, 0.62f, 0.62f, 0.36f, aco)
        caixa(raiz, 0f, 1.12f, 0.19f, 0.34f, 0.58f, 0.03f, 0xB3262B)   // túnica na frente
        caixa(raiz, 0f, 1.12f, -0.19f, 0.34f, 0.58f, 0.03f, 0xB3262B)  // e nas costas
        caixa(raiz, 0f, 0.84f, 0f, 0.66f, 0.1f, 0.4f, couro)          // cinto
        caixa(raiz, 0f, 0.84f, 0.21f, 0.14f, 0.12f, 0.04f, ouro)       // fivela
        caixa(raiz, -0.4f, 1.43f, 0f, 0.28f, 0.2f, 0.34f, aco, Meshes.SPHERE)
        caixa(raiz, 0.4f, 1.43f, 0f, 0.28f, 0.2f, 0.34f, aco, Meshes.SPHERE)

        // cabeça e elmo
        caixa(raiz, 0f, 1.62f, 0f, 0.3f, 0.3f, 0.3f, 0xF0B58A)
        caixa(raiz, 0f, 1.72f, 0f, 0.38f, 0.28f, 0.38f, aco, Meshes.CYLINDER)
        caixa(raiz, 0f, 1.63f, -0.12f, 0.34f, 0.34f, 0.1f, aco)       // elmo cobre a nuca (a câmera fica atrás)
        caixa(raiz, -0.17f, 1.63f, -0.02f, 0.05f, 0.32f, 0.3f, aco)    // e as laterais
        caixa(raiz, 0.17f, 1.63f, -0.02f, 0.05f, 0.32f, 0.3f, aco)
        caixa(raiz, 0f, 1.62f, 0.17f, 0.3f, 0.05f, 0.03f, 0x1B1220)    // fresta do elmo
        caixa(raiz, 0f, 1.62f, 0.18f, 0.05f, 0.22f, 0.03f, acoEscuro)  // protetor do nariz
        caixa(raiz, 0f, 1.94f, -0.02f, 0.07f, 0.2f, 0.36f, 0xB3262B)   // crista vermelha

        // braço da espada
        val repouso = -0.9f - sin(e.andarFase + PI.toFloat()) * 0.25f * e.andarFracao
        val braco = membro(raiz, 0.43f, 1.42f, 0f, anguloBraco(e, repouso, -2.9f, -0.55f), 0.6f, 0.18f, 0.18f, aco)
        val mao = Math3D.mul(braco, Math3D.translate(0f, -0.62f, 0f))
        caixa(mao, 0f, 0f, 0f, 0.2f, 0.2f, 0.2f, couro)
        espada(mao)

        // braço do escudo
        val braco2 = membro(raiz, -0.43f, 1.42f, 0f, -0.55f + sin(e.andarFase) * 0.15f * e.andarFracao, 0.5f, 0.17f, 0.17f, aco)
        val mao2 = Math3D.mul(braco2, Math3D.translate(-0.05f, -0.5f, 0.1f))
        caixa(mao2, 0f, 0f, 0f, 0.52f, 0.62f, 0.07f, 0x2F5FB0, Meshes.CUBE, 0.55f)
        caixa(mao2, 0f, 0f, 0.045f, 0.07f, 0.5f, 0.02f, ouro, Meshes.CUBE, 0.55f)
        caixa(mao2, 0f, 0f, 0.045f, 0.4f, 0.07f, 0.02f, ouro, Meshes.CUBE, 0.55f)
        caixa(mao2, 0f, 0f, 0.06f, 0.14f, 0.14f, 0.05f, ouro, Meshes.SPHERE, 0.55f)
    }

    /** Lutador: corpo forte, sem elmo, com um machado de lâmina dupla de cabo meio longo. */
    private fun corpoLutador(e: ColiseuEngine, raiz: FloatArray) {
        val pele = 0xD9A074
        val couro = 0x6B4423
        val couroEscuro = 0x4A3020
        val bronze = 0xB3722B
        val metal = 0x8E98A6
        val balanco = sin(e.andarFase) * 0.8f * e.andarFracao

        for (lado in intArrayOf(-1, 1)) {
            val perna = membro(raiz, lado * 0.2f, 0.82f, 0f, balanco * lado, 0.82f, 0.28f, 0.3f, couroEscuro)
            caixa(perna, 0f, -0.76f, 0.05f, 0.3f, 0.18f, 0.42f, couro)
        }

        // corpo largo e peito de couro
        caixa(raiz, 0f, 1.14f, 0f, 0.78f, 0.66f, 0.42f, pele)
        caixa(raiz, 0f, 1.2f, 0.21f, 0.6f, 0.5f, 0.05f, couro)
        caixa(raiz, 0f, 0.86f, 0f, 0.8f, 0.12f, 0.46f, couroEscuro)
        caixa(raiz, 0f, 0.86f, 0.25f, 0.16f, 0.14f, 0.05f, bronze)
        caixa(raiz, -0.52f, 1.46f, 0f, 0.32f, 0.24f, 0.4f, metal, Meshes.SPHERE)
        caixa(raiz, 0.52f, 1.46f, 0f, 0.32f, 0.24f, 0.4f, metal, Meshes.SPHERE)

        // cabeça com faixa vermelha
        caixa(raiz, 0f, 1.66f, 0f, 0.34f, 0.34f, 0.34f, pele)
        caixa(raiz, 0f, 1.78f, 0f, 0.38f, 0.1f, 0.38f, 0xB3262B)
        caixa(raiz, 0f, 1.6f, 0.18f, 0.12f, 0.06f, 0.03f, 0x1B1220)

        // braço livre
        membro(raiz, -0.52f, 1.44f, 0f, -0.5f + balanco * 0.3f, 0.56f, 0.2f, 0.2f, pele)
        // braço do machado
        val repouso = -1.05f - sin(e.andarFase + PI.toFloat()) * 0.2f * e.andarFracao
        val braco = membro(raiz, 0.55f, 1.44f, 0f, anguloBraco(e, repouso, -3.05f, -0.4f), 0.64f, 0.21f, 0.21f, pele)
        val mao = Math3D.mul(braco, Math3D.translate(0f, -0.66f, 0f))
        caixa(mao, 0f, 0f, 0f, 0.22f, 0.22f, 0.22f, couroEscuro)
        machado(mao)
    }

    /** Assassino: roupa escura e capuz, uma adaga em cada mão e o arremesso à distância. */
    private fun corpoAssassino(e: ColiseuEngine, raiz: FloatArray) {
        val pano = 0x2B2B36
        val panoEscuro = 0x1C1C24
        val pele = 0xE0B48C
        val roxo = 0x6A3FA0
        val balanco = sin(e.andarFase) * 0.9f * e.andarFracao

        for (lado in intArrayOf(-1, 1)) {
            val perna = membro(raiz, lado * 0.15f, 0.74f, 0f, balanco * lado, 0.74f, 0.2f, 0.22f, panoEscuro)
            caixa(perna, 0f, -0.68f, 0.05f, 0.23f, 0.15f, 0.36f, panoEscuro)
        }

        caixa(raiz, 0f, 1.06f, 0f, 0.5f, 0.6f, 0.32f, pano)
        caixa(raiz, 0f, 1.02f, 0.17f, 0.3f, 0.5f, 0.03f, roxo)
        caixa(raiz, 0f, 0.78f, 0f, 0.54f, 0.1f, 0.36f, panoEscuro)
        caixa(raiz, -0.34f, 1.34f, 0f, 0.24f, 0.18f, 0.3f, pano, Meshes.SPHERE)
        caixa(raiz, 0.34f, 1.34f, 0f, 0.24f, 0.18f, 0.3f, pano, Meshes.SPHERE)

        // cabeça, capuz e máscara
        caixa(raiz, 0f, 1.52f, 0f, 0.28f, 0.28f, 0.28f, pele)
        caixa(raiz, 0f, 1.62f, -0.04f, 0.36f, 0.3f, 0.34f, pano)
        caixa(raiz, 0f, 1.56f, -0.16f, 0.34f, 0.34f, 0.08f, pano)
        caixa(raiz, -0.1f, 1.68f, 0.12f, 0.14f, 0.05f, 0.05f, roxo)
        caixa(raiz, 0.1f, 1.68f, 0.12f, 0.14f, 0.05f, 0.05f, roxo)
        caixa(raiz, 0f, 1.48f, 0.15f, 0.22f, 0.22f, 0.05f, panoEscuro)

        // braço esquerdo com a adaga
        val repousoL = -0.75f + balanco * 0.3f
        val bracoEsq = membro(raiz, -0.4f, 1.32f, 0f, anguloBraco(e, repousoL, -2.4f, 0.5f), 0.52f, 0.15f, 0.15f, pano)
        val maoEsq = Math3D.mul(bracoEsq, Math3D.translate(0f, -0.54f, 0f))
        caixa(maoEsq, 0f, 0f, 0f, 0.16f, 0.16f, 0.16f, pele)
        adaga(maoEsq)

        // braço direito: é ele que arremessa (no meio do arremesso a adaga some da mão)
        val repouso = -0.9f - sin(e.andarFase + PI.toFloat()) * 0.2f * e.andarFracao
        val bracoDir = membro(raiz, 0.4f, 1.32f, 0f, anguloBraco(e, repouso, -2.6f, 0.7f), 0.52f, 0.15f, 0.15f, pano)
        val maoDir = Math3D.mul(bracoDir, Math3D.translate(0f, -0.54f, 0f))
        caixa(maoDir, 0f, 0f, 0f, 0.16f, 0.16f, 0.16f, pele)
        if (e.ataqueT < 0f || e.ataqueT < e.golpeInicio) adaga(maoDir)
    }

    /** Espada presa na mão: a lâmina continua o braço (para baixo no sistema da mão). */
    private fun espada(mao: FloatArray) {
        val ouro = 0xE3B341
        caixa(mao, 0f, 0.04f, 0f, 0.05f, 0.24f, 0.05f, 0x5A3A20)                  // cabo
        caixa(mao, 0f, 0.18f, 0f, 0.09f, 0.09f, 0.09f, ouro, Meshes.SPHERE)        // pomo
        caixa(mao, 0f, -0.14f, 0f, 0.36f, 0.05f, 0.1f, ouro)                       // guarda
        caixa(mao, 0f, -0.6f, 0f, 0.085f, 0.9f, 0.03f, 0xDCE3EA)                   // lâmina
        caixa(mao, 0f, -0.6f, 0.017f, 0.03f, 0.88f, 0.012f, 0xA9B4C0)              // friso
        caixa(mao, 0f, -1.1f, 0f, 0.085f, 0.14f, 0.03f, 0xDCE3EA, Meshes.PYRAMID, PI.toFloat()) // ponta
    }

    /** Machado de lâmina dupla com cabo meio longo, preso na mão (cabo para baixo). */
    private fun machado(mao: FloatArray) {
        val madeira = 0x6B4A2E
        val aco = 0xC3CAD3
        val acoEscuro = 0x8E98A6
        caixa(mao, 0f, 0.1f, 0f, 0.08f, 0.22f, 0.08f, madeira, Meshes.CYLINDER)      // ponta de cima do cabo
        caixa(mao, 0f, -0.42f, 0f, 0.08f, 1.05f, 0.08f, madeira, Meshes.CYLINDER)    // cabo
        caixa(mao, 0f, -0.98f, 0f, 0.17f, 0.22f, 0.17f, acoEscuro, Meshes.CYLINDER)  // olhal
        caixa(mao, 0.3f, -0.98f, 0f, 0.44f, 0.6f, 0.07f, aco)                        // lâmina direita
        caixa(mao, -0.3f, -0.98f, 0f, 0.44f, 0.6f, 0.07f, aco)                       // lâmina esquerda
        caixa(mao, 0f, -1.16f, 0f, 0.09f, 0.2f, 0.09f, acoEscuro, Meshes.CONE, PI.toFloat()) // ponta
    }

    /** Adaga presa na mão: lâmina curta continuando o braço (para baixo no sistema da mão). */
    private fun adaga(mao: FloatArray) {
        val aco = 0xDCE3EA
        caixa(mao, 0f, 0.05f, 0f, 0.05f, 0.16f, 0.05f, 0x2B2B33)                          // cabo
        caixa(mao, 0f, -0.05f, 0f, 0.18f, 0.05f, 0.06f, 0x8E98A6)                         // guarda
        caixa(mao, 0f, -0.26f, 0f, 0.05f, 0.36f, 0.02f, aco)                              // lâmina
        caixa(mao, 0f, -0.5f, 0f, 0.05f, 0.1f, 0.02f, aco, Meshes.PYRAMID, PI.toFloat())  // ponta
    }

    /** Adagas lançadas pelo assassino, voando na direção do arremesso. */
    private fun adagasVoando(e: ColiseuEngine) {
        flash = 0f; aviso = 0f; dano = 0f
        for (a in e.adagas) {
            val ang = atan2(a.vx, a.vz)
            val m = Math3D.mul(
                Math3D.mul(Math3D.translate(a.x, 1.05f, a.z), Math3D.rotY(ang)),
                Math3D.mul(Math3D.rotX(-(PI / 2).toFloat()), Math3D.scale(0.1f, 0.5f, 0.1f))
            )
            peca(Meshes.CONE, m, 0xDCE3EA)
        }
    }

    // ============================================================ goblins

    private fun goblin(g: Goblin, e: ColiseuEngine) {
        flash = if (g.brilho > 0f) min(1f, g.brilho / 0.16f) * 0.8f else 0f
        dano = 0f
        aviso = if (g.vivo && g.estado == GoblinEstado.PREPARAR) min(1f, g.tEstado / g.preparo) * 0.6f else 0f

        val chefe = g.chefe
        val pele = if (chefe) 0x4C8A2A else 0x6BAA3A
        val peleEscura = if (chefe) 0x366B1D else 0x4F8A2E
        val colete = if (chefe) 0x3A3F4A else 0x6B4423
        val pano = 0x8B2E2E

        var escala = g.escala
        val bob = abs(sin(g.andar)) * 0.05f * g.andarFracao
        var raiz = Math3D.mul(Math3D.translate(g.x, bob, g.z), Math3D.rotY(g.face))
        if (!g.vivo) {
            val k = suave(g.morrendo / 0.5f)
            val some = suave((g.morrendo - 1.1f) / 0.5f)
            escala *= (1f - some)
            raiz = Math3D.mul(
                Math3D.mul(Math3D.translate(g.x, 0.15f * k * g.escala, g.z), Math3D.rotY(g.face)),
                Math3D.rotX(-k * 1.45f)
            )
        } else {
            sombra(g.x, g.z, 0.95f * g.escala)
        }
        raiz = Math3D.mul(raiz, Math3D.scale(escala, escala, escala))
        if (escala < 0.02f) return

        val balanco = sin(g.andar) * 0.9f * g.andarFracao
        for (lado in intArrayOf(-1, 1)) {
            val perna = membro(raiz, lado * 0.12f, 0.45f, 0f, balanco * lado, 0.45f, 0.16f, 0.18f, peleEscura)
            caixa(perna, 0f, -0.42f, 0.04f, 0.18f, 0.08f, 0.28f, 0x4A3020)
        }
        // corpo
        caixa(raiz, 0f, 0.75f, 0f, 0.46f, 0.46f, 0.3f, pele)
        caixa(raiz, 0f, 0.72f, 0f, 0.5f, 0.3f, 0.34f, colete)
        caixa(raiz, 0f, 0.5f, 0.16f, 0.3f, 0.22f, 0.04f, pano)
        if (chefe) {
            caixa(raiz, -0.3f, 0.98f, 0f, 0.26f, 0.2f, 0.3f, 0x777E88, Meshes.SPHERE)
            caixa(raiz, 0.3f, 0.98f, 0f, 0.26f, 0.2f, 0.3f, 0x777E88, Meshes.SPHERE)
        }
        // cabeça
        caixa(raiz, 0f, 1.12f, 0f, 0.42f, 0.36f, 0.38f, pele)
        caixa(raiz, -0.3f, 1.17f, 0f, 0.15f, 0.36f, 0.12f, pele, Meshes.CONE, 0f, 0f, (PI / 2).toFloat() - 0.35f)
        caixa(raiz, 0.3f, 1.17f, 0f, 0.15f, 0.36f, 0.12f, pele, Meshes.CONE, 0f, 0f, -(PI / 2).toFloat() + 0.35f)
        caixa(raiz, 0f, 1.1f, 0.23f, 0.11f, 0.18f, 0.11f, peleEscura, Meshes.CONE, (PI / 2).toFloat())   // nariz
        caixa(raiz, -0.1f, 1.19f, 0.2f, 0.08f, 0.06f, 0.03f, 0xFFE066)
        caixa(raiz, 0.1f, 1.19f, 0.2f, 0.08f, 0.06f, 0.03f, 0xFFE066)
        caixa(raiz, -0.1f, 1.19f, 0.22f, 0.03f, 0.04f, 0.02f, 0x1B1220)
        caixa(raiz, 0.1f, 1.19f, 0.22f, 0.03f, 0.04f, 0.02f, 0x1B1220)
        caixa(raiz, 0f, 1.02f, 0.2f, 0.2f, 0.05f, 0.03f, 0x2B1A14)                                          // boca
        caixa(raiz, -0.07f, 1.04f, 0.21f, 0.04f, 0.09f, 0.04f, 0xF2EBD3, Meshes.CONE)                       // presas
        caixa(raiz, 0.07f, 1.04f, 0.21f, 0.04f, 0.09f, 0.04f, 0xF2EBD3, Meshes.CONE)
        if (chefe) {
            caixa(raiz, 0f, 1.3f, 0f, 0.46f, 0.16f, 0.42f, 0x777E88, Meshes.CYLINDER)                      // elmo
            caixa(raiz, -0.26f, 1.4f, 0f, 0.1f, 0.3f, 0.1f, 0xEDE3C8, Meshes.CONE, 0f, 0f, 0.6f)           // chifres
            caixa(raiz, 0.26f, 1.4f, 0f, 0.1f, 0.3f, 0.1f, 0xEDE3C8, Meshes.CONE, 0f, 0f, -0.6f)
            caixa(raiz, -0.1f, 1.12f, 0.2f, 0.04f, 0.14f, 0.02f, 0xB3261E)                                  // pintura de guerra
            caixa(raiz, 0.1f, 1.12f, 0.2f, 0.04f, 0.14f, 0.02f, 0xB3261E)
        }
        // braço livre
        membro(raiz, -0.3f, 0.95f, 0f, -0.5f - balanco * 0.3f, 0.5f, 0.13f, 0.13f, pele)
        // braço do porrete
        val repouso = -0.75f + balanco * 0.3f
        val angBraco = if (!g.vivo) repouso else when (g.estado) {
            GoblinEstado.PREPARAR -> lerp(repouso, -2.9f, suave(g.tEstado / g.preparo))
            GoblinEstado.GOLPE -> lerp(-2.9f, -0.65f, g.tEstado / 0.2f)
            GoblinEstado.RECUPERAR -> lerp(-0.65f, repouso, suave(g.tEstado / g.recuperar))
            else -> repouso
        }
        val braco = membro(raiz, 0.3f, 0.95f, 0f, angBraco, 0.5f, 0.13f, 0.13f, pele)
        val mao = Math3D.mul(braco, Math3D.translate(0f, -0.5f, 0f))
        caixa(mao, 0f, -0.2f, 0f, 0.07f, 0.5f, 0.07f, 0x7A5230, Meshes.CYLINDER)     // cabo
        caixa(mao, 0f, -0.52f, 0f, 0.22f, 0.32f, 0.22f, 0x5A3A20, Meshes.CYLINDER)  // cabeça do porrete
        for (i in 0 until 4) {                                                       // pregos
            val a = (PI / 2 * i).toFloat()
            caixa(mao, cos(a) * 0.12f, -0.5f, sin(a) * 0.12f, 0.06f, 0.1f, 0.06f, 0xBFC4C9, Meshes.CONE, 0f, 0f, 0f)
        }
        if (chefe) caixa(mao, 0f, -0.66f, 0f, 0.14f, 0.14f, 0.14f, 0xBFC4C9, Meshes.CONE, PI.toFloat())
        flash = 0f; aviso = 0f
    }

    // ============================================================ chamas e partículas

    private fun chamas(t: Float) {
        flash = 0f; aviso = 0f; dano = 0f
        for (k in 0 until 8) {
            val a = ((22.5 + 45.0 * k) * PI / 180.0).toFloat()
            val x = cos(a) * 17.85f
            val z = sin(a) * 17.85f
            val f = 0.55f + 0.14f * sin(t * 11f + k * 1.7f) + 0.06f * sin(t * 23f + k)
            val m = Math3D.mul(Math3D.translate(x, 2.95f + f * 0.2f, z), Math3D.scale(0.34f, f, 0.34f))
            saida.add(DrawItem(Meshes.CONE, m, 1f, 0.6f, 0.12f))
            val m2 = Math3D.mul(Math3D.translate(x, 2.9f + f * 0.12f, z), Math3D.scale(0.2f, f * 0.65f, 0.2f))
            saida.add(DrawItem(Meshes.CONE, m2, 1f, 0.9f, 0.4f))
        }
    }

    private fun particulas(e: ColiseuEngine) {
        for (p in e.particulas) {
            val s = p.tamanho * (0.4f + 0.6f * (p.vida / p.vidaMax))
            val m = Math3D.mul(Math3D.translate(p.x, p.y, p.z), Math3D.scale(s, s, s))
            saida.add(DrawItem(Meshes.CUBE, m, p.r, p.g, p.b))
        }
    }

    // ============================================================ o coliseu (estático)

    private fun construirArena(): Mesh {
        val mb = MeshBuilder()
        val rng = Random(11)
        val dois_pi = (2.0 * PI).toFloat()

        fun rgb(hex: Int) = floatArrayOf(
            ((hex shr 16) and 255) / 255f, ((hex shr 8) and 255) / 255f, (hex and 255) / 255f
        )

        fun add(mesh: Mesh, m: FloatArray, hex: Int) {
            val c = rgb(hex)
            mb.append(mesh, m, c[0], c[1], c[2])
        }

        fun caixaEstatica(
            pai: FloatArray, tx: Float, ty: Float, tz: Float, sx: Float, sy: Float, sz: Float, hex: Int,
            mesh: Mesh = Meshes.CUBE, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f
        ) {
            var m = Math3D.mul(pai, Math3D.translate(tx, ty, tz))
            if (rz != 0f) m = Math3D.mul(m, Math3D.rotZ(rz))
            if (ry != 0f) m = Math3D.mul(m, Math3D.rotY(ry))
            if (rx != 0f) m = Math3D.mul(m, Math3D.rotX(rx))
            add(mesh, Math3D.mul(m, Math3D.scale(sx, sy, sz)), hex)
        }

        /** Sistema de coordenadas de quem está na parede no ângulo a: z para fora, x ao longo da parede. */
        fun naParede(a: Float, r: Float) =
            Math3D.mul(Math3D.translate(cos(a) * r, 0f, sin(a) * r), Math3D.rotY((PI / 2).toFloat() - a))

        /** Anel de caixas lado a lado em volta do centro. */
        fun anel(
            n: Int, rc: Float, prof: Float, altura: Float, yBase: Float, corA: Int, corB: Int,
            pular: (Float) -> Boolean = { false }, alternar: Boolean = false
        ) {
            for (i in 0 until n) {
                val a = dois_pi * (i + 0.5f) / n
                if (pular(a)) continue
                if (alternar && i % 2 == 1) continue
                val w = 2f * rc * tan(PI / n).toFloat() + 0.05f
                caixaEstatica(naParede(a, rc), 0f, yBase + altura / 2f, 0f, w, altura, prof, if (i % 2 == 0) corA else corB)
            }
        }

        // ---- chão de areia
        val n = 48
        fun ponto(r: Float, a: Float, y: Float) = floatArrayOf(cos(a) * r, y, sin(a) * r)
        fun faixa(r0: Float, r1: Float, y: Float, cor: (Int) -> Int) {
            for (s in 0 until n) {
                val a0 = dois_pi * s / n
                val a1 = dois_pi * (s + 1) / n
                val p0 = ponto(r0, a0, y); val p1 = ponto(r0, a1, y)
                val p2 = ponto(r1, a1, y); val p3 = ponto(r1, a0, y)
                val c = rgb(cor(s))
                mb.quad(p0[0], p0[1], p0[2], p1[0], p1[1], p1[2], p2[0], p2[1], p2[2], p3[0], p3[1], p3[2], c[0], c[1], c[2])
            }
        }
        for (k in 0 until 6) {
            faixa(k * 3f, (k + 1) * 3f, 0f) { s ->
                if (k == 5) (if (s % 2 == 0) 0xBFA672 else 0xB59C69)
                else if ((k + s) % 2 == 0) 0xDEC590 else 0xD3B882
            }
        }
        faixa(2.3f, 2.9f, 0.04f) { 0x8E3B2E }     // anel vermelho no centro
        faixa(0f, 0.9f, 0.04f) { 0xC9A24A }       // medalhão dourado
        faixa(14.5f, 14.8f, 0.04f) { 0x9B7F55 }   // linha perto da parede
        repeat(16) {
            val a = rng.nextFloat() * dois_pi
            val r = 5f + rng.nextFloat() * 10f
            val s = 0.25f + rng.nextFloat() * 0.35f
            caixaEstatica(
                Math3D.translate(cos(a) * r, 0f, sin(a) * r), 0f, s * 0.3f, 0f, s * 1.3f, s * 0.6f, s,
                0x9A8F7D, Meshes.SPHERE, 0f, rng.nextFloat() * 6f
            )
        }

        // ---- muro da arena (com 4 portões)
        val portoes = ColiseuEngine.PORTOES
        fun perto(a: Float): Boolean = portoes.any { g ->
            var d = abs(a - g) % dois_pi
            if (d > PI) d = dois_pi - d
            d < 0.11f
        }
        anel(48, 18.4f, 0.8f, 3.2f, 0f, 0x9A8F80, 0x8B8174, pular = { perto(it) })
        anel(48, 18.4f, 0.8f, 0.6f, 3.2f, 0xA89C8A, 0xA89C8A, pular = { perto(it) }, alternar = true)
        anel(48, 18.4f, 0.9f, 0.18f, 3.2f, 0x6E6556, 0x6E6556, pular = { perto(it) })

        for (g in portoes) {
            val raiz = naParede(g, 18.4f)
            caixaEstatica(raiz, 0f, 1.4f, 0.2f, 4.1f, 2.8f, 0.15f, 0x1E140E)             // porta escura
            caixaEstatica(raiz, -2.25f, 1.9f, 0f, 0.8f, 3.8f, 1.1f, 0xA89C8A)             // pilares
            caixaEstatica(raiz, 2.25f, 1.9f, 0f, 0.8f, 3.8f, 1.1f, 0xA89C8A)
            caixaEstatica(raiz, 0f, 3.65f, 0f, 5.4f, 0.75f, 1.1f, 0xB5A995)               // verga
            caixaEstatica(raiz, 0f, 4.25f, 0f, 1.0f, 0.5f, 0.9f, 0xE3B341)                // brasão
            for (i in 0 until 7) {                                                         // grade levantada
                caixaEstatica(raiz, -1.8f + i * 0.6f, 2.55f, -0.35f, 0.09f, 0.95f, 0.09f, 0x4A4A52)
                caixaEstatica(raiz, -1.8f + i * 0.6f, 2.0f, -0.35f, 0.09f, 0.2f, 0.09f, 0x4A4A52, Meshes.PYRAMID, PI.toFloat())
            }
            caixaEstatica(raiz, 0f, 3.0f, -0.35f, 3.9f, 0.1f, 0.1f, 0x4A4A52)
        }

        // tochas e bandeiras
        for (k in 0 until 8) {
            val a = ((22.5 + 45.0 * k) * PI / 180.0).toFloat()
            val r = naParede(a, 17.85f)
            caixaEstatica(r, 0f, 2.3f, 0f, 0.12f, 0.7f, 0.12f, 0x3B2A1C)
            caixaEstatica(r, 0f, 2.72f, 0f, 0.26f, 0.16f, 0.26f, 0x2E2E34, Meshes.CYLINDER)
        }
        for (k in 0 until 4) {
            val r = naParede((PI / 2 * k).toFloat(), 17.96f)
            caixaEstatica(r, 0f, 1.85f, 0f, 1.2f, 2.4f, 0.05f, 0xA3202B)
            caixaEstatica(r, 0f, 3.0f, 0f, 1.3f, 0.1f, 0.07f, 0xE3B341)
            caixaEstatica(r, 0f, 0.7f, 0f, 1.2f, 0.12f, 0.07f, 0xE3B341)
            caixaEstatica(r, 0f, 1.9f, 0.04f, 0.4f, 0.4f, 0.04f, 0xE3B341, Meshes.CUBE, 0f, 0f, (PI / 4).toFloat())
        }

        // ---- arquibancadas
        val alturas = floatArrayOf(3.6f, 5.2f, 6.8f)
        for (t in 0 until 3) {
            val rc = 20.2f + 2.8f * t
            anel(64, rc, 2.9f, alturas[t], 0f, 0x8E8575, 0x7E7566)
            anel(64, rc, 2.9f, 0.14f, alturas[t], 0x6B4A2E, 0x5E4129)
        }

        // ---- plateia
        val roupas = intArrayOf(0xC0392B, 0x2E86C1, 0x27AE60, 0xF1C40F, 0x8E44AD, 0xE67E22, 0xECF0F1, 0x16A085, 0xD35400, 0x5D6D7E)
        val peles = intArrayOf(0xF0B58A, 0xD9A074, 0xA9734B, 0x7A4B30)
        val tiposPorTier = intArrayOf(52, 58, 64)
        for (t in 0 until 3) {
            for (i in 0 until tiposPorTier[t]) {
                val a = dois_pi * (i + rng.nextFloat() * 0.7f) / tiposPorTier[t]
                val r = 20.2f + 2.8f * t + (rng.nextFloat() - 0.5f) * 1.4f
                val y = alturas[t] + 0.14f
                val raiz = Math3D.mul(
                    Math3D.translate(cos(a) * r, y, sin(a) * r),
                    Math3D.rotY(kotlin.math.atan2(-cos(a), -sin(a)))
                )
                val roupa = roupas[rng.nextInt(roupas.size)]
                caixaEstatica(raiz, 0f, 0.4f, 0f, 0.5f, 0.62f, 0.34f, roupa)
                caixaEstatica(raiz, 0f, 0.9f, 0f, 0.28f, 0.28f, 0.28f, peles[rng.nextInt(peles.size)])
                if (rng.nextFloat() < 0.4f) caixaEstatica(raiz, 0f, 1.1f, 0f, 0.34f, 0.1f, 0.34f, roupas[rng.nextInt(roupas.size)])
                if (rng.nextFloat() < 0.35f) {
                    caixaEstatica(raiz, -0.3f, 0.85f, 0.05f, 0.12f, 0.5f, 0.12f, roupa, Meshes.CUBE, 0f, 0f, -0.35f)
                    caixaEstatica(raiz, 0.3f, 0.85f, 0.05f, 0.12f, 0.5f, 0.12f, roupa, Meshes.CUBE, 0f, 0f, 0.35f)
                }
            }
        }

        // ---- colunas e muro de fora
        for (k in 0 until 12) {
            val a = (PI / 6 * k).toFloat()
            val c = Math3D.translate(cos(a) * 19.2f, 0f, sin(a) * 19.2f)
            caixaEstatica(c, 0f, 0.25f, 0f, 1.5f, 0.5f, 1.5f, 0xC8BDA8)
            caixaEstatica(c, 0f, 4.7f, 0f, 0.95f, 8.4f, 0.95f, 0xB9AE9A, Meshes.CYLINDER)
            caixaEstatica(c, 0f, 9.1f, 0f, 1.5f, 0.5f, 1.5f, 0xC8BDA8)
        }
        anel(64, 27.7f, 1.0f, 11f, 0f, 0x9A8F80, 0x8B8174)
        anel(64, 27.7f, 1.2f, 0.7f, 11f, 0xA89C8A, 0xA89C8A, alternar = true)
        for (k in 0 until 32) {
            val a = dois_pi * (k + 0.5f) / 32
            val r = naParede(a, 27.1f)
            caixaEstatica(r, 0f, 9.0f, 0f, 1.3f, 2.2f, 0.1f, 0x2A2118)
            caixaEstatica(r, 0f, 10.1f, 0f, 1.3f, 0.3f, 0.12f, 0x2A2118, Meshes.CYLINDER, (PI / 2).toFloat())
        }
        return mb.build()
    }
}

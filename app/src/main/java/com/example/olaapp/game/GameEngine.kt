package com.example.olaapp.game

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/*
 * Motor do jogo: não usa nada do Android, então dá para testar no computador.
 * Unidades: 1 unidade = 1 pixel do desenho (1 bloco do cenário = 16). A tela tem 144 de altura.
 */

open class Actor(
    var x: Float, var y: Float,
    val w: Float, val h: Float,       // tamanho da caixa de colisão
    val offX: Float, val offY: Float  // onde a caixa fica dentro do desenho de 16x16
) {
    var vx = 0f
    var vy = 0f
    var onGround = false
    var hitWall = false
    var facing = 1

    val left get() = x + offX
    val right get() = x + offX + w
    val top get() = y + offY
    val bottom get() = y + offY + h
}

enum class MonsterType(
    val hp: Int, val points: Int,
    val w: Float, val h: Float, val offX: Float, val offY: Float,
    val particleColor: Int
) {
    SLIME(1, 10, 12f, 10f, 2f, 5f, 0xFF78D65B.toInt()),
    SKELETON(2, 25, 8f, 14f, 4f, 2f, 0xFFF5F5F0.toInt()),
    BAT(1, 15, 12f, 8f, 2f, 3f, 0xFF9A58D0.toInt())
}

/**
 * Avisos de som da partida. O motor só avisa o que aconteceu; quem escolhe e toca o efeito
 * é a tela (veja [GameEngine.aoSom]), porque o motor não conhece nada do Android.
 */
enum class EfeitoJogo { PULO, GOLPE, ACERTO, MORRE, DOR, PEGOU, FIM }

class Monster(val type: MonsterType, x: Float, y: Float) :
    Actor(x, y, type.w, type.h, type.offX, type.offY) {
    var hp = type.hp
    var hitFlash = 0f
    var stun = 0f
    var lastSwing = -1
    var animT = 0f
    var baseY = y
    var dir = 1
}

class Platform(val x: Float, val y: Float, val w: Float) {
    val h = 8f
}

class Pickup(val x: Float, val y: Float)

class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val color: Int)

class GameEngine(seed: Long, val viewW: Float) {

    companion object {
        const val TILE = 16f
        const val VIEW_H = 144f
        const val ROWS = 9
        const val STEP = 1f / 60f

        const val GRAVITY = 900f
        const val MAX_FALL = 420f
        const val RUN_SPEED = 100f
        const val JUMP_V = 330f
        const val MAX_HP = 3

        const val ATTACK_DUR = 0.28f
        const val HIT_START = 0.08f
        const val HIT_END = 0.20f

        private const val EPS = 0.001f
        private const val START_ROW = 6
        private const val FULL_DIFFICULTY_COLS = 900f
    }

    private val rng = Random(seed)

    /** Quem quiser saber dos sons da partida se inscreve aqui (a tela liga isso). */
    var aoSom: ((EfeitoJogo) -> Unit)? = null

    private fun som(e: EfeitoJogo) {
        aoSom?.invoke(e)
    }

    // ---- mundo
    private val ground = ArrayList<Int>() // linha da superfície por coluna; -1 = buraco
    val platforms = ArrayList<Platform>()
    val monsters = ArrayList<Monster>()
    val pickups = ArrayList<Pickup>()
    val particles = ArrayList<Particle>()
    private var curRow = START_ROW
    private var lastWasPit = false

    // ---- herói
    val hero = Actor(3 * TILE, START_ROW * TILE - 16f, 8f, 14f, 4f, 2f)
    private val startX = hero.x
    var hp = MAX_HP; private set
    var invuln = 0f; private set
    var attackT = -1f; private set
    var swingId = 0; private set
    private var attackCd = 0f
    private var hurtStun = 0f
    private var coyote = 0f
    private var jumpBuf = 0f

    // ---- entrada
    var inLeft = false
    var inRight = false
    var jumpHeld = false

    // ---- estado
    var camX = 0f; private set
    var time = 0f; private set
    var gameOver = false; private set
    var distance = 0; private set
    var kills = 0; private set
    private var bonus = 0
    private var acc = 0f

    val score get() = distance + bonus

    init {
        generateUntil(((viewW / TILE).toInt()) + 12)
    }

    // ============================================================ entrada

    fun pressJump() { jumpBuf = 0.10f }

    fun pressAttack() {
        if (gameOver || attackT >= 0f || attackCd > 0f || hurtStun > 0f) return
        attackT = 0f
        swingId++
        attackCd = 0.38f
        som(EfeitoJogo.GOLPE)
    }

    // ============================================================ laço

    fun update(dt: Float) {
        acc += min(dt, 0.1f)
        while (acc >= STEP) {
            step(STEP)
            acc -= STEP
        }
    }

    private fun step(dt: Float) {
        time += dt
        updateParticles(dt)
        if (gameOver) return

        generateUntil(((camX + viewW) / TILE).toInt() + 14)

        updateHero(dt)
        handleAttack()
        for (m in monsters.toList()) {
            if (abs(m.x - hero.x) < viewW * 1.4f) updateMonster(m, dt)
        }
        handleContacts()
        handlePickups()
        updateCamera()
        cleanup()

        val d = ((hero.x - startX) / TILE).toInt()
        if (d > distance) distance = d
        if (hero.y > VIEW_H + 20f) { gameOver = true; som(EfeitoJogo.FIM) }
    }

    // ============================================================ herói

    private fun updateHero(dt: Float) {
        val h = hero
        if (hurtStun > 0f) hurtStun -= dt
        if (invuln > 0f) invuln -= dt
        if (attackCd > 0f) attackCd -= dt
        if (attackT >= 0f) {
            attackT += dt
            if (attackT >= ATTACK_DUR) attackT = -1f
        }
        coyote = if (h.onGround) 0.09f else coyote - dt
        jumpBuf -= dt

        if (hurtStun <= 0f) {
            val dir = (if (inRight) 1 else 0) - (if (inLeft) 1 else 0)
            val k = if (h.onGround) 14f else 8f
            h.vx += (dir * RUN_SPEED - h.vx) * min(1f, k * dt)
            if (dir != 0) h.facing = dir
            else if (abs(h.vx) < 2f) h.vx = 0f
        }

        if (jumpBuf > 0f && coyote > 0f && hurtStun <= 0f) {
            h.vy = -JUMP_V
            h.onGround = false
            coyote = 0f
            jumpBuf = 0f
            som(EfeitoJogo.PULO)
        }
        if (!jumpHeld && h.vy < -110f) h.vy = -110f // soltou o botão: pulo mais curto

        h.vy = min(h.vy + GRAVITY * dt, MAX_FALL)
        moveActor(h, dt, usePlatforms = true)

        // não dá para voltar para fora da tela
        if (h.left < camX + 1f) {
            h.x = camX + 1f - h.offX
            if (h.vx < 0f) h.vx = 0f
        }
    }

    private fun handleAttack() {
        if (attackT < HIT_START || attackT > HIT_END) return
        val h = hero
        val ax1: Float
        val ax2: Float
        if (h.facing > 0) { ax1 = h.right - 2f; ax2 = h.right + 20f } else { ax1 = h.left - 20f; ax2 = h.left + 2f }
        val ay1 = h.top - 3f
        val ay2 = h.bottom - 1f
        for (m in monsters.toList()) {
            if (m.lastSwing == swingId) continue
            if (ax2 > m.left && ax1 < m.right && ay2 > m.top && ay1 < m.bottom) {
                m.lastSwing = swingId
                m.hp--
                m.hitFlash = 0.15f
                som(EfeitoJogo.ACERTO)
                m.stun = 0.2f
                m.vx = h.facing * 130f
                if (m.type != MonsterType.BAT) m.vy = -70f
                if (m.hp <= 0) killMonster(m)
            }
        }
    }

    private fun killMonster(m: Monster) {
        monsters.remove(m)
        kills++
        bonus += m.type.points
        som(EfeitoJogo.MORRE)
        repeat(9) {
            particles.add(
                Particle(
                    m.x + 8f, m.y + 9f,
                    rng.nextFloat() * 120f - 60f, -(rng.nextFloat() * 110f + 20f),
                    0.5f, m.type.particleColor
                )
            )
        }
    }

    private fun handleContacts() {
        if (invuln > 0f) return
        val h = hero
        for (m in monsters) {
            if (h.right > m.left && h.left < m.right && h.bottom > m.top && h.top < m.bottom) {
                hp--
                invuln = 1.3f
                hurtStun = 0.25f
                attackT = -1f
                h.vx = (if (h.x < m.x) -1f else 1f) * 130f
                h.vy = -140f
                h.onGround = false
                som(EfeitoJogo.DOR)
                if (hp <= 0) { gameOver = true; som(EfeitoJogo.FIM) }
                return
            }
        }
    }

    private fun handlePickups() {
        val h = hero
        val it = pickups.iterator()
        while (it.hasNext()) {
            val p = it.next()
            if (h.right > p.x && h.left < p.x + 9f && h.bottom > p.y && h.top < p.y + 8f) {
                it.remove()
                if (hp < MAX_HP) hp++
                bonus += 25
                som(EfeitoJogo.PEGOU)
            }
        }
    }

    private fun updateCamera() {
        val target = hero.x + 8f - viewW * 0.38f
        if (target > camX) camX = target
    }

    private fun cleanup() {
        val limit = camX - 64f
        monsters.removeAll { it.x < limit || it.y > VIEW_H + 40f }
        pickups.removeAll { it.x < limit }
        platforms.removeAll { it.x + it.w < limit }
    }

    private fun updateParticles(dt: Float) {
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += GRAVITY * 0.6f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    // ============================================================ monstros

    private fun difficulty() = min(1f, distance / FULL_DIFFICULTY_COLS)

    private fun updateMonster(m: Monster, dt: Float) {
        m.animT += dt
        if (m.hitFlash > 0f) m.hitFlash -= dt
        if (m.stun > 0f) m.stun -= dt
        val diff = difficulty()

        when (m.type) {
            MonsterType.SLIME -> {
                if (m.stun <= 0f) {
                    patrol(m)
                    m.vx = m.dir * (16f + 10f * diff)
                } else m.vx *= 0.95f
                m.facing = m.dir
                walkerPhysics(m, dt)
            }
            MonsterType.SKELETON -> {
                if (m.stun <= 0f) {
                    val dx = hero.x - m.x
                    val chase = abs(dx) < 90f && abs(hero.y - m.y) < 28f
                    val speed = 24f + 14f * diff
                    if (chase) {
                        val d = if (dx > 0f) 1 else -1
                        m.dir = d
                        m.vx = if (groundAhead(m, d)) d * speed else 0f
                    } else {
                        patrol(m)
                        m.vx = m.dir * speed * 0.6f
                    }
                } else m.vx *= 0.95f
                m.facing = m.dir
                walkerPhysics(m, dt)
            }
            MonsterType.BAT -> {
                m.facing = if (hero.x > m.x) 1 else -1
                if (m.stun <= 0f) {
                    if (abs(hero.x - m.x) < viewW) m.x += m.facing * (24f + 14f * diff) * dt
                } else {
                    m.x += m.vx * dt
                    m.vx *= 0.9f
                }
                m.y = m.baseY + sin(time * 3f + m.baseY) * 10f
            }
        }
    }

    private fun patrol(m: Monster) {
        if (m.onGround && (m.hitWall || !groundAhead(m, m.dir))) m.dir = -m.dir
    }

    private fun walkerPhysics(m: Monster, dt: Float) {
        m.vy = min(m.vy + GRAVITY * dt, MAX_FALL)
        moveActor(m, dt, usePlatforms = false)
    }

    private fun groundAhead(m: Monster, dir: Int): Boolean {
        val probeX = if (dir > 0) m.right + 2f else m.left - 2f
        val tx = floor(probeX / TILE).toInt()
        val ty = floor((m.bottom + 2f) / TILE).toInt()
        return solidTile(tx, ty)
    }

    // ============================================================ física

    fun solidTile(tx: Int, ty: Int): Boolean {
        if (tx < 0) return ty >= 0
        if (ty < 0) return false
        generateUntil(tx)
        val g = ground[tx]
        return g >= 0 && ty >= g && ty < ROWS
    }

    private fun moveActor(a: Actor, dt: Float, usePlatforms: Boolean) {
        // eixo X
        a.x += a.vx * dt
        a.hitWall = false
        val t0 = floor(a.top / TILE).toInt()
        val t1 = floor((a.bottom - EPS) / TILE).toInt()
        if (a.vx > 0f) {
            val tx = floor((a.right - EPS) / TILE).toInt()
            for (ty in t0..t1) if (solidTile(tx, ty)) {
                a.x = tx * TILE - a.offX - a.w
                a.vx = 0f
                a.hitWall = true
                break
            }
        } else if (a.vx < 0f) {
            val tx = floor(a.left / TILE).toInt()
            for (ty in t0..t1) if (solidTile(tx, ty)) {
                a.x = (tx + 1) * TILE - a.offX
                a.vx = 0f
                a.hitWall = true
                break
            }
        }

        // eixo Y
        val prevBottom = a.bottom
        a.y += a.vy * dt
        a.onGround = false
        val c0 = floor(a.left / TILE).toInt()
        val c1 = floor((a.right - EPS) / TILE).toInt()
        if (a.vy >= 0f) {
            val ty = floor(a.bottom / TILE).toInt()
            for (tx in c0..c1) if (solidTile(tx, ty)) {
                a.y = ty * TILE - a.offY - a.h
                a.vy = 0f
                a.onGround = true
                break
            }
            if (usePlatforms && !a.onGround) {
                for (p in platforms) {
                    if (a.right > p.x && a.left < p.x + p.w &&
                        prevBottom <= p.y + 0.5f && a.bottom >= p.y
                    ) {
                        a.y = p.y - a.offY - a.h
                        a.vy = 0f
                        a.onGround = true
                        break
                    }
                }
            }
        } else {
            val ty = floor(a.top / TILE).toInt()
            for (tx in c0..c1) if (solidTile(tx, ty)) {
                a.y = (ty + 1) * TILE - a.offY
                a.vy = 0f
                break
            }
        }
    }

    // ============================================================ geração do mundo

    fun groundRow(col: Int): Int {
        if (col < 0) return curRow
        generateUntil(col)
        return ground[col]
    }

    private fun generateUntil(col: Int) {
        while (ground.size <= col) addSegment()
    }

    private fun addFlat(len: Int) {
        repeat(len) { ground.add(curRow) }
    }

    private fun addSegment() {
        val start = ground.size
        val diff = min(1f, start / FULL_DIFFICULTY_COLS)

        if (start < 14) { // trecho inicial tranquilo
            addFlat(14 - start)
            return
        }

        val roll = rng.nextFloat()

        // buraco
        if (!lastWasPit && roll < 0.22f + 0.13f * diff) {
            var width = 2 + rng.nextInt(2) // 2 ou 3 blocos
            if (diff > 0.6f && rng.nextFloat() < 0.25f) width = 4
            repeat(width) { ground.add(-1) }
            lastWasPit = true
            return
        }

        // degrau (nunca logo depois de um buraco)
        if (!lastWasPit && roll > 0.55f) {
            val delta = if (rng.nextFloat() < 0.2f) 2 else 1
            val up = rng.nextBoolean()
            curRow = (curRow + if (up) -delta else delta).coerceIn(4, 7)
        }

        val len = 4 + rng.nextInt(6)
        val first = ground.size
        addFlat(len)
        lastWasPit = false
        decorate(first, len, diff)
    }

    private fun decorate(first: Int, len: Int, diff: Float) {
        val surfaceY = curRow * TILE

        if (first >= 20) {
            if (len >= 5 && rng.nextFloat() < 0.35f + 0.4f * diff) {
                spawnWalker(first + 2 + rng.nextInt(len - 3), surfaceY, diff)
                if (diff > 0.5f && len >= 8 && rng.nextFloat() < 0.5f) {
                    spawnWalker(first + 2 + rng.nextInt(len - 3), surfaceY, diff)
                }
            }
            if (rng.nextFloat() < 0.12f + 0.25f * diff) {
                val bat = Monster(
                    MonsterType.BAT,
                    (first + rng.nextInt(len)) * TILE,
                    surfaceY - 44f - rng.nextInt(20)
                )
                bat.baseY = bat.y
                monsters.add(bat)
            }
        }

        if (len >= 6 && first >= 14 && rng.nextFloat() < 0.30f) {
            val px = (first + 1 + rng.nextInt(len - 5)) * TILE
            val py = surfaceY - 3 * TILE
            platforms.add(Platform(px, py, 3 * TILE))
            if (rng.nextFloat() < 0.4f) pickups.add(Pickup(px + 19f, py - 10f))
        }
    }

    private fun spawnWalker(col: Int, surfaceY: Float, diff: Float) {
        val type = if (rng.nextFloat() < 0.7f - 0.5f * diff) MonsterType.SLIME else MonsterType.SKELETON
        val m = Monster(type, col * TILE, surfaceY - (type.offY + type.h))
        m.dir = if (rng.nextBoolean()) 1 else -1
        monsters.add(m)
    }
}

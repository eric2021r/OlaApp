package com.example.olaapp.game

import android.graphics.Bitmap

/** Transforma os desenhos de texto do SpriteData em imagens (Bitmap) prontas para desenhar. */
class PixelArt {

    private fun bitmap(rows: Array<String>, trocas: Map<Char, Char> = emptyMap()): Bitmap {
        val h = rows.size
        val w = rows[0].length
        val pixels = IntArray(w * h) // 0 = transparente
        for (y in 0 until h) {
            for (x in 0 until w) {
                var letra = rows[y][x]
                if (letra == '.') continue
                letra = trocas[letra] ?: letra
                val rgb = SpriteData.PALETTE[letra] ?: continue
                pixels[y * w + x] = 0xFF000000.toInt() or rgb
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    val heroIdle = bitmap(SpriteData.HERO_IDLE)
    val heroStepA = bitmap(SpriteData.HERO_STEP_A)
    val heroStepB = bitmap(SpriteData.HERO_STEP_B)
    val heroJump = bitmap(SpriteData.HERO_JUMP)
    val sword = bitmap(SpriteData.SWORD)
    val slash = bitmap(SpriteData.SLASH)

    val slimeA = bitmap(SpriteData.SLIME_A)
    val slimeB = bitmap(SpriteData.SLIME_B)
    val skelIdle = bitmap(SpriteData.SKEL_IDLE)
    val skelStep = bitmap(SpriteData.SKEL_STEP)
    val batUp = bitmap(SpriteData.BAT_UP)
    val batDown = bitmap(SpriteData.BAT_DOWN)

    val grass = bitmap(SpriteData.GRASS)
    val dirt = bitmap(SpriteData.DIRT)
    val platform = bitmap(SpriteData.PLATFORM)

    val heart = bitmap(SpriteData.HEART)
    val heartEmpty = bitmap(SpriteData.HEART, mapOf('r' to 'G', 'w' to 'G'))
}

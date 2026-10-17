package com.example.olaapp.game

/**
 * Todos os desenhos do jogo, em pixel art, definidos como texto.
 * Cada letra é uma cor da PALETTE e o ponto (.) é transparente.
 * Todos os personagens olham para a DIREITA (a tela espelha quando vão para a esquerda).
 */
object SpriteData {

    val PALETTE: Map<Char, Int> = mapOf(
        'k' to 0x1B1220, // contorno
        'g' to 0xC9D3DC, // aço claro
        'G' to 0x7D8A99, // aço escuro
        'b' to 0x3E74D8, // azul
        'B' to 0x264A9C, // azul escuro
        's' to 0xF3BA8A, // pele
        'S' to 0xD08C62, // pele sombra
        'n' to 0x8B5A2F, // marrom
        'N' to 0x56351B, // marrom escuro
        'y' to 0xF6D43F, // dourado
        'Y' to 0xB98A1B, // dourado escuro
        'r' to 0xD9423B, // vermelho
        'R' to 0x8F2433, // vermelho escuro
        'w' to 0xF5F5F0, // branco
        'W' to 0xBFC4C9, // osso sombra
        'l' to 0x78D65B, // verde claro
        'L' to 0x3E9140, // verde
        'p' to 0x9A58D0, // roxo
        'P' to 0x5A2F86, // roxo escuro
        'e' to 0xFF3B3B, // olho vermelho
        'c' to 0x8BE3F2, // ciano
        'm' to 0x7A4B28, // terra
        'M' to 0x5C361C  // terra escura
    )

    // ---------------------------------------------------------------- HERÓI (16x16)

    val HERO_TOP = arrayOf(
        ".....kkkkk......",
        "....kggggGk.....",
        "...kggggggGk....",
        "...kgggggggGk...",
        "...kkssssssskk..",
        "...kssssskssk...",
        "...ksssssssSk...",
        "....kSsssSk.....",
        "....kkbbbbkk....",
        "...kbbbbbbbbk...",
        "...kbbbbbbbbsk..",
        "...kBbbyybbBk...",
        "...kBBBBBBBBk..."
    )

    val HERO_LEGS_IDLE = arrayOf(
        "....knnk.knnk...",
        "....knnk.knnk...",
        "...kNNNk.kNNNk.."
    )

    val HERO_LEGS_A = arrayOf(
        "...knnk...knnk..",
        "..knnk.....knk..",
        ".kNNNk.....kNNk."
    )

    val HERO_LEGS_B = arrayOf(
        ".....knnnnk.....",
        ".....knnnnk.....",
        "....kNNNNNNk...."
    )

    val HERO_LEGS_JUMP = arrayOf(
        "...knnk..knnk...",
        "...kNNk..kNNk...",
        "................"
    )

    val HERO_IDLE = HERO_TOP + HERO_LEGS_IDLE
    val HERO_STEP_A = HERO_TOP + HERO_LEGS_A
    val HERO_STEP_B = HERO_TOP + HERO_LEGS_B
    val HERO_JUMP = HERO_TOP + HERO_LEGS_JUMP

    // ---------------------------------------------------------------- ESPADA (6x17, apontando para cima)

    val SWORD = arrayOf(
        "..kk..",
        ".kwwk.",
        ".kwgk.",
        ".kwgk.",
        ".kwgk.",
        ".kwgk.",
        ".kwgk.",
        ".kwgk.",
        ".kwgk.",
        ".kgGk.",
        ".kgGk.",
        ".kgGk.",
        "kyyyyk",
        ".kYYk.",
        ".knNk.",
        ".kNNk.",
        ".kkkk."
    )

    // ---------------------------------------------------------------- EFEITO DO GOLPE (16x16)

    val SLASH = arrayOf(
        "......cc........",
        "......cwcc......",
        ".......cwwcc....",
        ".......cwwwwcc..",
        "........cwwwwwc.",
        "........cwwwwwwc",
        ".........cwwwwwc",
        ".........cwwwwwc",
        ".........cwwwwwc",
        ".........cwwwwwc",
        "........cwwwwwwc",
        "........cwwwwwc.",
        ".......cwwwwcc..",
        ".......cwwcc....",
        "......cwcc......",
        "......cc........"
    )

    // ---------------------------------------------------------------- SLIME (16x16)

    val SLIME_A = arrayOf(
        "................",
        "................",
        "................",
        "................",
        ".....kkkkkk.....",
        "....kllllllk....",
        "...kllwwllllk...",
        "..kllwwlllllLk..",
        "..klllllllllLk..",
        ".klllkklllkklLk.",
        ".klllkklllkklLk.",
        ".kllllllllllLLk.",
        ".klllllllllLLLk.",
        ".kLLLLLLLLLLLLk.",
        "..kkkkkkkkkkkk..",
        "................"
    )

    val SLIME_B = arrayOf(
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "....kkkkkkkk....",
        "..kkllllllllkk..",
        ".kllwwllllllllk.",
        "kllllllllllllllk",
        "kllkkllllllkkllk",
        "kllkkllllllkkllk",
        "kllllllllllllLLk",
        "kLLLLLLLLLLLLLLk",
        ".kkkkkkkkkkkkkk.",
        "................"
    )

    // ---------------------------------------------------------------- ESQUELETO (16x16)

    val SKEL_TOP = arrayOf(
        ".....kkkkkk.....",
        "....kwwwwwwk....",
        "...kwwwwwwwwk...",
        "...kwkkwwkkwwk..",
        "...kwkkwwkkwwk..",
        "...kwwwwkkwwwk..",
        "....kwwwwwwwk...",
        ".....kwkwkwk....",
        "......kkkkk.....",
        ".....kwwWwwk....",
        "....kwkwWwkwk...",
        ".....kwWwWwk....",
        "....kkwwwwwkk..."
    )

    val SKEL_LEGS_IDLE = arrayOf(
        ".....kwk.kwk....",
        ".....kwk.kwk....",
        "....kwwk.kwwk..."
    )

    val SKEL_LEGS_STEP = arrayOf(
        "....kwk...kwk...",
        "...kwk.....kwk..",
        "..kwwk.....kwwk."
    )

    val SKEL_IDLE = SKEL_TOP + SKEL_LEGS_IDLE
    val SKEL_STEP = SKEL_TOP + SKEL_LEGS_STEP

    // ---------------------------------------------------------------- MORCEGO (16x16)

    val BAT_UP = arrayOf(
        "................",
        "................",
        ".kk..........kk.",
        ".kpk........kpk.",
        ".kppk..kk..kppk.",
        ".kpppkkppkkpppk.",
        ".kppppppppppppk.",
        "..kppppeppeppk..",
        "...kkpppppppkk..",
        ".....kpppppk....",
        "......kwkwk.....",
        ".......k.k......",
        "................",
        "................",
        "................",
        "................"
    )

    val BAT_DOWN = arrayOf(
        "................",
        "................",
        "................",
        "................",
        ".......kk.......",
        "......kppk......",
        "..kk.kppppk.kk..",
        ".kppkkppppkkppk.",
        "kppppppeppeppppk",
        ".kppppppppppppk.",
        "..kkppppppppkk..",
        "....kkppppkk....",
        "......kwwk......",
        ".......kk.......",
        "................",
        "................"
    )

    // ---------------------------------------------------------------- CENÁRIO

    val GRASS = arrayOf(
        "lllLlllllLllllLl",
        "lLLLlLlllLLLlLLl",
        "LLmLLLmLLmLLLmLL",
        "mmmmMmmmmmmMmmmm",
        "mmMmmmmmMmmmmmMm",
        "mmmmmmMmmmmMmmmm",
        "mmmmmmmmMmmmmmmm",
        "mmMmmmmmmmmmMmmm",
        "mmmmmmMmmmmmmmmm",
        "mmmmmmmmmmMmmmmm",
        "MmmmmMmmmmmmmmMm",
        "mmmmmmmmmmmmmmmm",
        "mmmMmmmmMmmmmmmm",
        "mmmmmmmmmmmmMmmm",
        "mMmmmmmmmmmmmmmm",
        "mmmmmmmMmmmmmmmm"
    )

    val DIRT = arrayOf(
        "mmmmmmmmMmmmmmmm",
        "mmMmmmmmmmmmMmmm",
        "mmmmmmMmmmmmmmmm",
        "mmmmmmmmmmMmmmmm",
        "MmmmmMmmmmmmmmMm",
        "mmmmmmmmmmmmmmmm",
        "mmmMmmmmMmmmmmmm",
        "mmmmmmmmmmmmMmmm",
        "mMmmmmmmmmmmmmmm",
        "mmmmmmmMmmmmmmmm",
        "mmmmmmmmmmMmmmmM",
        "mmmMmmmmmmmmmmmm",
        "mmmmmmmmMmmmMmmm",
        "mmMmmmmmmmmmmmmm",
        "mmmmmmmmmmmmmmMm",
        "mmmmMmmmmmmmmmmm"
    )

    val PLATFORM = arrayOf(
        "gggggggkgggggggk",
        "gggggggkgggggggk",
        "gGGGGGGkgGGGGGGk",
        "kkkkkkkkkkkkkkkk",
        "gggkgggggggkgggg",
        "gggkgggggggkgggg",
        "GGGkGGGGGGGkGGGG",
        "kkkkkkkkkkkkkkkk"
    )

    // ---------------------------------------------------------------- INTERFACE (9x8)

    val HEART = arrayOf(
        ".kk...kk.",
        "krrkkkrrk",
        "krwrrrrrk",
        "krrrrrrrk",
        ".krrrrrk.",
        "..krrrk..",
        "...krk...",
        "....k...."
    )
}

# Olá App

App Android simples em Kotlin (um botão com contador, mostrando a versão do Android e a arquitetura do aparelho).
Sem dependências externas, então o APK funciona em qualquer celular (ARM, ARM64 ou x86).

## O que tem no app
- **Contador:** o botão da tela inicial conta os cliques.
- **Calculadora:** `+ − × ÷`, `%`, `±`, vírgula, apagar e limpar.
- **Aventura da Espada:** jogo de plataforma 2D em pixel art, em tela cheia na horizontal. Corra, pule buracos e use a espada contra slimes, esqueletos e morcegos. Tem 3 corações, corações para recuperar vida, pontuação e recorde salvo no aparelho. Todos os desenhos estão no código (`game/SpriteData.kt`), sem arquivos de imagem.

- **Menu do Coliseu:** tela mostrada antes do jogo começar. Nela você escolhe o lutador e gasta **moedas** para subir o nível de cada um (até o nível 20). O preço dobra no começo (1, 2, 4, 8, 16, 32…) mas para no teto de **50 moedas**, então dá para chegar ao nível 20 jogando poucas partidas. As moedas são ganhas ao terminar cada partida: quanto melhor o desempenho (rounds, goblins derrotados e pontos), mais moedas. Tudo é salvo no aparelho (`Progresso.kt`).
  - **Cavaleiro:** espada e escudo. Dano normal (referência).
  - **Lutador:** machado de lâmina dupla com cabo meio longo. Dano **2x** o do cavaleiro, mas o golpe é mais lento.
  - **Assassino:** **lança adagas** (ataque à distância) mais rápido que os outros. Dano **1,5x** o do cavaleiro.
  - Subir de nível aumenta o dano de cada lutador (+10% por nível).

- **Coliseu do Eric (3D):** jogo 3D em terceira pessoa, em tela cheia na horizontal. Você (o lutador escolhido no menu) luta numa arena medieval contra rodadas de goblins (com um goblin chefe a cada 5 rounds). Use o joystick à esquerda para andar, `ATACAR` para usar a arma, `ROLAR` para desviar (você fica invulnerável por um instante) e arraste o dedo à direita para girar a câmera. Feito com OpenGL ES, que já vem no Android, sem bibliotecas nem arquivos de imagem: o cenário, os personagens e as armas são gerados no código (`coliseu/`).

## Ícone do app
O ícone é o **goblin do Coliseu segurando o porrete com pregos**, no estilo "blocos" do jogo (pele verde, orelhas pontudas, olhos amarelos, colete de couro e pano vermelho), sobre um fundo escuro com brilho de arena.
- Android 8+ usa o **ícone adaptativo** (`mipmap-anydpi-v26/` + `drawable/ic_launcher_background.xml`), então o ícone acompanha o formato do aparelho (círculo, quadrado arredondado…).
- Android 7 e anteriores usa os PNGs em `mipmap-mdpi` a `mipmap-xxxhdpi`.
- Os PNGs são desenhados por código (sem arquivos de imagem externos). Para refazer ou mudar algo, rode na raiz do repositório:
  ```
  powershell -ExecutionPolicy Bypass -File tools\gerar-icone.ps1
  ```
  O desenho fica em `tools/gerar-icone.ps1` (cores em `$P`, formas em `AddPoly`/`AddRect`/`AddLimb`).

## Baixar o APK pronto (release)
A cada tag `v*` (ou quando o workflow **Release APK** é executado manualmente na aba *Actions*), o GitHub Actions compila e publica uma **release** com o `app-debug.apk` anexado.
1. Abra a aba **Releases** do repositório (https://github.com/eric2021r/OlaApp/releases).
2. Baixe o **app-debug.apk** da release mais recente.
3. Instale no celular (permita "instalar apps de fontes desconhecidas").

Para publicar uma release nova, crie e envie uma tag (**sempre uma tag nova**):
```
git tag v1.0.2
git push origin v1.0.2
```

## Como gerar o APK

### Opção 1: GitHub Actions (sem instalar nada)
1. Crie um repositório no GitHub e suba todos os arquivos desta pasta (incluindo `.github`).
2. Abra a aba **Actions** → workflow **Build APK** → espere terminar (uns 3 a 5 minutos).
3. Baixe o artefato **OlaApp-debug-apk**, extraia o `app-debug.apk` e instale no celular.

### Opção 2: Android Studio
1. **File → Open** e escolha esta pasta.
2. Espere o Gradle sincronizar.
3. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. O arquivo fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Opção 3: linha de comando (precisa do Android SDK e JDK 17+)
```
./gradlew assembleDebug
```

## Instalar no celular
Copie o `app-debug.apk` para o aparelho, abra-o e permita "instalar apps de fontes desconhecidas" quando o Android pedir.

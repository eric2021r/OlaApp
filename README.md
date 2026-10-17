# Olá App

App Android simples em Kotlin (um botão com contador, mostrando a versão do Android e a arquitetura do aparelho).
Sem dependências externas, então o APK funciona em qualquer celular (ARM, ARM64 ou x86).

## O que tem no app
- **Contador:** o botão da tela inicial conta os cliques.
- **Calculadora:** `+ − × ÷`, `%`, `±`, vírgula, apagar e limpar.
- **Aventura da Espada:** jogo de plataforma 2D em pixel art, em tela cheia na horizontal. Corra, pule buracos e use a espada contra slimes, esqueletos e morcegos. Tem 3 corações, corações para recuperar vida, pontuação e recorde salvo no aparelho. Todos os desenhos estão no código (`game/SpriteData.kt`), sem arquivos de imagem.

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

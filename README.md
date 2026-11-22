# Aimo Bot (Android)
Requer Android 11+ (API 30). O APK debug pode ser gerado pelo GitHub Actions (sem Android Studio).

## Compilar no GitHub (pelo celular)
1. Crie um repositório no GitHub (pode ser privado).
2. Opção A (mais fácil no celular): "Add file → Upload files" e envie só o **AimoBot.zip**.
   Opção B: envie todos os arquivos do projeto descompactado.
3. "Add file → Create new file", nome `.github/workflows/build.yml`, cole o conteúdo do build.yml e faça commit.
4. Aba **Actions** → "Build APK" → (se não iniciou sozinho) **Run workflow**. Leva alguns minutos.
5. Abra a execução concluída → **Artifacts → AimoBot-resultado** (contém o app-debug.apk e o build.log) → baixe, extraia e instale o `app-debug.apk`.
   Se falhar, a causa aparece no passo "Mostrar causa do erro" (últimas linhas) e na aba Summary da execução.

## Usar
1. Abra o app → "Abrir configurações de Acessibilidade" → ative **Aimo Bot**.
   (Android 13+: se o botão ficar bloqueado, vá em Configurações → Apps → Aimo Bot → ⋮ → "Permitir configurações restritas".)
2. No app, marque os mobs a atacar, as skills e ajuste distâncias (alcance de ataque e distância a manter).
3. No jogo (paisagem) aparece um painel no topo: ▶ iniciar/pausar · ⌖ calibrar (tem "Pular") · 🎯 escolher mob · 👾 capturar sprite.
4. Capture 1+ sprites de cada mob marcado (🎯 escolhe o mob, 👾 e toque no mob) e toque ▶.

Ajuste fino: a linha de status mostra "match N" (menor = mais parecido com o sprite salvo). Se não reconhece, aumente a tolerância; se confunde cenário com mob, diminua.

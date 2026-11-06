# Capri Mobile

Port nativo em Kotlin/Android do [Capri AI](https://github.com/Capri-cmd/capri-ai). A persona, as falas, os
exemplos, o idioma e os sprites originais foram preservados; a casca PySide6 de desktop virou Views comuns de
Android. Feito para um Redmi Note 12 (Android 15 / HyperOS).

## Baixar no Android

**[⬇ capri-mobile-1.0.0-debug.apk](https://github.com/YK-CD/capri-mobile/releases/download/v1.0.0/capri-mobile-1.0.0-debug.apk)**
· 6,78 MB · [página da release](https://github.com/YK-CD/capri-mobile/releases/tag/v1.0.0)

1. Abra o link acima no celular.
2. Autorize o navegador/gerenciador de arquivos a instalar apps desconhecidos.
3. Abra o APK baixado e confirme. Atualizações instalam por cima (mesma assinatura de debug).

- Pacote `com.capri.mobile` · versão 1.0.0 · `minSdk 26` · `targetSdk 35`
- SHA-256: `aeda62c73f0f2878a2b0c88c5c048ba23bd81f5238d1e1c5410be71cdcec0f49`
- Assinado com o certificado de debug do Android (APK Signature Scheme v2) — instalável, não serve para a Play Store.

Sem chave configurada ela responde offline. Para usar um modelo, abra **Settings → Brain** e escolha um preset
compatível com OpenAI (OpenAI, OpenRouter, Groq, Ollama/LM Studio na LAN) ou cole uma URL própria.

## Documentação

`README-ANDROID.md` descreve o que funciona, as limitações conhecidas, o checklist de primeiro uso no HyperOS,
as permissões e como compilar (`./gradlew assembleDebug`, JDK 17 + SDK 35).

## Créditos e licenças

- Código: MIT, © 2026 yume_mud (Capri-cmd) — veja `LICENSE`.
- Arte da Capri: desenhada por **xilia_world00**; incluída com atribuição e **não** coberta pela licença MIT —
  veja `ART-CREDITS.md`.
- Fonte Share Tech Mono: SIL Open Font License.
- Projeto de fã, não comercial. Cyberpunk e seus personagens são marcas da CD PROJEKT RED; este app não é
  afiliado nem endossado por ela.

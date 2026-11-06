# Capri Mobile

Port nativo em Kotlin/Android do [Capri AI](https://github.com/Capri-cmd/capri-ai). A persona, as falas, os
exemplos, o idioma e os sprites originais foram preservados; a casca PySide6 de desktop virou Views comuns de
Android. Feito para um Redmi Note 12 (Android 15 / HyperOS).

A partir da 1.1.0 ela também **abre apps** e **agenda coisas**: alarmes, timers, eventos na agenda e lembretes
locais com notificação, em português e inglês.

## Baixar no Android

**[⬇ capri-mobile-1.1.0-debug.apk](https://github.com/YK-CD/capri-mobile/releases/download/v1.1.0/capri-mobile-1.1.0-debug.apk)**
· 6,75 MB · [página da release](https://github.com/YK-CD/capri-mobile/releases/tag/v1.1.0)

1. Abra o link acima no celular.
2. Autorize o navegador/gerenciador de arquivos a instalar apps desconhecidos.
3. Abra o APK baixado e confirme. Atualizações instalam por cima (mesma assinatura de debug).

- Pacote `com.capri.mobile` · versão 1.1.0 · `minSdk 26` · `targetSdk 35`
- SHA-256: `bb993907d1c3dea10d379c3a39ea0dc1c76b9cab2b5d1c3b5ae00d82a106323c`
- Assinado com o certificado de debug do Android (APK Signature Scheme v2) — instalável, não serve para a Play Store.

Sem chave configurada ela responde offline. Para usar um modelo, abra **Settings → Brain** e escolha um preset
compatível com OpenAI (OpenAI, OpenRouter, Groq, Ollama/LM Studio na LAN) ou cole uma URL própria.

## O que ela já faz no celular

Fale ou digite em português ou inglês; o roteador determinístico resolve antes do modelo:

| Você diz | O que acontece |
| --- | --- |
| `abre o whatsapp` / `open whatsapp` | Abre o app instalado, com apelidos (`zap`, `insta`, `yt`) e busca aproximada pelo nome |
| `alarme às 7h` / `set alarm for 7am` | Abre o alarme do sistema já preenchido |
| `timer de 10 minutos` / `set a timer for 10 minutes` | Abre o timer do sistema |
| `agende uma reunião amanhã às 15h` | Abre a agenda com título e horário para você confirmar |
| `me lembre de beber água em 20 minutos` | Cria um lembrete local e notifica na hora certa |
| `meus lembretes` / `cancela o lembrete 1` | Lista ou cancela lembretes |
| `toca jazz` / `pesquisa receita de bolo` | Abre YouTube/Spotify ou a busca do Google |
| `aumenta o volume` / `dorme` / `acorda` | Controla volume e o estado da personagem |

Eventos de calendário abrem o app de agenda para confirmação (`ACTION_INSERT`), em vez de inserir sem você ver.
Lembretes são locais (SQLite + `AlarmManager`); se a permissão de alarmes exatos não estiver liberada, a Capri
avisa e usa o melhor agendamento disponível. Em **Settings → Agenda / reminders** dá para ver, cancelar e liberar
alarmes exatos.

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

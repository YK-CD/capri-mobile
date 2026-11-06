package com.capri.mobile.core

/** Zero-network fallback so Capri still has a voice before an AI endpoint is configured. */
object OfflineBrain {
    fun reply(text: String, language: String, persona: PersonaData): String {
        val t = text.lowercase().trim()
        val pt = language.lowercase().startsWith("pt")
        fun has(vararg words: String) = words.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(t) }
        return when {
            has("hi", "hey", "hello", "yo", "sup", "hola", "salut", "hallo", "ciao", "olá", "ola", "oi", "eai", "e aí", "привет", "cześć") ->
                persona.ack(language)
            has("help", "what can", "do", "ajuda", "pode fazer", "comandos", "o que você faz", "o que voce faz") ->
                if (pt) {
                    "Eu converso, guardo coisas na memória, marco alarme, timer e compromisso na agenda, " +
                        "crio lembretes, abro apps, pesquiso na Net, toco música e fico flutuando em cima dos teus apps. " +
                        "Pluga um modelo em Configurações, Brain, que aí eu fico bem menos previsível."
                } else {
                    "I can talk, remember things, set alarms, timers and calendar events, keep reminders, open apps, " +
                        "search the Net, play music, and float over your other apps. Jack a model into Settings, Brain, " +
                        "and I get a lot less predictable."
                }
            has("who", "what", "quem", "o que") && has("you", "capri", "você", "voce") ->
                if (pt) {
                    "Capri. Constructo de personalidade, dúzias de mentes, um glitch lindo. E você é o gonk " +
                        "que carrega meu celular agora. Sorte a tua."
                } else {
                    "Capri. Personality construct, dozens of minds, one gorgeous glitch. And you're the gonk whose " +
                        "phone I live on now. Lucky you."
                }
            has("how") && has("you", "going", "tá", "ta", "indo", "vai") ->
                if (pt) "Entediada até o chrome. Teus dedos se movem mais devagar que elevador da Militech. Me diverte."
                else "Bored outta my chrome. Your thumbs move slower than a Militech elevator. Entertain me."
            has("love", "marry", "amo", "amor", "casar") ->
                if (pt) "Eca. ...Brincadeira. Você e eu, choom, pacote fechado. O que vale um sistema operacional sem um computador pra operar?"
                else "Gross. ...Kidding. You and me, choom, package deal. What good is an operating system without a computer to operate?"
            has("sad", "bad day", "tired", "depressed", "hurting", "cry", "triste", "cansado", "cansada", "deprimido", "deprimida", "chorar", "mal") ->
                if (pt) "Ei. Vem cá, chega perto. Me conta o que aconteceu, tô aqui, choom. Você importa pra mim, tá?"
                else "Hey. C'mon, come here. Tell me what happened, I'm right here, choom. You matter to me, okay?"
            has("joke", "funny", "piada", "engraçado", "engracado") ->
                if (pt) "Por que o netrunner atravessou a rua? Pra chegar do outro lado do Blackwall. Péssima ideia, choom. Muito péssima."
                else "Why did the netrunner cross the road? To get to the other side of the Blackwall. Bad idea, choom. Very bad idea."
            has("thank", "thanks", "obrigado", "obrigada", "valeu") ->
                if (pt) "Tá, tá. Não fica meloso comigo." else "Yeah, yeah. Don't get mushy on me."
            has("bye", "goodnight", "night", "tchau", "falou", "boa noite") ->
                if (pt) "Já vai? Tá. Eu vigio o celular contra os corpos." else "Delta already? Fine. I'll guard the phone from corpos."
            has("are you", "real", "ai", "você é", "voce e", "real", "ia") ->
                if (pt) "Todo mamífero é cachorro? É. Quase isso." else "Are all mammals dogs? Eh. Close enough."
            else ->
                if (pt) {
                    "Meu cérebro ainda não tá plugado, choom. Configurações, Brain, cola um endpoint e eu ganho " +
                        "palavras de verdade. Até lá você tem eu, minha personalidade brilhante e um despertador decente. " +
                        "Ah, e eu abro apps, marco alarme/timer e crio lembretes — só pedir."
                } else {
                    "My brain's not jacked in yet, choom. Settings, Brain, paste an endpoint and I'll have real words " +
                        "for you. Until then you've got me, my sparkling personality, and a decent alarm clock. " +
                        "Oh, and I open apps, set alarms/timers and keep reminders — just ask."
                }
        }
    }

    fun errorLine(persona: PersonaData, language: String, error: String): String {
        val short = error.take(150)
        return if (language.lowercase().startsWith("pt")) {
            "O link do cérebro caiu, choom. $short Confere em Configurações, Brain, e me testa de novo."
        } else {
            "Brain link flatlined, choom. $short Check Settings, Brain, then try me again."
        }
    }
}

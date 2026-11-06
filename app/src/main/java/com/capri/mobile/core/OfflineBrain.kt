package com.capri.mobile.core

/** Zero-network fallback so Capri still has a voice before an AI endpoint is configured. */
object OfflineBrain {
    fun reply(text: String, language: String, persona: PersonaData): String {
        val t = text.lowercase().trim()
        fun has(vararg words: String) = words.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(t) }
        return when {
            has("hi", "hey", "hello", "yo", "sup", "hola", "salut", "hallo", "ciao", "olá", "привет", "cześć") ->
                persona.ack(language)
            has("help", "what can", "do") ->
                "I can talk, remember things, set alarms and timers, open apps, search the Net, play music, and float over your other apps. Jack a model into Settings, Brain, and I get a lot less predictable."
            has("who", "what") && has("you", "capri") ->
                "Capri. Personality construct, dozens of minds, one gorgeous glitch. And you're the gonk whose phone I live on now. Lucky you."
            has("how") && has("you", "going") ->
                "Bored outta my chrome. Your thumbs move slower than a Militech elevator. Entertain me."
            has("love", "marry") ->
                "Gross. ...Kidding. You and me, choom, package deal. What good is an operating system without a computer to operate?"
            has("sad", "bad day", "tired", "depressed", "hurting", "cry") ->
                "Hey. C'mon, come here. Tell me what happened, I'm right here, choom. You matter to me, okay?"
            has("joke", "funny") ->
                "Why did the netrunner cross the road? To get to the other side of the Blackwall. Bad idea, choom. Very bad idea."
            has("thank", "thanks") -> "Yeah, yeah. Don't get mushy on me."
            has("bye", "goodnight", "night") -> "Delta already? Fine. I'll guard the phone from corpos."
            has("are you", "real", "ai") ->
                "Are all mammals dogs? Eh. Close enough."
            else ->
                "My brain's not jacked in yet, choom. Settings, Brain, paste an endpoint and I'll have real words for you. Until then you've got me, my sparkling personality, and a decent alarm clock."
        }
    }

    fun errorLine(persona: PersonaData, language: String, error: String): String {
        val short = error.take(150)
        return "Brain link flatlined, choom. $short Check Settings, Brain, then try me again."
    }
}

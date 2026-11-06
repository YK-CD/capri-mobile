package com.capri.mobile.core

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import java.util.Calendar
import java.util.Locale

/** Deterministic mobile "hands": apps, alarms, timers, calendar events, reminders, searches, music, volume. */
class IntentRouter(private val context: Context, private val memory: MemoryStore) {

    private val reminders = ReminderStore(context)

    data class Route(val reply: String, val action: String? = null)

    fun route(raw: String, language: String = "en"): Route? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val low = SchedulingParser.normalize(text)
        val pt = language.lowercase(Locale.ROOT).startsWith("pt") || looksPortuguese(low)
        return remember(text, pt)
            ?: recall(low, pt)
            ?: listReminders(low, pt)
            ?: cancelReminders(low, pt)
            ?: time(low, pt)
            ?: alarm(low, pt)
            ?: timer(low, pt)
            ?: addReminder(text, pt)
            ?: calendar(text, pt)
            ?: volume(low, pt)
            ?: play(low, pt)
            ?: search(low, pt)
            ?: openApp(low, pt)
            ?: state(low, pt)
    }

    private fun say(pt: Boolean, en: String, ptText: String): Route = Route(if (pt) ptText else en)

    private fun looksPortuguese(low: String): Boolean {
        val markers = listOf(
            "voce", "seu ", "sua ", "meu ", "minha ", "obrigad", "por favor", "que horas", "agendar",
            "agende", "lembre", "lembra", "abrir", "abre ", "tocar", "toca ", "pesquis", "alarme",
            "amanha", "hoje", "dia ", "agora", "cronometro", "despertador", "compromisso", "reuniao"
        )
        return markers.any { low.contains(it) }
    }

    private fun remember(raw: String, pt: Boolean): Route? {
        val fact = MemoryExtractor.explicit(raw) ?: return null
        memory.remember(fact)
        val tail = if (fact.length > 90) fact.take(87) + "…" else fact
        return say(
            pt,
            "Filed in the shard, choom: $tail. Try not to make me regret keeping it.",
            "Guardado no shard, choom: $tail. Não me faz me arrepender disso."
        )
    }

    private fun recall(low: String, pt: Boolean): Route? {
        if (!Regex(
                "\\b(?:what do you (?:remember|know)|remember about|my memory|memory shard)\\b|" +
                    "\\b(?:o que voce (?:lembra|sabe)(?: de mim)?|o que lembra de mim|minha memoria|meus dados)\\b"
            ).containsMatchIn(low)
        ) return null
        val facts = memory.facts().map { it.text }
        if (facts.isEmpty()) {
            return say(
                pt,
                "Shard's empty, choom. Tell me something worth keeping.",
                "Shard vazio, choom. Me conta algo que valha a pena guardar."
            )
        }
        val body = facts.takeLast(8).joinToString("\n") { "• $it" }
        return say(pt, "Here's what's in the shard:\n$body", "No shard tem isso:\n$body")
    }

    private fun listReminders(low: String, pt: Boolean): Route? {
        if (!Regex(
                "\\b(?:my reminders|show reminders?|list reminders?|scheduled reminders?|upcoming reminders?)\\b|" +
                    "\\b(?:meus lembretes|quais (?:sao )?(?:os )?meus lembretes|ver lembretes|listar lembretes|lembretes agendados)\\b"
            ).containsMatchIn(low)
        ) return null
        val now = System.currentTimeMillis()
        val list = reminders.upcoming(now, 10)
        if (list.isEmpty()) {
            return say(pt, "No reminders armed, choom.", "Nenhum lembrete armado, choom.")
        }
        val body = list.mapIndexed { i, r ->
            "${i + 1}. ${r.text} — ${SchedulingParser.describe(r.at, pt)}"
        }.joinToString("\n")
        return say(pt, "Reminders in the shard:\n$body", "Lembretes na fila:\n$body")
    }

    private fun cancelReminders(low: String, pt: Boolean): Route? {
        val one = Regex(
            "^(?:hey\\s+)?(?:capri[,\\s]+)?(?:cancelar|cancela|cancel|apagar|apaga|remover|remove|excluir|exclui|delete)\\s+" +
                "(?:o\\s+|the\\s+)?lembrete\\s+(\\d{1,2})\\b"
        ).find(low)
        if (one != null) {
            val index = one.groupValues[1].toIntOrNull() ?: return null
            val list = reminders.upcoming(System.currentTimeMillis(), 50)
            val target = list.getOrNull(index - 1)
                ?: return say(pt, "There's no reminder #$index.", "Não tem lembrete #$index.")
            ReminderScheduler.cancel(context, target.id)
            reminders.remove(target.id)
            return say(
                pt,
                "Reminder cancelled: \"${target.text}\".",
                "Lembrete cancelado: \"${target.text}\"."
            )
        }
        if (!Regex(
                "\\b(?:cancel|clear|delete|remove|wipe)\\b.{0,16}\\breminders?\\b|" +
                    "\\b(?:cancelar|cancela|limpar|limpa|apagar|apaga|remover|remove|excluir|exclui)\\b.{0,24}\\blembretes?\\b"
            ).containsMatchIn(low)
        ) return null
        ReminderScheduler.cancelAll(context)
        reminders.clear()
        return say(pt, "All reminders wiped, choom.", "Todos os lembretes apagados, choom.")
    }

    private fun time(low: String, pt: Boolean): Route? {
        if (Regex(
                "\\bwhat(?:'s| is)? the time\\b|\\bwhat time is it\\b|\\btime is it\\b|" +
                    "\\bque horas (?:sao|e)\\b|\\bque hora (?:sao|e)\\b|\\bhora agora\\b"
            ).containsMatchIn(low)
        ) {
            val now = Calendar.getInstance()
            val hm = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            return say(
                pt,
                "It's $hm. Time's a construct too, but yours is ticking.",
                "São $hm. Tempo também é constructo, mas o seu tá correndo."
            )
        }
        if (Regex(
                "\\bwhat(?:'s| is)? (?:the )?date\\b|\\bwhat day is it\\b|" +
                    "\\bque dia (?:e|eh) (?:hoje)?\\b|\\bdata de hoje\\b|\\bqual (?:e )?a data\\b"
            ).containsMatchIn(low)
        ) {
            val fmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", if (pt) Locale("pt", "BR") else Locale.ENGLISH)
            return say(
                pt,
                "It's ${fmt.format(java.util.Date())}. Don't waste it, gonk.",
                "É ${fmt.format(java.util.Date())}. Não desperdiça, gonk."
            )
        }
        return null
    }

    private fun alarm(low: String, pt: Boolean): Route? {
        if (!Regex(
                "\\bset (?:an? )?alarm\\b|\\balarm (?:for|at)\\b|\\bwake me\\b|" +
                    "\\balarme\\b|\\bdespertador\\b|\\bme acorde\\b|\\bme acorda\\b|\\bdespertar\\b"
            ).containsMatchIn(low)
        ) return null
        val clock = SchedulingParser.parseClock(low) ?: return null
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, clock.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, clock.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Capri alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val hm = String.format(Locale.US, "%02d:%02d", clock.hour, clock.minute)
        return launch(
            intent,
            if (pt) "Alarme pra $hm. Durmo com um olho aberto." else "Alarm set for $hm. I'll wake you. Probably.",
            pt
        )
    }

    private fun timer(low: String, pt: Boolean): Route? {
        if (!Regex(
                "\\btimer\\b|\\bcronometro\\b|\\btemporizador\\b|\\bcontagem regressiva\\b|\\bcountdown\\b"
            ).containsMatchIn(low)
        ) return null
        val ms = SchedulingParser.duration(low) ?: return null
        val secs = (ms / 1000L).coerceIn(1L, 3 * 86_400L)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs.toInt())
            putExtra(AlarmClock.EXTRA_MESSAGE, "Capri timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val label = humanDuration(secs * 1000L, pt)
        return launch(
            intent,
            if (pt) "Timer de $label rodando. Vai fazer o que tem que fazer."
            else "Timer's ticking: $label. Go do the thing, choom.",
            pt
        )
    }

    private fun humanDuration(ms: Long, pt: Boolean): String {
        val total = (ms / 1000L).coerceAtLeast(1L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += if (pt) "$h h" else "$h h"
        if (m > 0) parts += if (pt) "$m min" else "$m min"
        if (s > 0 && h == 0L) parts += if (pt) "$s s" else "$s s"
        return parts.joinToString(" ")
    }

    private fun addReminder(raw: String, pt: Boolean): Route? {
        val m = Regex(
            "^(?:hey\\s+)?(?:capri[,\\s]+)?(?:(?:me\\s+)?(?:lembre-se|lembr(?:e|a|ar))|remind\\s+me|reminder)\\b" +
                "[\\s,:]*(?:-?me\\b)?[\\s,:]*(?:de|que|to|about|para|that)?\\s*(.+)$",
            RegexOption.IGNORE_CASE
        ).find(raw.trim()) ?: return null
        val body = m.groupValues[1].trim()
        val whenParsed = SchedulingParser.parseWhen(body, System.currentTimeMillis())
        if (whenParsed == null) {
            return say(
                pt,
                "Remind you when? Add a time, like \"remind me to drink water in 20 minutes\".",
                "Te lembro quando? Diz um horário, tipo \"me lembre de beber água em 20 minutos\"."
            )
        }
        var what = SchedulingParser.stripTimeExpression(body)
            .replace(Regex("^(?:de|que|to|about|para|that)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()
            .trimEnd('.', '!', '?', ' ')
        if (what.length < 2) what = if (pt) "seu lembrete" else "your reminder"
        val reminder = reminders.add(what, whenParsed.at)
        ReminderScheduler.schedule(context, reminder)
        val whenText = SchedulingParser.describe(whenParsed.at, pt)
        val exact = exactAlarmsAllowed()
        val hint = if (exact) "" else if (pt) " (ativa os alarmes exatos nas configurações pra precisão total)" else " (allow exact alarms in Settings for tighter timing)"
        return say(
            pt,
            "Locked in: \"$what\" — $whenText.$hint",
            "Anotado: \"$what\" — $whenText.$hint"
        )
    }

    private fun exactAlarmsAllowed(): Boolean = try {
        android.os.Build.VERSION.SDK_INT < 31 ||
            (context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).canScheduleExactAlarms()
    } catch (_: Exception) {
        false
    }

    private fun calendar(raw: String, pt: Boolean): Route? {
        val low = SchedulingParser.normalize(raw)
        val trigger = Regex(
            "^(?:hey\\s+)?(?:capri[,\\s]+)?(?:agende|agendar|marque|marcar|crie|criar|adicione|adicionar|" +
                "coloque|colocar|schedule|create|add|new event|novo evento)\\b"
        )
        if (!trigger.containsMatchIn(low)) return null
        val parsed = SchedulingParser.parseWhen(raw, System.currentTimeMillis())
        if (parsed == null) {
            return say(
                pt,
                "Agenda it for when? Say \"tomorrow at 3pm\" or \"dia 12 às 9h\".",
                "Agendar pra quando? Diz \"amanhã às 15h\" ou \"dia 12 às 9h\"."
            )
        }
        val title = SchedulingParser.calendarTitle(raw)
            ?: if (pt) "Compromisso" else "Event"
        val begin = parsed.at
        val end = begin + 60L * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val whenText = SchedulingParser.describe(begin, pt)
        return try {
            context.startActivity(intent)
            say(
                pt,
                "Calendar's open — confirm \"$title\" for $whenText.",
                "Agenda aberta — confirma \"$title\" pra $whenText."
            )
        } catch (_: Exception) {
            say(
                pt,
                "No calendar app would take it, choom. Install/update your calendar and try again.",
                "Nenhuma agenda quis abrir, choom. Instala/atualiza teu calendário e tenta de novo."
            )
        }
    }

    private fun volume(low: String, pt: Boolean): Route? {
        if (!Regex("\\b(volume|mute|unmute|mudo|silenciar|silencioso)\\b").containsMatchIn(low)) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val up = Regex("\\b(up|louder|raise|increase|aumenta|aumentar|sobe|subir|mais alto)\\b").containsMatchIn(low)
        val down = Regex("\\b(down|lower|reduce|quieter|diminui|diminuir|abaixa|abaixar|mais baixo)\\b").containsMatchIn(low)
        val mute = Regex("\\b(mute|mudo|silenciar|silencioso)\\b").containsMatchIn(low) && !low.contains("unmute")
        return try {
            when {
                mute -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    say(pt, "Muted. Night City finally shuts up.", "No mudo. Night City finalmente cala a boca.")
                }
                up -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    say(pt, "Louder. Hope your ears can take it.", "Mais alto. Espero que teus ouvidos aguentem.")
                }
                down -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    say(pt, "Turned it down. You're welcome, neighbor.", "Baixei. De nada, vizinho.")
                }
                else -> null
            }
        } catch (_: Exception) {
            say(
                pt,
                "Can't touch the volume from here, choom. Android's being a corpo about it.",
                "Não consigo mexer no volume daqui, choom. O Android tá sendo corporativo."
            )
        }
    }

    private fun play(low: String, pt: Boolean): Route? {
        val m = Regex(
            "^(?:hey\\s+)?(?:capri[,\\s]+)?(?:play|put on|queue|toca|tocar|coloca|colocar|reproduz|reproduzir)\\s+(.+?)" +
                "(?:\\s+on (?:youtube|spotify)|\\s+no (?:youtube|spotify))?[.!]?$"
        ).find(low) ?: return null
        val song = m.groupValues[1].trim()
        if (song.isBlank() || Regex("\\b(?:a game|games|with me)\\b").containsMatchIn(song)) return null
        val uri = if (low.contains("spotify")) {
            Uri.parse("https://open.spotify.com/search/" + Uri.encode(song))
        } else {
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(song))
        }
        return launch(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            if (pt) "Botei \"$song\" pra tocar. Se for ruim, a culpa é tua."
            else "Cued up \"$song\". If it's scop, that's on you.",
            pt
        )
    }

    private fun search(low: String, pt: Boolean): Route? {
        val m = Regex(
            "^(?:hey\\s+)?(?:capri[,\\s]+)?(?:search(?: for| up)?|look up|google|pesquisa(?:r)?|busca(?:r)?|procura(?:r)?)\\s+(.+?)[.!]?$"
        ).find(low) ?: return null
        val q = m.groupValues[1].trim()
        if (q.length < 2) return null
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
        return launch(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            if (pt) "Abri a Net pra \"$q\". Não cai no buraco do wiki."
            else "Opened the Net for \"$q\". Don't fall down a wiki hole.",
            pt
        )
    }

    private fun openApp(low: String, pt: Boolean): Route? {
        val m = Regex(
            "^(?:hey\\s+)?(?:capri[,\\s]+)?(?:open|launch|start|run|abrir|abre|abra|iniciar|inicia|executar|executa|rodar)\\s+" +
                "(?:o\\s+|a\\s+|os\\s+|as\\s+|the\\s+|my\\s+)?(.+?)[.!]?$"
        ).find(low) ?: return null
        var wanted = m.groupValues[1].trim()
        wanted = wanted.replace(Regex("\\s+(?:app|aplicativo|pra mim|para mim|por favor|please)$"), "").trim()
        if (wanted.isBlank()) return null
        systemSetting(wanted, pt)?.let { return it }

        val pm = context.packageManager
        if (wanted.contains('.')) {
            pm.getLaunchIntentForPackage(wanted)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return launch(it, if (pt) "Aberto $wanted. Não quebra nada." else "Opened $wanted. Try not to break it.", pt)
            }
        }
        val variants = appVariants(wanted)
        val apps = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).mapNotNull { ri ->
            val label = ri.loadLabel(pm).toString()
            Triple(label, ri.activityInfo.packageName, ri.activityInfo.name)
        }
        fun labelScore(label: String): Int {
            val l = SchedulingParser.normalize(label)
            var best = 0
            for (v in variants) {
                best = maxOf(
                    best,
                    when {
                        l == v -> 100
                        l.startsWith(v) -> 80
                        v.startsWith(l) -> 70
                        l.contains(v) -> 60
                        v.contains(l) -> 50
                        else -> wordOverlap(l, v)
                    }
                )
            }
            return best
        }
        val hit = apps.maxByOrNull { labelScore(it.first) }?.takeIf { labelScore(it.first) >= 50 }
        if (hit == null) {
            return say(
                pt,
                "Ain't seeing \"$wanted\" installed, choom. Check the name or open it yourself.",
                "Não achei \"$wanted\" instalado, choom. Confere o nome ou abre na mão."
            )
        }
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(hit.second, hit.third)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(
            launchIntent,
            if (pt) "Aberto ${hit.first}. Não quebra nada." else "Opened ${hit.first}. Try not to break it.",
            pt
        )
    }

    private fun wordOverlap(label: String, wanted: String): Int {
        val a = label.split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val b = wanted.split(Regex("\\W+")).filter { it.length > 2 }.toSet()
        val shared = a.intersect(b).size
        return if (shared == 0) 0 else 20 + shared * 5
    }

    private fun appVariants(wanted: String): Set<String> {
        val out = mutableSetOf(wanted)
        val canonical = APP_ALIASES[wanted] ?: wanted
        out += canonical
        APP_VARIANTS[canonical]?.let { out += it }
        APP_VARIANTS[wanted]?.let { out += it }
        return out
    }

    private fun systemSetting(wanted: String, pt: Boolean): Route? {
        val action = when {
            wanted.contains("wifi") || wanted.contains("wi-fi") -> Settings.ACTION_WIFI_SETTINGS
            wanted.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            wanted.contains("bateria") || wanted.contains("battery") -> "android.settings.BATTERY_SAVER_SETTINGS"
            wanted.contains("tela") || wanted.contains("display") || wanted.contains("screen settings") -> Settings.ACTION_DISPLAY_SETTINGS
            wanted.contains("som") || wanted.contains("sound") -> Settings.ACTION_SOUND_SETTINGS
            wanted == "configuracoes" || wanted == "settings" || wanted == "config" ||
                wanted.contains("configuracoes do") || wanted.contains("app settings") -> Settings.ACTION_SETTINGS
            wanted.contains("camera") || wanted.contains("cameras") -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            else -> return null
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            val pretty = wanted.replaceFirstChar { it.uppercase() }
            say(pt, "Opened $pretty settings.", "Abri as configurações de $pretty.")
        } catch (_: Exception) {
            null
        }
    }

    private fun state(low: String, pt: Boolean): Route? {
        if (Regex("\\b(?:go to sleep|take a nap|sleep mode|go offline)\\b|\\b(?:dorme|durma|vai dormir|modo sleep|modo dormir)\\b")
                .containsMatchIn(low)
        ) {
            context.sendBroadcast(Intent(ACTION_SLEEP).setPackage(context.packageName))
            return Route(
                if (pt) "Apagando. Me acorda se os corpos aparecerem." else "Going dark. Wake me if the corpos show up.",
                "sleep"
            )
        }
        if (Regex("\\b(?:wake up|you awake|rise and shine)\\b|\\b(?:acorda|acorde|acordar)\\b").containsMatchIn(low)) {
            context.sendBroadcast(Intent(ACTION_WAKE).setPackage(context.packageName))
            return Route(if (pt) "Tô acordada, tô acordada. Sentiu minha falta?" else "I'm up, I'm up. Miss me?", "wake")
        }
        return null
    }

    private fun launch(intent: Intent, line: String, pt: Boolean = false): Route = try {
        context.startActivity(intent)
        Route(line)
    } catch (_: Exception) {
        Route(
            if (pt) "O Android bloqueou essa, choom. Nenhum app quis abrir."
            else "Android blocked that one, choom. No app wants to take it."
        )
    }

    companion object {
        const val ACTION_SLEEP = "com.capri.mobile.SLEEP"
        const val ACTION_WAKE = "com.capri.mobile.WAKE"
        const val ACTION_SHOW_CHAT = "com.capri.mobile.SHOW_CHAT"

        /** Nicknames people actually type, mapped to the label family an app is likely to use. */
        private val APP_ALIASES = mapOf(
            "zap" to "whatsapp", "wpp" to "whatsapp", "whats" to "whatsapp", "whatsapp business" to "whatsapp",
            "insta" to "instagram", "face" to "facebook", "yt" to "youtube", "yt music" to "youtube music",
            "tel" to "telefone", "phone" to "telefone", "celular" to "telefone", "discador" to "telefone",
            "msg" to "mensagens", "sms" to "mensagens", "messages" to "mensagens",
            "calc" to "calculadora", "calculator" to "calculadora",
            "cam" to "camera", "foto" to "fotos", "photo" to "fotos", "photos" to "fotos", "gallery" to "galeria",
            "mapa" to "mapas", "maps" to "mapas",
            "clock" to "relogio", "alarm" to "relogio",
            "config" to "configuracoes", "settings" to "configuracoes",
            "calendar" to "agenda", "calendario" to "agenda"
        )

        private val APP_VARIANTS = mapOf(
            "whatsapp" to setOf("whatsapp", "whatsapp business"),
            "instagram" to setOf("instagram"),
            "facebook" to setOf("facebook", "facebook lite"),
            "youtube" to setOf("youtube", "youtube music"),
            "spotify" to setOf("spotify"),
            "telegram" to setOf("telegram", "telegram x"),
            "tiktok" to setOf("tiktok"),
            "chrome" to setOf("chrome", "google chrome", "navegador", "browser"),
            "telefone" to setOf("telefone", "phone", "dialer", "discador"),
            "mensagens" to setOf("mensagens", "messages", "sms"),
            "calculadora" to setOf("calculadora", "calculator"),
            "camera" to setOf("camera", "cameras", "câmera"),
            "fotos" to setOf("fotos", "photos", "galeria", "gallery"),
            "relogio" to setOf("relogio", "relógio", "clock"),
            "configuracoes" to setOf("configuracoes", "configurações", "settings"),
            "mapas" to setOf("mapas", "maps", "google maps"),
            "agenda" to setOf("agenda", "calendar", "calendario", "calendário"),
            "gmail" to setOf("gmail", "email", "e-mail"),
            "banco" to setOf("banco", "bank"),
            "ifood" to setOf("ifood"),
            "uber" to setOf("uber"),
            "netflix" to setOf("netflix")
        )
    }
}

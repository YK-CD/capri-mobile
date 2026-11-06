package com.capri.mobile.core

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.util.Calendar
import java.util.Locale

/** Deterministic mobile "hands": alarms, timers, apps, searches, music, volume. */
class IntentRouter(private val context: Context, private val memory: MemoryStore) {

    data class Route(val reply: String, val action: String? = null)

    fun route(raw: String): Route? {
        val text = raw.trim()
        val low = text.lowercase(Locale.ROOT)
        remember(low, text)?.let { return it }
        recall(low)?.let { return it }
        time(low)?.let { return it }
        alarm(low)?.let { return it }
        timer(low)?.let { return it }
        volume(low)?.let { return it }
        play(low)?.let { return it }
        search(low)?.let { return it }
        openApp(low)?.let { return it }
        state(low)?.let { return it }
        return null
    }

    private fun remember(low: String, raw: String): Route? {
        val fact = MemoryExtractor.explicit(raw) ?: return null
        memory.remember(fact)
        val tail = if (fact.length > 90) fact.take(87) + "…" else fact
        return Route("Filed in the shard, choom: $tail. Try not to make me regret keeping it.")
    }

    private fun recall(low: String): Route? {
        if (!Regex("\\b(?:what do you (?:remember|know)|remember about|my memory|memory shard)\\b").containsMatchIn(low)) return null
        val facts = memory.facts().map { it.text }
        if (facts.isEmpty()) return Route("Shard's empty, choom. Tell me something worth keeping.")
        return Route("Here's what's in the shard:\n" + facts.takeLast(8).joinToString("\n") { "• $it" })
    }

    private fun time(low: String): Route? {
        if (Regex("\\bwhat(?:'s| is)? the time\\b|\\bwhat time is it\\b|\\btime is it\\b").containsMatchIn(low)) {
            val now = Calendar.getInstance()
            val hm = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            return Route("It's $hm. Time's a construct too, but yours is ticking.")
        }
        if (Regex("\\bwhat(?:'s| is)? (?:the )?date\\b|\\bwhat day is it\\b").containsMatchIn(low)) {
            val fmt = java.text.SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH)
            return Route("It's ${fmt.format(java.util.Date())}. Don't waste it, gonk.")
        }
        return null
    }

    private fun alarm(low: String): Route? {
        if (!Regex("\\bset (?:an? )?alarm\\b|\\balarm (?:for|at)\\b").containsMatchIn(low)) return null
        val m = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(low) ?: return null
        var hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val ap = m.groupValues[3]
        if (ap == "pm" && hour < 12) hour += 12
        if (ap == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Capri alarm")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(intent, String.format(Locale.US, "Alarm set for %02d:%02d. I'll wake you. Probably.", hour, minute))
    }

    private fun timer(low: String): Route? {
        if (!Regex("\\btimer\\b").containsMatchIn(low)) return null
        val m = Regex("(\\d{1,3})\\s*(second|sec|minute|min|hour|hr)s?").find(low) ?: return null
        val n = m.groupValues[1].toIntOrNull() ?: return null
        val secs = when (m.groupValues[2]) {
            "second", "sec" -> n
            "minute", "min" -> n * 60
            else -> n * 3600
        }
        if (secs <= 0) return null
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, secs)
            putExtra(AlarmClock.EXTRA_MESSAGE, "Capri timer")
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val label = when (m.groupValues[2]) {
            "second", "sec" -> "$n seconds"
            "minute", "min" -> "$n minutes"
            else -> "$n hours"
        }
        return launch(intent, "Timer's ticking: $label. Go do the thing, choom.")
    }

    private fun volume(low: String): Route? {
        if (!Regex("\\b(volume|mute|unmute)\\b").containsMatchIn(low)) return null
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val up = Regex("\\b(up|louder|raise|increase)\\b").containsMatchIn(low)
        val down = Regex("\\b(down|lower|reduce|quieter)\\b").containsMatchIn(low)
        val mute = Regex("\\bmute\\b").containsMatchIn(low) && !low.contains("unmute")
        return try {
            when {
                mute -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    Route("Muted. Night City finally shuts up.")
                }
                up -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    Route("Louder. Hope your ears can take it.")
                }
                down -> {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    Route("Turned it down. You're welcome, neighbor.")
                }
                else -> null
            }
        } catch (_: Exception) {
            Route("Can't touch the volume from here, choom. Android's being a corpo about it.")
        }
    }

    private fun play(low: String): Route? {
        val m = Regex("^(?:hey\\s+)?(?:capri[,\\s]+)?(?:play|put on|queue)\\s+(.+?)(?:\\s+on (?:youtube|spotify))?[.!]?$",
            RegexOption.IGNORE_CASE).find(low) ?: return null
        val song = m.groupValues[1].trim()
        if (song.isBlank() || Regex("\\b(?:a game|games|with me)\\b").containsMatchIn(song)) return null
        val uri = if (low.contains("spotify")) {
            Uri.parse("https://open.spotify.com/search/" + Uri.encode(song))
        } else {
            Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(song))
        }
        return launch(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Cued up \"$song\". If it's scop, that's on you.")
    }

    private fun search(low: String): Route? {
        val m = Regex("^(?:hey\\s+)?(?:capri[,\\s]+)?(?:search(?: for| up)?|look up|google)\\s+(.+?)[.!]?$")
            .find(low) ?: return null
        val q = m.groupValues[1].trim()
        if (q.length < 2) return null
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
        return launch(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            "Opened the Net for \"$q\". Don't fall down a wiki hole.")
    }

    private fun openApp(low: String): Route? {
        val m = Regex("^(?:hey\\s+)?(?:capri[,\\s]+)?(?:open|launch|start|run)\\s+(?:the\\s+|my\\s+)?(.+?)[.!]?$").find(low)
            ?: return null
        val wanted = m.groupValues[1].trim().lowercase(Locale.ROOT)
        if (wanted.isBlank()) return null
        systemSetting(wanted)?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0).mapNotNull { ri ->
            val label = ri.loadLabel(pm).toString()
            Triple(label, ri.activityInfo.packageName, ri.activityInfo.name)
        }
        val hit = apps.firstOrNull { it.first.equals(wanted, true) }
            ?: apps.firstOrNull { it.first.lowercase().contains(wanted) }
            ?: apps.firstOrNull { wanted.contains(it.first.lowercase()) }
        if (hit == null) return Route("Ain't seeing \"$wanted\" installed, choom. Check the name or open it yourself.")
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(hit.second, hit.third)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(launch, "Opened ${hit.first}. Try not to break it.")
    }

    private fun systemSetting(wanted: String): Route? {
        val action = when {
            wanted.contains("wifi") || wanted.contains("wi-fi") -> Settings.ACTION_WIFI_SETTINGS
            wanted.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            wanted.contains("battery") -> "android.settings.BATTERY_SAVER_SETTINGS"
            wanted.contains("display") || wanted.contains("screen settings") -> Settings.ACTION_DISPLAY_SETTINGS
            wanted.contains("sound") -> Settings.ACTION_SOUND_SETTINGS
            wanted.contains("app settings") || wanted == "settings" -> Settings.ACTION_SETTINGS
            wanted.contains("camera") -> MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
            else -> return null
        }
        return try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Route("Opened ${wanted.replaceFirstChar { it.uppercase() }} settings.")
        } catch (_: Exception) {
            null
        }
    }

    private fun state(low: String): Route? {
        if (Regex("\\b(?:go to sleep|take a nap|sleep mode|go offline)\\b").containsMatchIn(low)) {
            context.sendBroadcast(Intent(ACTION_SLEEP).setPackage(context.packageName))
            return Route("Going dark. Wake me if the corpos show up.", "sleep")
        }
        if (Regex("\\b(?:wake up|you awake|rise and shine)\\b").containsMatchIn(low)) {
            context.sendBroadcast(Intent(ACTION_WAKE).setPackage(context.packageName))
            return Route("I'm up, I'm up. Miss me?", "wake")
        }
        return null
    }

    private fun launch(intent: Intent, line: String): Route = try {
        context.startActivity(intent)
        Route(line)
    } catch (_: Exception) {
        Route("Android blocked that one, choom. No app wants to take it.")
    }

    companion object {
        const val ACTION_SLEEP = "com.capri.mobile.SLEEP"
        const val ACTION_WAKE = "com.capri.mobile.WAKE"
        const val ACTION_SHOW_CHAT = "com.capri.mobile.SHOW_CHAT"
    }
}

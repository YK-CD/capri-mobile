package com.capri.mobile.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class Fact(val id: Long, val text: String, val createdAt: Long)
data class StoredMessage(val id: Long, val role: String, val content: String, val createdAt: Long)

/** Capri's memory shard: facts, chat turns and summarized episodes. SQLite, WAL, tiny queries. */
class MemoryStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "capri.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE facts(_id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, subject TEXT NOT NULL DEFAULT 'user', created_at INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE messages(_id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, content TEXT NOT NULL, created_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE episodes(_id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_messages_time ON messages(created_at)")
        db.execSQL("CREATE INDEX idx_facts_subject ON facts(subject)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS facts")
        db.execSQL("DROP TABLE IF EXISTS messages")
        db.execSQL("DROP TABLE IF EXISTS episodes")
        onCreate(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    @Synchronized
    fun remember(text: String, subject: String = "user"): Boolean {
        val clean = text.trim().replace(Regex("\\s+"), " ")
        if (clean.length < 2) return false
        val existing = facts(subject)
        val near = existing.firstOrNull { similarity(it.text, clean) > 0.82 }
        val cv = ContentValues().apply {
            put("text", clean)
            put("subject", subject)
            put("created_at", System.currentTimeMillis())
        }
        return if (near != null) {
            writableDatabase.update("facts", cv, "_id = ?", arrayOf(near.id.toString()))
            true
        } else {
            writableDatabase.insert("facts", null, cv) != -1L
        }
    }

    @Synchronized
    fun facts(subject: String = "user"): List<Fact> {
        val out = mutableListOf<Fact>()
        readableDatabase.rawQuery(
            "SELECT _id, text, created_at FROM facts WHERE subject = ? ORDER BY _id ASC",
            arrayOf(subject)
        ).use { c ->
            while (c.moveToNext()) out += Fact(c.getLong(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    /** Facts worth putting in this turn's prompt, closest first; small talk gets none. */
    @Synchronized
    fun relevant(query: String, speaker: String, limit: Int = 14): List<String> {
        val all = facts().map { it.text }
        val hardNo = all.filter { it.contains("never suggest", ignoreCase = true) }.takeLast(4)
        val out = LinkedHashSet<String>()
        val words = stems(query) - speaker.take(4).lowercase()
        if (words.isNotEmpty()) {
            all.mapIndexed { i, f -> Triple(words.intersect(stems(f)).size, i, f) }
                .filter { it.first > 0 }
                .sortedWith(compareByDescending<Triple<Int, Int, String>> { it.first }.thenByDescending { it.second })
                .take(6)
                .forEach { out += it.third }
        }
        val asksMe = ASKS_ABOUT_ME.containsMatchIn(query)
        if (asksMe && (out.isNotEmpty() || QUESTION.containsMatchIn(query))) {
            all.asReversed().forEach { out += it }
        }
        return (hardNo + out).distinct().take(limit)
    }

    @Synchronized
    fun addMessage(role: String, content: String) {
        val cv = ContentValues().apply {
            put("role", role)
            put("content", content)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("messages", null, cv)
    }

    @Synchronized
    fun history(turns: Int = 12): List<ChatMessage> {
        val out = mutableListOf<StoredMessage>()
        val want = (turns * 2).coerceAtLeast(2)
        readableDatabase.rawQuery(
            "SELECT _id, role, content, created_at FROM messages WHERE archived = 0 ORDER BY _id DESC LIMIT ?",
            arrayOf(want.toString())
        ).use { c ->
            while (c.moveToNext()) out += StoredMessage(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return out.asReversed().map { ChatMessage(it.role, it.content) }
    }

    @Synchronized
    fun allMessages(limit: Int = 200): List<StoredMessage> {
        val out = mutableListOf<StoredMessage>()
        readableDatabase.rawQuery(
            "SELECT _id, role, content, created_at FROM messages ORDER BY _id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += StoredMessage(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return out.asReversed()
    }

    @Synchronized
    fun messageCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun clearHistory() {
        writableDatabase.delete("messages", null, null)
        writableDatabase.delete("episodes", null, null)
    }

    @Synchronized
    fun clearAll() {
        writableDatabase.delete("messages", null, null)
        writableDatabase.delete("episodes", null, null)
        writableDatabase.delete("facts", null, null)
    }

    @Synchronized
    fun forgetFact(id: Long) {
        writableDatabase.delete("facts", "_id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun addEpisode(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        val cv = ContentValues().apply {
            put("text", clean.take(500))
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("episodes", null, cv)
        writableDatabase.execSQL(
            "DELETE FROM episodes WHERE _id NOT IN (SELECT _id FROM episodes ORDER BY _id DESC LIMIT 200)"
        )
    }

    @Synchronized
    fun episodes(query: String, limit: Int = 3): List<String> {
        data class Ep(val text: String, val t: Long)
        val all = mutableListOf<Ep>()
        readableDatabase.rawQuery("SELECT text, created_at FROM episodes ORDER BY _id ASC", null).use { c ->
            while (c.moveToNext()) all += Ep(c.getString(0), c.getLong(1))
        }
        if (all.isEmpty()) return emptyList()
        val picked: List<Ep> = if (ASKS_BEFORE.containsMatchIn(query)) {
            all.takeLast(limit)
        } else {
            val words = stems(query)
            val need = if (ASKS_ABOUT_ME.containsMatchIn(query) && QUESTION.containsMatchIn(query)) 1 else 2
            val scored = all.mapIndexed { i, e -> Triple(words.intersect(stems(e.text)).size, i, e) }
                .filter { it.first >= need }
                .sortedByDescending { it.first }
                .take(limit)
                .map { it.third }
            scored.sortedBy { it.t }
        }
        return picked.map { "${ago(it.t)}: ${it.text}" }
    }

    @Synchronized
    fun unarchivedCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM messages WHERE archived = 0", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    @Synchronized
    fun oldUnsummarized(limit: Int = 14): List<StoredMessage> {
        if (unarchivedCount() <= 26) return emptyList()
        val out = mutableListOf<StoredMessage>()
        readableDatabase.rawQuery(
            "SELECT _id, role, content, created_at FROM messages WHERE archived = 0 ORDER BY _id ASC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += StoredMessage(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3))
        }
        return out
    }

    @Synchronized
    fun archive(ids: List<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            ids.chunked(200).forEach { chunk ->
                writableDatabase.execSQL(
                    "UPDATE messages SET archived = 1 WHERE _id IN (${chunk.joinToString(",")})"
                )
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun deleteMessages(ids: List<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.beginTransaction()
        try {
            ids.chunked(200).forEach { chunk ->
                writableDatabase.delete("messages", "_id IN (${chunk.joinToString(",")})", null)
            }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    private fun similarity(a: String, b: String): Double {
        val x = a.lowercase().trim()
        val y = b.lowercase().trim()
        if (x == y) return 1.0
        val tx = words(x)
        val ty = words(y)
        if (tx.isEmpty() || ty.isEmpty()) return 0.0
        val inter = tx.intersect(ty).size.toDouble()
        val union = tx.union(ty).size.toDouble()
        val jaccard = inter / union
        val lenPenalty = 1.0 - (abs(x.length - y.length).toDouble() / maxOf(x.length, y.length, 1))
        return jaccard * 0.8 + lenPenalty * 0.2
    }

    private fun words(t: String): Set<String> = Regex("[\\p{L}\\p{N}']+").findAll(t.lowercase()).map { it.value }.toSet()

    private fun stems(t: String): Set<String> {
        val clean = t.lowercase().replace(Regex("['’]s\\b"), "")
        return Regex("[\\p{L}\\p{N}']{3,}").findAll(clean).map { it.value }
            .filter { it !in STOP }
            .map { it.take(4) }
            .toSet()
    }

    private fun ago(t: Long): String {
        val days = (System.currentTimeMillis() - t) / 86_400_000.0
        return when {
            days < 0.75 -> "Earlier today"
            days < 1.75 -> "Yesterday"
            days < 14 -> "${Math.round(days)} days ago"
            else -> "On " + SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(t))
        }
    }

    private companion object {
        val ASKS_ABOUT_ME = Regex("\\b(?:my|me|i|remember|about me)\\b", RegexOption.IGNORE_CASE)
        val QUESTION = Regex("\\?|^\\W*(?:what|who|whats|what's|where|when|which|how|do you|did i|do i|am i|tell me)\\b", RegexOption.IGNORE_CASE)
        val ASKS_BEFORE = Regex("\\b(?:remember|recall|last time|yesterday|the other day|earlier|before|last week|we (?:talked|spoke|chatted)|(?:talk|talked|spoke) about|did i (?:tell|say)|i told you)\\b", RegexOption.IGNORE_CASE)
        val STOP = setOf(
            "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "for", "with", "is", "are", "was", "were",
            "be", "it", "this", "that", "i", "you", "my", "your", "me", "he", "she", "they", "what", "how", "why",
            "when", "who", "do", "does", "did", "can", "could", "would", "should", "just", "like", "really", "so",
            "very", "not", "no", "yes"
        )
    }
}

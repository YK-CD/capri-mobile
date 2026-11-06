package com.capri.mobile.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Reminder(val id: Long, val text: String, val at: Long)

/** Reminders Capri promised to bring up later. Its own tiny DB so the chat memory can be wiped independently. */
class ReminderStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "capri_reminders.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE reminders(_id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, trigger_at INTEGER NOT NULL, created_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX idx_reminders_time ON reminders(trigger_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS reminders")
        onCreate(db)
    }

    @Synchronized
    fun add(text: String, at: Long): Reminder {
        val clean = text.trim().replace(Regex("\\s+"), " ").take(240)
        val cv = ContentValues().apply {
            put("text", clean)
            put("trigger_at", at)
            put("created_at", System.currentTimeMillis())
        }
        val id = writableDatabase.insert("reminders", null, cv)
        return Reminder(id, clean, at)
    }

    @Synchronized
    fun upcoming(now: Long, limit: Int = 20): List<Reminder> {
        val out = mutableListOf<Reminder>()
        readableDatabase.rawQuery(
            "SELECT _id, text, trigger_at FROM reminders WHERE trigger_at >= ? ORDER BY trigger_at ASC LIMIT ?",
            arrayOf((now - 60_000L).toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) out += Reminder(c.getLong(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    @Synchronized
    fun pending(): List<Reminder> {
        val out = mutableListOf<Reminder>()
        readableDatabase.rawQuery(
            "SELECT _id, text, trigger_at FROM reminders ORDER BY trigger_at ASC",
            null
        ).use { c ->
            while (c.moveToNext()) out += Reminder(c.getLong(0), c.getString(1), c.getLong(2))
        }
        return out
    }

    @Synchronized
    fun remove(id: Long) {
        writableDatabase.delete("reminders", "_id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun clear() {
        writableDatabase.delete("reminders", null, null)
    }
}

package com.vulnforgeai.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Abas de conversação persistidas em SQLite.
 * Cada conversa tem um título (gerado automaticamente pelo contexto), pode ser
 * fixada no topo, e pode ser excluída. O histórico segue o mesmo prazo de
 * expiração configurável ("Uso da memória persistente").
 */
class ConversationStore(context: Context) {

    data class Conversation(
        val id: Long,
        val title: String,
        val isPinned: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )

    private val helper = ConvDbHelper(context.applicationContext)

    @Synchronized
    fun create(title: String): Long {
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COL_TITLE, title)
            put(COL_PINNED, 0)
            put(COL_CREATED, now)
            put(COL_UPDATED, now)
        }
        return db.insertOrThrow(TABLE_CONV, null, values)
    }

    @Synchronized
    fun all(): List<Conversation> {
        val db = helper.readableDatabase
        val out = mutableListOf<Conversation>()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_CONV ORDER BY $COL_PINNED DESC, $COL_UPDATED DESC", null
        )
        cursor.use {
            while (it.moveToNext()) {
                out.add(toConv(it))
            }
        }
        return out
    }

    @Synchronized
    fun getById(id: Long): Conversation? {
        val db = helper.readableDatabase
        val cursor = db.query(TABLE_CONV, null, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
        cursor.use {
            if (it.moveToFirst()) return toConv(it)
        }
        return null
    }

    @Synchronized
    fun setTitle(id: Long, title: String) {
        val db = helper.writableDatabase
        val values = ContentValues().apply { put(COL_TITLE, title) }
        db.update(TABLE_CONV, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun touch(id: Long) {
        val db = helper.writableDatabase
        val values = ContentValues().apply { put(COL_UPDATED, System.currentTimeMillis()) }
        db.update(TABLE_CONV, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun setPinned(id: Long, pinned: Boolean) {
        val db = helper.writableDatabase
        val values = ContentValues().apply { put(COL_PINNED, if (pinned) 1 else 0) }
        db.update(TABLE_CONV, values, "$COL_ID = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun delete(id: Long) {
        val db = helper.writableDatabase
        db.delete(TABLE_CONV, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /** Exclui conversas com updatedAt mais antigo que o prazo (dias). */
    @Synchronized
    fun cleanup(expiryDays: Int): Int {
        val cutoff = System.currentTimeMillis() - expiryDays * 86_400_000L
        val db = helper.writableDatabase
        return db.delete(TABLE_CONV, "$COL_UPDATED < ?", arrayOf(cutoff.toString()))
    }

    @Synchronized
    fun clearAll() {
        helper.writableDatabase.delete(TABLE_CONV, null, null)
    }

    private fun toConv(c: android.database.Cursor): Conversation = Conversation(
        id = c.getLong(c.getColumnIndexOrThrow(COL_ID)),
        title = c.getString(c.getColumnIndexOrThrow(COL_TITLE)),
        isPinned = c.getInt(c.getColumnIndexOrThrow(COL_PINNED)) == 1,
        createdAt = c.getLong(c.getColumnIndexOrThrow(COL_CREATED)),
        updatedAt = c.getLong(c.getColumnIndexOrThrow(COL_UPDATED))
    )

    class ConvDbHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_CONV (" +
                    "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COL_TITLE TEXT NOT NULL, " +
                    "$COL_PINNED INTEGER NOT NULL DEFAULT 0, " +
                    "$COL_CREATED INTEGER NOT NULL, " +
                    "$COL_UPDATED INTEGER NOT NULL" +
                    ");"
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_CONV")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "vulnforge_conversations.db"
        private const val DB_VERSION = 1
        private const val TABLE_CONV = "conversations"
        private const val COL_ID = "_id"
        private const val COL_TITLE = "title"
        private const val COL_PINNED = "pinned"
        private const val COL_CREATED = "created_at"
        private const val COL_UPDATED = "updated_at"
    }
}
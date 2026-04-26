package com.maticcm.openwebuiclient

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

class OfflineCacheDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "openwebui_cache.db"
        private const val DATABASE_VERSION = 1

        // Table names
        private const val TABLE_CHAT_HISTORY = "chat_history"
        private const val TABLE_FORM_DATA = "form_data"
        private const val TABLE_SESSION_STATE = "session_state"

        // Chat History columns
        private const val KEY_ID = "id"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_MESSAGE = "message"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_SYNCED = "synced"

        // Form Data columns
        private const val KEY_FORM_KEY = "form_key"
        private const val KEY_FORM_VALUE = "form_value"
        private const val KEY_URL = "url"

        // Session State columns
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_SESSION_VALUE = "session_value"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Create Chat History table
        val CREATE_CHAT_HISTORY_TABLE = """
            CREATE TABLE $TABLE_CHAT_HISTORY (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_CHAT_ID TEXT,
                $KEY_MESSAGE TEXT,
                $KEY_TIMESTAMP INTEGER,
                $KEY_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()

        // Create Form Data table
        val CREATE_FORM_DATA_TABLE = """
            CREATE TABLE $TABLE_FORM_DATA (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_FORM_KEY TEXT,
                $KEY_FORM_VALUE TEXT,
                $KEY_URL TEXT,
                $KEY_TIMESTAMP INTEGER
            )
        """.trimIndent()

        // Create Session State table
        val CREATE_SESSION_STATE_TABLE = """
            CREATE TABLE $TABLE_SESSION_STATE (
                $KEY_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_SESSION_KEY TEXT UNIQUE,
                $KEY_SESSION_VALUE TEXT,
                $KEY_TIMESTAMP INTEGER
            )
        """.trimIndent()

        db.execSQL(CREATE_CHAT_HISTORY_TABLE)
        db.execSQL(CREATE_FORM_DATA_TABLE)
        db.execSQL(CREATE_SESSION_STATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHAT_HISTORY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FORM_DATA")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSION_STATE")
        onCreate(db)
    }

    // Chat History operations
    fun saveChatMessage(chatId: String, message: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_CHAT_ID, chatId)
            put(KEY_MESSAGE, message)
            put(KEY_TIMESTAMP, Date().time)
            put(KEY_SYNCED, 0)
        }
        return db.insert(TABLE_CHAT_HISTORY, null, values)
    }

    fun getChatHistory(chatId: String): List<String> {
        val messages = mutableListOf<String>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_CHAT_HISTORY,
            arrayOf(KEY_MESSAGE),
            "$KEY_CHAT_ID = ?",
            arrayOf(chatId),
            null, null, "$KEY_TIMESTAMP ASC"
        )

        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    messages.add(it.getString(0))
                } while (it.moveToNext())
            }
        }
        return messages
    }

    fun markChatAsSynced(chatId: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_SYNCED, 1)
        }
        db.update(TABLE_CHAT_HISTORY, values, "$KEY_CHAT_ID = ?", arrayOf(chatId))
    }

    // Form Data operations
    fun saveFormData(key: String, value: String, url: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_FORM_KEY, key)
            put(KEY_FORM_VALUE, value)
            put(KEY_URL, url)
            put(KEY_TIMESTAMP, Date().time)
        }
        db.insert(TABLE_FORM_DATA, null, values)
    }

    fun getFormData(url: String): Map<String, String> {
        val formData = mutableMapOf<String, String>()
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_FORM_DATA,
            arrayOf(KEY_FORM_KEY, KEY_FORM_VALUE),
            "$KEY_URL = ?",
            arrayOf(url),
            null, null, null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                do {
                    formData[it.getString(0)] = it.getString(1)
                } while (it.moveToNext())
            }
        }
        return formData
    }

    // Session State operations
    fun saveSessionState(key: String, value: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(KEY_SESSION_KEY, key)
            put(KEY_SESSION_VALUE, value)
            put(KEY_TIMESTAMP, Date().time)
        }
        db.insertWithOnConflict(
            TABLE_SESSION_STATE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getSessionState(key: String): String? {
        val db = this.readableDatabase
        val cursor = db.query(
            TABLE_SESSION_STATE,
            arrayOf(KEY_SESSION_VALUE),
            "$KEY_SESSION_KEY = ?",
            arrayOf(key),
            null, null, null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    // Clear old data
    fun clearOldData(daysToKeep: Int = 7) {
        val db = this.writableDatabase
        val cutoffTime = Date().time - (daysToKeep * 24 * 60 * 60 * 1000L)

        db.delete(
            TABLE_CHAT_HISTORY,
            "$KEY_TIMESTAMP < ?",
            arrayOf(cutoffTime.toString())
        )

        db.delete(
            TABLE_FORM_DATA,
            "$KEY_TIMESTAMP < ?",
            arrayOf(cutoffTime.toString())
        )
    }

    fun clearAllCache() {
        val db = this.writableDatabase
        db.delete(TABLE_CHAT_HISTORY, null, null)
        db.delete(TABLE_FORM_DATA, null, null)
        db.delete(TABLE_SESSION_STATE, null, null)
    }
}

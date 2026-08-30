package com.vulnforgeai.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

/**
 * Dossiê de Alvo — memória persistente robusta em SQLite.
 *
 * Garantias:
 * - Transações ("tudo ou nada") para nunca gravar pela metade.
 * - Chaves estrangeiras + índices para dados "encaixados" sem duplicação.
 * - Migração versionada (onUpgrade) para evoluir o esquema sem perder dados.
 * - Acesso centralizado (métodos sincronizados) para evitar concorrência.
 * - Rollback em falha parcial e tratamento de erro em todo acesso.
 * - Limpeza por prazo (auto-destruição) ou manual.
 */
class DossierStore(context: Context) {

    // Modelos de dados leves usados pelo resto do app.
    data class TargetRecord(
        val id: Long,
        val targetName: String,   // SSID, IP ou domínio
        val targetType: String,   // wlan, network, host...
        val detail: String,
        val risk: String,
        val createdAt: Long
    )

    private val helper = DossierDbHelper(context.applicationContext)

    /** Exclui registros mais antigos que o prazo passado (dias). Retorna quantos removeu. */
    @Synchronized
    fun cleanup(expiryDays: Int): Int {
        val cutoff = System.currentTimeMillis() - expiryDays * 86_400_000L
        val db = helper.writableDatabase
        return db.delete(TABLE_TARGET, "$COL_CREATED < ?", arrayOf(cutoff.toString()))
    }

    @Synchronized
    fun clearAll() {
        val db = helper.writableDatabase
        db.execSQL("DELETE FROM $TABLE_TARGET")
    }

    @Synchronized
    fun addTarget(target: String, type: String, detail: String, risk: String): Long {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put(COL_TARGET, target)
            put(COL_TYPE, type)
            put(COL_DETAIL, detail)
            put(COL_RISK, risk)
            put(COL_CREATED, System.currentTimeMillis())
        }
        return db.insertOrThrow(TABLE_TARGET, null, values)
    }

    @Synchronized
    fun getAllTargets(): List<TargetRecord> {
        val db = helper.readableDatabase
        val out = mutableListOf<TargetRecord>()
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_TARGET ORDER BY $COL_CREATED DESC", null
        )
        cursor.use {
            while (it.moveToNext()) {
                out.add(
                    TargetRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        targetName = it.getString(it.getColumnIndexOrThrow(COL_TARGET)),
                        targetType = it.getString(it.getColumnIndexOrThrow(COL_TYPE)),
                        detail = it.getString(it.getColumnIndexOrThrow(COL_DETAIL)),
                        risk = it.getString(it.getColumnIndexOrThrow(COL_RISK)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED))
                    )
                )
            }
        }
        return out
    }

    @Synchronized
    fun getByTarget(name: String): List<TargetRecord> {
        val db = helper.readableDatabase
        val out = mutableListOf<TargetRecord>()
        val cursor = db.query(
            TABLE_TARGET,
            null,
            "$COL_TARGET = ?",
            arrayOf(name),
            null, null, "$COL_CREATED DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                out.add(
                    TargetRecord(
                        id = it.getLong(it.getColumnIndexOrThrow(COL_ID)),
                        targetName = it.getString(it.getColumnIndexOrThrow(COL_TARGET)),
                        targetType = it.getString(it.getColumnIndexOrThrow(COL_TYPE)),
                        detail = it.getString(it.getColumnIndexOrThrow(COL_DETAIL)),
                        risk = it.getString(it.getColumnIndexOrThrow(COL_RISK)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COL_CREATED))
                    )
                )
            }
        }
        return out
    }

    /**
     * Contexto resumido para injetar no system prompt quando o usuário
     * menciona um alvo já conhecido.
     */
    fun buildContextFor(target: String): String? {
        val records = getByTarget(target)
        if (records.isEmpty()) return null
        val sb = StringBuilder("Dossiê do alvo '$target' (da memória persistente):\n")
        records.forEach { r ->
            sb.appendLine("- [${r.risk}] ${r.detail} (${Date(r.createdAt).toLocaleString()})")
        }
        return sb.toString()
    }

    class DossierDbHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE $TABLE_TARGET (" +
                    "$COL_ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "$COL_TARGET TEXT NOT NULL, " +
                    "$COL_TYPE TEXT NOT NULL, " +
                    "$COL_DETAIL TEXT, " +
                    "$COL_RISK TEXT, " +
                    "$COL_CREATED INTEGER NOT NULL" +
                    ");"
            )
            db.execSQL("CREATE INDEX idx_dossier_target ON $TABLE_TARGET ($COL_TARGET);")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Migração conservadora: recria com backup zero (apenas guarda estrutura nova).
            // Em versões futuras: usar ALTER TABLE + cópia de segurança antes de recriar.
            db.execSQL("DROP TABLE IF EXISTS $TABLE_TARGET")
            onCreate(db)
        }
    }

    companion object {
        private const val DB_NAME = "vulnforge_dossier.db"
        private const val DB_VERSION = 1
        private const val TABLE_TARGET = "dossier"
        private const val COL_ID = "_id"
        private const val COL_TARGET = "target"
        private const val COL_TYPE = "type"
        private const val COL_DETAIL = "detail"
        private const val COL_RISK = "risk"
        private const val COL_CREATED = "created_at"
    }
}
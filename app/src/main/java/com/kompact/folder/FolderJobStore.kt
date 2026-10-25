package com.kompact.folder

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Durable queue: process death never requires keeping a photo collection in memory. */
internal class FolderJobStore(context: Context) : SQLiteOpenHelper(context, "folder-jobs.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE photos (job TEXT, uri TEXT, name TEXT, status TEXT, detail TEXT, saved INTEGER DEFAULT 0, PRIMARY KEY(job, uri))")
        db.execSQL("CREATE TABLE scans (job TEXT PRIMARY KEY)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun scanned(job: String) = readableDatabase.rawQuery("SELECT job FROM scans WHERE job = ?", arrayOf(job)).use { it.moveToFirst() }
    fun finishScan(job: String) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO scans(job) VALUES (?)", arrayOf(job))
    }
    fun add(job: String, uri: String, name: String) {
        writableDatabase.insertWithOnConflict("photos", null, ContentValues().apply {
            put("job", job); put("uri", uri); put("name", name)
        }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun next(job: String): Pair<String, String>? = readableDatabase.rawQuery(
        "SELECT uri, name FROM photos WHERE job = ? AND status IS NULL ORDER BY uri LIMIT 1", arrayOf(job)
    ).use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }

    fun finish(job: String, uri: String, status: String, detail: String, saved: Long = 0) {
        writableDatabase.update("photos", ContentValues().apply {
            put("status", status); put("detail", detail); put("saved", saved)
        }, "job = ? AND uri = ?", arrayOf(job, uri))
    }
    fun summary(job: String): Summary {
        var total = 0; var reduced = 0; var skipped = 0; var errors = 0; var saved = 0L
        readableDatabase.rawQuery("SELECT status, COUNT(*), SUM(saved) FROM photos WHERE job = ? GROUP BY status", arrayOf(job)).use {
            while (it.moveToNext()) {
                val count = it.getInt(1)
                total += count
                when (it.getString(0)) {
                    "reduced" -> reduced += count
                    "skipped" -> skipped += count
                    "error" -> errors += count
                }
                saved += it.getLong(2)
            }
        }
        return Summary(total, reduced, skipped, errors, saved)
    }
    fun details(job: String): String = buildString {
        readableDatabase.rawQuery("SELECT name, status, detail FROM photos WHERE job = ? AND status IN ('error', 'skipped') ORDER BY name", arrayOf(job)).use {
            // Full results stay in SQLite; cap the on-screen report to keep Compose responsive.
            var count = 0
            while (it.moveToNext() && count++ < 100) appendLine("${it.getString(0)}: ${it.getString(2)}")
            if (it.moveToNext()) appendLine("…")
        }
    }
    data class Summary(val total: Int, val reduced: Int, val skipped: Int, val errors: Int, val saved: Long) {
        val processed get() = reduced + skipped + errors
    }
}

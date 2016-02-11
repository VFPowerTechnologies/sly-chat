@file:JvmName("SQLiteUtils")
package com.vfpowertech.keytap.core.persistence.sqlite

import com.almworks.sqlite4java.SQLiteConnection
import com.almworks.sqlite4java.SQLiteStatement

inline fun <R> SQLiteConnection.use(body: (SQLiteConnection) -> R): R =
    try {
        body(this)
    }
    finally {
        this.dispose()
    }

inline fun <R> SQLiteStatement.use(body: (SQLiteStatement) -> R): R =
    try {
        body(this)
    }
    finally {
        this.dispose()
    }

inline fun <R> SQLiteConnection.withTransaction(body: (SQLiteConnection) -> R): R {
    this.exec("BEGIN TRANSACTION")
    return try {
        val r = body(this)
        this.exec("COMMIT TRANSACTION")
        r
    }
    catch (e: Throwable) {
        this.exec("ROLLBACK TRANSACTION")
        throw e
    }
}

/**
 * @param binder Function that binds values of T to an SQLiteStatement
 */
fun <T> SQLiteConnection.batchInsert(sql: String, items: Iterable<T>, binder: (SQLiteStatement, T) -> Unit) {
    prepare(sql).use { stmt ->
        for (item in items) {
            binder(stmt, item)
            stmt.step()
            stmt.reset(true)
        }
    }
}

fun <T> SQLiteConnection.batchInsertWithinTransaction(sql: String, items: Iterable<T>, binder: (SQLiteStatement, T) -> Unit) =
    withTransaction { batchInsert(sql, items, binder) }

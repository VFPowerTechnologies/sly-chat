@file:JvmName("SQLiteUtils")
package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.*
import io.slychat.messenger.core.Os
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.loadSharedLibFromResource
import io.slychat.messenger.core.persistence.*
import org.slf4j.LoggerFactory
import java.util.*

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
        //this is here because if an SQLiteException is thrown outside this block, sqlite4java'll pointlessly log the full
        //exception trace if we call dispose before reset
        this.reset()
        this.dispose()
    }

fun SQLiteStatement.columnNullableInt(index: Int): Int? =
    if (columnNull(index)) null else columnInt(index)

fun SQLiteStatement.columnNullableLong(index: Int): Long? =
    if (columnNull(index)) null else columnLong(index)

fun SQLiteStatement.columnBool(index: Int): Boolean =
    columnInt(index) != 0

fun SQLiteStatement.bind(index: Int, value: GroupId?) {
    bind(index, value?.string)
}

fun SQLiteStatement.bind(index: Int, enum: Enum<*>?) {
    bind(index, enum?.toString())
}

fun SQLiteStatement.bind(index: Int, boolean: Boolean) {
    val v = if (boolean) 1 else 0
    bind(index, v)
}

fun SQLiteStatement.bind(index: Int, value: UserId?) {
    if (value != null)
        bind(index, value.long)
    else
        bindNull(index)
}

fun SQLiteStatement.bind(index: Int, value: Int?) {
    if (value != null)
        bind(index, value)
    else
        bindNull(index)
}

fun SQLiteStatement.bind(index: Int, value: Long?) {
    if (value != null)
        bind(index, value)
    else
        bindNull(index)
}

fun SQLiteStatement.bind(index: Int, allowedMessageLevel: AllowedMessageLevel) {
    bind(index, allowedMessageLevel.toInt())
}

fun SQLiteStatement.bind(index: Int, groupMembershipLevel: GroupMembershipLevel) {
    bind(index, groupMembershipLevel.toInt())
}

fun SQLiteStatement.columnAllowedMessageLevel(index: Int): AllowedMessageLevel {
    return columnInt(index).toAllowedMessageLevel()
}

fun SQLiteStatement.columnConversationId(index: Int): ConversationId {
    return ConversationId.fromString(columnString(index))
}

fun SQLiteStatement.columnNullableConversationId(index: Int): ConversationId? {
    val string = columnString(index)
    return string?.let { ConversationId.fromString(it) }
}

fun SQLiteStatement.columnGroupMembershipLevel(index: Int): GroupMembershipLevel {
    return columnInt(index).toGroupMembershipLevel()
}

fun SQLiteStatement.columnLogEventType(index: Int): LogEventType {
    return LogEventType.valueOf(columnString(index))
}

private fun AllowedMessageLevel.toInt(): Int = when (this) {
    AllowedMessageLevel.BLOCKED -> 0
    AllowedMessageLevel.GROUP_ONLY -> 1
    AllowedMessageLevel.ALL -> 2
}

private fun Int.toAllowedMessageLevel(): AllowedMessageLevel = when (this) {
    0 -> AllowedMessageLevel.BLOCKED
    1 -> AllowedMessageLevel.GROUP_ONLY
    2 -> AllowedMessageLevel.ALL
    else -> throw IllegalArgumentException("Invalid integer value for AllowedMessageLevel: $this")
}

private fun GroupMembershipLevel.toInt(): Int = when (this) {
    GroupMembershipLevel.BLOCKED -> 0
    GroupMembershipLevel.PARTED -> 1
    GroupMembershipLevel.JOINED -> 2
}

private fun Int.toGroupMembershipLevel(): GroupMembershipLevel = when (this) {
    0 -> GroupMembershipLevel.BLOCKED
    1 -> GroupMembershipLevel.PARTED
    2 -> GroupMembershipLevel.JOINED
    else -> throw IllegalArgumentException("Invalid integer value for MembershipLevel: $this")
}

fun SQLiteStatement.bind(index: Int, conversationId: ConversationId?) {
    bind(index, conversationId?.asString())
}

fun SQLiteStatement.bind(index: Int, logEventType: LogEventType) {
    bind(index, logEventType.toString())
}

inline fun <R> SQLiteConnection.withPrepared(sql: String, body: (SQLiteStatement) -> R): R {
    return this.prepare(sql).use { body(it) }
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
fun <T> SQLiteConnection.batchInsert(sql: String, items: Collection<T>, binder: (SQLiteStatement, T) -> Unit) {
    if (items.isEmpty())
        return

    prepare(sql).use { stmt ->
        for (item in items) {
            binder(stmt, item)
            stmt.step()
            stmt.reset(true)
        }
    }
}

fun <T> SQLiteConnection.batchInsertWithinTransaction(sql: String, items: Collection<T>, binder: (SQLiteStatement, T) -> Unit) =
    withTransaction { batchInsert(sql, items, binder) }

/** Escapes the given string for use with the LIKE operator. */
fun escapeLikeString(s: String, escape: Char): String =
    Regex("[%_$escape]").replace(s) { m ->
        "$escape${m.groups[0]!!.value}"
    }

fun isInvalidTableException(e: SQLiteException): Boolean {
    val message = e.message
    return if (message == null)
        false
    else
        e.baseErrorCode == SQLiteConstants.SQLITE_ERROR && "no such table:" in message
}

/** Collects all results into the given mutable collection and returns the same collection. */
inline fun <T, C : MutableCollection<T>> SQLiteStatement.mapToCollection(body: (SQLiteStatement) -> T, results: C): C {
    while (step())
        results.add(body(this))

    return results
}

/** Calls the given function on all available query results. */
inline fun <T> SQLiteStatement.map(body: (SQLiteStatement) -> T): List<T> =
    mapToCollection(body, ArrayList<T>())

inline fun <T> SQLiteStatement.mapToSet(body: (SQLiteStatement) -> T): Set<T> =
    mapToCollection(body, HashSet<T>())

/** Iterates through all results. */
inline fun SQLiteStatement.foreach(body: (SQLiteStatement) -> Unit) {
    while (step())
        body(this)
}

fun escapeBackticks(s: String) = s.replace("`", "``")

fun getPlaceholders(n: Int): String =
    "?".repeat(n).toList().joinToString(", ")

//not exposed; taken from Internal.getArch, getOS so we can unpack + load the shared lib from resources for the proper OS
private fun getArch(os: String): String {
    val arch = System.getProperty("os.arch").toLowerCase()
    return if (os == "win32" && arch == "amd64")
        "x64"
    else
        arch
}

private fun getOs(): String {
    return when (currentOs.type) {
        Os.Type.OSX -> "osx"
        Os.Type.WINDOWS -> "win32"
        Os.Type.ANDROID -> "android"
        Os.Type.LINUX -> "linux"
        else -> throw RuntimeException("")
    }
}

fun sqlite4JavaGetLibraryName(): String {
    val os = getOs()
    val arch = getArch(os)

    return "sqlite4java-$os-$arch"
}

/** Loads the proper SQLite native library from the resource root. In core so it can be used by tests. */
fun Class<*>.loadSQLiteLibraryFromResources() {
    val base = sqlite4JavaGetLibraryName()
    LoggerFactory.getLogger(javaClass).info("Attempting to load SQLite native library: {}", base)
    loadSharedLibFromResource(base)
    SQLite.loadLibrary()
}
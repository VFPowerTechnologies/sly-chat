@file:JvmName("SQLiteUtils")
package io.slychat.messenger.core.persistence.sqlite

import com.almworks.sqlite4java.*
import io.slychat.messenger.core.Os
import io.slychat.messenger.core.UserId
import io.slychat.messenger.core.crypto.ciphers.CipherId
import io.slychat.messenger.core.crypto.ciphers.Key
import io.slychat.messenger.core.currentOs
import io.slychat.messenger.core.loadSharedLibFromResource
import io.slychat.messenger.core.persistence.*
import org.slf4j.LoggerFactory
import java.util.*

internal inline fun <R> SQLiteConnection.use(body: (SQLiteConnection) -> R): R =
    try {
        body(this)
    }
    finally {
        this.dispose()
    }

internal inline fun <R> SQLiteStatement.use(body: (SQLiteStatement) -> R): R =
    try {
        body(this)
    }
    finally {
        //this is here because if an SQLiteException is thrown outside this block, sqlite4java'll pointlessly log the full
        //exception trace if we call dispose before reset
        this.reset()
        this.dispose()
    }

internal fun SQLiteStatement.columnKey(index: Int): Key =
    Key(columnBlob(index))

internal fun SQLiteStatement.columnCipherId(index: Int): CipherId =
    CipherId(columnInt(index).toShort())

internal fun SQLiteStatement.columnUserId(index: Int): UserId =
    UserId(columnLong(index))

internal fun SQLiteStatement.columnGroupId(index: Int): GroupId =
    GroupId(columnString(index))

internal fun SQLiteStatement.columnNullableUserId(index: Int): UserId? {
    return if (!columnNull(index))
        UserId(columnLong(index))
    else
        null
}

internal fun SQLiteStatement.columnNullableGroupId(index: Int): GroupId? =
    columnString(index)?.let(::GroupId)

internal fun SQLiteStatement.columnNullableInt(index: Int): Int? =
    if (columnNull(index)) null else columnInt(index)

internal fun SQLiteStatement.columnNullableLong(index: Int): Long? =
    if (columnNull(index)) null else columnLong(index)

internal fun SQLiteStatement.columnBool(index: Int): Boolean =
    columnInt(index) != 0

internal fun SQLiteStatement.bind(name: String, value: GroupId?) {
    bind(name, value?.string)
}

internal fun SQLiteStatement.bind(index: Int, value: GroupId?) {
    bind(index, value?.string)
}

internal fun SQLiteStatement.bind(index: Int, enum: Enum<*>?) {
    bind(index, enum?.toString())
}

internal fun SQLiteStatement.bind(name: String, boolean: Boolean) {
    val v = if (boolean) 1 else 0
    bind(name, v)
}

internal fun SQLiteStatement.bind(index: Int, boolean: Boolean) {
    val v = if (boolean) 1 else 0
    bind(index, v)
}

internal fun SQLiteStatement.bind(name: String, value: UserId?) {
    if (value != null)
        bind(name, value.long)
    else
        bindNull(name)
}

internal fun SQLiteStatement.bind(index: Int, value: UserId?) {
    if (value != null)
        bind(index, value.long)
    else
        bindNull(index)
}

internal fun SQLiteStatement.bind(index: Int, value: Int?) {
    if (value != null)
        bind(index, value)
    else
        bindNull(index)
}

internal fun SQLiteStatement.bind(index: Int, value: Long?) {
    if (value != null)
        bind(index, value)
    else
        bindNull(index)
}

internal fun SQLiteStatement.bind(index: Int, allowedMessageLevel: AllowedMessageLevel) {
    bind(index, allowedMessageLevel.toInt())
}

internal fun SQLiteStatement.bind(index: Int, groupMembershipLevel: GroupMembershipLevel) {
    bind(index, groupMembershipLevel.toInt())
}

internal fun SQLiteStatement.columnAllowedMessageLevel(index: Int): AllowedMessageLevel {
    return columnInt(index).toAllowedMessageLevel()
}

internal fun SQLiteStatement.columnConversationId(index: Int): ConversationId {
    return ConversationId.fromString(columnString(index))
}

internal fun SQLiteStatement.columnNullableConversationId(index: Int): ConversationId? {
    val string = columnString(index)
    return string?.let { ConversationId.fromString(it) }
}

internal fun SQLiteStatement.columnGroupMembershipLevel(index: Int): GroupMembershipLevel {
    return columnInt(index).toGroupMembershipLevel()
}

internal fun SQLiteStatement.columnLogEventType(index: Int): LogEventType {
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

internal fun SQLiteStatement.bind(name: String, conversationId: ConversationId?) {
    bind(name, conversationId?.asString())
}

internal fun SQLiteStatement.bind(name: String, key: Key?) {
    bind(name, key?.raw)
}

internal fun SQLiteStatement.bind(name: String, cipherId: CipherId) {
    bind(name, cipherId.short.toInt())
}

internal fun SQLiteStatement.bind(index: Int, conversationId: ConversationId?) {
    bind(index, conversationId?.asString())
}

internal fun SQLiteStatement.bind(index: Int, logEventType: LogEventType) {
    bind(index, logEventType.toString())
}

internal inline fun <R> SQLiteConnection.withPrepared(sql: String, body: (SQLiteStatement) -> R): R {
    return this.prepare(sql).use { body(it) }
}

internal inline fun <R> SQLiteConnection.withTransaction(body: (SQLiteConnection) -> R): R {
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
internal fun <T> SQLiteConnection.batchInsert(sql: String, items: Collection<T>, binder: (SQLiteStatement, T) -> Unit) {
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

internal fun <T> SQLiteConnection.batchInsertWithinTransaction(sql: String, items: Collection<T>, binder: (SQLiteStatement, T) -> Unit) =
    withTransaction { batchInsert(sql, items, binder) }

/** Escapes the given string for use with the LIKE operator. */
internal fun escapeLikeString(s: String, escape: Char): String =
    Regex("[%_$escape]").replace(s) { m ->
        "$escape${m.groups[0]!!.value}"
    }

internal fun isInvalidTableException(e: SQLiteException): Boolean {
    val message = e.message
    return if (message == null)
        false
    else
        e.baseErrorCode == SQLiteConstants.SQLITE_ERROR && "no such table:" in message
}

/** Collects all results into the given mutable collection and returns the same collection. */
internal inline fun <T, C : MutableCollection<T>> SQLiteStatement.mapToCollection(body: (SQLiteStatement) -> T, results: C): C {
    while (step())
        results.add(body(this))

    return results
}

/** Calls the given function on all available query results. */
internal inline fun <T> SQLiteStatement.map(body: (SQLiteStatement) -> T): List<T> =
    mapToCollection(body, ArrayList<T>())

internal inline fun <T> SQLiteStatement.mapToSet(body: (SQLiteStatement) -> T): Set<T> =
    mapToCollection(body, HashSet<T>())

/** Iterates through all results. */
internal inline fun SQLiteStatement.foreach(body: (SQLiteStatement) -> Unit) {
    while (step())
        body(this)
}

internal fun escapeBackticks(s: String) = s.replace("`", "``")

internal fun getPlaceholders(n: Int): String =
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
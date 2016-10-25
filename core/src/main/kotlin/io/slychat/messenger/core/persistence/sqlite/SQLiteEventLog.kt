package io.slychat.messenger.core.persistence.sqlite
import com.almworks.sqlite4java.SQLiteStatement
import com.fasterxml.jackson.databind.ObjectMapper
import io.slychat.messenger.core.persistence.*
import nl.komponents.kovenant.Promise
import java.util.*

class SQLiteEventLog(
    private val sqlitePersistenceManager: SQLitePersistenceManager
) : EventLog {
    override fun addEvent(event: LogEvent): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val sql = """
INSERT INTO
    event_log
    (conversation_id, type, timestamp, data)
VALUES
    (?, ?, ?, ?)
"""
        connection.withPrepared(sql) { stmt ->
            val objectMapper = ObjectMapper()

            val target = event.target

            val conversationId = when (target) {
                is LogTarget.System -> null
                is LogTarget.Conversation -> target.id
            }

            stmt.bind(1, conversationId)
            stmt.bind(2, event.type)
            stmt.bind(3, event.timestamp)
            stmt.bind(4, objectMapper.writeValueAsBytes(event.data))

            stmt.step()

            Unit
        }
    }

    private fun clauseFromConstraints(types: Set<LogEventType>, target: LogTarget?): String {
        val constraints = ArrayList<String>()

        if (types.isNotEmpty())
            constraints.add("type IN (${types.joinToString(separator = ",", prefix = "'", postfix = "'")})")

        if (target != null) {
            val conversationIdConstraint = when (target) {
                is LogTarget.System -> "conversation_id IS null"
                is LogTarget.Conversation -> "conversation_id = '${target.id.asString()}'"
            }

            constraints.add(conversationIdConstraint)
        }

        return if (constraints.isNotEmpty())
            constraints.joinToString(separator = " AND ")
        else
            ""
    }

    override fun getEvents(types: Set<LogEventType>, target: LogTarget?, startingAt: Int, count: Int): Promise<List<LogEvent>, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val clause = clauseFromConstraints(types, target)

        val whereClause = if (clause.isNotEmpty())
            "WHERE " + clause
        else
            ""

        val sql = """
SELECT
    conversation_id,
    type,
    timestamp,
    data
FROM
    event_log
$whereClause
ORDER BY
    timestamp DESC
LIMIT
    $count
OFFSET
    $startingAt
"""

        connection.withPrepared(sql) { stmt ->
            stmt.map { rowToLogEvent(it) }
        }
    }

    private fun rowToLogEvent(stmt: SQLiteStatement): LogEvent {
        val maybeConversationId = stmt.columnNullableConversationId(0)

        val target = if (maybeConversationId != null)
            LogTarget.Conversation(maybeConversationId)
        else
            LogTarget.System

        val type = stmt.columnLogEventType(1)

        val timestamp = stmt.columnLong(2)

        val rawData = stmt.columnBlob(3)

        val objectMapper = ObjectMapper()

        return when (type) {
            LogEventType.SECURITY -> {
                val data = objectMapper.readValue(rawData, SecurityEventData::class.java)
                LogEvent.Security(target, timestamp, data)
            }

            LogEventType.GROUP -> {
                val data = objectMapper.readValue(rawData, GroupEventData::class.java)
                LogEvent.Group(target, timestamp, data)
            }
        }
    }

    //TODO constraints
    override fun deleteEventRange(types: Set<LogEventType>, target: LogTarget?, startTimestamp: Long, endTimestamp: Long): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val clause = clauseFromConstraints(types, target)
        val additionalClauses = if (clause.isNotEmpty())
            "AND " + clause
        else
            ""

        val sql = """
DELETE FROM
    event_log
WHERE
    timestamp >= $startTimestamp
AND
    timestamp <= $endTimestamp
$additionalClauses
"""
        connection.withPrepared(sql) { it.step() }

        Unit
    }

    override fun deleteEvents(types: Set<LogEventType>, target: LogTarget?): Promise<Unit, Exception> = sqlitePersistenceManager.runQuery { connection ->
        val clause = clauseFromConstraints(types, target)
        val whereClause = if (clause.isNotEmpty())
            "WHERE " + clause
        else
            ""

        val sql = """
DELETE FROM
    event_log
$whereClause
"""
        connection.withPrepared(sql) { it.step() }

        Unit
    }
}
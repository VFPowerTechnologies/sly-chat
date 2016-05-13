package io.slychat.messenger.core.persistence.sqlite

class TableCreationFailedException(private val tableName: String, cause: Throwable) :
    RuntimeException("Failed to create table $tableName", cause)
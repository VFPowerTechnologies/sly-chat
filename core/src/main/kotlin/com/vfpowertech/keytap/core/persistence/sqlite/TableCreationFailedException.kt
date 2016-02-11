package com.vfpowertech.keytap.core.persistence.sqlite

class TableCreationFailedException(private val tableName: String, cause: Throwable) :
    RuntimeException("Failed to create table $tableName", cause)
package io.slychat.messenger.core.persistence.sqlite.migrations

import io.slychat.messenger.core.persistence.sqlite.DatabaseMigration

@Suppress("unused")
class DatabaseMigration1 : DatabaseMigration(1, listOf("package_queue"))
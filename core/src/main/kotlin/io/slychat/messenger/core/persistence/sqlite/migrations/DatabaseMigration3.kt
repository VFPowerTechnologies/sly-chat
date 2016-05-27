package io.slychat.messenger.core.persistence.sqlite.migrations

import io.slychat.messenger.core.persistence.sqlite.DatabaseMigration

@Suppress("unused")
class DatabaseMigration3 : DatabaseMigration(3, listOf("remote_contact_updates"))
package dev.staticvar.mcp.indexer.database.column

import org.postgresql.util.PGobject

/**
 * Wrapper to let Exposed bind Kotlin enums to PostgreSQL enum columns.
 */
class PgEnum<T : Enum<T>>(enumName: String, enumValue: T) : PGobject() {
    init {
        type = enumName
        value = enumValue.name
    }
}

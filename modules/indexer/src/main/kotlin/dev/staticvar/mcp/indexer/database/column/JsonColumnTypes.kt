package dev.staticvar.mcp.indexer.database.column

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.postgresql.util.PGobject

private val json = Json { ignoreUnknownKeys = true }
private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

class JsonbMapColumnType : ColumnType<Map<String, String>>() {
    override fun sqlType(): String = "jsonb"

    override fun valueFromDB(value: Any): Map<String, String> =
        when (value) {
            is PGobject -> decode(value.value)
            is Map<*, *> -> value.entries.associate { it.key.toString() to it.value.toString() }
            is String -> decode(value)
            else -> error("Unsupported JSONB value (${value::class.qualifiedName})")
        }

    override fun notNullValueToDB(value: Map<String, String>): Any =
        PGobject().apply {
            type = "jsonb"
            this.value = encode(value)
        }

    override fun nonNullValueToString(value: Map<String, String>): String = "'${encode(value).replace("'", "''")}'"

    private fun encode(value: Map<String, String>): String = json.encodeToString(mapSerializer, value)

    private fun decode(raw: String?): Map<String, String> =
        if (raw.isNullOrBlank()) emptyMap() else json.decodeFromString(mapSerializer, raw)
}

fun Table.jsonbMap(name: String): Column<Map<String, String>> = registerColumn(name, JsonbMapColumnType())

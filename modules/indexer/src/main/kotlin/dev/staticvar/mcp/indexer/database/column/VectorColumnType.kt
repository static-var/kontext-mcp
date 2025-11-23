package dev.staticvar.mcp.indexer.database.column

import com.pgvector.PGvector
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import java.sql.SQLException

class VectorColumnType(
    private val dimension: Int,
) : ColumnType<FloatArray>() {
    override fun sqlType(): String = "vector($dimension)"

    override fun valueFromDB(value: Any): FloatArray =
        when (value) {
            is PGvector -> value.toArray()
            is FloatArray -> value
            is DoubleArray -> value.map { it.toFloat() }.toFloatArray()
            is String -> parseVector(value)
            else -> error("Unsupported vector value (${value::class.qualifiedName})")
        }

    override fun notNullValueToDB(value: FloatArray): Any = PGvector(value)

    override fun nonNullValueToString(value: FloatArray): String {
        val pgVector = PGvector(value)
        val raw = pgVector.value ?: value.joinToString(prefix = "[", postfix = "]")
        return "'${escape(raw)}'"
    }

    private fun parseVector(value: String): FloatArray =
        try {
            PGvector(value).toArray()
        } catch (exception: SQLException) {
            throw IllegalArgumentException("Unable to parse vector string", exception)
        }

    private fun escape(raw: String): String = raw.replace("'", "''")
}

fun Table.vector(
    name: String,
    dimension: Int,
): Column<FloatArray> = registerColumn(name, VectorColumnType(dimension))

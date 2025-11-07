package dev.staticvar.mcp.indexer.database

import com.pgvector.PGvector
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.staticvar.mcp.shared.config.DatabaseConfig
import java.sql.Connection
import java.sql.SQLException
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import javax.sql.DataSource

/**
 * Factory for database initialization and migration.
 * Manages connection pooling via HikariCP and schema migrations via Flyway.
 */
object DatabaseFactory {

    fun init(config: DatabaseConfig): Database {
        val dataSource = createDataSource(config)
        runMigrations(dataSource)
        return Database.connect(
            datasource = dataSource,
            manager = ::transactionManagerWithPgVector
        )
    }

    private fun createDataSource(config: DatabaseConfig): DataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            validate()
        }

        return HikariDataSource(hikariConfig)
    }

    private fun runMigrations(dataSource: DataSource) {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()

        flyway.migrate()
    }

    private fun transactionManagerWithPgVector(database: Database): TransactionManager =
        TransactionManager(database) { exposedConnection, _ ->
            val jdbcConnection = exposedConnection.connection
            if (jdbcConnection is Connection) {
                try {
                    PGvector.addVectorType(jdbcConnection)
                } catch (exception: SQLException) {
                    throw IllegalStateException("Unable to register pgvector JDBC type", exception)
                }
            }
        }
}

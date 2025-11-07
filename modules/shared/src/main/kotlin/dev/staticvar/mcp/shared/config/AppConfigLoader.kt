package dev.staticvar.mcp.shared.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Loads [AppConfig] from the default `config/application.conf` file or a user-provided path.
 * Environment variables are resolved via HOCON's `${?VAR}` mechanism.
 */
object AppConfigLoader {

    private const val DEFAULT_CONFIG_PATH = "config/application.conf"
    private const val CONFIG_PATH_ENV = "APP_CONFIG_PATH"

    /**
     * Load application configuration.
     *
     * @param overridePath Optional path that takes precedence over defaults.
     * @param allowMissing When true and the resolved file is missing, fall back to `ConfigFactory.load()`.
     * @throws IllegalStateException when the resolved file is missing and [allowMissing] is false.
     */
    fun load(overridePath: Path? = null, allowMissing: Boolean = true): AppConfig {
        val resolvedPath = resolvePath(overridePath)
        val fileConfig = parseFileConfig(resolvedPath, allowMissing)

        val mergedConfig = fileConfig
            .withFallback(ConfigFactory.load())
            .resolve()

        return mergedConfig.toAppConfig()
    }

    private fun resolvePath(overridePath: Path?): Path =
        overridePath
            ?: System.getenv(CONFIG_PATH_ENV)?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
            ?: Paths.get(DEFAULT_CONFIG_PATH)

    private fun parseFileConfig(path: Path, allowMissing: Boolean) =
        if (Files.exists(path)) {
            ConfigFactory.parseFile(path.toFile())
        } else {
            if (!allowMissing) {
                error("Configuration file not found at $path")
            }
            ConfigFactory.empty()
        }

    private fun Config.toAppConfig(): AppConfig {
        val databaseConfig = getConfig("database").let {
            DatabaseConfig(
                host = it.getString("host"),
                port = it.getInt("port"),
                database = it.getString("database"),
                username = it.getString("username"),
                password = it.getString("password"),
                maxPoolSize = it.getInt("maxPoolSize")
            )
        }

        val embeddingConfig = getConfig("embedding").let {
            EmbeddingConfig(
                modelPath = it.getString("modelPath"),
                modelCacheDir = it.getString("modelCacheDir"),
                dimension = it.getInt("dimension"),
                batchSize = it.getInt("batchSize"),
                maxTokens = it.getInt("maxTokens")
            )
        }

        val chunkingConfig = getConfig("chunking").let {
            ChunkingConfig(
                targetTokens = it.getInt("targetTokens"),
                overlapTokens = it.getInt("overlapTokens"),
                maxTokens = it.getInt("maxTokens"),
                preserveCodeBlocks = it.getBoolean("preserveCodeBlocks"),
                preserveSectionHierarchy = it.getBoolean("preserveSectionHierarchy")
            )
        }

        val retrievalConfig = getConfig("retrieval").let {
            RetrievalConfig(
                defaultTokenBudget = it.getInt("defaultTokenBudget"),
                maxTokenBudget = it.getInt("maxTokenBudget"),
                defaultSimilarityThreshold = it.getDouble("defaultSimilarityThreshold").toFloat(),
                topKCandidates = it.getInt("topKCandidates"),
                enableReranking = it.getBoolean("enableReranking")
            )
        }

        val crawlerConfig = getConfig("crawler").let {
            CrawlerConfig(
                maxConcurrentRequests = it.getInt("maxConcurrentRequests"),
                requestDelayMs = it.getLong("requestDelayMs"),
                userAgent = it.getString("userAgent"),
                timeout = it.getLong("timeout"),
                retryAttempts = it.getInt("retryAttempts")
            )
        }

        return AppConfig(
            database = databaseConfig,
            embedding = embeddingConfig,
            chunking = chunkingConfig,
            retrieval = retrievalConfig,
            crawler = crawlerConfig
        )
    }
}

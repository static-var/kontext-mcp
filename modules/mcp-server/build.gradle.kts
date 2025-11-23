plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass.set("dev.staticvar.mcp.server.McpServerAppKt")
}

dependencies {
    implementation(project(":modules:shared"))
    implementation(project(":modules:embedder"))
    implementation(project(":modules:indexer"))

    // MCP Kotlin SDK
    implementation(libs.mcp.kotlin.sdk)
    implementation(libs.kotlinx.io.core.jvm)
    implementation(libs.ktorServerSse)

    // Database + Exposed (transitive from indexer but required at runtime)
    implementation(libs.bundles.database)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.flyway)

    // Ktor server (if needed for HTTP transport later)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    // Configuration
    implementation(libs.typesafe.config)

    // Tokenization for response size estimation
    implementation(libs.jtokkit)

    // Logging
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

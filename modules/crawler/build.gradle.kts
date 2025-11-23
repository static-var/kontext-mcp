plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass.set("dev.staticvar.mcp.crawler.server.CrawlerServerKt")
}

dependencies {
    implementation(project(":modules:shared"))
    implementation(project(":modules:parser"))
    implementation(project(":modules:embedder"))
    implementation(project(":modules:indexer"))

    // Web server & client
    implementation(libs.ktorServerCore)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)
    implementation(libs.ktorServerAuth)
    implementation(libs.ktorServerSessions)
    implementation(libs.ktorServerHtmlBuilder)
    implementation(libs.ktorServerStatusPages)
    implementation(libs.ktorServerCallLogging)
    implementation(libs.ktorServerSse)
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorClientLogging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.bundles.exposed)

    // Serialization & configuration
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.typesafe.config)
    implementation(libs.auth0.jwt)

    // Scheduling
    implementation(libs.krontab)

    // Logging
    implementation(libs.bundles.logging)
    implementation(libs.bcrypt)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.ktorClientMock)
}

// Web assets are copied into src/main/resources/static by the Dockerfile before build

plugins {
    application
    alias(libs.plugins.kotlin.serialization)
}

import org.gradle.language.jvm.tasks.ProcessResources

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

tasks.register("buildWeb") {
    val webDir = project(":modules:web").projectDir
    inputs.dir(webDir.resolve("src"))
    inputs.dir(webDir.resolve("public"))
    inputs.file(webDir.resolve("package.json"))
    inputs.file(webDir.resolve("package-lock.json"))
    inputs.file(webDir.resolve("vite.config.ts"))
    inputs.file(webDir.resolve("index.html"))
    inputs.file(webDir.resolve("tsconfig.json"))
    inputs.file(webDir.resolve("tailwind.config.cjs"))
    inputs.file(webDir.resolve("postcss.config.cjs"))
    outputs.dir(webDir.resolve("dist"))

    doLast {
        exec {
            workingDir = webDir
            commandLine = listOf("npm", "install")
        }
        exec {
            workingDir = webDir
            commandLine = listOf("npm", "run", "build")
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn("buildWeb")
    // Exclude the placeholder index.html from src/main/resources
    exclude("static/index.html")
    
    from(project(":modules:web").projectDir.resolve("dist")) {
        into("static")
    }
}

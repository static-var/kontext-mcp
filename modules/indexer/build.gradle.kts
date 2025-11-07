dependencies {
    implementation(project(":modules:shared"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    // PostgreSQL + pgvector + connection pooling
    implementation(libs.bundles.database)

    // SQL DSL (Exposed)
    implementation(libs.bundles.exposed)

    // Database migrations
    implementation(libs.bundles.flyway)

    // Logging
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
}

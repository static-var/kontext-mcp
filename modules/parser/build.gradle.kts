dependencies {
    implementation(project(":modules:shared"))

    // HTML parsing
    implementation(libs.jsoup)

    // HTTP client for fetching pages
    implementation(libs.bundles.ktor.client)

    // Markdown parsing (for certain Kotlin docs)
    implementation(libs.markdown)

    // Tokenization for chunk size estimation
    implementation(libs.jtokkit)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)

    // Logging
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.mockk)
}

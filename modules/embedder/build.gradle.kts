dependencies {
    implementation(project(":modules:shared"))

    // ONNX Runtime for embedding model inference
    implementation(libs.onnx.runtime)

    // Tokenizer (using HuggingFace tokenizers via JNI)
    implementation(libs.djl.tokenizers)

    // HTTP client for model downloading
    implementation(libs.bundles.ktor.client)

    // Logging
    implementation(libs.bundles.logging)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.ktorClientMock)
    testImplementation(libs.mockk)
}

-- Insert initial source URLs for Android and Kotlin documentation
-- These are starting points; the crawler will discover more pages

-- Android Developer Docs
INSERT INTO source_urls (url, parser_type, enabled, status) VALUES
('https://developer.android.com/guide', 'ANDROID_DOCS', true, 'PENDING'),
('https://developer.android.com/reference', 'ANDROID_DOCS', true, 'PENDING'),
('https://developer.android.com/jetpack/compose', 'ANDROID_DOCS', true, 'PENDING'),
('https://developer.android.com/kotlin', 'ANDROID_DOCS', true, 'PENDING'),
('https://developer.android.com/topic/architecture', 'ANDROID_DOCS', true, 'PENDING'),
('https://developer.android.com/codelabs', 'ANDROID_DOCS', true, 'PENDING');

-- Kotlin Language Docs
INSERT INTO source_urls (url, parser_type, enabled, status) VALUES
('https://kotlinlang.org/docs/home.html', 'KOTLIN_LANG', true, 'PENDING'),
('https://kotlinlang.org/docs/reference/', 'KOTLIN_LANG', true, 'PENDING'),
('https://kotlinlang.org/docs/coroutines-overview.html', 'KOTLIN_LANG', true, 'PENDING'),
('https://kotlinlang.org/docs/multiplatform.html', 'KOTLIN_LANG', true, 'PENDING'),
('https://kotlinlang.org/api/latest/jvm/stdlib/', 'KOTLIN_LANG', true, 'PENDING');

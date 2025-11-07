package dev.staticvar.mcp.parser

import dev.staticvar.mcp.parser.android.AndroidDocsParser
import dev.staticvar.mcp.parser.generic.GenericHtmlParser
import dev.staticvar.mcp.parser.jetbrains.JetbrainsHelpParser
import dev.staticvar.mcp.parser.kotlinlang.KotlinLangParser
import dev.staticvar.mcp.parser.registry.ParserRegistry

object ParserModule {
    fun defaultRegistry(): ParserRegistry = ParserRegistry(
        listOf(
            AndroidDocsParser(),
            KotlinLangParser(),
            JetbrainsHelpParser(),
            GenericHtmlParser()
        )
    )
}

package dev.staticvar.mcp.parser.chunk

import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.Encoding
import com.knuddels.jtokkit.api.EncodingType
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger(TokenEstimator::class.java)

/**
 * Utility responsible for token estimation using jtokkit.
 */
class TokenEstimator(
    encodingType: EncodingType = EncodingType.CL100K_BASE,
) {
    private val encoding: Encoding = Encodings.newDefaultEncodingRegistry().getEncoding(encodingType)

    fun countTokens(text: String): Int =
        try {
            encoding.encode(text).size()
        } catch (exception: Exception) {
            logger.warn("Token estimation failed, falling back to length heuristic", exception)
            (text.length / 4).coerceAtLeast(1)
        }
}

package io.github.nelurea.muninn.capture

object UrlResolver {

    private val pixivPattern =
        Regex("""pixiv\.net/artworks/(\d+)""")

    private val xPattern =
        Regex("""x\.com/.+/status/(\d+)""")

    private val redditPattern =
        Regex("""reddit\.com/.+/comments/([A-Za-z0-9]+)""")

    fun resolve(
        request: CaptureRequest
    ): ResolvedCapture? {

        pixivPattern.find(request.sourceUrl)
            ?.let { match ->
                return ResolvedCapture(
                    sourceType = SourceType.PIXIV,
                    sourceId = match.groupValues[1],
                    imageIndex = request.imageIndex
                )
            }

        xPattern.find(request.sourceUrl)
            ?.let { match ->
                return ResolvedCapture(
                    sourceType = SourceType.X,
                    sourceId = match.groupValues[1],
                    imageIndex = request.imageIndex
                )
            }

        redditPattern.find(request.sourceUrl)
            ?.let { match ->
                return ResolvedCapture(
                    sourceType = SourceType.REDDIT,
                    sourceId = match.groupValues[1],
                    imageIndex = request.imageIndex
                )
            }

        if (request.sourceUrl.contains("reddit.com")) {

            return ResolvedCapture(
                sourceType = SourceType.REDDIT,
                sourceId = request.sourceUrl,
                imageIndex = request.imageIndex
            )
        }

        return null
    }
}
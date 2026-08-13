package io.github.nelurea.muninn.discovery.pixiv

data class PixivSearchResponse(
    val error: Boolean,
    val message: String?,
    val body: PixivSearchBody?
)

data class PixivSearchBody(
    val illustManga: PixivSearchIllustManga?
)

data class PixivSearchIllustManga(
    val data: List<PixivSearchIllust>,
    val total: Int?,
    val lastPage: Int?
)

data class PixivSearchIllust(
    val id: String?,
    val title: String?,
    val url: String?,
    val userId: String?,
    val userName: String?,
    val pageCount: Int?,
    val xRestrict: Int?,
    val width: Int?,
    val height: Int?,
    val isAdContainer: Boolean
)
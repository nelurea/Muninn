package io.github.nelurea.muninn.discovery.pixiv

data class PixivBookmarksResponse(
    val error: Boolean,
    val message: String?,
    val body: PixivBookmarksBody?
)

data class PixivBookmarksBody(
    val bookmarks: List<PixivBookmarkIllust>?,
    val total: Int?,
    val lastPage: Int?
)

data class PixivBookmarkIllust(
    val id: String?,
    val url: String?,
    val url_s: String?,
    val url_sm: String?,
    val url_w: String?,
    val title: String?,
    val alt: String?,
    val tags: List<String>?,
    val page_count: Int?,
    val x_restrict: Int?,
    val width: Int?,
    val height: Int?,
    val author_details: PixivFollowingAuthor?
)
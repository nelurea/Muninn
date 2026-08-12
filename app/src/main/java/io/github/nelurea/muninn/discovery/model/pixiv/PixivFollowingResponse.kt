package io.github.nelurea.muninn.discovery.pixiv

data class PixivFollowingResponse(
    val error: Boolean,
    val message: String?,
    val body: PixivFollowingBody?
)

data class PixivFollowingBody(
    val illusts: List<PixivFollowingIllust>?,
    val total: Int?,
    val lastPage: Boolean?
)

data class PixivFollowingIllust(
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
    val author_details: PixivFollowingAuthor?,
    val is_ad_container: Int?
)

data class PixivFollowingAuthor(
    val user_id: String?,
    val user_name: String?,
    val user_account: String?
)
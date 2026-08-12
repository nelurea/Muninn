package io.github.nelurea.muninn.discovery.pixiv

import android.webkit.CookieManager

class PixivAccountIdentityProvider(
    private val cookieManager: CookieManager =
        CookieManager.getInstance()
) {

    fun getLoggedInUserId(): String? {
        val cookie =
            cookieManager.getCookie(
                PIXIV_ORIGIN
            )
                ?: return null

        return cookie
            .split(
                ";"
            )
            .asSequence()
            .map {
                it.trim()
            }
            .firstOrNull {
                it.startsWith(
                    PHPSESSID_PREFIX
                )
            }
            ?.substringAfter(
                PHPSESSID_PREFIX
            )
            ?.substringBefore(
                "_"
            )
            ?.takeIf {
                    value ->

                value.isNotBlank() &&
                        value.all {
                            it.isDigit()
                        }
            }
    }

    private companion object {

        const val PIXIV_ORIGIN =
            "https://www.pixiv.net"

        const val PHPSESSID_PREFIX =
            "PHPSESSID="
    }
}
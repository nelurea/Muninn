package io.github.nelurea.muninn.discovery.x

import android.net.Uri
import android.webkit.CookieManager

class XWebSessionState(
    private val cookieManager: CookieManager =
        CookieManager.getInstance()
) {

    fun getAuthenticatedUserId(): String? {
        val cookies =
            cookieManager.getCookie(
                X_ORIGIN
            )
                ?: return null

        if (
            findCookieValue(
                cookies,
                AUTH_TOKEN_COOKIE
            ) == null
        ) {
            return null
        }

        if (
            findCookieValue(
                cookies,
                CSRF_COOKIE
            ) == null
        ) {
            return null
        }

        val twid =
            findCookieValue(
                cookies,
                TWID_COOKIE
            )
                ?: return null

        val decoded =
            Uri.decode(
                twid
            )

        val start =
            decoded.indexOf(
                USER_ID_PREFIX
            )

        if (start < 0) {
            return null
        }

        val userId =
            decoded
                .substring(
                    start +
                        USER_ID_PREFIX.length
                )
                .takeWhile {
                    it.isDigit()
                }

        return userId
            .takeIf {
                it.isNotBlank()
            }
    }

    private fun findCookieValue(
        cookies: String,
        name: String
    ): String? {
        val prefix =
            "$name="

        return cookies
            .split(
                ";"
            )
            .asSequence()
            .map {
                it.trim()
            }
            .firstOrNull {
                it.startsWith(
                    prefix
                )
            }
            ?.substringAfter(
                prefix
            )
    }

    private companion object {

        const val X_ORIGIN =
            "https://x.com"

        const val AUTH_TOKEN_COOKIE =
            "auth_token"

        const val CSRF_COOKIE =
            "ct0"

        const val TWID_COOKIE =
            "twid"

        const val USER_ID_PREFIX =
            "u="
    }
}

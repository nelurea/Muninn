package io.github.nelurea.muninn.ui.discovery

import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.nelurea.muninn.discovery.model.DiscoveryMode
import io.github.nelurea.muninn.discovery.x.XDiscoveryBatch
import io.github.nelurea.muninn.discovery.x.XDiscoveryBatchParseResult
import io.github.nelurea.muninn.discovery.x.XDiscoveryBatchParser
import io.github.nelurea.muninn.discovery.x.XDiscoveryObservationStore

@Composable
fun XDiscoveryWebSession(
    mode: DiscoveryMode,
    searchQuery: String,
    refreshToken: Int,
    onBatchObserved: (
        XDiscoveryBatch
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var webView by remember {
        mutableStateOf<WebView?>(
            null
        )
    }

    var likesNavigationStarted by remember {
        mutableStateOf(
            false
        )
    }

    var lastNavigationKey by remember {
        mutableStateOf<String?>(
            null
        )
    }

    val currentMode =
        rememberUpdatedState(
            mode
        )

    val currentSearchQuery =
        rememberUpdatedState(
            searchQuery
        )

    val currentOnBatchObserved =
        rememberUpdatedState(
            onBatchObserved
        )

    DisposableEffect(
        Unit
    ) {
        onDispose {
            webView
                ?.stopLoading()

            webView
                ?.destroy()

            webView =
                null
        }
    }

    AndroidView(
        modifier =
            modifier.fillMaxSize(),
        factory = {
                context ->

            WebView(
                context
            ).apply {
                webView =
                    this

                /*
                 * This WebView exists only as an authenticated
                 * X data source for Discovery.
                 *
                 * It stays attached so X behaves like a normal
                 * WebView, but is never shown to the user.
                 */
                visibility =
                    View.INVISIBLE

                settings.javaScriptEnabled =
                    true

                settings.domStorageEnabled =
                    true

                settings.allowFileAccess =
                    false

                settings.allowContentAccess =
                    false

                settings.mixedContentMode =
                    WebSettings
                        .MIXED_CONTENT_NEVER_ALLOW

                val cookieManager =
                    CookieManager
                        .getInstance()

                cookieManager
                    .setAcceptCookie(
                        true
                    )

                cookieManager
                    .setAcceptThirdPartyCookies(
                        this,
                        true
                    )

                webViewClient =
                    object :
                        WebViewClient() {

                        override fun onPageFinished(
                            view: WebView,
                            url: String?
                        ) {
                            super.onPageFinished(
                                view,
                                url
                            )

                            if (
                                currentMode.value !=
                                DiscoveryMode.BOOKMARKS ||
                                likesNavigationStarted
                            ) {
                                return
                            }

                            val normalizedUrl =
                                url
                                    ?.substringBefore(
                                        "?"
                                    )
                                    ?.trimEnd(
                                        '/'
                                    )
                                    ?: return

                            /*
                             * /i/user/<id> redirects to the
                             * authenticated user's canonical
                             * profile URL:
                             *
                             * https://x.com/<username>
                             */
                            val profileMatch =
                                X_PROFILE_URL_REGEX
                                    .matchEntire(
                                        normalizedUrl
                                    )
                                    ?: return

                            val username =
                                profileMatch
                                    .groupValues[
                                    1
                                ]

                            if (
                                username.lowercase() in
                                X_RESERVED_PATHS
                            ) {
                                return
                            }

                            likesNavigationStarted =
                                true

                            val likesUrl =
                                "$normalizedUrl/likes"

                            Log.d(
                                LOG_TAG,
                                "Resolved X Likes page."
                            )

                            view.loadUrl(
                                likesUrl
                            )
                        }
                    }

                installXDiscoveryBridge(
                    webView =
                        this,
                    onBatchObserved = {
                            batch ->

                        currentOnBatchObserved
                            .value(
                                batch
                            )
                    }
                )
            }
        }
    )

    LaunchedEffect(
        webView,
        mode,
        refreshToken
    ) {
        val currentWebView =
            webView
                ?: return@LaunchedEffect

        val navigationKey =
            when (
                mode
            ) {
                DiscoveryMode.LATEST ->
                    "LATEST:$refreshToken"

                DiscoveryMode.BOOKMARKS ->
                    "BOOKMARKS:$refreshToken"

                DiscoveryMode.SEARCH -> {
                    val query =
                        currentSearchQuery
                            .value
                            .trim()

                    "SEARCH:$query:$refreshToken"
                }
            }

        /*
         * Recomposition can restart this effect while the
         * WebView remains the same. Do not issue the same
         * navigation twice.
         */
        if (
            lastNavigationKey ==
            navigationKey
        ) {
            return@LaunchedEffect
        }

        lastNavigationKey =
            navigationKey

        when (
            mode
        ) {
            DiscoveryMode.LATEST -> {
                Log.d(
                    LOG_TAG,
                    "Loading X For You."
                )

                currentWebView.loadUrl(
                    X_HOME_URL
                )
            }

            DiscoveryMode.BOOKMARKS -> {
                val userId =
                    resolveAuthenticatedXUserId()

                if (
                    userId == null
                ) {
                    Log.e(
                        LOG_TAG,
                        "Authenticated X user id is unavailable."
                    )

                    return@LaunchedEffect
                }

                likesNavigationStarted =
                    false

                Log.d(
                    LOG_TAG,
                    "Loading X Likes."
                )

                currentWebView.loadUrl(
                    "https://x.com/i/user/$userId"
                )
            }

            DiscoveryMode.SEARCH -> {
                val query =
                    currentSearchQuery
                        .value
                        .trim()

                if (
                    query.isBlank()
                ) {
                    return@LaunchedEffect
                }

                Log.d(
                    LOG_TAG,
                    "Loading X Search."
                )

                currentWebView.loadUrl(
                    buildXSearchUrl(
                        query
                    )
                )
            }
        }
    }
}

private fun WebView.installXDiscoveryBridge(
    webView: WebView,
    onBatchObserved: (
        XDiscoveryBatch
    ) -> Unit
) {
    if (
        !WebViewFeature
            .isFeatureSupported(
                WebViewFeature
                    .JS_INJECTION_IN_FRAME_AND_WORLD
            )
    ) {
        Log.e(
            LOG_TAG,
            "Page-world JavaScript injection is unsupported."
        )

        return
    }

    val xOrigins =
        setOf(
            X_ORIGIN
        )

    val pageWorld =
        WebViewCompat
            .getExecutionWorld(
                webView,
                JavaScriptExecutionWorld
                    .PAGE_WORLD_NAME
            )

    WebViewCompat
        .addWebMessageListener(
            webView,
            "MuninnBridge",
            xOrigins,
            pageWorld
        ) {
                _,
                message,
                sourceOrigin,
                isMainFrame,
                _ ->

            if (
                !isMainFrame
            ) {
                return@addWebMessageListener
            }

            if (
                sourceOrigin
                    .toString() !=
                X_ORIGIN
            ) {
                return@addWebMessageListener
            }

            val rawMessage =
                message.data
                    ?: return@addWebMessageListener

            when (
                val parseResult =
                    XDiscoveryBatchParser
                        .parse(
                            rawMessage
                        )
            ) {
                is XDiscoveryBatchParseResult.Success -> {
                    val batch =
                        parseResult.batch

                    val mergeResult =
                        XDiscoveryObservationStore
                            .merge(
                                batch
                            )

                    Log.d(
                        LOG_TAG,
                        buildString {
                            append(
                                "mode=${batch.mode}"
                            )

                            append(
                                ", received=${mergeResult.receivedCount}"
                            )

                            append(
                                ", added=${mergeResult.addedCount}"
                            )

                            append(
                                ", total=${mergeResult.totalCount}"
                            )
                        }
                    )

                    onBatchObserved(
                        batch
                    )
                }

                is XDiscoveryBatchParseResult.Failure -> {
                    /*
                     * The injected hook can also emit ordinary
                     * X_CAPTURE_RESULT messages.
                     *
                     * This WebView only owns Discovery batches,
                     * so unrelated bridge messages are ignored.
                     */
                    if (
                        rawMessage.contains(
                            "\"type\":\"X_DISCOVERY_BATCH\""
                        )
                    ) {
                        Log.e(
                            LOG_TAG,
                            "Discovery batch parse failed: ${parseResult.error}"
                        )
                    }
                }
            }
        }

    val xCaptureHook =
        webView
            .context
            .assets
            .open(
                "webcapture/x/capture-hook.js"
            )
            .bufferedReader()
            .use {
                it.readText()
            }

    WebViewCompat
        .addJavaScriptOnEvent(
            webView,
            xCaptureHook,
            WebViewCompat
                .INJECTION_EVENT_DOCUMENT_START,
            xOrigins,
            pageWorld
        )
}

private fun resolveAuthenticatedXUserId(): String? {
    val cookies =
        CookieManager
            .getInstance()
            .getCookie(
                "$X_ORIGIN/"
            )
            ?: return null

    val twid =
        cookies
            .split(
                ";"
            )
            .asSequence()
            .map {
                it.trim()
            }
            .firstOrNull {
                it.startsWith(
                    "twid="
                )
            }
            ?.substringAfter(
                "twid="
            )
            ?: return null

    val decodedTwid =
        Uri.decode(
            twid
        )

    return X_TWID_USER_REGEX
        .find(
            decodedTwid
        )
        ?.groupValues
        ?.getOrNull(
            1
        )
}

private fun buildXSearchUrl(
    query: String
): String {
    return buildString {
        append(
            "$X_ORIGIN/search?q="
        )

        append(
            Uri.encode(
                query
            )
        )

        append(
            "&src=typed_query"
        )
    }
}

private const val LOG_TAG =
    "Muninn/X/DiscoveryWeb"

private const val X_ORIGIN =
    "https://x.com"

private const val X_HOME_URL =
    "$X_ORIGIN/home"

private val X_PROFILE_URL_REGEX =
    Regex(
        """^https://x\.com/([^/]+)$"""
    )

private val X_TWID_USER_REGEX =
    Regex(
        """u=(\d+)"""
    )

private val X_RESERVED_PATHS =
    setOf(
        "home",
        "explore",
        "search",
        "notifications",
        "messages",
        "settings",
        "compose",
        "login",
        "logout",
        "i"
    )
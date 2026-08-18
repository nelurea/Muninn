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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun XDiscoveryWebSession(
    mode: DiscoveryMode,
    searchQuery: String,
    refreshToken: Int,
    loadMoreToken: Int,
    onLoadMoreStateChange: (
        Boolean
    ) -> Unit,
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

    var bookmarksNavigationState by remember {
        mutableStateOf(
            XBookmarksNavigationState.IDLE
        )
    }

    var bookmarksUserId by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var bookmarksRetryCount by remember {
        mutableIntStateOf(
            0
        )
    }

    var lastNavigationKey by remember {
        mutableStateOf<String?>(
            null
        )
    }

    var xLoadMoreActive by remember {
        mutableStateOf(
            false
        )
    }

    var xLoadMoreRetryCount by remember {
        mutableIntStateOf(
            0
        )
    }

    var xAutoRefreshAttempted by remember {
        mutableStateOf(
            false
        )
    }

    var xLastAutoRefreshAtMs by remember {
        mutableStateOf(
            0L
        )
    }

    val coroutineScope =
        rememberCoroutineScope()

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

    val currentOnLoadMoreStateChange =
        rememberUpdatedState(
            onLoadMoreStateChange
        )

    fun tryResolveXProfileAndLoadLikes(
        currentWebView: WebView,
        url: String?
    ): Boolean {
        if (
            currentMode.value !=
            DiscoveryMode.BOOKMARKS ||
            bookmarksNavigationState !=
            XBookmarksNavigationState.RESOLVING_PROFILE
        ) {
            return false
        }

        val normalizedUrl =
            url
                ?.substringBefore(
                    "?"
                )
                ?.trimEnd(
                    '/'
                )
                ?: return false

        val profileMatch =
            X_PROFILE_URL_REGEX
                .matchEntire(
                    normalizedUrl
                )
                ?: return false

        val username =
            profileMatch
                .groupValues[
                1
            ]

        if (
            username.lowercase() in
            X_RESERVED_PATHS
        ) {
            return false
        }

        bookmarksNavigationState =
            XBookmarksNavigationState.LOADING_LIKES

        Log.d(
            LOG_TAG,
            "Resolved X profile; loading Likes."
        )

        currentWebView.loadUrl(
            "$normalizedUrl/likes"
        )

        return true
    }
    fun tryAutoRefreshAfterStall(
        currentWebView: WebView
    ): Boolean {
        if (
            xAutoRefreshAttempted
        ) {
            return false
        }

        val now =
            System.currentTimeMillis()

        val cooldownElapsed =
            xLastAutoRefreshAtMs == 0L ||
                    now - xLastAutoRefreshAtMs >=
                    X_AUTO_REFRESH_COOLDOWN_MS

        if (
            !cooldownElapsed
        ) {
            Log.d(
                LOG_TAG,
                "Automatic X refresh skipped because cooldown is active."
            )

            return false
        }

        val refreshed =
            refreshCurrentXDiscoveryPage(
                webView =
                    currentWebView,
                mode =
                    currentMode.value,
                searchQuery =
                    currentSearchQuery.value
            )

        if (
            !refreshed
        ) {
            return false
        }

        xAutoRefreshAttempted =
            true

        xLastAutoRefreshAtMs =
            now

        return true
    }

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

                            tryResolveXProfileAndLoadLikes(
                                currentWebView =
                                    view,
                                url =
                                    url
                            )
                        }

                        override fun doUpdateVisitedHistory(
                            view: WebView,
                            url: String?,
                            isReload: Boolean
                        ) {
                            super.doUpdateVisitedHistory(
                                view,
                                url,
                                isReload
                            )

                            tryResolveXProfileAndLoadLikes(
                                currentWebView =
                                    view,
                                url =
                                    url
                            )
                        }
                    }

                installXDiscoveryBridge(
                    webView =
                        this,
                    shouldAcceptBatch = {
                            batch ->

                        when (
                            batch.mode
                        ) {
                            DiscoveryMode.LATEST ->
                                currentMode.value ==
                                    DiscoveryMode.LATEST

                            DiscoveryMode.BOOKMARKS ->
                                currentMode.value ==
                                    DiscoveryMode.BOOKMARKS &&
                                    (
                                            bookmarksNavigationState ==
                                                XBookmarksNavigationState.LOADING_LIKES ||
                                            bookmarksNavigationState ==
                                                XBookmarksNavigationState.READY
                                            )

                            DiscoveryMode.SEARCH -> {
                                val currentQuery =
                                    currentSearchQuery
                                        .value
                                        .trim()

                                val observedQuery =
                                    batch.query
                                        ?.trim()
                                        .orEmpty()

                                currentMode.value ==
                                    DiscoveryMode.SEARCH &&
                                        currentQuery ==
                                        observedQuery
                            }
                        }
                    },
                    onBatchObserved = {
                            batch,
                            addedCount ->

                        if (
                            batch.mode ==
                            DiscoveryMode.BOOKMARKS &&
                            currentMode.value ==
                            DiscoveryMode.BOOKMARKS
                        ) {
                            bookmarksNavigationState =
                                XBookmarksNavigationState.READY

                            bookmarksRetryCount =
                                0
                        }

                        if (
                            xLoadMoreActive
                        ) {
                            if (
                                addedCount > 0
                            ) {
                                xLoadMoreActive =
                                    false

                                xLoadMoreRetryCount =
                                    0

                                xAutoRefreshAttempted =
                                    false

                                currentOnLoadMoreStateChange
                                    .value(
                                        false
                                    )
                            } else if (
                                xLoadMoreRetryCount <
                                MAX_X_LOAD_MORE_RETRIES
                            ) {
                                xLoadMoreRetryCount +=
                                    1

                                val retryNumber =
                                    xLoadMoreRetryCount

                                Log.d(
                                    LOG_TAG,
                                    "X timeline added 0 items; retrying ($retryNumber/$MAX_X_LOAD_MORE_RETRIES)."
                                )

                                coroutineScope.launch {
                                    delay(
                                        X_LOAD_MORE_RETRY_DELAY_MS
                                    )

                                    if (
                                        xLoadMoreActive
                                    ) {
                                        requestMoreXTimeline(
                                            this@apply
                                        )
                                    }
                                }
                            } else {
                                Log.d(
                                    LOG_TAG,
                                    "X timeline load-more retries exhausted."
                                )

                                val autoRefreshStarted =
                                    tryAutoRefreshAfterStall(
                                        this@apply
                                    )

                                if (
                                    autoRefreshStarted
                                ) {
                                    Log.d(
                                        LOG_TAG,
                                        "Automatic X refresh started after stalled load-more."
                                    )
                                } else {
                                    xLoadMoreActive =
                                        false

                                    currentOnLoadMoreStateChange
                                        .value(
                                            false
                                        )
                                }
                            }
                        }

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
        bookmarksNavigationState,
        bookmarksUserId,
        bookmarksRetryCount
    ) {
        val currentWebView =
            webView
                ?: return@LaunchedEffect

        val userId =
            bookmarksUserId
                ?: return@LaunchedEffect

        when (
            bookmarksNavigationState
        ) {
            XBookmarksNavigationState.RESOLVING_PROFILE -> {
                repeat(
                    X_PROFILE_RESOLVE_POLL_COUNT
                ) {
                    if (
                        currentMode.value !=
                        DiscoveryMode.BOOKMARKS ||
                        bookmarksNavigationState !=
                        XBookmarksNavigationState.RESOLVING_PROFILE
                    ) {
                        return@LaunchedEffect
                    }

                    if (
                        tryResolveXProfileAndLoadLikes(
                            currentWebView =
                                currentWebView,
                            url =
                                currentWebView.url
                        )
                    ) {
                        return@LaunchedEffect
                    }

                    delay(
                        X_PROFILE_RESOLVE_POLL_INTERVAL_MS
                    )
                }

                if (
                    currentMode.value !=
                    DiscoveryMode.BOOKMARKS ||
                    bookmarksNavigationState !=
                    XBookmarksNavigationState.RESOLVING_PROFILE
                ) {
                    return@LaunchedEffect
                }

                if (
                    bookmarksRetryCount <
                    MAX_X_BOOKMARKS_NAVIGATION_RETRIES
                ) {
                    bookmarksRetryCount +=
                        1

                    Log.d(
                        LOG_TAG,
                        "Retrying X profile resolution."
                    )

                    currentWebView.loadUrl(
                        "$X_ORIGIN/i/user/$userId"
                    )
                } else {
                    Log.e(
                        LOG_TAG,
                        "X profile resolution timed out."
                    )

                    bookmarksNavigationState =
                        XBookmarksNavigationState.IDLE
                }
            }

            XBookmarksNavigationState.LOADING_LIKES -> {
                delay(
                    X_BOOKMARKS_BATCH_TIMEOUT_MS
                )

                if (
                    currentMode.value !=
                    DiscoveryMode.BOOKMARKS ||
                    bookmarksNavigationState !=
                    XBookmarksNavigationState.LOADING_LIKES
                ) {
                    return@LaunchedEffect
                }

                if (
                    bookmarksRetryCount <
                    MAX_X_BOOKMARKS_NAVIGATION_RETRIES
                ) {
                    bookmarksRetryCount +=
                        1

                    bookmarksNavigationState =
                        XBookmarksNavigationState.RESOLVING_PROFILE

                    Log.d(
                        LOG_TAG,
                        "X Likes batch timed out; resolving profile again."
                    )

                    currentWebView.loadUrl(
                        "$X_ORIGIN/i/user/$userId"
                    )
                } else {
                    Log.e(
                        LOG_TAG,
                        "X Likes batch timed out."
                    )

                    bookmarksNavigationState =
                        XBookmarksNavigationState.IDLE
                }
            }

            XBookmarksNavigationState.IDLE,
            XBookmarksNavigationState.READY ->
                Unit
        }
    }

    LaunchedEffect(
        webView,
        loadMoreToken
    ) {
        if (
            loadMoreToken <= 0
        ) {
            return@LaunchedEffect
        }

        val currentWebView =
            webView
                ?: return@LaunchedEffect

        xLoadMoreActive =
            true

        xLoadMoreRetryCount =
            0

        xAutoRefreshAttempted =
            false

        currentOnLoadMoreStateChange
            .value(
                true
            )

        requestMoreXTimeline(
            currentWebView
        )

        /*
         * X can occasionally ignore a synthetic scroll
         * without producing another HomeTimeline response.
         * Never leave the Discovery loading indicator active
         * indefinitely in that case.
         */
        delay(
            X_LOAD_MORE_TIMEOUT_MS
        )

        if (
            xLoadMoreActive
        ) {
            Log.d(
                LOG_TAG,
                "X timeline load-more timed out."
            )

            val autoRefreshAlreadyStarted =
                xAutoRefreshAttempted

            val autoRefreshStarted =
                if (
                    autoRefreshAlreadyStarted
                ) {
                    true
                } else {
                    tryAutoRefreshAfterStall(
                        currentWebView
                    )
                }

            if (
                autoRefreshStarted
            ) {
                /*
                 * Give the refreshed X page one bounded
                 * opportunity to produce a new timeline batch.
                 */
                delay(
                    X_AUTO_REFRESH_TIMEOUT_MS
                )
            }

            if (
                xLoadMoreActive
            ) {
                Log.d(
                    LOG_TAG,
                    "X timeline did not recover after fallback."
                )

                xLoadMoreActive =
                    false

                xLoadMoreRetryCount =
                    0

                xAutoRefreshAttempted =
                    false

                currentOnLoadMoreStateChange
                    .value(
                        false
                    )
            }
        }
    }


    LaunchedEffect(
        webView,
        mode,
        refreshToken
    ) {
        val currentWebView =
            webView
                ?: return@LaunchedEffect

        if (
            xLoadMoreActive
        ) {
            xLoadMoreActive =
                false

            xLoadMoreRetryCount =
                0

            xAutoRefreshAttempted =
                false

            currentOnLoadMoreStateChange
                .value(
                    false
                )
        }

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
                bookmarksNavigationState =
                    XBookmarksNavigationState.IDLE

                bookmarksUserId =
                    null

                bookmarksRetryCount =
                    0

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
                    bookmarksNavigationState =
                        XBookmarksNavigationState.IDLE

                    bookmarksUserId =
                        null

                    Log.e(
                        LOG_TAG,
                        "Authenticated X user id is unavailable."
                    )

                    return@LaunchedEffect
                }

                bookmarksUserId =
                    userId

                bookmarksRetryCount =
                    0

                bookmarksNavigationState =
                    XBookmarksNavigationState.RESOLVING_PROFILE

                Log.d(
                    LOG_TAG,
                    "Loading X Likes."
                )

                currentWebView.loadUrl(
                    "$X_ORIGIN/i/user/$userId"
                )
            }

            DiscoveryMode.SEARCH -> {
                bookmarksNavigationState =
                    XBookmarksNavigationState.IDLE

                bookmarksUserId =
                    null

                bookmarksRetryCount =
                    0

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

private fun refreshCurrentXDiscoveryPage(
    webView: WebView,
    mode: DiscoveryMode,
    searchQuery: String
): Boolean {
    val currentUrl =
        webView.url
            ?.takeIf {
                it.startsWith(
                    "$X_ORIGIN/"
                )
            }

    val fallbackUrl =
        when (
            mode
        ) {
            DiscoveryMode.LATEST ->
                X_HOME_URL

            DiscoveryMode.BOOKMARKS ->
                null

            DiscoveryMode.SEARCH -> {
                val query =
                    searchQuery.trim()

                if (
                    query.isBlank()
                ) {
                    null
                } else {
                    buildXSearchUrl(
                        query
                    )
                }
            }
        }

    val targetUrl =
        currentUrl
            ?: fallbackUrl
            ?: return false

    Log.d(
        LOG_TAG,
        "Refreshing hidden X Discovery page after stalled pagination."
    )

    /*
     * Reload only the authenticated hidden WebView.
     *
     * XDiscoveryObservationStore and the visible Discovery
     * list are intentionally left intact, so newly observed
     * items are merged without resetting the user's position.
     */
    webView.loadUrl(
        targetUrl
    )

    return true
}


private fun requestMoreXTimeline(
    webView: WebView
) {
    Log.d(
        LOG_TAG,
        "Requesting more X timeline items."
    )

    webView.evaluateJavascript(
        """
        (() => {
          const scrollingElement =
            document.scrollingElement ||
            document.documentElement;

          if (!scrollingElement) {
            return false;
          }

          const viewport =
            window.innerHeight || 800;

          scrollingElement.scrollTo({
            top:
              Math.max(
                0,
                scrollingElement.scrollHeight -
                  viewport * 1.5
              ),
            behavior:
              "instant"
          });

          setTimeout(
            () => {
              scrollingElement.scrollTo({
                top:
                  scrollingElement.scrollHeight,
                behavior:
                  "instant"
              });
            },
            180
          );

          return true;
        })();
        """.trimIndent(),
        null
    )
}


private fun WebView.installXDiscoveryBridge(
    webView: WebView,
    shouldAcceptBatch: (
        XDiscoveryBatch
    ) -> Boolean,
    onBatchObserved: (
        XDiscoveryBatch,
        Int
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

                    if (
                        !shouldAcceptBatch(
                            batch
                        )
                    ) {
                        Log.d(
                            LOG_TAG,
                            "Ignoring stale X Discovery batch for ${batch.mode}."
                        )

                        return@addWebMessageListener
                    }

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
                        batch,
                        mergeResult.addedCount
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

private enum class XBookmarksNavigationState {
    IDLE,
    RESOLVING_PROFILE,
    LOADING_LIKES,
    READY
}

private const val LOG_TAG =
    "Muninn/X/DiscoveryWeb"

private const val X_ORIGIN =
    "https://x.com"

private const val X_HOME_URL =
    "$X_ORIGIN/home"

private const val MAX_X_LOAD_MORE_RETRIES =
    3

private const val X_LOAD_MORE_RETRY_DELAY_MS =
    700L

private const val X_LOAD_MORE_TIMEOUT_MS =
    1_500L

private const val X_AUTO_REFRESH_TIMEOUT_MS =
    1_500L

private const val X_AUTO_REFRESH_COOLDOWN_MS =
    30_000L

private const val X_PROFILE_RESOLVE_POLL_INTERVAL_MS =
    200L

private const val X_PROFILE_RESOLVE_POLL_COUNT =
    25

private const val X_BOOKMARKS_BATCH_TIMEOUT_MS =
    8_000L

private const val MAX_X_BOOKMARKS_NAVIGATION_RETRIES =
    2

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

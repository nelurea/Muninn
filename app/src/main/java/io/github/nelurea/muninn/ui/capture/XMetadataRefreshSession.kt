package io.github.nelurea.muninn.ui.capture

import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.nelurea.muninn.capture.web.x.XCaptureParseResult
import io.github.nelurea.muninn.capture.web.x.XCaptureParser
import io.github.nelurea.muninn.capture.web.x.XCapturePayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun XMetadataRefreshSession(
    canonicalUrl: String,
    sourceId: String,
    onPayload: (XCapturePayload) -> Unit,
    onFailure: (String) -> Unit
) {
    val coroutineScope =
        rememberCoroutineScope()

    val currentOnPayload =
        rememberUpdatedState(
            onPayload
        )

    val currentOnFailure =
        rememberUpdatedState(
            onFailure
        )

    val completed =
        remember(
            canonicalUrl,
            sourceId
        ) {
            mutableStateOf(
                false
            )
        }

    val lastError =
        remember(
            canonicalUrl,
            sourceId
        ) {
            mutableStateOf<String?>(
                null
            )
        }

    val captureStarted =
        remember(
            canonicalUrl,
            sourceId
        ) {
            mutableStateOf(
                false
            )
        }

    val webViewState =
        remember {
            mutableStateOf<WebView?>(
                null
            )
        }

    LaunchedEffect(
        canonicalUrl,
        sourceId
    ) {
        delay(
            SESSION_TIMEOUT_MS
        )

        if (
            !completed.value
        ) {
            completed.value =
                true

            currentOnFailure.value(
                lastError.value
                    ?: "Timed out while refreshing X metadata"
            )
        }
    }

    DisposableEffect(
        canonicalUrl,
        sourceId
    ) {
        onDispose {
            webViewState.value
                ?.stopLoading()

            webViewState.value
                ?.destroy()

            webViewState.value =
                null
        }
    }

    key(
        canonicalUrl,
        sourceId
    ) {
        AndroidView(
            modifier =
                Modifier.size(
                    1.dp
                ),
            factory = {
                context ->

            WebView(
                context
            ).apply {
                webViewState.value =
                    this

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

                cookieManager.setAcceptCookie(
                    true
                )

                cookieManager
                    .setAcceptThirdPartyCookies(
                        this,
                        true
                    )

                if (
                    !WebViewFeature
                        .isFeatureSupported(
                            WebViewFeature
                                .JS_INJECTION_IN_FRAME_AND_WORLD
                        )
                ) {
                    completed.value =
                        true

                    currentOnFailure.value(
                        "X metadata refresh is unsupported by this WebView"
                    )

                    return@apply
                }

                val xOrigins =
                    setOf(
                        X_ORIGIN
                    )

                val pageWorld =
                    WebViewCompat
                        .getExecutionWorld(
                            this,
                            JavaScriptExecutionWorld
                                .PAGE_WORLD_NAME
                        )

                WebViewCompat
                    .addWebMessageListener(
                        this,
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
                            completed.value ||
                            !isMainFrame ||
                            sourceOrigin.toString() !=
                            X_ORIGIN
                        ) {
                            return@addWebMessageListener
                        }

                        val rawMessage =
                            message.data
                                ?: return@addWebMessageListener
if (
                            !rawMessage.contains(
                                "\"type\":\"X_CAPTURE_RESULT\""
                            )
                        ) {
                            return@addWebMessageListener
                        }

                        when (
                            val result =
                                XCaptureParser.parse(
                                    rawMessage
                                )
                        ) {
                            is XCaptureParseResult.Success -> {
                                if (
                                    result.payload.sourceId !=
                                    sourceId
                                ) {
                                    lastError.value =
                                        "Refreshed X post did not match the saved work"

                                    return@addWebMessageListener
                                }

                                completed.value =
                                    true
currentOnPayload.value(
                                    result.payload
                                )
                            }

                            is XCaptureParseResult.Failure -> {
                                lastError.value =
                                    result.error
                            }
                        }
                    }

                val captureHook =
                    context
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
                        this,
                        captureHook,
                        WebViewCompat
                            .INJECTION_EVENT_DOCUMENT_START,
                        xOrigins,
                        pageWorld
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
                                captureStarted.value
                            ) {
                                return
                            }

                            captureStarted.value =
                                true

                            coroutineScope.launch {
                                repeat(
                                    CAPTURE_ATTEMPTS
                                ) {
                                    if (
                                        completed.value
                                    ) {
                                        return@launch
                                    }

                                    delay(
                                        CAPTURE_RETRY_DELAY_MS
                                    )

                                    view.evaluateJavascript(
                                        """
                                        (() => {
                                          if (
                                            typeof window.__muninnCaptureX !==
                                            "function"
                                          ) {
                                            return "hook-unavailable";
                                          }

                                          return window.__muninnCaptureX();
                                        })();
                                        """.trimIndent(),
                                        null
                                    )
                                }

                                if (
                                    !completed.value
                                ) {
                                    completed.value =
                                        true

                                    currentOnFailure.value(
                                        lastError.value
                                            ?: "Timed out while refreshing X metadata"
                                    )
                                }
                            }
                        }
                    }

                loadUrl(
                    canonicalUrl
                )
            }
            }
        )
    }
}

private const val X_ORIGIN =
    "https://x.com"

private const val CAPTURE_ATTEMPTS =
    24

private const val CAPTURE_RETRY_DELAY_MS =
    350L
private const val SESSION_TIMEOUT_MS =
    12_000L

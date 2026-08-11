package io.github.nelurea.muninn.ui.browser

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.nelurea.muninn.capture.web.pixiv.PixivCaptureParseResult
import io.github.nelurea.muninn.capture.web.pixiv.PixivCaptureParser
import io.github.nelurea.muninn.capture.web.pixiv.PixivMediaDownloadResult
import io.github.nelurea.muninn.capture.web.pixiv.PixivMediaDownloader
import kotlinx.coroutines.launch
import io.github.nelurea.muninn.capture.model.CaptureDraft
import io.github.nelurea.muninn.capture.usecase.SaveCaptureResult
import io.github.nelurea.muninn.capture.usecase.SaveCaptureUseCase
import io.github.nelurea.muninn.capture.web.pixiv.PixivCaptureMapper

@Composable
fun WebCaptureScreen(
    saveCaptureUseCase: SaveCaptureUseCase,
    onBack: () -> Unit
) {
    val initialUrl =
        "https://www.pixiv.net/"

    var address by rememberSaveable {
        mutableStateOf(initialUrl)
    }

    var currentUrl by rememberSaveable {
        mutableStateOf(initialUrl)
    }

    var canGoBack by remember {
        mutableStateOf(false)
    }

    var canGoForward by remember {
        mutableStateOf(false)
    }

    var webView by remember {
        mutableStateOf<WebView?>(null)
    }

    val coroutineScope =
        rememberCoroutineScope()

    fun loadAddress() {
        val normalizedUrl =
            if (
                address.startsWith("http://") ||
                address.startsWith("https://")
            ) {
                address
            } else {
                "https://$address"
            }

        address =
            normalizedUrl

        webView?.loadUrl(
            normalizedUrl
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(8.dp),
        verticalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBack
            ) {
                Text("Close")
            }

            Button(
                enabled =
                    canGoBack,
                onClick = {
                    webView?.goBack()
                }
            ) {
                Text("←")
            }

            Button(
                enabled =
                    canGoForward,
                onClick = {
                    webView?.goForward()
                }
            ) {
                Text("→")
            }

            Button(
                onClick = {
                    webView?.reload()
                }
            ) {
                Text("Reload")
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value =
                    address,
                onValueChange = {
                    address = it
                },
                modifier =
                    Modifier.weight(1f),
                singleLine =
                    true,
                label = {
                    Text("URL")
                }
            )

            Button(
                onClick = {
                    loadAddress()
                }
            ) {
                Text("Open")
            }
        }

        Text(
            text =
                currentUrl,
            modifier =
                Modifier.fillMaxWidth()
        )

        AndroidView(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            factory = { context ->

                WebView(context).apply {

                    webView =
                        this

                    settings.javaScriptEnabled =
                        true

                    settings.domStorageEnabled =
                        true

                    settings.allowFileAccess =
                        false

                    settings.allowContentAccess =
                        false

                    settings.mixedContentMode =
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    CookieManager
                        .getInstance()
                        .setAcceptCookie(true)

                    webViewClient =
                        object : WebViewClient() {

                            private fun updateNavigationState(
                                view: WebView,
                                url: String?
                            ) {
                                if (url != null) {
                                    currentUrl =
                                        url

                                    address =
                                        url
                                }

                                canGoBack =
                                    view.canGoBack()

                                canGoForward =
                                    view.canGoForward()
                            }

                            override fun onPageFinished(
                                view: WebView,
                                url: String?
                            ) {
                                super.onPageFinished(
                                    view,
                                    url
                                )

                                updateNavigationState(
                                    view,
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

                                updateNavigationState(
                                    view,
                                    url
                                )
                            }
                        }

                    val pixivOrigins =
                        setOf(
                            "https://www.pixiv.net"
                        )

                    if (
                        WebViewFeature.isFeatureSupported(
                            WebViewFeature.JS_INJECTION_IN_FRAME_AND_WORLD
                        )
                    ) {
                        val pageWorld =
                            WebViewCompat.getExecutionWorld(
                                this,
                                JavaScriptExecutionWorld.PAGE_WORLD_NAME
                            )

                        WebViewCompat.addWebMessageListener(
                            this,
                            "MuninnBridge",
                            pixivOrigins,
                            pageWorld
                        ) {
                                _,
                                message,
                                sourceOrigin,
                                isMainFrame,
                                _ ->

                            if (
                                !isMainFrame ||
                                sourceOrigin.toString() !=
                                "https://www.pixiv.net"
                            ) {
                                return@addWebMessageListener
                            }

                            val rawMessage =
                                message.data
                                    ?: return@addWebMessageListener

                            when (
                                val result =
                                    PixivCaptureParser.parse(
                                        rawMessage
                                    )
                            ) {
                                is PixivCaptureParseResult.Success -> {

                                    val payload =
                                        result.payload

                                    Log.d(
                                        "MuninnPixivCapture",
                                        "Parsed capture: " +
                                                "sourceId=${payload.sourceId}, " +
                                                "title=${payload.title}, " +
                                                "author=${payload.authorName}, " +
                                                "mediaCount=${payload.media.size}"
                                    )

                                    val currentWebView =
                                        webView
                                            ?: return@addWebMessageListener

                                    val userAgent =
                                        currentWebView
                                            .settings
                                            .userAgentString

                                    coroutineScope.launch {

                                        val downloader =
                                            PixivMediaDownloader(
                                                context
                                            )

                                        when (
                                            val downloadResult =
                                                downloader.download(
                                                    payload =
                                                        payload,
                                                    userAgent =
                                                        userAgent
                                                )
                                        ) {
                                            is PixivMediaDownloadResult.Success -> {

                                                val draft =
                                                    try {
                                                        PixivCaptureMapper.toCaptureDraft(
                                                            payload = payload,
                                                            downloadedFiles =
                                                                downloadResult.files
                                                        )
                                                    } catch (exception: Exception) {
                                                        Log.e(
                                                            "MuninnPixivCapture",
                                                            "Could not create CaptureDraft: " +
                                                                    (
                                                                            exception.message
                                                                                ?: "unknown error"
                                                                            )
                                                        )

                                                        return@launch
                                                    }

                                                when (
                                                    val saveResult =
                                                        saveCaptureUseCase.save(
                                                            draft
                                                        )
                                                ) {
                                                    is SaveCaptureResult.Success -> {

                                                        Log.d(
                                                            "MuninnPixivCapture",
                                                            "Saved capture: " +
                                                                    "workId=${saveResult.workId}, " +
                                                                    "mediaCount=${saveResult.mediaCount}"
                                                        )
                                                    }

                                                    is SaveCaptureResult.Failure -> {

                                                        Log.e(
                                                            "MuninnPixivCapture",
                                                            "Save failed: " +
                                                                    saveResult.errors.joinToString()
                                                        )
                                                    }
                                                }

                                                downloadResult.files
                                                    .firstOrNull()
                                                    ?.parentFile
                                                    ?.deleteRecursively()
                                            }

                                            is PixivMediaDownloadResult.Failure -> {

                                                Log.e(
                                                    "MuninnPixivCapture",
                                                    "Media download failed: " +
                                                            downloadResult.error
                                                )
                                            }
                                        }
                                    }
                                }

                                is PixivCaptureParseResult.Failure -> {

                                    Log.e(
                                        "MuninnPixivCapture",
                                        "Parse failed: ${result.error}"
                                    )
                                }
                            }
                        }

                        val captureHook =
                            context.assets
                                .open(
                                    "webcapture/pixiv/capture-hook.js"
                                )
                                .bufferedReader()
                                .use {
                                    it.readText()
                                }

                        WebViewCompat.addJavaScriptOnEvent(
                            this,
                            captureHook,
                            WebViewCompat.INJECTION_EVENT_DOCUMENT_START,
                            pixivOrigins,
                            pageWorld
                        )
                    } else {
                        Log.e(
                            "MuninnPixivCapture",
                            "JS_INJECTION_IN_FRAME_AND_WORLD is unsupported"
                        )
                    }

                    loadUrl(
                        initialUrl
                    )
                }
            }
        )

        Button(
            enabled =
                currentUrl.contains(
                    "pixiv.net/artworks/"
                ),
            modifier =
                Modifier.fillMaxWidth(),
            onClick = {
                webView?.evaluateJavascript(
                    """
                    if (
                        typeof window.__muninnCapturePixiv === "function"
                    ) {
                        window.__muninnCapturePixiv();
                    }
                    """.trimIndent(),
                    null
                )
            }
        ) {
            Text("Capture")
        }
    }
}
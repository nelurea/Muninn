package io.github.nelurea.muninn.ui.browser

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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.util.Log
import androidx.webkit.JavaScriptExecutionWorld
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

@Composable
fun WebCaptureScreen(
    onBack: () -> Unit
) {
    val initialUrl = "https://www.pixiv.net/"

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

        address = normalizedUrl
        webView?.loadUrl(normalizedUrl)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onBack
            ) {
                Text("Close")
            }

            Button(
                enabled = canGoBack,
                onClick = {
                    webView?.goBack()
                }
            ) {
                Text("←")
            }

            Button(
                enabled = canGoForward,
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
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
            text = currentUrl,
            modifier = Modifier.fillMaxWidth()
        )

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->

                WebView(context).apply {

                    webView = this

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    settings.allowFileAccess = false
                    settings.allowContentAccess = false

                    settings.mixedContentMode =
                        WebSettings.MIXED_CONTENT_NEVER_ALLOW

                    CookieManager
                        .getInstance()
                        .setAcceptCookie(true)

                    webViewClient =
                        object : WebViewClient() {

                            override fun onPageFinished(
                                view: WebView,
                                url: String?
                            ) {
                                super.onPageFinished(
                                    view,
                                    url
                                )

                                if (url != null) {
                                    currentUrl = url
                                    address = url
                                }

                                canGoBack =
                                    view.canGoBack()

                                canGoForward =
                                    view.canGoForward()
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

                            Log.d(
                                "MuninnPixivCapture",
                                message.data ?: "<null>"
                            )
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

                    loadUrl(initialUrl)
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
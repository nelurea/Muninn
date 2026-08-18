package io.github.nelurea.muninn.ui.browser

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.nelurea.muninn.discovery.x.XWebSessionState

class XLoginActivity :
    ComponentActivity() {

    private lateinit var webView:
        WebView

    private lateinit var sessionState:
        XWebSessionState

    private var initialUserId:
        String? =
        null

    private var switchAccount =
        false

    private val handler =
        Handler(
            Looper.getMainLooper()
        )

    private val authenticationCheck =
        object :
            Runnable {

            override fun run() {
                val currentUserId =
                    sessionState
                        .getAuthenticatedUserId()

                val completed =
                    if (
                        switchAccount
                    ) {
                        currentUserId != null &&
                            currentUserId !=
                            initialUserId
                    } else {
                        currentUserId != null
                    }

                if (
                    completed
                ) {
                    CookieManager
                        .getInstance()
                        .flush()

                    setResult(
                        Activity.RESULT_OK
                    )

                    finish()
                    return
                }

                handler.postDelayed(
                    this,
                    AUTH_CHECK_INTERVAL_MS
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        sessionState =
            XWebSessionState()

        initialUserId =
            sessionState
                .getAuthenticatedUserId()

        switchAccount =
            intent.getBooleanExtra(
                EXTRA_SWITCH_ACCOUNT,
                false
            )

        val root =
            FrameLayout(
                this
            )

        ViewCompat
            .setOnApplyWindowInsetsListener(
                root
            ) {
                    view,
                    insets ->

                val systemBars =
                    insets.getInsets(
                        WindowInsetsCompat
                            .Type
                            .systemBars()
                    )

                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    systemBars.bottom
                )

                insets
            }

        webView =
            WebView(
                this
            ).apply {
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
                    WebViewClient()

                loadUrl(
                    if (
                        switchAccount
                    ) {
                        X_HOME_URL
                    } else {
                        X_LOGIN_URL
                    }
                )
            }

        root.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout
                    .LayoutParams
                    .MATCH_PARENT,
                FrameLayout
                    .LayoutParams
                    .MATCH_PARENT
            )
        )

        setContentView(
            root
        )

        ViewCompat
            .requestApplyInsets(
                root
            )

        onBackPressedDispatcher
            .addCallback(
                this,
                object :
                    OnBackPressedCallback(
                        true
                    ) {

                    override fun handleOnBackPressed() {
                        if (
                            webView.canGoBack()
                        ) {
                            webView.goBack()
                        } else {
                            finish()
                        }
                    }
                }
            )

        handler.post(
            authenticationCheck
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(
            authenticationCheck
        )

        CookieManager
            .getInstance()
            .flush()

        webView.stopLoading()
        webView.destroy()

        super.onDestroy()
    }

    companion object {

        private const val X_LOGIN_URL =
            "https://x.com/login"

        private const val X_HOME_URL =
            "https://x.com/home"

        private const val EXTRA_SWITCH_ACCOUNT =
            "switchAccount"

        private const val AUTH_CHECK_INTERVAL_MS =
            500L

        fun createLoginIntent(
            context: Context
        ): Intent {
            return Intent(
                context,
                XLoginActivity::class.java
            )
        }

        fun createSwitchAccountIntent(
            context: Context
        ): Intent {
            return Intent(
                context,
                XLoginActivity::class.java
            ).putExtra(
                EXTRA_SWITCH_ACCOUNT,
                true
            )
        }
    }
}

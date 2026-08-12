package io.github.nelurea.muninn.ui.browser.cosmetic

import android.net.Uri
import android.webkit.WebView
import org.json.JSONObject

object CosmeticRuleInjector {

    private const val STYLE_ELEMENT_ID =
        "muninn-cosmetic-rules"

    fun apply(
        webView: WebView,
        url: String,
        rules: List<CosmeticRule>
    ) {
        val host =
            runCatching {
                Uri.parse(url).host
            }.getOrNull()
                ?: return

        val selectors =
            rules
                .filter { rule ->
                    host == rule.host ||
                            host.endsWith(
                                ".${rule.host}"
                            )
                }
                .map {
                    it.selector.trim()
                }
                .filter {
                    it.isNotEmpty()
                }
                .distinct()

        if (selectors.isEmpty()) {
            removeInjectedStyle(
                webView
            )
            return
        }

        val css =
            selectors.joinToString(
                separator = ",\n"
            ) {
                it
            } +
                    """
                
                {
                    display: none !important;
                }
                """.trimIndent()

        val cssJson =
            JSONObject.quote(css)

        val script =
            """
            (() => {
                const styleId =
                    ${JSONObject.quote(STYLE_ELEMENT_ID)};

                let style =
                    document.getElementById(
                        styleId
                    );

                if (!style) {
                    style =
                        document.createElement(
                            "style"
                        );

                    style.id =
                        styleId;

                    (
                        document.head ||
                        document.documentElement
                    ).appendChild(
                        style
                    );
                }

                style.textContent =
                    $cssJson;
            })();
            """.trimIndent()

        webView.evaluateJavascript(
            script,
            null
        )
    }

    private fun removeInjectedStyle(
        webView: WebView
    ) {
        val script =
            """
            (() => {
                document
                    .getElementById(
                        ${JSONObject.quote(STYLE_ELEMENT_ID)}
                    )
                    ?.remove();
            })();
            """.trimIndent()

        webView.evaluateJavascript(
            script,
            null
        )
    }
}
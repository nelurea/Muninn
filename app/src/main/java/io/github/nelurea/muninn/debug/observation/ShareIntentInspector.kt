package io.github.nelurea.muninn.debug.observation

import android.content.Intent
import android.net.Uri
import android.util.Log

object ShareIntentInspector {

    private const val TAG = "ShareInspector"

    fun inspect(intent: Intent?) {

        if (intent == null) {
            Log.d(TAG, "intent=null")
            return
        }

        Log.d(TAG, "")
        Log.d(TAG, "========== SHARE INTENT ==========")

        Log.d(TAG, "action=${intent.action}")
        Log.d(TAG, "type=${intent.type}")

        val extras = intent.extras

        if (extras == null) {
            Log.d(TAG, "extras=null")
        } else {

            for (key in extras.keySet()) {

                Log.d(
                    TAG,
                    "extra[$key]=${extras.get(key)}"
                )
            }
        }

        Log.d(
            TAG,
            "EXTRA_TEXT=${
                intent.getStringExtra(
                    Intent.EXTRA_TEXT
                )
            }"
        )

        Log.d(
            TAG,
            "EXTRA_SUBJECT=${
                intent.getStringExtra(
                    Intent.EXTRA_SUBJECT
                )
            }"
        )

        val uri =
            intent.getParcelableExtra<Uri>(
                Intent.EXTRA_STREAM
            )

        Log.d(TAG, "EXTRA_STREAM=$uri")

        Log.d(TAG, "==================================")
    }
}
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
        Log.d(TAG, "flags=${intent.flags}")

        Log.d(
            TAG,
            "EXTRA_TEXT=${intent.getStringExtra(Intent.EXTRA_TEXT)}"
        )

        Log.d(
            TAG,
            "EXTRA_SUBJECT=${intent.getStringExtra(Intent.EXTRA_SUBJECT)}"
        )

        val streamUri =
            intent.getParcelableExtra<Uri>(
                Intent.EXTRA_STREAM
            )

        Log.d(TAG, "EXTRA_STREAM=$streamUri")

        Log.d(TAG, "")
        Log.d(TAG, "----- CLIP DATA -----")

        val clipData = intent.clipData

        if (clipData == null) {

            Log.d(TAG, "clipData=null")

        } else {

            Log.d(
                TAG,
                "clipData.itemCount=${clipData.itemCount}"
            )

            for (i in 0 until clipData.itemCount) {

                val item = clipData.getItemAt(i)

                Log.d(TAG, "item[$i].uri=${item.uri}")
                Log.d(TAG, "item[$i].text=${item.text}")
            }
        }

        Log.d(TAG, "")
        Log.d(TAG, "----- EXTRAS -----")

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

        Log.d(TAG, "==================================")
    }
}
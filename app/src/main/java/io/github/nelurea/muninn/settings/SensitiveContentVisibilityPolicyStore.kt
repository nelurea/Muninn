package io.github.nelurea.muninn.settings

import android.content.Context

class SensitiveContentVisibilityPolicyStore(
    context: Context
) {

    private val preferences =
        context.applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun getPolicy():
            SensitiveContentVisibilityPolicy {

        val threshold =
            preferences.getString(
                KEY_THRESHOLD,
                null
            )

        val visibility =
            preferences.getString(
                KEY_VISIBILITY_MODE,
                null
            )

        return SensitiveContentVisibilityPolicy(
            threshold =
                threshold
                    ?.let {
                        runCatching {
                            ContentRatingLevel.valueOf(
                                it
                            )
                        }.getOrNull()
                    }
                    ?: ContentRatingLevel.SENSITIVE,

            visibilityMode =
                visibility
                    ?.let {
                        runCatching {
                            SensitiveVisibilityMode.valueOf(
                                it
                            )
                        }.getOrNull()
                    }
                    ?: SensitiveVisibilityMode.BLURRED
        )
    }

    fun setPolicy(
        policy:
        SensitiveContentVisibilityPolicy
    ) {
        preferences
            .edit()
            .putString(
                KEY_THRESHOLD,
                policy.threshold.name
            )
            .putString(
                KEY_VISIBILITY_MODE,
                policy.visibilityMode.name
            )
            .apply()
    }

    private companion object {

        const val PREFERENCES_NAME =
            "muninn_sensitive_content"

        const val KEY_THRESHOLD =
            "threshold"

        const val KEY_VISIBILITY_MODE =
            "visibility_mode"
    }
}
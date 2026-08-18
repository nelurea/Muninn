package io.github.nelurea.muninn.discovery.x

import android.content.Context

class XProfileHandleStore(
    context: Context
) {

    private val preferences =
        context
            .applicationContext
            .getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

    fun get(
        userId: String
    ): String? {
        return preferences
            .getString(
                key(
                    userId
                ),
                null
            )
            ?.takeIf {
                it.isNotBlank()
            }
    }

    fun put(
        userId: String,
        handle: String
    ) {
        preferences
            .edit()
            .putString(
                key(
                    userId
                ),
                handle
            )
            .apply()
    }

    fun remove(
        userId: String
    ) {
        preferences
            .edit()
            .remove(
                key(
                    userId
                )
            )
            .apply()
    }

    private fun key(
        userId: String
    ): String {
        return "user_$userId"
    }

    private companion object {

        const val PREFERENCES_NAME =
            "x_profile_handles"
    }
}

package io.github.nelurea.muninn.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensitiveContentVisibilityController(
    context: Context
) {
    private val store =
        SensitiveContentVisibilityPolicyStore(
            context.applicationContext
        )

    private val _policy =
        MutableStateFlow(
            store.getPolicy()
        )

    val policy: StateFlow<SensitiveContentVisibilityPolicy> =
        _policy.asStateFlow()

    fun setPolicy(
        policy: SensitiveContentVisibilityPolicy
    ) {
        store.setPolicy(policy)
        _policy.value = policy
    }
}

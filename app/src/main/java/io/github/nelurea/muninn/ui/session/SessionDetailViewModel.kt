package io.github.nelurea.muninn.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nelurea.muninn.data.db.SessionWithImages
import io.github.nelurea.muninn.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionDetailViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    private val _session =
        MutableStateFlow<SessionWithImages?>(
            null
        )

    val session:
            StateFlow<SessionWithImages?> =
        _session

    fun loadSession(
        sessionId: Long
    ) {

        viewModelScope.launch {

            _session.value =
                repository.getSession(
                    sessionId
                )
        }
    }
}
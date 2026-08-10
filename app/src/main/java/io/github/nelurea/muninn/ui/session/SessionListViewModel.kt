package io.github.nelurea.muninn.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nelurea.muninn.data.db.SessionWithImages
import io.github.nelurea.muninn.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SessionListViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    private val _sessions =
        MutableStateFlow<List<SessionWithImages>>(
            emptyList()
        )

    val sessions: StateFlow<List<SessionWithImages>>
            = _sessions

    fun loadSessions() {

        viewModelScope.launch {

            _sessions.value =
                repository
                    .getSessions()
                    .filter {
                        it.images.isNotEmpty() ||
                                it.capturedWorks.isNotEmpty()
                    }
        }
    }
}
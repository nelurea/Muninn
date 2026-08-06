package io.github.nelurea.muninn.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.nelurea.muninn.data.db.ResolvedCaptureEntity
import io.github.nelurea.muninn.data.repository.ResolvedCaptureRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ResolvedCaptureViewModel(
    private val repository: ResolvedCaptureRepository
) : ViewModel() {

    private val _captures =
        MutableStateFlow<List<ResolvedCaptureEntity>>(
            emptyList()
        )

    val captures: StateFlow<List<ResolvedCaptureEntity>>
            = _captures

    init {
        load()
    }

    private fun load() {

        viewModelScope.launch {

            _captures.value =
                repository.getAll()
        }
    }
}
package io.github.nelurea.muninn.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.nelurea.muninn.data.repository.ImageRepository

class GalleryViewModelFactory(
    private val repository: ImageRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        return GalleryViewModel(
            repository
        ) as T
    }
}
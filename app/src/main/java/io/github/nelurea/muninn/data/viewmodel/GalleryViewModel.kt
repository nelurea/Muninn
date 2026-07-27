package io.github.nelurea.muninn.data.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nelurea.muninn.data.repository.ImageRepository

class GalleryViewModel(
    private val repository: ImageRepository
) : ViewModel() {

    val images = repository.getImages()
}
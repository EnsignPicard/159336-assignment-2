package nz.ac.massey.galleryapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


/**
 * A viewModel to save state for the photo view screen UI.
 */
class PhotoViewViewModel(
    private val repository: GalleryRepository,
    private val photoId: String
) : ViewModel() {

    private val _photo = MutableStateFlow<Photo?>(null)
    val photo = _photo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadPhoto()
    }

    //This function loads the photo from the repository and updates the _photo state
    private fun loadPhoto() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val allPhotos = repository.getAllPhotos()
                val targetPhoto = allPhotos.find { it.id == photoId }

                if (targetPhoto != null) {
                    val photoCopy = targetPhoto.copy()
                    _photo.value = photoCopy

                    val fullImage = repository.loadFullImage(photoCopy)
                    if (fullImage != null) {
                        photoCopy.fullImage = fullImage
                        _photo.value = photoCopy
                    }
                }
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }
}
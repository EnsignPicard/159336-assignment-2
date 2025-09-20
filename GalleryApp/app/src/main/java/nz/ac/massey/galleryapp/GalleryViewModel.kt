package nz.ac.massey.galleryapp

import android.graphics.Bitmap
import android.util.LruCache
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GalleryViewModel (private val repository: GalleryRepository) : ViewModel(){

    private val _photos = MutableStateFlow<List<Photo>>(emptyList())
    val photos = _photos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission = _hasPermission.asStateFlow()

    fun setPermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) {
            loadPhotos()
        }
    }

    fun loadPhotos() {
        if (!_hasPermission.value) return

        viewModelScope.launch {
            _isLoading.value = true

            try {
                val newPhotoList = repository.getAllPhotos()
                val existingPhotos = _photos.value

                val mergedPhotos = newPhotoList.map { newPhoto ->
                    val existingPhoto = existingPhotos.find { it.id == newPhoto.id }

                    when {
                        existingPhoto?.thumbnail != null -> {
                            existingPhoto
                        }
                        else -> {
                            val cachedBitmap = getBitmapFromMemCache(newPhoto.id)
                            if (cachedBitmap != null) {
                                newPhoto.thumbnail = cachedBitmap
                                newPhoto
                            } else {
                                newPhoto
                            }
                        }
                    }
                }

                _photos.value = mergedPhotos
            } catch (e: Exception) {
                _photos.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshPhotos() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                thumbnailCache.evictAll()

                val clearedPhotos = _photos.value.map { photo ->
                    photo.copy(thumbnail = null, fullImage = null)
                }
                _photos.value = clearedPhotos

                val photoList = repository.getAllPhotos()
                _photos.value = photoList
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val loadingPhotos = mutableSetOf<String>()

    fun loadThumbnail(photo: Photo) {
        if (photo.thumbnail != null) {
            return
        }

        getBitmapFromMemCache(photo.id)?.let { cachedBitmap ->
            updatePhotoWithThumbnail(photo.id, cachedBitmap)
            return
        }

        if (loadingPhotos.contains(photo.id)) {
            return
        }

        loadingPhotos.add(photo.id)
        viewModelScope.launch {
            try {
                val thumbnail = repository.loadThumbnail(photo)
                thumbnail?.let {
                    addBitmapToMemoryCache(photo.id, it)
                    updatePhotoWithThumbnail(photo.id, it)
                }
            } catch (e: Exception) {
            } finally {
                loadingPhotos.remove(photo.id)
            }
        }
    }

    private fun updatePhotoWithThumbnail(photoId: String, thumbnail: Bitmap) {
        val currentPhotos = _photos.value.toMutableList()
        val photoIndex = currentPhotos.indexOfFirst { it.id == photoId }
        if (photoIndex != -1) {
            val oldPhoto = currentPhotos[photoIndex]
            val newPhoto = oldPhoto.copy(thumbnail = thumbnail)
            currentPhotos[photoIndex] = newPhoto
            _photos.value = currentPhotos
        }
    }

    // Get memory class of this device, exceeding this amount will throw an
    // OutOfMemory exception.
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()

    // Use 1/8th of the available memory for this memory cache.
    private val cacheSize = maxMemory / 8

    private val thumbnailCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            // The cache size will be measured in kilobytes rather than
            // number of items.
            return bitmap.byteCount / 1024
        }
    }

    private fun addBitmapToMemoryCache(key: String, bitmap: Bitmap) {
        if (getBitmapFromMemCache(key) == null) {
            thumbnailCache.put(key, bitmap)
        }
    }

    private fun getBitmapFromMemCache(key: String): Bitmap? {
        return thumbnailCache.get(key)
    }

    override fun onCleared() {
        super.onCleared()
        thumbnailCache.evictAll()
    }
}
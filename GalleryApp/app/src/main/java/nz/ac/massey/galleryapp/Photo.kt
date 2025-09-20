package nz.ac.massey.galleryapp

import android.graphics.Bitmap

/**
 * A class to represent a photo. returned from the GalleryRepository.
 */
data class Photo (
    val id: String,
    val orientation: Int,
    val width: Int,
    val height: Int,
    var thumbnail: Bitmap? = null,
    var fullImage: Bitmap? = null

    )

package nz.ac.massey.galleryapp

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryRepository(private val contentResolver: ContentResolver)  {

    suspend fun getAllPhotos(): List<Photo> = withContext(Dispatchers.IO){
        val photos = mutableListOf<Photo>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.ORIENTATION
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor: Cursor? = contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val orientationColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION)

            while (it.moveToNext()) {
                val id = it.getString(idColumn)
                val width = it.getInt(widthColumn)
                val height = it.getInt(heightColumn)
                val orientation = it.getInt(orientationColumn)

                photos.add(Photo(id, orientation, width, height))
            }
        }
        photos
    }

    suspend fun loadThumbnail(photo: Photo): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id
            )

            // First decode with inJustDecodeBounds=true to check dimensions
            return@withContext BitmapFactory.Options().run {
                inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, this)
                }

                // Calculate inSampleSize
                inSampleSize = calculateInSampleSize(this, 100, 100)

                // Decode bitmap with inSampleSize set
                inJustDecodeBounds = false
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, this)
                    bitmap?.let { rotateIfNeeded(it, photo.orientation) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun rotateIfNeeded(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            90 -> matrix.postRotate(90f)
            180 -> matrix.postRotate(180f)
            270 -> matrix.postRotate(270f)
            else -> return bitmap
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    suspend fun loadFullImage(photo: Photo): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id
            )

            // First decode with inJustDecodeBounds=true to check dimensions
            return@withContext BitmapFactory.Options().run {
                inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, this)
                }

                // Calculate inSampleSize
                inSampleSize = calculateInSampleSize(this, 2048, 2048)

                // Decode bitmap with inSampleSize set
                inJustDecodeBounds = false
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val bitmap = BitmapFactory.decodeStream(inputStream, null, this)
                    bitmap?.let { rotateIfNeeded(it, photo.orientation) }
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
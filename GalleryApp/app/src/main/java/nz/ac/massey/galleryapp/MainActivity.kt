package nz.ac.massey.galleryapp

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import nz.ac.massey.galleryapp.ui.theme.GalleryAppTheme
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var repository: GalleryRepository
    private lateinit var viewModel: GalleryViewModel

    override fun onResume() {
        super.onResume()
        //Check both permissions depending on the API level
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.setPermissionGranted(hasPermission)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = GalleryRepository(contentResolver)
        viewModel = GalleryViewModel(repository)

        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            viewModel.setPermissionGranted(isGranted)
        }

        val alreadyHasPermission = ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED

        viewModel.setPermissionGranted(alreadyHasPermission)

        if (!alreadyHasPermission) {
            requestPermissionLauncher.launch(permission)
        }
        setContent {
            GalleryAppTheme {
                Scaffold(
                    topBar = { GalleryTopAppBar(onRefreshClick = { viewModel.refreshPhotos() }) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    GalleryScreen(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryScreen(viewModel: GalleryViewModel, modifier: Modifier = Modifier) {
    val photos by viewModel.photos.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val hasPermission by viewModel.hasPermission.collectAsState()

    var columns by remember { mutableStateOf(3) }
    var zoom by remember { mutableStateOf(1f) }

    val columnSize = when (columns) {
        1 -> 300.dp
        2 -> 180.dp
        3 -> 120.dp
        4 -> 90.dp
        else -> 60.dp
    }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            columns = 3
            zoom = 1f
        }
    }

    when {
        !hasPermission -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "permission to access your photos needed",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        isLoading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        photos.isEmpty() -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No photos found",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = columnSize),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                zoom *= event.calculateZoom()

                                if (zoom > 1.5f) {
                                    columns = (columns - 1).coerceAtLeast(1)
                                    zoom = 1f
                                }
                                if (zoom < 0.5f) {
                                    columns = (columns + 1).coerceAtMost(6)
                                    zoom = 1f
                                }
                            } while (event.changes.any { it.pressed })
                            zoom = 1f
                        }
                    }
            ) {
                items(photos.size, key = { index -> photos[index].id }) { index ->
                    PhotoThumbnailPlaceholder(
                        photo = photos[index],
                        viewModel = viewModel,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoThumbnailPlaceholder(
    photo: Photo,
    viewModel: GalleryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LaunchedEffect(photo.id) {
        viewModel.loadThumbnail(photo)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = {
                        val intent = Intent(context, PhotoViewActivity::class.java).apply {
                            putExtra("PHOTO_ID", photo.id)
                        }
                        context.startActivity(intent)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (photo.thumbnail != null) {
            Image(
                bitmap = photo.thumbnail!!.asImageBitmap(),
                contentDescription = "Photo ${photo.id}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryTopAppBar(onRefreshClick: () -> Unit) {
    TopAppBar(
        title = { Text("Gallery") },
        actions = {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh"
                )
            }
        }
    )
}
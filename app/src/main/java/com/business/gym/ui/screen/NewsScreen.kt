package com.business.gym.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.business.gym.R
import com.business.gym.ui.component.NewsMediaItem
import com.business.gym.ui.component.VideoPlayer
import com.business.gym.ui.viewmodel.NewsViewModel

@Composable
fun NewsScreen(
    isAdmin: Boolean,
    viewModel: NewsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val newsItems by viewModel.newsItems
    val isUploading by viewModel.isUploading
    val context = LocalContext.current
    
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf("") }
    var urlType by remember { mutableStateOf("image") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.uploadMedia(context, it) }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.add_by_url_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.url_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = urlType == "image", onClick = { urlType = "image" })
                        Text(stringResource(R.string.url_type_image))
                        Spacer(modifier = Modifier.width(8.dp))
                        RadioButton(selected = urlType == "video", onClick = { urlType = "video" })
                        Text(stringResource(R.string.url_type_video))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (urlInput.isNotBlank()) {
                        viewModel.addByUrl(urlInput, urlType)
                        showUrlDialog = false
                        urlInput = ""
                    }
                }) { Text(stringResource(R.string.btn_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAdmin) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isUploading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { expanded = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color.White,
                                containerColor = Color(0xFF8B0000)
                            )
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Content")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_photo_firebase)) },
                                onClick = { expanded = false; launcher.launch("image/*") },
                                leadingIcon = { Icon(Icons.Default.AddAPhoto, null) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_video_firebase)) },
                                onClick = { expanded = false; launcher.launch("video/*") },
                                leadingIcon = { Icon(Icons.Default.VideoCall, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.add_by_url_title)) },
                                onClick = { expanded = false; showUrlDialog = true },
                                leadingIcon = { Icon(Icons.Default.Link, null) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                val videoUri = "android.resource://${context.packageName}/raw/promo_video"
                VideoPlayer(
                    videoUrl = videoUri, 
                    modifier = Modifier.fillMaxWidth().height(400.dp).padding(vertical = 8.dp),
                    autoPlay = true,
                    muted = true,
                    looping = true
                )
            }
            items(newsItems) { item ->
                NewsMediaItem(item, isAdmin, onDelete = { viewModel.deleteNewsItem(item) })
            }
        }
    }
}

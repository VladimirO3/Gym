package com.business.gym.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.AboutViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.unit.sp
import com.business.gym.data.api.NewsApiService
import com.business.gym.data.local.entity.CoachEntity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun AboutScreen(
    isAdmin: Boolean,
    viewModel: AboutViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp > 600
    val contentModifier = if (isWideScreen) Modifier.width(600.dp) else Modifier.fillMaxWidth()

    val aboutTitle by viewModel.aboutTitle
    val aboutDescription by viewModel.aboutDescription
    val aboutServices by viewModel.aboutServices
    val aboutFooter by viewModel.aboutFooter
    val contactTitle by viewModel.contactTitle
    val contactPhone by viewModel.contactPhone
    val coaches by viewModel.coaches
    val isRefreshing by viewModel.isRefreshing

    var selectedCoachId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var showCoachDialog by remember { mutableStateOf(false) }
    var editingCoach by remember { mutableStateOf<CoachEntity?>(null) }

    if (selectedCoachId != null) {
        val coach = coaches.find { it.id == selectedCoachId }
        if (coach != null) {
            CoachDetailDialog(
                coach = coach,
                onDismiss = { selectedCoachId = null }
            )
        }
    }

    if (showCoachDialog) {
        CoachEditDialog(
            coach = editingCoach,
            onDismiss = { showCoachDialog = false; editingCoach = null },
            onConfirm = { name: String, desc: String, uri: Uri? ->
                android.util.Log.d("AboutScreen", "Coach dialog confirmed: $name")
                try {
                    val imagePart = if (uri != null) {
                        android.util.Log.d("AboutScreen", "Processing image URI: $uri")
                        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
                        if (bytes != null) {
                            android.util.Log.d("AboutScreen", "Image bytes size: ${bytes.size}")
                            val mediaType = context.contentResolver.getType(uri)?.toMediaTypeOrNull()
                            val requestFile = bytes.toRequestBody(mediaType)
                            MultipartBody.Part.createFormData("file", "coach_image", requestFile)
                        } else {
                            android.util.Log.e("AboutScreen", "Failed to read image bytes")
                            null
                        }
                    } else {
                        android.util.Log.d("AboutScreen", "No image selected")
                        null
                    }

                    if (editingCoach != null) {
                        android.util.Log.d("AboutScreen", "Updating existing coach: ${editingCoach!!.id}")
                        viewModel.updateCoach(editingCoach!!.id, name, desc, imagePart)
                    } else {
                        android.util.Log.d("AboutScreen", "Adding new coach")
                        viewModel.addCoach(name, desc, imagePart)
                    }
                    showCoachDialog = false
                    editingCoach = null
                } catch (e: Exception) {
                    android.util.Log.e("AboutScreen", "Failed to process coach data", e)
                    android.widget.Toast.makeText(context, "Ошибка при обработке фото", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    val defaultTitle = stringResource(R.string.about_title)
    val defaultDescription = stringResource(R.string.about_description)
    val defaultServices = stringResource(R.string.about_services)
    val defaultFooter = stringResource(R.string.about_footer)
    val defaultContactTitle = stringResource(R.string.contact_title)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAdmin) {
            // Admin Toggle Header
            Row(
                modifier = contentModifier
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showEditor) "Режим редактирования" else "Предпросмотр (как видит юзер)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                IconButton(onClick = { showEditor = !showEditor }) {
                    Icon(
                        imageVector = if (showEditor) Icons.Default.Visibility else Icons.Default.Edit,
                        contentDescription = "Toggle Editor",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            if (showEditor) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Редактирование текста", style = MaterialTheme.typography.titleMedium, color = Color.Red, modifier = contentModifier)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = aboutTitle,
                    onValueChange = { viewModel.updateAboutTitle(it) },
                    label = { Text("Заголовок", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
                    modifier = contentModifier,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = aboutDescription,
                    onValueChange = { viewModel.updateAboutDescription(it) },
                    label = { Text("Описание", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
                    modifier = contentModifier,
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = aboutServices,
                    onValueChange = { viewModel.updateAboutServices(it) },
                    label = { Text("Услуги", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
                    modifier = contentModifier,
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = aboutFooter,
                    onValueChange = { viewModel.updateAboutFooter(it) },
                    label = { Text("Футер (низ)", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
                    modifier = contentModifier,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Text("Контактные данные", style = MaterialTheme.typography.titleMedium, color = Color.Red, modifier = contentModifier)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contactTitle,
                    onValueChange = { viewModel.updateContactTitle(it) },
                    label = { Text("Описание контактов", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
                    modifier = contentModifier,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { viewModel.updateContactPhone(it) },
                    label = { Text("Номер телефона", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)) },
                    modifier = contentModifier,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = Color.Red,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = contentModifier)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // --- USER PERSPECTIVE CONTENT ---
        
        Card(
            modifier = contentModifier.padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(R.drawable.price)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Price List 1",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(16.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(R.drawable.price2)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Price List 2",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = aboutTitle.ifEmpty { defaultTitle },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = aboutDescription.ifEmpty { defaultDescription },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = aboutServices.ifEmpty { defaultServices },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = aboutFooter.ifEmpty { defaultFooter },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = contentModifier
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = contentModifier)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = contactTitle.ifEmpty { defaultContactTitle },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = contentModifier
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = contactPhone.ifEmpty { "89655109132" },
            style = MaterialTheme.typography.headlineSmall,
            color = Color.Red,
            fontWeight = FontWeight.Bold,
            modifier = contentModifier.clickable {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${contactPhone.ifEmpty { "89655109132" }}")
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(48.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = contentModifier)
        Spacer(modifier = Modifier.height(32.dp))

        // --- Coaches Section ---
        Row(
            modifier = contentModifier,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Наши тренеры",
                style = MaterialTheme.typography.titleLarge,
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.Red,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (isAdmin) {
                    IconButton(onClick = { viewModel.refreshCoaches() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Coaches",
                            tint = Color.Gray
                        )
                    }
                    IconButton(onClick = { showCoachDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Coach", tint = Color.Red)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        if (coaches.isEmpty()) {
            Text(
                text = "Информация о тренерах скоро появится...",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = contentModifier
            )
        } else {
            coaches.forEach { coach ->
                CoachCard(
                    coach = coach,
                    isAdmin = isAdmin,
                    onEdit = { editingCoach = coach; showCoachDialog = true },
                    onDelete = { viewModel.deleteCoach(coach.id) },
                    onClick = { selectedCoachId = coach.id },
                    modifier = contentModifier
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), modifier = contentModifier)
        Spacer(modifier = Modifier.height(24.dp))

        // --- Связь с автором ---
        Text(
            text = "Связаться с автором:",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Red,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Telegram
            AuthorContactIcon(
                icon = Icons.AutoMirrored.Filled.Send,
                label = "Telegram",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BBC2331"))
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.width(24.dp))
            
            // GitHub
            AuthorContactIcon(
                icon = Icons.Default.Code,
                label = "GitHub",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VladimirO3"))
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.width(24.dp))

            // Email
            AuthorContactIcon(
                icon = Icons.Default.Email,
                label = "Email",
                onClick = {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:verso0100@gmail.com")
                    }
                    context.startActivity(intent)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
fun AuthorContactIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black.copy(alpha = 0.3f),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.Red,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@Composable
fun CoachCard(
    coach: CoachEntity,
    isAdmin: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (coach.imageUrl != null) {
                    AsyncImage(
                        model = NewsApiService.getFullUrl(context, coach.imageUrl),
                        contentDescription = coach.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = coach.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = coach.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    maxLines = 3
                )
            }

            if (isAdmin) {
                Column {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Edit", tint = Color.Gray)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
@Composable
fun CoachDetailDialog(
    coach: CoachEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxWidth().height(350.dp)) {
                    if (coach.imageUrl != null) {
                        AsyncImage(
                            model = NewsApiService.getFullUrl(context, coach.imageUrl),
                            contentDescription = coach.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(100.dp))
                        }
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = coach.name,
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = coach.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        lineHeight = 24.sp
                    )
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Назад", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CoachEditDialog(
    coach: CoachEntity?,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(coach?.name ?: "") }
    var desc by remember { mutableStateOf(coach?.description ?: "") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (coach == null) "Добавить тренера" else "Редактировать тренера") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { pickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text(if (selectedUri != null) "Фото выбрано" else "Выбрать фото")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, desc, selectedUri) },
                enabled = name.isNotBlank() && desc.isNotBlank()
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}


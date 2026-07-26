package com.business.gym.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.AboutViewModel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration

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

    val defaultTitle = stringResource(R.string.about_title)
    val defaultDescription = stringResource(R.string.about_description)
    val defaultServices = stringResource(R.string.about_services)
    val defaultFooter = stringResource(R.string.about_footer)
    val defaultContactTitle = stringResource(R.string.contact_title)

    var showEditor by remember { mutableStateOf(false) }

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
                Image(
                    painter = painterResource(id = R.drawable.price),
                    contentDescription = "Price List 1",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                Spacer(modifier = Modifier.height(16.dp))
                Image(
                    painter = painterResource(id = R.drawable.price2),
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
    }
}

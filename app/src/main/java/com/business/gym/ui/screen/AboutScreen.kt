package com.business.gym.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.business.gym.R
import com.business.gym.ui.viewmodel.AboutViewModel

@Composable
fun AboutScreen(
    isAdmin: Boolean,
    viewModel: AboutViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (isAdmin) {
            Text(stringResource(R.string.auth_admin_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aboutTitle,
                onValueChange = { viewModel.updateAboutTitle(it) },
                label = { Text(stringResource(R.string.auth_title_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aboutDescription,
                onValueChange = { viewModel.updateAboutDescription(it) },
                label = { Text(stringResource(R.string.auth_desc_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aboutServices,
                onValueChange = { viewModel.updateAboutServices(it) },
                label = { Text(stringResource(R.string.auth_services_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = aboutFooter,
                onValueChange = { viewModel.updateAboutFooter(it) },
                label = { Text(stringResource(R.string.auth_footer_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(stringResource(R.string.auth_contact_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = contactTitle,
                onValueChange = { viewModel.updateContactTitle(it) },
                label = { Text(stringResource(R.string.auth_contact_desc_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { viewModel.updateContactPhone(it) },
                label = { Text(stringResource(R.string.auth_phone_label_simple)) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Text(
                text = aboutTitle.ifEmpty { defaultTitle },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = aboutDescription.ifEmpty { defaultDescription },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = aboutServices.ifEmpty { defaultServices },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = aboutFooter.ifEmpty { defaultFooter },
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = contactTitle.ifEmpty { defaultContactTitle },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = contactPhone.ifEmpty { "89655109132" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${contactPhone.ifEmpty { "89655109132" }}")
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

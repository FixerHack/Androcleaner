package app.androcleaner.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.androcleaner.R
import app.androcleaner.core.permissions.Permissions
import app.androcleaner.core.permissions.rememberPermissionStatus
import app.androcleaner.ui.components.AppCard
import app.androcleaner.ui.components.GradientButton
import app.androcleaner.ui.components.IconBadge
import app.androcleaner.ui.theme.BrandGradient
import app.androcleaner.ui.theme.BrandTeal

@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val status = rememberPermissionStatus()

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(BrandGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.onboarding_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        PermissionCard(
            icon = Icons.Rounded.FolderOpen,
            title = R.string.perm_files_title,
            description = R.string.perm_files_desc,
            granted = status.allFilesAccess,
            onGrant = { Permissions.requestAllFilesAccess(context) },
        )
        PermissionCard(
            icon = Icons.Rounded.BarChart,
            title = R.string.perm_usage_title,
            description = R.string.perm_usage_desc,
            granted = status.usageAccess,
            onGrant = { Permissions.requestUsageAccess(context) },
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.onboarding_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GradientButton(
            stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = status.allFilesAccess,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, title: Int, description: Int, granted: Boolean, onGrant: () -> Unit) {
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, if (granted) BrandTeal else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, stringResource(R.string.granted), tint = BrandTeal)
            } else {
                FilledTonalButton(onClick = onGrant) { Text(stringResource(R.string.grant)) }
            }
        }
    }
}

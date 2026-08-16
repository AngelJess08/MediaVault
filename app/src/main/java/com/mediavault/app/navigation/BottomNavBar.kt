package com.mediavault.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    NavigationBar {
        val items = listOf(
            Triple(Screen.Home, "Inicio", Icons.Filled.Home to Icons.Outlined.Home),
            Triple(Screen.Queue, "Cola", Icons.Filled.Download to Icons.Outlined.Download),
            Triple(Screen.Library, "Biblioteca", Icons.Filled.VideoLibrary to Icons.Outlined.VideoLibrary),
            Triple(Screen.Upscale, "Escalado IA", Icons.Filled.AutoAwesome to Icons.Outlined.AutoAwesome)
        )

        items.forEach { (screen, label, icons) ->
            val isSelected = currentRoute == screen.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen.route) },
                icon = {
                    AnimatedContent(targetState = isSelected, label = "icon_anim") { selected ->
                        Icon(
                            imageVector = if (selected) icons.first else icons.second,
                            contentDescription = label
                        )
                    }
                },
                label = { Text(text = label) }
            )
        }
    }
}

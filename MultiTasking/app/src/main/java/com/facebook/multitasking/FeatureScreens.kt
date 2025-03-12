package com.facebook.multitasking

// ui/screens/FeatureScreens.kt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectRecognitionScreen(navController: NavController) {
    FeatureScreenTemplate(
        title = "Object Recognition",
        navController = navController
    ) {
        // Feature-specific content will go here
        Text("Object Recognition feature coming soon!")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(navController: NavController) {
    FeatureScreenTemplate(
        title = "Document Scanner",
        navController = navController
    ) {
        // Feature-specific content will go here
        Text("Document Scanner feature coming soon!")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageProcessingScreen(navController: NavController) {
    FeatureScreenTemplate(
        title = "Image Processing",
        navController = navController
    ) {
        // Feature-specific content will go here
        Text("Image Processing feature coming soon!")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAnalysisScreen(navController: NavController) {
    FeatureScreenTemplate(
        title = "Audio Analysis",
        navController = navController
    ) {
        // Feature-specific content will go here
        Text("Audio Analysis feature coming soon!")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScreenTemplate(
    title: String,
    navController: NavController,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}
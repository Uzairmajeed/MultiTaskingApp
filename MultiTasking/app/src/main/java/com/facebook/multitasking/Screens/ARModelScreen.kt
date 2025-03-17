package com.facebook.multitasking

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.ar.core.Config
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.node.ArModelNode
import io.github.sceneview.ar.node.ArNode
import io.github.sceneview.ar.node.PlacementMode

data class ModelItem(
    val name: String,
    val fileName: String,
    val color: Color
)

@Composable
fun ARModelScreen(navController: NavController) {
    val context = LocalContext.current

    // Define the available 3D models
    val modelItems = listOf(
        ModelItem("Robot", "robot", Color(0xFF4CC9F0)),
        ModelItem("Chair", "chair", Color(0xFFF72585)),
        ModelItem("Car", "car", Color(0xFF3A0CA3))
    )

    // State for the currently selected model
    val currentModel = remember { mutableStateOf(modelItems[0].fileName) }
    val selectedModelIndex = remember { mutableStateOf(0) }
    val showARView = remember { mutableStateOf(false) }
    val placeModelButton = remember { mutableStateOf(false) }

    // Permission handling
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showARView.value = true
        } else {
            Toast.makeText(context, "Camera permission is required for AR", Toast.LENGTH_LONG).show()
        }
    }

    // Check if camera permission is already granted
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        if (showARView.value) {
            // AR View with the selected model
            ARView(
                model = currentModel.value,
                placeModelButton = placeModelButton,
                onBack = { showARView.value = false }
            )
        } else {
            // Model selection UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "3D Model Viewer",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }

                // Instruction text
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E1E1E)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Select a 3D model to view in augmented reality",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "The camera will open to detect surfaces where you can place your model",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Model selection
                Text(
                    text = "Choose a Model",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
                )

                // Model items
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(modelItems) { model ->
                        ModelCard(
                            model = model,
                            isSelected = modelItems.indexOf(model) == selectedModelIndex.value
                        ) {
                            currentModel.value = model.fileName
                            selectedModelIndex.value = modelItems.indexOf(model)
                        }
                    }
                }

                // Launch button
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED) {
                            showARView.value = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CC9F0)
                    )
                ) {
                    Text(
                        text = "Launch AR Camera",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModelCard(model: ModelItem, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(160.dp)
            .height(180.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = model.color.copy(alpha = if (isSelected) 1f else 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Placeholder for model icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = model.name.first().toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ARView(model: String, placeModelButton: MutableState<Boolean>, onBack: () -> Unit) {
    val nodes = remember { mutableListOf<ArNode>() }
    val modelNode = remember { mutableStateOf<ArModelNode?>(null) }

    Box(modifier = Modifier.fillMaxSize()
        .padding(vertical = 16.dp)

    ) {
        ARScene(
            modifier = Modifier.fillMaxSize(),
            nodes = nodes,
            planeRenderer = true,
            onCreate = { arSceneView ->
                arSceneView.lightEstimationMode = Config.LightEstimationMode.DISABLED
                arSceneView.planeRenderer.isShadowReceiver = false

                modelNode.value = ArModelNode(arSceneView.engine, PlacementMode.INSTANT).apply {
                    loadModelGlbAsync(
                        glbFileLocation = "models/${model}.glb",
                        scaleToUnits = 0.8f
                    ) {
                        // Model loaded successfully
                    }

                    onAnchorChanged = {
                        placeModelButton.value = !isAnchored
                    }

                    onHitResult = { node, hitResult ->
                        placeModelButton.value = node.isTracking
                    }
                }

                nodes.add(modelNode.value!!)
            },
            onSessionCreate = {
                planeRenderer.isVisible = true
            }
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        // Model placement button
        if (placeModelButton.value) {
            Button(
                onClick = {
                    modelNode.value?.anchor()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CC9F0)
                )
            ) {
                Text(
                    text = "Place Model",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }

    // Update model when it changes
    LaunchedEffect(key1 = model) {
        modelNode.value?.loadModelGlbAsync(
            glbFileLocation = "models/${model}.glb",
            scaleToUnits = 0.8f
        )
        Log.d("ARModelScreen", "Loading model: $model")
    }
}
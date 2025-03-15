package com.facebook.multitasking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.facebook.multitasking.ui.theme.AppTheme
import com.facebook.multitasking.Navigation.*
import com.facebook.multitasking.Screens.DigitalInkRecognitionScreen
import com.facebook.multitasking.Screens.DocumentScannerScreen
import com.facebook.multitasking.Screens.FaceMeshDetectionScreen
import com.facebook.multitasking.Screens.FaceRecognitionScreen
import com.facebook.multitasking.Screens.ImageProcessingScreen
import com.facebook.multitasking.Screens.PoseDetectionScreen
import com.facebook.multitasking.Screens.SelfieSegmentationScreen
import com.facebook.multitasking.Screens.TextRecognitionScreen
import com.facebook.multitasking.ui.theme.primaryDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        // Use the route() extension function to get the string route
                        startDestination = HomeScreen.route()
                    ) {
                        composable<HomeScreen> {
                            HomeScreen(navController = navController)
                        }

                        composable<TextRecognitionScreen> {
                            TextRecognitionScreen(navController = navController)
                        }

                        composable<FaceRecognitionScreen> {
                            FaceRecognitionScreen(navController = navController)
                        }

                        composable<FaceMeshDetectionScreen> {
                            FaceMeshDetectionScreen(navController = navController)
                        }

                        composable<PoseDetectionScreen> {
                            PoseDetectionScreen(navController = navController)
                        }

                        composable<SelfieSegmentationScreen> {
                            SelfieSegmentationScreen(navController = navController)
                        }

                        composable<DocumentScannerScreen> {
                            DocumentScannerScreen(navController = navController)
                        }

                        composable<ImageProcessingScreen> {
                            ImageProcessingScreen(navController = navController)
                        }

                        composable<DigitalInkRecognitionScreen> {
                            DigitalInkRecognitionScreen(navController = navController)
                        }



                    }
                }
            }
        }
    }
}
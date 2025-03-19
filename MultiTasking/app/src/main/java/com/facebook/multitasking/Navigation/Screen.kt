package com.facebook.multitasking.Navigation

// Navigation/Screen.kt


import kotlinx.serialization.Serializable

sealed class Screen

@Serializable
object SplashScreen : Screen()

@Serializable
object HomeScreen : Screen()

@Serializable
object TextRecognitionScreen : Screen()

@Serializable
object FaceRecognitionScreen : Screen()

@Serializable
object FaceMeshDetectionScreen : Screen()

@Serializable
object PoseDetectionScreen : Screen()

@Serializable
object SelfieSegmentationScreen : Screen()

@Serializable
object DocumentScannerScreen : Screen()

@Serializable
object ImageProcessingScreen : Screen()

@Serializable
object DigitalInkRecognitionScreen : Screen()

@Serializable
object  ARModelScreen : Screen()





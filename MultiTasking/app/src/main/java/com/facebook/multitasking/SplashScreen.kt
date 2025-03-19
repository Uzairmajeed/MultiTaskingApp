package com.facebook.multitasking

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.facebook.multitasking.Navigation.HomeScreen
import com.facebook.multitasking.Navigation.route
import com.facebook.multitasking.ui.theme.onTertiaryContainerLight

import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(onTertiaryContainerLight),
        contentAlignment = Alignment.Center
    ) {
        // Your app logo
        Image(
            painter = painterResource(id = R.drawable.multitasking),
            contentDescription = "App Logo",
            modifier = Modifier.size(200.dp)
        )


        // Navigate to HomeScreen after 4 seconds
        LaunchedEffect(key1 = true) {
            delay(4000) // 4 seconds delay
            navController.navigate(HomeScreen.route())
        }
    }
}
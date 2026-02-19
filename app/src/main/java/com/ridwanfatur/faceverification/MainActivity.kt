package com.ridwanfatur.faceverification

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.ridwanfatur.faceverification.ui.theme.FaceVerificationTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ridwanfatur.faceverification.pages.add_face.AddFacePage
import com.ridwanfatur.faceverification.pages.home.HomePage
import com.ridwanfatur.faceverification.pages.verify_face.VerifyFacePage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FaceVerificationTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = RouteConstants.HOME_PAGE) {
                    composable(RouteConstants.HOME_PAGE) { HomePage(navController) }
                    composable(RouteConstants.ADD_FACE_PAGE) { AddFacePage(navController) }
                    composable(RouteConstants.VERIFY_FACE_PAGE) { VerifyFacePage(navController) }
                }
            }
        }
    }
}

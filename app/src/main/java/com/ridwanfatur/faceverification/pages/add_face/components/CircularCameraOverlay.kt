package com.ridwanfatur.faceverification.pages.add_face.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType

@Composable
fun CircularCameraOverlay() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val radius = minOf(canvasWidth, canvasHeight) / 2.5f

        val centerX = canvasWidth / 2
        val centerY = canvasHeight / 2

        val path = Path().apply {
            addRect(Rect(0f, 0f, canvasWidth, canvasHeight))
            addOval(
                Rect(
                    centerX - radius,
                    centerY - radius,
                    centerX + radius,
                    centerY + radius
                )
            )
            fillType = PathFillType.EvenOdd
        }

        drawPath(
            path = path,
            color = Color.White
        )
    }
}
package com.koisv.kcdesktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.io.files.FileNotFoundException
import org.jetbrains.skia.Image

object Tools {
    @Composable
    internal fun painterResource(
        resourcePath: String
    ): Painter = when (resourcePath.substringAfterLast(".")) {
        else -> rememberBitmapResource(resourcePath)
    }

    @Composable
    internal fun rememberBitmapResource(path: String): Painter {
        return remember(path) { BitmapPainter(Image.makeFromEncoded(readResourceBytes(path)).toComposeImageBitmap()) }
    }

    private object ResourceLoader
    private fun readResourceBytes(resourcePath: String) =
        ResourceLoader.javaClass.classLoader.getResourceAsStream(resourcePath)?.readAllBytes()
            ?: throw FileNotFoundException()
}
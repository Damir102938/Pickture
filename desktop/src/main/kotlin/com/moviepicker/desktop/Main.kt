package com.moviepicker.desktop

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.moviepicker.desktop.ui.PicktureApp

fun main() {
    val container = DesktopContainer()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Pickture",
            icon = painterResource("pickture.png"),
            state = rememberWindowState(size = DpSize(1320.dp, 860.dp)),
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            PicktureApp(container)
        }
    }
}

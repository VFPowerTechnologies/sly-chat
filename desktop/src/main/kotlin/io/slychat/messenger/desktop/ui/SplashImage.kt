package io.slychat.messenger.desktop.ui

import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle

class SplashImage(image: Image) : StackPane() {
    init {
        val background = Rectangle()
        background.fill = Color.BLACK
        background.heightProperty().bind(heightProperty())
        background.widthProperty().bind(widthProperty())
        children.add(background)

        val imageView = ImageView(image)
        imageView.isPreserveRatio = true
        imageView.maxHeight(image.height)
        imageView.maxWidth(image.height)
        imageView.fitHeightProperty().bind(heightProperty().divide(2))
        imageView.fitWidthProperty().bind(heightProperty().divide(2))
        children.add(imageView)
    }
}
package io.slychat.messenger.desktop

import io.slychat.messenger.services.files.cache.ThumbnailGenerator
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.Image
import java.awt.Transparency
import java.awt.image.BufferedImage
import java.io.InputStream
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

class DesktopThumbnailGenerator : ThumbnailGenerator {
    private fun writeJPEG(bufferedImage: BufferedImage, outputStream: OutputStream) {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val params = writer.defaultWriteParam
        params.compressionMode = ImageWriteParam.MODE_EXPLICIT
        params.compressionQuality = 1.0f

        writer.output = ImageIO.createImageOutputStream(outputStream)
        writer.write(null, IIOImage(bufferedImage, null, null), params)
    }

    private fun writePNG(bufferedImage: BufferedImage, outputStream: OutputStream) {
        val writer = ImageIO.getImageWritersByFormatName("png").next()

        writer.output = ImageIO.createImageOutputStream(outputStream)
        writer.write(null, IIOImage(bufferedImage, null, null), null)
    }

    private fun resizeImage(inputStream: InputStream, outputStream: OutputStream, resolution: Double) {
        val image = Image(inputStream, resolution, resolution, true, true)
        if (image.isError)
            throw image.exception

        val bufferedImage = SwingFXUtils.fromFXImage(image, null)
        val isTransparent = bufferedImage.transparency != Transparency.OPAQUE

        if (!isTransparent)
            writeJPEG(bufferedImage, outputStream)
        else
            writePNG(bufferedImage, outputStream)
    }

    override fun generateThumbnail(originalInputStream: InputStream, thumbnailOutputStream: OutputStream, thumbnailResolution: Int) {
        resizeImage(originalInputStream, thumbnailOutputStream, thumbnailResolution.toDouble())
    }
}


package app.fugitive.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Renders [content] as a crisp QR code. */
@Composable
fun QrCode(content: String, modifier: Modifier = Modifier, dark: Int = 0xFF0B0F15.toInt(), light: Int = 0xFFFFFFFF.toInt()) {
    val bitmap = remember(content, dark, light) { qrBitmap(content, dark, light) }
    Image(bitmap, contentDescription = "QR code for $content", modifier = modifier, filterQuality = FilterQuality.None)
}

fun qrBitmap(content: String, dark: Int, light: Int): ImageBitmap {
    val matrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
    )
    val w = matrix.width; val h = matrix.height
    val pixels = IntArray(w * h) { i -> if (matrix.get(i % w, i / w)) dark else light }
    val bmp = createBitmap(w, h)
    bmp.setPixels(pixels, 0, w, 0, 0, w, h)
    return bmp.asImageBitmap()
}

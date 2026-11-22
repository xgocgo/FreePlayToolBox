package com.freeplay.toolbox.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 极简远程图片：直接解码 HTTP 流，不引入第三方图片库。
 * 加载失败静默不显示，避免公告卡片出现破图方块。
 */
@Composable
fun RemoteImage(url: String, modifier: Modifier = Modifier) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        // data URI（编辑器内嵌图片）直接解码，不走网络
        if (url.startsWith("data:", ignoreCase = true)) {
            bmp = withContext(Dispatchers.IO) {
                com.freeplay.toolbox.data.ImageEmbed.decodeDataUri(url)?.asImageBitmap()
            }
            return@LaunchedEffect
        }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (java.net.URL(url.trim()).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "FreePlayToolBox")
                }
                conn.inputStream.use { ins ->
                    android.graphics.BitmapFactory.decodeStream(ins)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    bmp?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

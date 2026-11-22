package com.freeplay.toolbox.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.freeplay.toolbox.ui.components.NeonCard
import com.freeplay.toolbox.ui.components.SectionTitle
import com.freeplay.toolbox.ui.components.SmallPill
import com.freeplay.toolbox.ui.theme.NeonCyan
import com.freeplay.toolbox.ui.theme.NeonGreen
import com.freeplay.toolbox.ui.theme.NeonRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DRAFT_PREF = "announce_draft"

/** 不可变草稿：任何修改都整条替换，保证 Compose 能观察到变化 */
data class DraftItem(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val level: String = "info",
    val image: String = "",
)

/**
 * 公告编辑器：纯本地生成 JSON，App 内不含任何 GitHub 写权限。
 */
@Composable
fun AnnouncementEditorScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<DraftItem>() }
    var toast by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var dirty by remember { mutableIntStateOf(0) }
    var pickTarget by remember { mutableIntStateOf(-1) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun update(index: Int, block: (DraftItem) -> DraftItem) {
        if (index !in items.indices) return
        items[index] = block(items[index])
        dirty++
    }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        val idx = pickTarget
        if (uri != null && idx in items.indices) {
            scope.launch {
                toast = "正在压缩图片…"
                val data = withContext(Dispatchers.IO) {
                    com.freeplay.toolbox.data.ImageEmbed.toDataUri(context, uri)
                }
                if (data == null) {
                    toast = "图片处理失败"
                } else {
                    update(idx) { it.copy(image = data) }
                    toast = "图片已内嵌（%.0f KB）".format(data.length / 1024.0)
                }
            }
        }
    }

    BackHandler { onBack() }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toast = null
        }
    }

    // 载入本地草稿
    LaunchedEffect(Unit) {
        if (items.isEmpty()) {
            val raw = context.getSharedPreferences("toolbox_prefs", Context.MODE_PRIVATE)
                .getString(DRAFT_PREF, null)
            if (raw != null) {
                runCatching {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        items.add(
                            DraftItem(
                                id = o.optString("id"),
                                title = o.optString("title"),
                                body = o.optString("body"),
                                level = o.optString("level", "info"),
                                image = o.optString("image", ""),
                            )
                        )
                    }
                }
            }
            if (items.isEmpty()) items.add(newDraft(0))
        }
    }

    fun saveDraft() {
        val arr = JSONArray()
        items.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id)
                put("title", d.title)
                put("body", d.body)
                put("level", d.level)
                if (d.image.isNotBlank()) put("image", d.image)
            })
        }
        context.getSharedPreferences("toolbox_prefs", Context.MODE_PRIVATE)
            .edit().putString(DRAFT_PREF, arr.toString()).apply()
    }

    // 输入停顿 700ms 再落盘，避免每敲一个字都写文件造成卡顿
    LaunchedEffect(dirty) {
        if (dirty > 0) {
            delay(700)
            saveDraft()
        }
    }

    fun buildJson(): String {
        val root = JSONObject()
        val arr = JSONArray()
        items.filter { it.title.isNotBlank() || it.body.isNotBlank() }.forEach { d ->
            arr.put(JSONObject().apply {
                put("id", d.id.ifBlank { newDraft(0).id })
                put("title", d.title.ifBlank { "公告" })
                put("body", d.body)
                put("level", d.level)
                if (d.image.isNotBlank()) put("image", d.image.trim())
            })
        }
        root.put("items", arr)
        return root.toString(2)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { saveDraft(); onBack() }) { Text("← 返回") }
            Text("公告编辑器", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showPreview = !showPreview }) {
                Text(if (showPreview) "回到编辑" else "预览 JSON", fontSize = 12.sp)
            }
        }

        if (!showPreview) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "写好后复制 JSON，粘到仓库的 announcements.json 提交。App 不保存任何 GitHub 凭据。",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                itemsIndexed(items) { index, draft ->
                    NeonCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 头部：序号 + 等级 + 删除
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("第 ${index + 1} 条", fontSize = 13.sp, color = NeonCyan)
                                Spacer(Modifier.weight(1f))
                                SmallPill(
                                    when (draft.level) {
                                        "important" -> "重要"
                                        "warn" -> "提醒"
                                        else -> "普通"
                                    },
                                    true,
                                ) {
                                    update(index) {
                                        it.copy(
                                            level = when (it.level) {
                                                "info" -> "warn"
                                                "warn" -> "important"
                                                else -> "info"
                                            }
                                        )
                                    }
                                }
                                if (items.size > 1) {
                                    Text(
                                        "删除",
                                        fontSize = 11.sp,
                                        color = NeonRed,
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clickable {
                                                items.removeAt(index)
                                                dirty++
                                            },
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = draft.title,
                                onValueChange = { v -> update(index) { it.copy(title = v) } },
                                label = { Text("标题") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            OutlinedTextField(
                                value = draft.body,
                                onValueChange = { v -> update(index) { it.copy(body = v) } },
                                label = { Text("正文") },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // 图片
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(onClick = {
                                    pickTarget = index
                                    picker.launch("image/*")
                                }) { Text("从相册选图", fontSize = 12.sp) }
                                if (draft.image.isNotBlank()) {
                                    Text(
                                        "移除图片",
                                        fontSize = 11.sp,
                                        color = NeonRed,
                                        modifier = Modifier.clickable {
                                            update(index) { it.copy(image = "") }
                                        },
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (draft.image.startsWith("data:")) "内嵌图片" else
                                        if (draft.image.isBlank()) "无图" else "外链图片",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (draft.image.isBlank()) {
                                Text(
                                    "选图后会自动压缩并内嵌进 JSON，别人也能看到，不需要图床",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (draft.image.isNotBlank()) {
                                RemoteThumb(draft.image)
                                Row {
                                    Text(
                                        "预览如上；若空白说明链接不可达（很多图床防盗链，建议用 GitHub 仓库内的图片直链）",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // ID
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ID", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                OutlinedTextField(
                                    value = draft.id,
                                    onValueChange = { v -> update(index) { it.copy(id = v) } },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    update(index) { it.copy(id = newDraft(index).id) }
                                    toast = "已换新 ID（用户会重新看到这条）"
                                }) { Text("刷新", fontSize = 11.sp) }
                            }
                            Text(
                                "ID 唯一才有效：同 ID 用户只看一次，改 ID 才会重新弹出",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            items.add(newDraft(items.size))
                            dirty++
                        },
                    ) { Text("+ 添加一条") }
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            copyToClipboard(context, buildJson())
                            saveDraft()
                            toast = "JSON 已复制，去 GitHub 粘贴提交"
                        },
                    ) { Text("复制 JSON") }
                }

                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(
                                            com.freeplay.toolbox.core.RemoteConfig.editUrl(context)
                                        ),
                                    )
                                )
                            }
                        },
                    ) { Text("打开编辑页并粘贴") }
                }

                item {
                    Text(
                        "流程：写好 → 复制 JSON → 打开编辑页 → 全选替换 → Commit → 重启 App 生效",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            var previewKey by remember { mutableIntStateOf(0) }
            val json = remember(previewKey) { buildJson() }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionTitle("生成的 JSON")
                Text(
                    json,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = NeonGreen,
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        copyToClipboard(context, json)
                        toast = "已复制"
                    },
                ) { Text("复制 JSON") }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { previewKey++ },
                ) { Text("刷新预览") }
            }
        }
    }
}

/** 远程缩略图预览（无第三方图片库，直接解码流） */
@Composable
private fun RemoteThumb(url: String) {
    var bmp by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url) {
        bmp = null
        failed = false
        if (url.startsWith("data:", ignoreCase = true)) {
            bmp = withContext(Dispatchers.IO) {
                com.freeplay.toolbox.data.ImageEmbed.decodeDataUri(url)?.asImageBitmap()
            }
            failed = bmp == null
            return@LaunchedEffect
        }
        bmp = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (java.net.URL(url.trim()).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "FreePlayToolBox")
                }
                conn.inputStream.use { ins ->
                    android.graphics.BitmapFactory.decodeStream(ins)?.asImageBitmap()
                }
            }.getOrNull()
        }
        failed = bmp == null
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
        )
    } else if (failed) {
        Text("图片加载失败", fontSize = 10.sp, color = NeonRed)
    } else {
        Text("图片加载中…", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun newDraft(index: Int): DraftItem {
    val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
    return DraftItem(id = "ann-$ts-$index")
}

private fun copyToClipboard(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("announcements", text))
    }
}

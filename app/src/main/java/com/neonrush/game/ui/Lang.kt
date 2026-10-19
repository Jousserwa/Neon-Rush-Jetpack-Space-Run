package com.neonrush.game.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** In-game language. Every Text() in the ui package goes through Lang.tr(). English is the source text. */
object Lang {
    val options = listOf(
        "en" to "English", "es" to "Español", "fr" to "Français", "pt" to "Português",
        "de" to "Deutsch", "ar" to "العربية", "hi" to "हिन्दी", "zh" to "中文", "sw" to "Kiswahili",
        "ru" to "Русский", "id" to "Indonesia", "tr" to "Türkçe", "ja" to "日本語"
    )

    var code by mutableStateOf("en")
        private set
    private var table: HashMap<String, String>? = null
    private val numRe = Regex("\\d+")

    fun init(ctx: Context) {
        val saved = try { ctx.getSharedPreferences("neonrush_lang", Context.MODE_PRIVATE).getString("code", null) } catch (_: Exception) { null }
        val dev = Locale.getDefault().language.let { if (it == "iw") "he" else it }
        val pick = saved ?: if (options.any { it.first == dev }) dev else "en"
        apply(pick)
    }

    fun set(ctx: Context, c: String) {
        try { ctx.getSharedPreferences("neonrush_lang", Context.MODE_PRIVATE).edit().putString("code", c).apply() } catch (_: Exception) {}
        apply(c)
    }

    private fun apply(c: String) {
        val vals: Array<String>? = when (c) {
            "es" -> LANG_ES; "fr" -> LANG_FR; "pt" -> LANG_PT; "de" -> LANG_DE
            "ar" -> LANG_AR; "hi" -> LANG_HI; "zh" -> LANG_ZH; "sw" -> LANG_SW
            "ru" -> LANG_RU; "id" -> LANG_ID; "tr" -> LANG_TR; "ja" -> LANG_JA
            else -> null
        }
        if (vals == null) { table = null; code = "en"; return }
        val m = HashMap<String, String>(LANG_KEYS.size * 2)
        for (i in LANG_KEYS.indices) if (i < vals.size) m[LANG_KEYS[i]] = vals[i]
        table = m
        code = c
    }

    fun tr(s: String): String {
        if (code == "en") return s
        val t = table ?: return s
        t[s]?.let { return it }
        if (s.isEmpty() || s.none { it.isDigit() }) return s
        val nums = ArrayList<String>()
        val key = numRe.replace(s) { nums.add(it.value); "#" }
        val v = t[key] ?: return s
        var i = 0
        return buildString { for (ch in v) if (ch == '#' && i < nums.size) append(nums[i++]) else append(ch) }
    }
}

@Composable
fun LanguagePicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val cur = Lang.code
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text("Language", color = Color.White, fontFamily = FontFamily.Monospace)
        Lang.options.chunked(3).forEach { rowOpts ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowOpts.forEach { (c, name) ->
                    val on = c == cur
                    Surface(
                        modifier = Modifier.weight(1f).clickable { Lang.set(ctx, c) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (on) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, if (on) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            name, color = Color.White, fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center, maxLines = 1,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                    }
                }
                repeat(3 - rowOpts.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

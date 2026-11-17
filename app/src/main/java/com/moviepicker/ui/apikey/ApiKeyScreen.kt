package com.moviepicker.ui.apikey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import com.moviepicker.ui.design.BackButton
import com.moviepicker.ui.design.Brand
import com.moviepicker.ui.design.MarqueeButton
import com.moviepicker.ui.design.Perforation
import com.moviepicker.ui.design.PkType
import com.moviepicker.ui.design.ScreenHeader
import com.moviepicker.ui.design.TextAction
import com.moviepicker.ui.design.TicketShape
import com.moviepicker.ui.design.ticketFieldColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.moviepicker.container
import com.moviepicker.data.remote.KeyCheck
import com.moviepicker.data.remote.TmdbLinks
import kotlinx.coroutines.launch

/**
 * Ввод ключа TMDB. Показывается при первом запуске (если ключа нет)
 * и из настроек — чтобы заменить ключ.
 */
@Composable
fun ApiKeyScreen(onSaved: () -> Unit, onBack: (() -> Unit)? = null) {
    val container = LocalContext.current.container
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offerSaveAnyway by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        key = container.settings.current().tmdbApiKey
    }

    fun save(value: String) = scope.launch {
        container.settings.setTmdbApiKey(value)
        container.recommendations.invalidate()
        onSaved()
    }

    fun check() {
        val value = key.trim()
        if (value.isEmpty()) {
            error = "Вставьте ключ"
            return
        }
        checking = true
        error = null
        offerSaveAnyway = false
        scope.launch {
            when (val result = container.keyValidator.check(value)) {
                KeyCheck.Valid -> save(value)
                KeyCheck.Invalid -> error = "TMDB не принял этот ключ. Проверьте, что скопировали его целиком."
                is KeyCheck.Failed -> {
                    error = result.message
                    offerSaveAnyway = true
                }
            }
            checking = false
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        if (onBack != null) {
            BackButton(onBack, Modifier.padding(start = 12.dp, top = 10.dp))
        } else {
            Spacer(Modifier.height(16.dp))
        }
        ScreenHeader(
            "Ключ TMDB",
            subtitle = "Pickture берёт данные о фильмах из TMDB — крупной открытой базы кино. " +
                "Нужен личный ключ: он бесплатный, а получить его можно за пару минут.",
        )
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(TicketShape(notchRadius = 7.dp, cornerRadius = 10.dp))
                .background(Brand.Ticket)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Step(1, "Зарегистрируйтесь на TMDB, если ещё нет аккаунта") {
                LinkButton("Регистрация на themoviedb.org") { uriHandler.openUri(TmdbLinks.SIGN_UP) }
            }
            Perforation(inset = 0.dp)
            Step(2, "Откройте страницу API и создайте ключ (бесплатный план Developer)") {
                LinkButton("Открыть страницу ключей TMDB") { uriHandler.openUri(TmdbLinks.API_SETTINGS) }
            }
            Perforation(inset = 0.dp)
            Step(3, "Скопируйте «API Key» или «API Read Access Token» и вставьте ниже")
        }

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = key,
            onValueChange = {
                key = it
                error = null
                offerSaveAnyway = false
            },
            label = { Text("Ключ TMDB") },
            placeholder = { Text("Например, 1a2b3c…") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = {
                IconButton(onClick = { clipboard.getText()?.text?.let { key = it.trim() } }) {
                    Icon(Icons.Filled.ContentPaste, "Вставить из буфера")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { check() }),
            shape = TicketShape(notchRadius = 6.dp, cornerRadius = 8.dp),
            colors = ticketFieldColors(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            MarqueeButton("Проверить и сохранить", ::check, loading = checking)
            if (offerSaveAnyway) {
                Spacer(Modifier.width(4.dp))
                TextAction("Сохранить без проверки", { save(key.trim()) })
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Ключ хранится только на этом устройстве и отправляется только в TMDB.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Step(number: Int, text: String, action: (@Composable () -> Unit)? = null) {
    Row {
        Text(
            "%02d".format(number),
            style = PkType.number.copy(fontSize = 15.sp),
            color = Brand.Gold,
            modifier = Modifier.width(28.dp),
        )
        Column {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

@Composable
private fun LinkButton(text: String, onClick: () -> Unit) {
    TextAction(text, onClick, modifier = Modifier.offset(x = (-10).dp), trailingIcon = Icons.AutoMirrored.Filled.OpenInNew)
}

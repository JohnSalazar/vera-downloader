package com.vera.downloader.ui

import androidx.compose.ui.res.painterResource
import com.vera.downloader.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vera.downloader.BuildConfig
import com.vera.downloader.MainViewModel
import com.vera.downloader.Screen
import com.vera.downloader.UiState
import com.vera.downloader.core.MediaItem
import com.vera.downloader.core.MediaKind
import com.vera.downloader.core.LinkDetector
import com.vera.downloader.core.ParseResult
import com.vera.downloader.i18n.I18n
import com.vera.downloader.i18n.LocalLang
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.vera.downloader.core.Platform
import com.vera.downloader.data.DownloadRecord
import com.vera.downloader.data.Status
import com.vera.downloader.ui.theme.VeraCyan
import com.vera.downloader.ui.theme.VeraPink
import com.vera.downloader.ui.theme.VeraPurple
import kotlinx.coroutines.launch

@Composable
fun VeraRoot(vm: MainViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val language = I18n.resolve(state.settings.language)

    LaunchedEffect(state.toast) {
        val toast = state.toast
        if (toast != null) {
            snackbar.showSnackbar(I18n.translate(language, toast))
            vm.clearToast()
        }
    }

    BackHandler(enabled = state.screen != Screen.Home) { vm.back() }

    CompositionLocalProvider(
        LocalLang provides language,
        LocalLayoutDirection provides if (I18n.isRtl(language)) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                DrawerContent(state, vm) { scope.launch { drawerState.close() } }
            }
        ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                VeraTopBar(
                    screen = state.screen,
                    vm = vm,
                    onMenu = { scope.launch { drawerState.open() } }
                )
            },
            bottomBar = {
                if (state.screen == Screen.Home || state.screen == Screen.History) {
                    VeraBottomBar(
                        screen = state.screen,
                        running = state.history.count { it.isRunning },
                        vm = vm
                    )
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (state.screen) {
                    Screen.Home -> HomeScreen(state, vm)
                    Screen.Preview -> PreviewScreen(state, vm)
                    Screen.History -> HistoryScreen(state, vm)
                    Screen.Settings -> SettingsScreen(state, vm)
                    Screen.Vip -> VipScreen(state, vm)
                    Screen.HowTo -> HowToScreen(vm)
                    Screen.Feedback -> FeedbackScreen(vm)
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VeraTopBar(screen: Screen, vm: MainViewModel, onMenu: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = when (screen) {
                    Screen.Home -> "Vera Downloader"
                    Screen.Preview -> "Prévia"
                    Screen.History -> "Downloads"
                    Screen.Settings -> "Ajustes"
                    Screen.Vip -> "Vera VIP"
                    Screen.HowTo -> "Como baixar"
                    Screen.Feedback -> "Enviar feedback"
                },
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            if (screen == Screen.Home) {
                IconButton(onClick = onMenu) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu")
                }
            } else {
                IconButton(onClick = { vm.back() }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = {
            if (screen != Screen.Settings) {
                IconButton(onClick = { vm.go(Screen.Settings) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes")
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

@Composable
private fun VeraBottomBar(screen: Screen, running: Int, vm: MainViewModel) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = screen == Screen.Home,
            onClick = { vm.go(Screen.Home) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text("Início") }
        )
        NavigationBarItem(
            selected = screen == Screen.History,
            onClick = { vm.go(Screen.History) },
            icon = {
                BadgedBox(
                    badge = {
                        if (running > 0) {
                            Badge { Text(running.toString()) }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                }
            },
            label = { Text("Histórico") }
        )
    }
}

@Composable
private fun DrawerContent(state: UiState, vm: MainViewModel, onClose: () -> Unit) {
    ModalDrawerSheet {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                "Vera",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Downloader",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(16.dp))
            DrawerItem(Icons.Filled.Star, "Como baixar") {
                onClose()
                vm.go(Screen.HowTo)
            }
            DrawerItem(Icons.Filled.HelpOutline, "Enviar feedback") {
                onClose()
                vm.go(Screen.Feedback)
            }
            DrawerItem(Icons.Filled.PersonAdd, "Recomende aos amigos") {
                onClose()
                vm.shareApp()
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "V${BuildConfig.VERSION_NAME} (debug)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun VipDrawerCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            Modifier
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF5B5BF0), VeraPurple))
                )
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    null,
                    tint = Color(0xFFFFD54F)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Remover todos os anúncios",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "A partir de BRL 9,58/mês",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, null) },
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
private fun HowToScreen(vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Como baixar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Text(
                "Funciona com TikTok, YouTube, Instagram e Facebook. No Instagram, posts que exigem login mostram apenas a prévia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item { StepCard("1", "Abra o app do vídeo e toque em Compartilhar.") }
        item { StepCard("2", "Toque em Copiar link e volte ao Vera.") }
        item { StepCard("3", "Cole, toque em Analisar e escolha o formato.") }
        item {
            Button(
                onClick = { vm.openTikTok() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.MusicNote, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Abrir TikTok e copiar link")
            }
        }
        item {
            OutlinedButton(
                onClick = { vm.go(Screen.Home) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Voltar ao início")
            }
        }
    }
}

@Composable
private fun StepCard(number: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HomeScreen(state: UiState, vm: MainViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "Baixe em 1 toque",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Baixe vídeos do TikTok, YouTube, Instagram e Facebook em segundos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            PasteCard(state, vm)
        }
        if (state.loading) {
            item { LoadingCard() }
        }
        state.error?.let { message ->
            item { ErrorCard(message, vm) }
        }
        item { PlatformsRow() }
        val errorCount = state.history.count { it.status == Status.ERROR }
        if (errorCount > 0) {
            item { ErrorBanner(errorCount) { vm.go(Screen.History) } }
        }
        if (state.history.isNotEmpty()) {
            item { FrequentAuthors(state, vm) }
        }
        if (state.history.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recentes",
                    action = "Ver tudo",
                    onAction = { vm.go(Screen.History) }
                )
            }
            items(state.history.take(3), key = { it.id }) { record ->
                RecentRow(record, vm)
            }
        } else {
            item { HowItWorks() }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun PasteCard(state: UiState, vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var field by remember {
        mutableStateOf(TextFieldValue(state.input, TextRange(state.input.length)))
    }
    LaunchedEffect(state.input) {
        if (field.text != state.input) {
            field = TextFieldValue(state.input, TextRange(state.input.length))
        }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = {
                    field = it
                    vm.onInput(it.text)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cole um ou vários links…") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                trailingIcon = {
                    if (field.text.isNotEmpty()) {
                        IconButton(onClick = {
                            field = TextFieldValue("", TextRange(0))
                            vm.onInput("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Limpar campo")
                        }
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val raw = clipboard.getText()?.text?.trim().orEmpty()
                        if (raw.isNotEmpty()) {
                            val urls = LinkDetector.extractAll(raw)
                            val pasted = if (urls.size == 1) urls.first() else raw
                            // Limpa em um frame e só depois cola o novo link.
                            scope.launch {
                                field = TextFieldValue("", TextRange(0))
                                vm.onInput("")
                                withFrameNanos { }
                                field = TextFieldValue(pasted, TextRange(pasted.length))
                                vm.paste(pasted)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ContentPaste, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Colar")
                }
                Button(
                    onClick = { vm.parse() },
                    enabled = state.input.isNotBlank() && !state.loading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Analisar")
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Analisando o link…")
        }
    }
}

@Composable
private fun ErrorCard(message: String, vm: MainViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("Não foi possível baixar", fontWeight = FontWeight.Bold)
            }
            Text(message, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.parse() }) { Text("Tentar novamente") }
                TextButton(onClick = { vm.openInBrowser() }) { Text("Abrir no navegador") }
            }
        }
    }
}

@Composable
private fun PlatformsRow() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PlatformPill(Platform.TIKTOK, R.drawable.ic_tiktok, Color(0xFF111111), Modifier.weight(1f))
        PlatformPill(Platform.YOUTUBE, R.drawable.ic_youtube, Color(0xFFFF3355), Modifier.weight(1f))
        PlatformPill(Platform.INSTAGRAM, R.drawable.ic_instagram, Color(0xFFE1306C), Modifier.weight(1f))
        PlatformPill(Platform.FACEBOOK, R.drawable.ic_facebook, Color(0xFF1877F2), Modifier.weight(1f))
    }
}

@Composable
private fun PlatformPill(
    platform: Platform,
    iconRes: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(50), color = color.copy(alpha = 0.12f)) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painterResource(iconRes),
                platform.label,
                tint = Color.Unspecified,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun VipBanner(vm: MainViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { vm.go(Screen.Vip) },
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            Modifier
                .background(Brush.horizontalGradient(listOf(VeraPink, VeraPurple)))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WorkspacePremium,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Vera VIP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Downloads ilimitados e sem anúncios",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
                Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun HowItWorks() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Como funciona", fontWeight = FontWeight.Bold)
            StepRow("1", "Copie o link no app do vídeo ou use Compartilhar.")
            StepRow("2", "Cole aqui e toque em Analisar.")
            StepRow("3", "Escolha o formato e baixe direto para a galeria.")
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(VeraPink.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = VeraPink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorBanner(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Download,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Pausado e falhado",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Badge(containerColor = MaterialTheme.colorScheme.error) {
                Text(count.toString(), color = MaterialTheme.colorScheme.onError)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
}

@Composable
private fun FrequentAuthors(state: UiState, vm: MainViewModel) {
    val authors = state.history
        .mapNotNull { it.author }
        .filter { it.isNotBlank() }
        .distinct()
        .take(8)
    if (authors.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Visitados com frequência", "Ver tudo") { vm.go(Screen.History) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(authors) { author ->
                val record = state.history.firstOrNull { it.author == author }
                Column(
                    Modifier
                        .width(72.dp)
                        .clickable { vm.openAuthor(author) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = record?.thumb,
                        contentDescription = author,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "@$author",
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun PreviewScreen(state: UiState, vm: MainViewModel) {
    val result = state.result ?: return
    val images = result.items.filter { it.kind == MediaKind.IMAGE }
    val multiImage = images.size > 1
    val media = if (multiImage) result.items.filter { it.kind != MediaKind.IMAGE } else result.items
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { CoverCard(result) }
        item { TitleBlock(result) }
        if (result.items.isNotEmpty()) {
            item { QuickActions(result, vm) }
        }
        if (result.items.isEmpty()) {
            item { NoteCard(result.note ?: "Não foi possível liberar o download desta publicação.") }
            item {
                Button(
                    onClick = { vm.openInBrowser() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInBrowser, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir no ${result.platform.label}")
                }
            }
        } else {
            item {
                Text(
                    if (media.isEmpty()) "Selecione as imagens" else "Escolha o formato",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(media) { item ->
                MediaRow(item) { vm.download(item) }
            }
            if (multiImage) {
                item { ImageSelector(images, vm) }
            }
            if (result.items.any { it.kind == MediaKind.AUDIO }) {
                item {
                    Text(
                        "Para baixar somente o áudio, toque em “Baixar” na opção Áudio acima.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            item {
                Text(
                    "Dica: quando a plataforma entrega a versão sem marca d'água, ela é usada automaticamente.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { vm.go(Screen.History) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ver downloads")
            }
        }
    }
}

@Composable
private fun QuickActions(result: ParseResult, vm: MainViewModel) {
    val hasVideo = result.items.any { it.kind == MediaKind.VIDEO }
    val hasAudio = result.items.any { it.kind == MediaKind.AUDIO }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickAction(Icons.Filled.Star, "Full HD", hasVideo, Modifier.weight(1f)) {
            vm.downloadFirst(MediaKind.VIDEO)
        }
        QuickAction(Icons.Filled.MusicNote, "Áudio", hasAudio, Modifier.weight(1f)) {
            vm.downloadFirst(MediaKind.AUDIO)
        }
        QuickAction(Icons.Filled.Download, "Lote", result.items.size > 1, Modifier.weight(1f)) {
            vm.downloadAll()
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = tint, maxLines = 1)
    }
}

@Composable
private fun NoteCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Lock,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CoverCard(result: ParseResult) {
    Card(shape = RoundedCornerShape(24.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(
                    Brush.linearGradient(
                        listOf(VeraPink.copy(alpha = 0.8f), VeraPurple.copy(alpha = 0.9f))
                    )
                )
        ) {
            if (!result.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = result.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                )
            }
            val duration = formatDuration(result.durationMs)
            if (duration != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        duration,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(result: ParseResult) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            result.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    result.platform.label,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            result.author?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    "@$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "via ${result.provider}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImageSelector(images: List<MediaItem>, vm: MainViewModel) {
    var selected by remember(images) { mutableStateOf(setOf<Int>()) }
    var confirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(images) { index, item ->
                val isSelected = index in selected
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            selected = if (isSelected) {
                                selected - index
                            } else {
                                selected + index
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.url)
                            .apply {
                                item.headers.forEach { (key, value) -> addHeader(key, value) }
                            }
                            .build(),
                        contentDescription = item.quality,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isSelected) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                selected = if (selected.size == images.size) {
                    emptySet()
                } else {
                    images.indices.toSet()
                }
            }) {
                Text(
                    if (selected.size == images.size) {
                        "Limpar seleção"
                    } else {
                        "Selecionar todas"
                    }
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { confirm = true },
                enabled = selected.isNotEmpty()
            ) {
                Text("Baixar selecionadas (${selected.size})")
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Baixar imagens") },
            text = {
                Text("Baixar ${selected.size} imagem(ns) selecionada(s)?")
            },
            confirmButton = {
                TextButton(onClick = {
                    val items = selected.sorted().map { images[it] }
                    confirm = false
                    selected = emptySet()
                    vm.downloadItems(items)
                }) { Text("Baixar") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MediaRow(item: MediaItem, onDownload: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    kindIcon(item.kind),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    mediaTitle(item),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    mediaSubtitle(item),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onDownload,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Baixar", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RecentRow(record: DownloadRecord, vm: MainViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (record.isDone) vm.open(record) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumb(record, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${record.platform} • ${statusLabel(record)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (record.isRunning) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun HistoryScreen(state: UiState, vm: MainViewModel) {
    if (state.history.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.Download,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Text("Nenhum download ainda", fontWeight = FontWeight.Bold)
            Text(
                "Compartilhe um link de vídeo com o Vera ou cole na tela inicial.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { vm.go(Screen.Home) }) { Text("Ir para o início") }
        }
        return
    }

    var filter by remember { mutableStateOf(0) }
    val filtered = when (filter) {
        1 -> state.history.filter { it.kind == MediaKind.VIDEO.name }
        2 -> state.history.filter { it.kind == MediaKind.IMAGE.name }
        3 -> state.history.filter { it.kind == MediaKind.AUDIO.name }
        else -> state.history
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            TabRow(
                selectedTabIndex = filter,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                listOf("Todos", "Vídeo", "Imagem", "Música").forEachIndexed { index, label ->
                    Tab(
                        selected = filter == index,
                        onClick = { filter = index },
                        text = { Text(label, fontSize = 12.sp) }
                    )
                }
            }
        }
        if (state.history.any { it.status == Status.ERROR }) {
            item {
                Button(
                    onClick = { vm.retryAllErrors() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tentar novamente tudo")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${filtered.size} downloads",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.clearFinished() }) { Text("Limpar concluídos") }
            }
        }
        if (filtered.isEmpty()) {
            item {
                Text(
                    "Nada nesta categoria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filtered, key = { it.id }) { record ->
                HistoryCard(record, vm)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HistoryCard(record: DownloadRecord, vm: MainViewModel) {
    var confirmWallpaper by remember { mutableStateOf(false) }
    var confirmRingtone by remember { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(record, 58.dp) { if (record.isDone) vm.open(record) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        record.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            record.platform,
                            record.quality,
                            record.kind.lowercase()
                        ).joinToString(" • "),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(record)
            }
            if (record.isRunning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { record.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${(record.progress * 100).toInt()}% • ${formatBytes(record.bytes) ?: "0 KB"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (record.status == Status.ERROR) {
                Spacer(Modifier.height(6.dp))
                Text(
                    record.error ?: "Falha no download",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.isDone) {
                    IconButton(onClick = { vm.open(record) }) {
                        Icon(Icons.Filled.OpenInNew, "Abrir")
                    }
                    IconButton(onClick = { vm.share(record) }) {
                        Icon(Icons.Filled.Share, "Compartilhar")
                    }
                    if (record.kind == MediaKind.IMAGE.name) {
                        IconButton(onClick = { confirmWallpaper = true }) {
                            Icon(Icons.Filled.Wallpaper, "Papel de parede")
                        }
                    }
                    if (record.kind == MediaKind.AUDIO.name) {
                        IconButton(onClick = { confirmRingtone = true }) {
                            Icon(Icons.Filled.RingVolume, "Toque")
                        }
                    }
                }
                if (record.status == Status.ERROR) {
                    TextButton(onClick = { vm.retry(record) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Tentar novamente", fontSize = 12.sp)
                    }
                    IconButton(onClick = { vm.openSource(record) }) {
                        Icon(Icons.Filled.OpenInBrowser, "Abrir no navegador")
                    }
                }
                IconButton(onClick = { vm.delete(record) }) {
                    Icon(Icons.Filled.Delete, "Excluir")
                }
            }
        }
    }

    if (confirmWallpaper) {
        AlertDialog(
            onDismissRequest = { confirmWallpaper = false },
            title = { Text("Papel de parede") },
            text = { Text("Aplicar esta imagem como papel de parede?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWallpaper = false
                    vm.setWallpaper(record)
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWallpaper = false }) { Text("Cancelar") }
            }
        )
    }

    if (confirmRingtone) {
        AlertDialog(
            onDismissRequest = { confirmRingtone = false },
            title = { Text("Toque") },
            text = { Text("Usar este áudio como toque do celular?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRingtone = false
                    vm.setRingtone(record)
                }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRingtone = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun StatusPill(record: DownloadRecord) {
    val (text, color) = when (record.status) {
        Status.DONE -> "Pronto" to Color(0xFF2E9E5B)
        Status.ERROR -> "Erro" to MaterialTheme.colorScheme.error
        Status.RUNNING -> "Baixando" to VeraPink
        else -> "Na fila" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = 0.14f)) {
        Text(
            text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun Thumb(
    record: DownloadRecord,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val model = if (record.kind == MediaKind.IMAGE.name && !record.localUri.isNullOrBlank()) {
            record.localUri
        } else {
            record.thumb
        }
        if (!model.isNullOrBlank()) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                when (record.kind) {
                    MediaKind.AUDIO.name -> Icons.Filled.MusicNote
                    MediaKind.IMAGE.name -> Icons.Filled.PhotoCamera
                    else -> Icons.Filled.PlayArrow
                },
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsScreen(state: UiState, vm: MainViewModel) {
    val settings = state.settings
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SettingsSection("Downloads") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column {
                    SwitchRow(
                        "Baixar automaticamente ao compartilhar",
                        "Vale só para links recebidos pelo Compartilhar; “Analisar” sempre mostra as opções",
                        settings.autoDownload,
                        vm::setAutoDownload
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    OverlaySwitchRow(state, vm)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    SwitchRow(
                        "Somente Wi-Fi",
                        "Evita gastar dados móveis em arquivos grandes",
                        settings.wifiOnly,
                        vm::setWifiOnly
                    )
                }
            }
        }
        item { SettingsSection("Qualidade preferida") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.hdPreferred,
                    onClick = { vm.setHdPreferred(true) },
                    label = { Text("HD") }
                )
                FilterChip(
                    selected = !settings.hdPreferred,
                    onClick = { vm.setHdPreferred(false) },
                    label = { Text("Econômica") }
                )
            }
        }
        item { SettingsSection("Idioma") }
        item { LanguageCard(settings.language, vm) }
        item { SettingsSection("Aparência") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("Sistema", "system", settings.theme, vm)
                ThemeChip("Claro", "light", settings.theme, vm)
                ThemeChip("Escuro", "dark", settings.theme, vm)
            }
        }
        item { SettingsSection("Atualizações") }
        item { UpdateCard(vm) }
        item { SettingsSection("Sobre") }
        item { AboutCard() }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun UpdateCard(vm: MainViewModel) {
    val context = LocalContext.current
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Atualizações", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Versão ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        checking = true
                        val manager = AppUpdateManagerFactory.create(context)
                        manager.appUpdateInfo
                            .addOnSuccessListener { info ->
                                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                                    val activity = context as? Activity
                                    if (activity != null &&
                                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                                    ) {
                                        runCatching {
                                            manager.startUpdateFlowForResult(
                                                info,
                                                AppUpdateType.IMMEDIATE,
                                                activity,
                                                1001
                                            )
                                        }.onFailure {
                                            openStore(context)
                                        }
                                    } else {
                                        openStore(context)
                                    }
                                } else {
                                    message = "Nenhuma atualização disponível na loja."
                                }
                                checking = false
                            }
                            .addOnFailureListener {
                                checking = false
                                openStore(context)
                            }
                    },
                    enabled = !checking
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Verificar")
                    }
                }
            }
        }
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Atualização") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { message = null; openStore(context) }) {
                    Text("Abrir loja")
                }
            },
            dismissButton = {
                TextButton(onClick = { message = null }) { Text("Fechar") }
            }
        )
    }
}

private fun openStore(context: Context) {
    val pkg = context.packageName
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(market) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun FeedbackScreen(vm: MainViewModel) {
    var category by remember { mutableStateOf("Sugestão") }
    var message by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Conte o que aconteceu, o que podemos melhorar ou o que você gostou.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Bug", "Sugestão", "Elogio", "Reclamação").forEach { option ->
                    FilterChip(
                        selected = category == option,
                        onClick = { category = option },
                        label = { Text(option) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Mensagem") },
                minLines = 6,
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Seu e-mail (opcional)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
        }
        item {
            Button(
                onClick = { vm.sendFeedback(category, message, contact) },
                enabled = message.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enviar")
            }
        }
        item {
            Text(
                "O endereço de destino não é exibido no app. O envio abre seu aplicativo de e-mail.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun LanguageCard(current: String, vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Language,
                null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Idioma", fontWeight = FontWeight.SemiBold)
                Text(
                    I18n.languageLabel(current),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Filled.ChevronRight, null)
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Idioma") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(I18n.LANGUAGES) { language ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setLanguage(language.code)
                                    open = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = language.code == current,
                                onClick = {
                                    vm.setLanguage(language.code)
                                    open = false
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(language.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Fechar") }
            }
        )
    }
}

@Composable
private fun ThemeChip(label: String, value: String, current: String, vm: MainViewModel) {
    FilterChip(
        selected = current == value,
        onClick = { vm.setTheme(value) },
        label = { Text(label) }
    )
}

@Composable
private fun OverlaySwitchRow(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    SwitchRow(
        "Baixar sem sair do TikTok",
        "Bolha flutuante: copie o link no TikTok e toque na bolha",
        state.settings.overlayEnabled
    ) { checked ->
        if (!checked) {
            vm.setOverlayEnabled(false)
        } else if (AndroidSettings.canDrawOverlays(context)) {
            vm.setOverlayEnabled(true)
        } else {
            vm.showToast("Autorize a sobreposição para usar a bolha")
            runCatching {
                context.startActivity(
                    Intent(
                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun AccountCard(premium: Boolean, vm: MainViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                null,
                tint = if (premium) VeraPink else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (premium) "Vera VIP ativo" else "Plano gratuito",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (premium) "Obrigado por apoiar o projeto!" else "Assine para liberar todos os recursos",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!premium) {
                Button(onClick = { vm.go(Screen.Vip) }) { Text("Ver") }
            }
        }
    }
}

@Composable
private fun AboutCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Vera Downloader", fontWeight = FontWeight.Bold)
            Text("Versão ${BuildConfig.VERSION_NAME}", fontSize = 12.sp)
            Text(
                "Projeto de estudo inspirado em apps de download. Respeite os termos de uso das plataformas e os direitos autorais dos criadores.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Sem coleta de dados. Nenhum anúncio ou rastreador está ativo nesta versão.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VipScreen(state: UiState, vm: MainViewModel) {
    val premium = state.settings.premium
    var selected by remember { mutableStateOf(1) }
    val plans = listOf(
        Triple("Semanal", "R$ 9,90", "7 dias"),
        Triple("Mensal", "R$ 24,90", "30 dias"),
        Triple("Anual", "R$ 99,90", "365 dias")
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            Icons.Filled.WorkspacePremium,
            null,
            tint = VeraPink,
            modifier = Modifier.size(72.dp)
        )
        Text("Vera VIP", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Text(
            "Apoie o projeto e desbloqueie a experiência completa.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Benefit("Downloads ilimitados")
                Benefit("Sem anúncios")
                Benefit("Download automático")
                Benefit("Prioridade no suporte")
                Benefit("Recursos futuros primeiro")
            }
        }
        plans.forEachIndexed { index, plan ->
            PlanCard(
                title = plan.first,
                price = plan.second,
                period = plan.third,
                selected = selected == index,
                onClick = { selected = index }
            )
        }
        Button(
            onClick = { if (premium) vm.cancelDemo() else vm.subscribeDemo() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (premium) "Cancelar demonstração" else "Assinar (demonstração)")
        }
        Text(
            "Demonstração: nenhuma cobrança real é feita e nenhum dado de pagamento é coletado.",
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun Benefit(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.CheckCircle,
            null,
            tint = Color(0xFF2E9E5B),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (selected) Icons.Filled.Star else Icons.Filled.Lock,
                null,
                tint = if (selected) VeraPink else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(period, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(price, fontWeight = FontWeight.Bold, color = VeraPink)
        }
    }
}

private fun kindIcon(kind: MediaKind): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Filled.PlayArrow
    MediaKind.AUDIO -> Icons.Filled.MusicNote
    MediaKind.IMAGE -> Icons.Filled.PhotoCamera
}

private fun mediaTitle(item: MediaItem): String = when (item.kind) {
    MediaKind.VIDEO -> "Vídeo ${item.quality ?: ""}".trim()
    MediaKind.AUDIO -> "Áudio"
    MediaKind.IMAGE -> "Imagem"
}

private fun mediaSubtitle(item: MediaItem): String {
    val parts = mutableListOf<String>()
    if (item.kind == MediaKind.AUDIO) parts += "somente áudio"
    if (item.watermarkFree) parts += "sem marca d'água"
    formatBytes(item.sizeBytes)?.let { parts += it }
    parts += item.extension.uppercase()
    if (item.muxAudioUrl != null) parts += "com áudio"
    return parts.joinToString(" • ")
}

private fun statusLabel(record: DownloadRecord): String = when (record.status) {
    Status.DONE -> "Concluído"
    Status.ERROR -> "Falhou"
    Status.RUNNING -> "${(record.progress * 100).toInt()}%"
    else -> "Na fila"
}

private fun formatBytes(bytes: Long?): String? {
    if (bytes == null || bytes <= 0) return null
    val kb = bytes / 1024.0
    return if (kb < 1024) {
        "%.0f KB".format(kb)
    } else {
        "%.1f MB".format(kb / 1024.0)
    }
}

private fun formatDuration(ms: Long?): String? {
    if (ms == null || ms <= 0) return null
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

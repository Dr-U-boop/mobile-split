package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Paint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MyApplicationApp()
            }
        }
    }
}

private data class RecordTypeOption(
    val label: String,
    val apiValue: String,
    val requiresValue: Boolean,
    val detailsRequired: Boolean = false,
    val detailsLabel: String = "Комментарий (optional)"
)

private val recordTypeOptions = listOf(
    RecordTypeOption(
        label = "Уровень глюкозы в крови",
        apiValue = "glucose",
        requiresValue = true
    ),
    RecordTypeOption(
        label = "Количество принятых углеводов",
        apiValue = "carbs",
        requiresValue = true
    ),
    RecordTypeOption(
        label = "Количество введенного инсулина",
        apiValue = "insulin_bolus",
        requiresValue = true
    ),
    RecordTypeOption(
        label = "Дневник самоконтроля",
        apiValue = "self_monitoring_diary",
        requiresValue = false,
        detailsRequired = true,
        detailsLabel = "Текст записи дневника"
    )
)

private enum class AppSection {
    Home,
    Charts,
    Record,
    Diary,
    Profile
}

private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
private val serverSpaceTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val fixedServerUrl = "https://www.glucose-sibr-tech.ru/"
private val panelShape = RoundedCornerShape(26.dp)
private val fieldShape = RoundedCornerShape(18.dp)

private fun currentTimestamp(): String = LocalDateTime.now().format(timestampFormatter)

private data class SavedSession(
    val login: String,
    val token: String,
    val rememberMe: Boolean
)

private object SessionStorage {
    private const val prefsName = "auth_prefs"
    private const val keyLogin = "login"
    private const val keyToken = "token"
    private const val keyRememberMe = "remember_me"

    fun prefs(context: Context): SharedPreferences =
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse {
            context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        }

    fun load(prefs: SharedPreferences): SavedSession = SavedSession(
        login = prefs.getString(keyLogin, "").orEmpty(),
        token = prefs.getString(keyToken, "").orEmpty(),
        rememberMe = prefs.getBoolean(keyRememberMe, false)
    )

    fun save(prefs: SharedPreferences, login: String, token: String, rememberMe: Boolean) {
        prefs.edit()
            .putString(keyLogin, login)
            .putString(keyToken, token)
            .putBoolean(keyRememberMe, rememberMe)
            .apply()
    }

    fun clear(prefs: SharedPreferences) {
        prefs.edit()
            .remove(keyLogin)
            .remove(keyToken)
            .putBoolean(keyRememberMe, false)
            .apply()
    }
}

@Composable
fun MyApplicationApp() {
    val context = LocalContext.current
    val prefs = remember(context) { SessionStorage.prefs(context) }
    val savedSession = remember(prefs) { SessionStorage.load(prefs) }

    var authToken by rememberSaveable { mutableStateOf<String?>(null) }
    var login by rememberSaveable { mutableStateOf(savedSession.login) }
    var password by rememberSaveable { mutableStateOf("") }
    var rememberMe by rememberSaveable { mutableStateOf(savedSession.rememberMe) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var isCheckingSavedSession by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(savedSession.token, savedSession.rememberMe, savedSession.login) {
        if (!savedSession.rememberMe || savedSession.token.isBlank()) {
            isCheckingSavedSession = false
            if (!savedSession.rememberMe) {
                SessionStorage.clear(prefs)
            }
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        AuthRepository.validateToken(savedSession.token)
            .onSuccess { isValid ->
                if (isValid) {
                    authToken = savedSession.token
                    login = savedSession.login
                } else {
                    SessionStorage.clear(prefs)
                    errorMessage = "Сессия истекла. Войдите заново."
                }
            }
            .onFailure {
                errorMessage = it.message ?: "Не удалось проверить сохраненную сессию"
            }
            .getOrNull()
        isLoading = false
        isCheckingSavedSession = false
    }

    if (isCheckingSavedSession) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (authToken == null) {
        val backgroundShift = rememberInfiniteTransition(label = "login-bg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "login-bg-shift"
        )
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                Color(0xFFE6F0FF)
                            ),
                            startY = backgroundShift.value * 500f,
                            endY = 1400f + backgroundShift.value * 700f
                        )
                    )
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                WowCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Авторизация",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary
                        )

                        AppTextField(
                            value = login,
                            onValueChange = { login = it },
                            label = "Логин",
                            singleLine = true
                        )
                        AppTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "Пароль",
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it }
                            )
                            Text(
                                text = "Запомнить меня",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        PrimaryActionButton(
                            text = "Войти",
                            enabled = !isLoading,
                            loading = isLoading,
                            onClick = {
                                if (login.isBlank() || password.isBlank()) {
                                    errorMessage = "Заполните все поля"
                                    return@PrimaryActionButton
                                }

                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    authToken = AuthRepository.login(
                                        login = login,
                                        password = password
                                    ).onFailure {
                                        errorMessage = it.message ?: "Ошибка авторизации"
                                    }.getOrNull()?.also { token ->
                                        if (rememberMe) {
                                            SessionStorage.save(
                                                prefs = prefs,
                                                login = login.trim(),
                                                token = token,
                                                rememberMe = true
                                            )
                                        } else {
                                            SessionStorage.clear(prefs)
                                        }
                                    }
                                    isLoading = false
                                }
                            }
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        return
    }

    AuthenticatedRoot(
        login = login,
        token = authToken.orEmpty(),
        onLogout = {
            authToken = null
            password = ""
            SessionStorage.clear(prefs)
        },
        onSessionExpired = {
            authToken = null
            password = ""
            SessionStorage.clear(prefs)
            errorMessage = "Сессия истекла. Войдите заново."
        }
    )
}

@Composable
private fun AuthenticatedRoot(
    login: String,
    token: String,
    onLogout: () -> Unit,
    onSessionExpired: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentSection by rememberSaveable { mutableStateOf(AppSection.Home) }

    var chartData by remember { mutableStateOf<ComprehensiveDataResponse?>(null) }
    var chartError by remember { mutableStateOf<String?>(null) }
    var isLoadingChart by remember { mutableStateOf(false) }

    val pendingPoints = remember { mutableStateListOf<TimeseriesDataPointRequest>() }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendSuccess by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    val sendAllPending: () -> Unit = {
        if (pendingPoints.isEmpty()) {
            sendError = "Сначала добавьте хотя бы одну запись"
            sendSuccess = null
        } else {
            scope.launch {
                isSending = true
                sendError = null
                sendSuccess = null
                AuthRepository.sendMyTimeseriesData(
                    token = token,
                    points = pendingPoints.toList()
                ).onSuccess {
                    sendSuccess = "Данные успешно отправлены"
                    pendingPoints.clear()
                }.onFailure {
                    if (it is UnauthorizedException) {
                        onSessionExpired()
                    } else {
                        sendError = it.message ?: "Ошибка отправки данных"
                    }
                }
                isSending = false
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AppBottomBar(
                selectedSection = currentSection,
                onSelectSection = { currentSection = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (currentSection) {
                AppSection.Home -> HomeScreen(
                    login = login,
                    pendingCount = pendingPoints.size,
                    lastGlucoseCount = chartData?.glucose?.size ?: 0
                )

                AppSection.Charts -> ChartsScreen(
                    chartData = chartData,
                    chartError = chartError,
                    isLoadingChart = isLoadingChart,
                    onLoadChart = {
                        scope.launch {
                            isLoadingChart = true
                            chartError = null
                            chartData = AuthRepository.getMyComprehensiveData(
                                token = token
                            ).onFailure {
                                if (it is UnauthorizedException) {
                                    onSessionExpired()
                                } else {
                                    chartError = it.message ?: "Ошибка загрузки данных мониторинга"
                                }
                            }.getOrNull()
                            isLoadingChart = false
                        }
                    }
                )

                AppSection.Record -> RecordScreen(
                    pendingPoints = pendingPoints,
                    sendError = sendError,
                    sendSuccess = sendSuccess,
                    isSending = isSending,
                    onSend = sendAllPending
                )

                AppSection.Diary -> DiaryScreen(
                    pendingPoints = pendingPoints,
                    sendError = sendError,
                    sendSuccess = sendSuccess,
                    isSending = isSending,
                    onSend = sendAllPending
                )

                AppSection.Profile -> ProfileScreen(
                    login = login,
                    token = token,
                    onLogout = onLogout
                )
            }
        }
    }
}

@Composable
private fun ScreenContainer(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        content()
    }
}

@Composable
private fun WowCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = panelShape,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun StatusBadge(
    text: String,
    background: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = fieldShape,
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Composable
private fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    accent: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            contentColor = if (accent) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = if (accent) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    supporting: String? = null
) {
    WowCard {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        if (!supporting.isNullOrBlank()) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppBottomBar(
    selectedSection: AppSection,
    onSelectSection: (AppSection) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BottomBarItem(
                        label = "Главная",
                        icon = Icons.Filled.Home,
                        selected = selectedSection == AppSection.Home
                    ) {
                        onSelectSection(AppSection.Home)
                    }
                    BottomBarItem(
                        label = "Графики",
                        icon = Icons.AutoMirrored.Filled.ShowChart,
                        selected = selectedSection == AppSection.Charts
                    ) {
                        onSelectSection(AppSection.Charts)
                    }

                    Spacer(modifier = Modifier.width(72.dp))

                    BottomBarItem(
                        label = "Дневник",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        selected = selectedSection == AppSection.Diary
                    ) {
                        onSelectSection(AppSection.Diary)
                    }
                    BottomBarItem(
                        label = "Профиль",
                        icon = Icons.Filled.Person,
                        selected = selectedSection == AppSection.Profile
                    ) {
                        onSelectSection(AppSection.Profile)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
            ) {
                FloatingActionButton(
                    onClick = { onSelectSection(AppSection.Record) },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    containerColor = if (selectedSection == AppSection.Record) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (selectedSection == AppSection.Record) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 6.dp,
                        focusedElevation = 4.dp,
                        hoveredElevation = 4.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Запись"
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
                } else {
                    Color.Transparent
                }
            )
            .padding(vertical = 5.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HomeScreen(
    login: String,
    pendingCount: Int,
    lastGlucoseCount: Int
) {
    ScreenContainer(
        title = "Главная"
    ) {
        WowCard {
            Text(
                text = "Здравствуйте, $login",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        MetricCard(
            title = "Очередь отправки",
            value = pendingCount.toString()
        )
        MetricCard(
            title = "Последний график",
            value = "$lastGlucoseCount точек"
        )
    }
}

@Composable
private fun ChartsScreen(
    chartData: ComprehensiveDataResponse?,
    chartError: String?,
    isLoadingChart: Boolean,
    onLoadChart: () -> Unit
) {
    ScreenContainer(
        title = "Графики"
    ) {
        PrimaryActionButton(
            text = "Загрузить данные мониторинга",
            onClick = onLoadChart,
            enabled = !isLoadingChart,
            loading = isLoadingChart
        )

        if (chartError != null) {
            Text(text = chartError, color = MaterialTheme.colorScheme.error)
        }

        chartData?.let {
            ComprehensiveMonitoringPanel(chartData = it)
        }
    }
}

@Composable
private fun RecordScreen(
    pendingPoints: MutableList<TimeseriesDataPointRequest>,
    sendError: String?,
    sendSuccess: String?,
    isSending: Boolean,
    onSend: () -> Unit
) {
    val options = remember { recordTypeOptions.filter { it.apiValue != "self_monitoring_diary" } }
    var pointTimestamp by remember { mutableStateOf(currentTimestamp()) }
    var pointRecordType by remember { mutableStateOf(options.first().apiValue) }
    var pointValue by remember { mutableStateOf("") }
    var pointDetails by remember { mutableStateOf("") }

    val selectedRecordType = options.firstOrNull { it.apiValue == pointRecordType } ?: options.first()

    ScreenContainer(
        title = "Запись"
    ) {
        WowCard {
            AppTextField(
                value = pointTimestamp,
                onValueChange = { pointTimestamp = it },
                label = "Timestamp (YYYY-MM-DDTHH:MM:SS)",
                singleLine = true
            )

            Text(
                text = "Тип записи",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )

            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (pointRecordType == option.apiValue) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (pointRecordType == option.apiValue) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                            },
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = pointRecordType == option.apiValue,
                        onClick = { pointRecordType = option.apiValue }
                    )
                    Text(text = option.label, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (selectedRecordType.requiresValue) {
                AppTextField(
                    value = pointValue,
                    onValueChange = { pointValue = it },
                    label = "Value",
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            AppTextField(
                value = pointDetails,
                onValueChange = { pointDetails = it },
                label = selectedRecordType.detailsLabel,
                singleLine = true
            )
        }

        PrimaryActionButton(
            text = "Добавить запись",
            onClick = {
                val numericValue = pointValue.toDoubleOrNull()
                if (pointTimestamp.isBlank()) return@PrimaryActionButton
                if (selectedRecordType.requiresValue && numericValue == null) return@PrimaryActionButton

                pendingPoints.add(
                    TimeseriesDataPointRequest(
                        timestamp = pointTimestamp.trim(),
                        recordType = selectedRecordType.apiValue,
                        value = numericValue,
                        details = pointDetails.trim().ifBlank { null }
                    )
                )
                pointTimestamp = currentTimestamp()
                pointValue = ""
                pointDetails = ""
            }
        )

        PrimaryActionButton(
            text = "Отправить врачу",
            onClick = onSend,
            enabled = !isSending,
            loading = isSending,
            accent = true
        )

        MetricCard(
            title = "Очередь",
            value = pendingPoints.size.toString()
        )

        if (sendError != null) {
            Text(text = sendError, color = MaterialTheme.colorScheme.error)
        }
        if (sendSuccess != null) {
            Text(text = sendSuccess, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun DiaryScreen(
    pendingPoints: MutableList<TimeseriesDataPointRequest>,
    sendError: String?,
    sendSuccess: String?,
    isSending: Boolean,
    onSend: () -> Unit
) {
    var pointTimestamp by remember { mutableStateOf(currentTimestamp()) }
    var pointDetails by remember { mutableStateOf("") }

    val diaryItems = pendingPoints.filter { it.recordType == "self_monitoring_diary" }

    ScreenContainer(
        title = "Дневник"
    ) {
        WowCard {
            AppTextField(
                value = pointTimestamp,
                onValueChange = { pointTimestamp = it },
                label = "Timestamp (YYYY-MM-DDTHH:MM:SS)",
                singleLine = true
            )
            AppTextField(
                value = pointDetails,
                onValueChange = { pointDetails = it },
                label = "Текст записи дневника"
            )
        }

        PrimaryActionButton(
            text = "Добавить запись в дневник",
            onClick = {
                if (pointTimestamp.isBlank() || pointDetails.isBlank()) return@PrimaryActionButton
                pendingPoints.add(
                    TimeseriesDataPointRequest(
                        timestamp = pointTimestamp.trim(),
                        recordType = "self_monitoring_diary",
                        value = null,
                        details = pointDetails.trim()
                    )
                )
                pointTimestamp = currentTimestamp()
                pointDetails = ""
            }
        )

        PrimaryActionButton(
            text = "Отправить врачу",
            onClick = onSend,
            enabled = !isSending,
            loading = isSending,
            accent = true
        )

        MetricCard(
            title = "Записи дневника",
            value = diaryItems.size.toString()
        )

        diaryItems.forEachIndexed { index, item ->
            WowCard {
                Text(
                    text = "${index + 1}. ${item.timestamp}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.details.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (sendError != null) {
            Text(text = sendError, color = MaterialTheme.colorScheme.error)
        }
        if (sendSuccess != null) {
            Text(text = sendSuccess, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProfileScreen(
    login: String,
    token: String,
    onLogout: () -> Unit
) {
    ScreenContainer(
        title = "Профиль"
    ) {
        WowCard {
            Text(text = "Логин: $login", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Токен: ${token.take(24)}...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        PrimaryActionButton(
            text = "Выйти",
            onClick = onLogout
        )
    }
}

private enum class MonitoringSeriesKind { Line, Bar }

private data class MonitoringSeries(
    val name: String,
    val points: List<MonitoringPoint>,
    val color: Color,
    val unit: String,
    val axis: Int,
    val kind: MonitoringSeriesKind
)

private data class MonitoringPoint(
    val timestampMillis: Long,
    val value: Double
)

private data class MonitoringDataset(
    val glucose: MonitoringSeries,
    val carbs: MonitoringSeries,
    val insulin: MonitoringSeries,
    val domainStart: Long,
    val domainEnd: Long
) {
    val allSeries: List<MonitoringSeries> = listOf(glucose, carbs, insulin)
    val allTimestamps: List<Long> = allSeries.flatMap { series -> series.points.map { it.timestampMillis } }.distinct().sorted()
    val isEmpty: Boolean = allSeries.all { it.points.isEmpty() }
}

private data class MonitoringStats(
    val readingsCount: Int,
    val avgGlucose: Double,
    val minGlucose: Double,
    val maxGlucose: Double,
    val stdDev: Double,
    val tir: Double,
    val hypoCount: Int,
    val hyperCount: Int,
    val totalCarbs: Double,
    val totalInsulin: Double
)

private data class AxisBounds(
    val min: Double,
    val max: Double
)

@Composable
private fun ComprehensiveMonitoringPanel(
    chartData: ComprehensiveDataResponse
) {
    val dataset = remember(chartData) { chartData.toMonitoringDataset() }
    if (dataset.isEmpty) {
        WowCard {
            Text(
                text = "Нет данных за выбранный период",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val stats = remember(chartData) { chartData.toMonitoringStats() }
    val defaultRange = remember(dataset.domainStart, dataset.domainEnd) {
        defaultLastDayZoomRange(dataset.domainStart, dataset.domainEnd)
    }
    var zoomRange by remember(dataset.domainStart, dataset.domainEnd) { mutableStateOf(defaultRange) }
    var committedZoomRange by remember(dataset.domainStart, dataset.domainEnd) { mutableStateOf(defaultRange) }
    var selectedTimestamp by remember(dataset.allTimestamps) { mutableStateOf(dataset.allTimestamps.lastOrNull()) }
    var startDateText by remember(dataset.domainStart, dataset.domainEnd) {
        mutableStateOf(formatDateInput(interpolateMillis(dataset.domainStart, dataset.domainEnd, defaultRange.start)))
    }
    var startTimeText by remember(dataset.domainStart, dataset.domainEnd) {
        mutableStateOf(formatTimeInput(interpolateMillis(dataset.domainStart, dataset.domainEnd, defaultRange.start)))
    }
    var endDateText by remember(dataset.domainStart, dataset.domainEnd) {
        mutableStateOf(formatDateInput(interpolateMillis(dataset.domainStart, dataset.domainEnd, defaultRange.endInclusive)))
    }
    var endTimeText by remember(dataset.domainStart, dataset.domainEnd) {
        mutableStateOf(formatTimeInput(interpolateMillis(dataset.domainStart, dataset.domainEnd, defaultRange.endInclusive)))
    }
    var periodError by remember { mutableStateOf<String?>(null) }

    val windowStart = interpolateMillis(dataset.domainStart, dataset.domainEnd, zoomRange.start)
    val windowEnd = interpolateMillis(dataset.domainStart, dataset.domainEnd, zoomRange.endInclusive)
    val committedWindowStart = interpolateMillis(dataset.domainStart, dataset.domainEnd, committedZoomRange.start)
    val committedWindowEnd = interpolateMillis(dataset.domainStart, dataset.domainEnd, committedZoomRange.endInclusive)
    val committedVisibleTimestamps = remember(dataset, committedWindowStart, committedWindowEnd) {
        dataset.allTimestamps.filter { it in committedWindowStart..committedWindowEnd }
    }
    val effectiveSelectedTimestamp = selectedTimestamp?.takeIf { it in committedWindowStart..committedWindowEnd }
        ?: committedVisibleTimestamps.lastOrNull()
    val syncPeriodFields: (ClosedFloatingPointRange<Float>) -> Unit = { range ->
        val start = interpolateMillis(dataset.domainStart, dataset.domainEnd, range.start)
        val end = interpolateMillis(dataset.domainStart, dataset.domainEnd, range.endInclusive)
        startDateText = formatDateInput(start)
        startTimeText = formatTimeInput(start)
        endDateText = formatDateInput(end)
        endTimeText = formatTimeInput(end)
        periodError = null
    }
    val commitRangeSelection: (ClosedFloatingPointRange<Float>) -> Unit = { range ->
        committedZoomRange = range
        selectedTimestamp = dataset.allTimestamps.lastOrNull {
            it in interpolateMillis(dataset.domainStart, dataset.domainEnd, range.start)..
                interpolateMillis(dataset.domainStart, dataset.domainEnd, range.endInclusive)
        } ?: selectedTimestamp
    }
    val selectedRows = remember(dataset, effectiveSelectedTimestamp) {
        effectiveSelectedTimestamp?.let { timestamp ->
            dataset.allSeries.mapNotNull { series ->
                series.points.find { it.timestampMillis == timestamp }?.let { point -> series to point }
            }
        }.orEmpty()
    }

    WowCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppTextField(
                value = startDateText,
                onValueChange = { startDateText = it },
                label = "Дата начала",
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            AppTextField(
                value = startTimeText,
                onValueChange = { startTimeText = it },
                label = "Время начала",
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppTextField(
                value = endDateText,
                onValueChange = { endDateText = it },
                label = "Дата конца",
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            AppTextField(
                value = endTimeText,
                onValueChange = { endTimeText = it },
                label = "Время конца",
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChartActionButton(
                text = "Показать",
                modifier = Modifier.weight(1f)
            ) {
                val start = parseUserDateTimeMillis(startDateText, startTimeText)
                val end = parseUserDateTimeMillis(endDateText, endTimeText)
                if (start == null || end == null) {
                    periodError = "Используй формат даты YYYY-MM-DD и времени HH:MM"
                    return@ChartActionButton
                }
                if (start >= end) {
                    periodError = "Дата начала должна быть раньше даты конца"
                    return@ChartActionButton
                }
                periodError = null
                zoomRange = toZoomFractions(
                    domainStart = dataset.domainStart,
                    domainEnd = dataset.domainEnd,
                    rangeStart = start.coerceIn(dataset.domainStart, dataset.domainEnd),
                    rangeEnd = end.coerceIn(dataset.domainStart, dataset.domainEnd)
                )
                syncPeriodFields(zoomRange)
                commitRangeSelection(zoomRange)
            }
            ChartActionButton(
                text = "Последний день",
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall
            ) {
                val range = defaultLastDayZoomRange(dataset.domainStart, dataset.domainEnd)
                zoomRange = range
                syncPeriodFields(range)
                commitRangeSelection(range)
            }
        }
        if (periodError != null) {
            Text(
                text = periodError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }

    if (selectedRows.isNotEmpty()) {
        WowCard(
            modifier = Modifier.height(156.dp)
        ) {
            Text(
                text = formatChartDateTime(effectiveSelectedTimestamp ?: dataset.domainEnd),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            selectedRows.forEach { (series, point) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(series.color)
                        )
                        Text(
                            text = series.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${formatChartValue(point.value)} ${series.unit}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            repeat((3 - selectedRows.size).coerceAtLeast(0)) {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    WowCard {
        ChartLegend(dataset = dataset)
        MultiSeriesMonitoringChart(
            dataset = dataset,
            zoomRange = zoomRange,
            selectedTimestamp = effectiveSelectedTimestamp,
            onTimestampSelected = { selectedTimestamp = it }
        )

        CombinedRangeSlider(
            value = zoomRange,
            onValueChange = { next ->
                zoomRange = next
                syncPeriodFields(next)
            },
            onValueChangeFinished = {
                commitRangeSelection(zoomRange)
            }
        )
    }

    MonitoringStatsGrid(stats = stats)
}

@Composable
private fun ChartLegend(dataset: MonitoringDataset) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        dataset.allSeries.forEach { series ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 16.dp, height = 8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(series.color)
                    )
                    Text(
                        text = series.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChartActionButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(text = text, style = textStyle)
    }
}

private enum class SliderDragMode {
    LeftThumb,
    RightThumb,
    Window
}

@Composable
private fun CombinedRangeSlider(
    value: ClosedFloatingPointRange<Float>,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    var widthPx by remember { mutableStateOf(0f) }
    val minSpan = 0.04f
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(widthPx) {
                var dragMode: SliderDragMode? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        if (widthPx <= 0f) return@detectDragGestures
                        val currentValue = latestValue
                        val leftPx = currentValue.start * widthPx
                        val rightPx = currentValue.endInclusive * widthPx
                        val thumbRadiusPx = 24f
                        dragMode = when {
                            abs(offset.x - leftPx) <= thumbRadiusPx -> SliderDragMode.LeftThumb
                            abs(offset.x - rightPx) <= thumbRadiusPx -> SliderDragMode.RightThumb
                            offset.x in leftPx..rightPx -> SliderDragMode.Window
                            else -> null
                        }
                    },
                    onDragCancel = { dragMode = null },
                    onDragEnd = {
                        dragMode = null
                        onValueChangeFinished()
                    }
                ) { change, dragAmount ->
                    if (widthPx <= 0f) return@detectDragGestures
                    val currentValue = latestValue
                    val delta = dragAmount.x / widthPx
                    val next = when (dragMode) {
                        SliderDragMode.LeftThumb -> {
                            val nextStart = (currentValue.start + delta).coerceIn(0f, currentValue.endInclusive - minSpan)
                            normalizeZoomRange(nextStart..currentValue.endInclusive)
                        }
                        SliderDragMode.RightThumb -> {
                            val nextEnd = (currentValue.endInclusive + delta).coerceIn(currentValue.start + minSpan, 1f)
                            normalizeZoomRange(currentValue.start..nextEnd)
                        }
                        SliderDragMode.Window -> {
                            shiftWindowKeepingSpan(
                                previous = currentValue,
                                attempted = (currentValue.start + delta)..(currentValue.endInclusive + delta)
                            )
                        }
                        null -> currentValue
                    }
                    if (next != currentValue) {
                        change.consume()
                        latestOnValueChange(next)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        RangeSlider(
            value = value,
            onValueChange = {},
            enabled = false,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                disabledThumbColor = MaterialTheme.colorScheme.primary,
                disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                disabledInactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            )
        )
    }
}

@Composable
private fun MultiSeriesMonitoringChart(
    dataset: MonitoringDataset,
    zoomRange: ClosedFloatingPointRange<Float>,
    selectedTimestamp: Long?,
    onTimestampSelected: (Long) -> Unit
) {
    val windowStart = interpolateMillis(dataset.domainStart, dataset.domainEnd, zoomRange.start)
    val windowEnd = interpolateMillis(dataset.domainStart, dataset.domainEnd, zoomRange.endInclusive)
    val visibleDataset = remember(dataset, windowStart, windowEnd) {
        dataset.copy(
            glucose = dataset.glucose.copy(points = dataset.glucose.points.filter { it.timestampMillis in windowStart..windowEnd }),
            carbs = dataset.carbs.copy(points = dataset.carbs.points.filter { it.timestampMillis in windowStart..windowEnd }),
            insulin = dataset.insulin.copy(points = dataset.insulin.points.filter { it.timestampMillis in windowStart..windowEnd })
        )
    }
    val leftAxis = remember(visibleDataset) { computeGlucoseAxisBounds(visibleDataset.glucose.points) }
    val rightAxis = remember(visibleDataset) { computeEventAxisBounds(visibleDataset.carbs.points, visibleDataset.insulin.points) }
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.9f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val markerSurfaceColor = MaterialTheme.colorScheme.surface
    val latestVisibleTimestamps by rememberUpdatedState(visibleDataset.allTimestamps)
    val latestOnTimestampSelected by rememberUpdatedState(onTimestampSelected)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    findNearestTimestamp(
                        touchX = offset.x,
                        chartWidth = size.width.toFloat(),
                        timestamps = latestVisibleTimestamps
                    )?.let(latestOnTimestampSelected)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    findNearestTimestamp(
                        touchX = change.position.x,
                        chartWidth = size.width.toFloat(),
                        timestamps = latestVisibleTimestamps
                    )?.let(latestOnTimestampSelected)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
            val leftPadding = 76f
            val rightPadding = 76f
            val topPadding = 24f
            val bottomPadding = 48f
            val chartWidth = (size.width - leftPadding - rightPadding).coerceAtLeast(1f)
            val chartHeight = (size.height - topPadding - bottomPadding).coerceAtLeast(1f)
            val originX = leftPadding
            val originY = topPadding
            val bottomY = originY + chartHeight
            val labelPaint = Paint().apply {
                color = labelColor.toArgb()
                textSize = 28f
                isAntiAlias = true
            }

            fun xFor(timestamp: Long): Float {
                val ratio = ((timestamp - windowStart).toDouble() / (windowEnd - windowStart).coerceAtLeast(1L).toDouble()).toFloat()
                return originX + chartWidth * ratio.coerceIn(0f, 1f)
            }

            fun leftYFor(value: Double): Float {
                val ratio = ((value - leftAxis.min) / (leftAxis.max - leftAxis.min).coerceAtLeast(0.1)).toFloat()
                return bottomY - chartHeight * ratio.coerceIn(0f, 1f)
            }

            fun rightYFor(value: Double): Float {
                val ratio = ((value - rightAxis.min) / (rightAxis.max - rightAxis.min).coerceAtLeast(0.1)).toFloat()
                return bottomY - chartHeight * ratio.coerceIn(0f, 1f)
            }

            for (step in 0..4) {
                val y = originY + chartHeight * step / 4f
                drawLine(
                    color = gridColor,
                    start = Offset(originX, y),
                    end = Offset(originX + chartWidth, y),
                    strokeWidth = if (step == 4) 2f else 1f
                )
                val leftValue = leftAxis.max - (leftAxis.max - leftAxis.min) * step / 4.0
                val rightValue = rightAxis.max - (rightAxis.max - rightAxis.min) * step / 4.0
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(formatAxisValue(leftValue), 10f, y + 8f, labelPaint)
                    canvas.nativeCanvas.drawText(
                        formatAxisValue(rightValue),
                        originX + chartWidth + 14f,
                        y + 8f,
                        labelPaint
                    )
                }
            }

            drawLine(color = axisColor, start = Offset(originX, originY), end = Offset(originX, bottomY), strokeWidth = 2f)
            drawLine(color = axisColor, start = Offset(originX + chartWidth, originY), end = Offset(originX + chartWidth, bottomY), strokeWidth = 2f)
            drawLine(color = axisColor, start = Offset(originX, bottomY), end = Offset(originX + chartWidth, bottomY), strokeWidth = 2f)

            val xSteps = 4
            for (step in 0..xSteps) {
                val fraction = step / xSteps.toFloat()
                val x = originX + chartWidth * fraction
                val timestamp = interpolateMillis(windowStart, windowEnd, fraction)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(formatChartTime(timestamp), x - 30f, bottomY + 28f, labelPaint)
                }
            }

            visibleDataset.carbs.points.forEach { point ->
                val barX = xFor(point.timestampMillis)
                val top = rightYFor(point.value)
                drawRoundRect(
                    color = visibleDataset.carbs.color.copy(alpha = 0.84f),
                    topLeft = Offset(barX - 7f, top),
                    size = androidx.compose.ui.geometry.Size(14f, bottomY - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                )
            }

            drawSeriesLine(visibleDataset.glucose.points, visibleDataset.glucose.color, ::xFor, ::leftYFor)
            drawSeriesLine(visibleDataset.insulin.points, visibleDataset.insulin.color, ::xFor, ::rightYFor, strokeWidth = 4f)

            selectedTimestamp?.takeIf { it in windowStart..windowEnd }?.let { selected ->
                val selectedX = xFor(selected)
                drawLine(
                    color = selectionLineColor,
                    start = Offset(selectedX, originY),
                    end = Offset(selectedX, bottomY),
                    strokeWidth = 2f
                )
                visibleDataset.allSeries.forEach { series ->
                    series.points.find { it.timestampMillis == selected }?.let { point ->
                        val y = if (series.axis == 0) leftYFor(point.value) else rightYFor(point.value)
                        drawCircle(markerSurfaceColor, 9f, Offset(selectedX, y))
                        drawCircle(series.color, 6f, Offset(selectedX, y))
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeriesLine(
    points: List<MonitoringPoint>,
    color: Color,
    xMapper: (Long) -> Float,
    yMapper: (Double) -> Float,
    strokeWidth: Float = 6.5f
) {
    if (points.isEmpty()) return
    val chartPoints = points.map { Offset(xMapper(it.timestampMillis), yMapper(it.value)) }
    val path = Path().apply {
        moveTo(chartPoints.first().x, chartPoints.first().y)
        if (chartPoints.size == 2) {
            lineTo(chartPoints.last().x, chartPoints.last().y)
        } else {
            for (index in 1 until chartPoints.size) {
                val previous = chartPoints[index - 1]
                val current = chartPoints[index]
                val midPoint = Offset(
                    x = (previous.x + current.x) / 2f,
                    y = (previous.y + current.y) / 2f
                )
                quadraticTo(previous.x, previous.y, midPoint.x, midPoint.y)
            }
            lineTo(chartPoints.last().x, chartPoints.last().y)
        }
    }
    drawPath(
        path = path,
        color = color.copy(alpha = 0.16f),
        style = Stroke(width = strokeWidth + 6f, cap = StrokeCap.Round)
    )
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    )
}

@Composable
private fun MonitoringStatsGrid(stats: MonitoringStats) {
    val items = listOf(
        "Измерений" to stats.readingsCount.toString(),
        "Средняя глюкоза" to "${formatChartValue(stats.avgGlucose)} ммоль/л",
        "Минимум" to "${formatChartValue(stats.minGlucose)} ммоль/л",
        "Максимум" to "${formatChartValue(stats.maxGlucose)} ммоль/л",
        "Станд. отклонение" to formatChartValue(stats.stdDev, 2),
        "В диапазоне 3.9-10.0" to "${formatChartValue(stats.tir)}%",
        "Гипо (<3.9)" to stats.hypoCount.toString(),
        "Гипер (>10.0)" to stats.hyperCount.toString(),
        "Сумма углеводов" to formatChartValue(stats.totalCarbs),
        "Сумма инсулина" to formatChartValue(stats.totalInsulin)
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (label, value) ->
            MetricCard(
                title = label,
                value = value
            )
        }
    }
}

private fun ComprehensiveDataResponse.toMonitoringDataset(): MonitoringDataset {
    val glucoseSeries = MonitoringSeries(
        name = "Глюкоза",
        points = glucose.toMonitoringPoints(),
        color = Color(0xFF2F6FED),
        unit = "ммоль/л",
        axis = 0,
        kind = MonitoringSeriesKind.Line
    )
    val carbsSeries = MonitoringSeries(
        name = "Углеводы",
        points = carbs.toMonitoringPoints(),
        color = Color(0xFFDC4C64),
        unit = "г",
        axis = 1,
        kind = MonitoringSeriesKind.Bar
    )
    val insulinSeries = MonitoringSeries(
        name = "Инсулин",
        points = insulin.toMonitoringPoints(),
        color = Color(0xFFF3B4C1),
        unit = "ЕД",
        axis = 1,
        kind = MonitoringSeriesKind.Line
    )
    val allTimestamps = listOf(glucoseSeries, carbsSeries, insulinSeries)
        .flatMap { series -> series.points.map { it.timestampMillis } }
        .sorted()

    val defaultNow = System.currentTimeMillis()
    return MonitoringDataset(
        glucose = glucoseSeries,
        carbs = carbsSeries,
        insulin = insulinSeries,
        domainStart = allTimestamps.firstOrNull() ?: defaultNow,
        domainEnd = allTimestamps.lastOrNull() ?: defaultNow + 1
    )
}

private fun List<ChartPoint>.toMonitoringPoints(): List<MonitoringPoint> =
    mapNotNull { point ->
        parseTimestampMillis(point.x)?.let { timestamp ->
            MonitoringPoint(timestampMillis = timestamp, value = point.y)
        }
    }.sortedBy { it.timestampMillis }

private fun ComprehensiveDataResponse.toMonitoringStats(): MonitoringStats {
    val glucoseValues = glucose.map { it.y }
    val carbsValues = carbs.map { it.y }
    val insulinValues = insulin.map { it.y }
    if (glucoseValues.isEmpty()) {
        return MonitoringStats(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, carbsValues.sum(), insulinValues.sum())
    }
    val avg = glucoseValues.average()
    val variance = glucoseValues.sumOf { value -> (value - avg) * (value - avg) } / glucoseValues.size
    val inRange = glucoseValues.count { it in 3.9..10.0 }
    return MonitoringStats(
        readingsCount = glucoseValues.size,
        avgGlucose = avg,
        minGlucose = glucoseValues.minOrNull() ?: 0.0,
        maxGlucose = glucoseValues.maxOrNull() ?: 0.0,
        stdDev = sqrt(variance),
        tir = inRange * 100.0 / glucoseValues.size,
        hypoCount = glucoseValues.count { it < 3.9 },
        hyperCount = glucoseValues.count { it > 10.0 },
        totalCarbs = carbsValues.sum(),
        totalInsulin = insulinValues.sum()
    )
}

private fun computeGlucoseAxisBounds(points: List<MonitoringPoint>): AxisBounds {
    val values = points.map { it.value }
    if (values.isEmpty()) return AxisBounds(0.0, 10.0)
    val min = floor(((values.minOrNull() ?: 0.0) - 0.5) * 2) / 2
    val max = ceil(((values.maxOrNull() ?: 10.0) + 0.5) * 2) / 2
    return AxisBounds(min.coerceAtLeast(0.0), max.coerceAtLeast(min + 1.0))
}

private fun computeEventAxisBounds(carbs: List<MonitoringPoint>, insulin: List<MonitoringPoint>): AxisBounds {
    val max = (carbs + insulin).maxOfOrNull { it.value } ?: 10.0
    return AxisBounds(0.0, ceil(max + 1.0).coerceAtLeast(5.0))
}

private fun parseTimestampMillis(value: String): Long? =
    runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(value).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(value, serverSpaceTimestampFormatter).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()

private fun formatChartDateTime(timestampMillis: Long): String =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
        .format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()))

private fun formatChartTime(timestampMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()))

private fun formatDateInput(timestampMillis: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd")
        .format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()))

private fun formatTimeInput(timestampMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()))

private fun formatAxisValue(value: Double): String =
    if (abs(value) >= 10 || value % 1.0 == 0.0) value.roundToInt().toString() else formatChartValue(value)

private fun formatChartValue(value: Double, digits: Int = 1): String =
    String.format(Locale.US, "%.${digits}f", value)

private fun interpolateMillis(start: Long, end: Long, fraction: Float): Long =
    (start + ((end - start).coerceAtLeast(1L) * fraction.coerceIn(0f, 1f))).toLong()

private fun normalizeZoomRange(range: ClosedFloatingPointRange<Float>): ClosedFloatingPointRange<Float> {
    val start = range.start.coerceIn(0f, 1f)
    val end = range.endInclusive.coerceIn(0f, 1f)
    if (end - start < 0.04f) {
        val adjustedEnd = (start + 0.04f).coerceAtMost(1f)
        val adjustedStart = (adjustedEnd - 0.04f).coerceAtLeast(0f)
        return adjustedStart..adjustedEnd
    }
    return start..end
}

private fun parseUserDateTimeMillis(date: String, time: String): Long? =
    runCatching {
        LocalDateTime.parse("${date.trim()}T${time.trim()}:00")
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

private fun defaultLastDayZoomRange(
    domainStart: Long,
    domainEnd: Long
): ClosedFloatingPointRange<Float> {
    val lastDayStart = maxOf(domainStart, domainEnd - 24L * 60L * 60L * 1000L)
    return toZoomFractions(
        domainStart = domainStart,
        domainEnd = domainEnd,
        rangeStart = lastDayStart,
        rangeEnd = domainEnd
    )
}

private fun shiftWindowKeepingSpan(
    previous: ClosedFloatingPointRange<Float>,
    attempted: ClosedFloatingPointRange<Float>
): ClosedFloatingPointRange<Float> {
    val span = (previous.endInclusive - previous.start).coerceIn(0.04f, 1f)
    val startDelta = attempted.start - previous.start
    val endDelta = attempted.endInclusive - previous.endInclusive
    val delta = when {
        abs(startDelta) > abs(endDelta) -> startDelta
        abs(endDelta) > 0f -> endDelta
        else -> 0f
    }
    val nextStart = (previous.start + delta).coerceIn(0f, 1f - span)
    return normalizeZoomRange(nextStart..(nextStart + span))
}

private fun toZoomFractions(
    domainStart: Long,
    domainEnd: Long,
    rangeStart: Long,
    rangeEnd: Long
): ClosedFloatingPointRange<Float> {
    val domain = (domainEnd - domainStart).coerceAtLeast(1L).toFloat()
    val start = ((rangeStart - domainStart) / domain).coerceIn(0f, 1f)
    val end = ((rangeEnd - domainStart) / domain).coerceIn(0f, 1f)
    return normalizeZoomRange(start..end)
}

private fun pixelToTimestamp(
    touchX: Float,
    chartWidth: Float,
    windowStart: Long,
    windowEnd: Long
): Long {
    val ratio = (touchX / chartWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return interpolateMillis(windowStart, windowEnd, ratio)
}

private fun findNearestTimestamp(
    touchX: Float,
    chartWidth: Float,
    timestamps: List<Long>
): Long? {
    if (timestamps.isEmpty()) return null
    val min = timestamps.first()
    val max = timestamps.last()
    val target = pixelToTimestamp(touchX, chartWidth, min, max)
    return timestamps.minByOrNull { abs(it - target) }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    MyApplicationTheme {
        MyApplicationApp()
    }
}

interface AuthApi {
    @POST("api/auth/patient/login")
    suspend fun login(@Body request: AuthRequest): Response<TokenResponse>

    @GET("api/patients/me/glucose_data")
    suspend fun getMyGlucoseData(
        @Header("Authorization") authorization: String
    ): Response<GlucoseDataResponse>

    @GET("api/patients/me/comprehensive_data")
    suspend fun getMyComprehensiveData(
        @Header("Authorization") authorization: String
    ): Response<ComprehensiveDataResponse>

    @POST("api/patients/me/timeseries_data")
    suspend fun sendMyTimeseriesData(
        @Header("Authorization") authorization: String,
        @Body request: TimeseriesDataRequest
    ): Response<Unit>
}

object AuthRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun createApi(): AuthApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(fixedServerUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(AuthApi::class.java)
    }

    suspend fun login(login: String, password: String): Result<String> {
        return runCatching {
            val api = createApi()
            val response = api.login(AuthRequest(username = login, password = password))
            if (!response.isSuccessful) {
                throwApiException(response.code())
            }
            response.body()?.accessToken ?: error("Token missing in response")
        }
    }

    suspend fun validateToken(token: String): Result<Boolean> {
        return getMyGlucoseData(token).map { true }.recoverCatching { throwable ->
            if (throwable is UnauthorizedException) {
                false
            } else {
                throw throwable
            }
        }
    }

    suspend fun getMyGlucoseData(token: String): Result<GlucoseDataResponse> {
        return runCatching {
            val api = createApi()
            val response = api.getMyGlucoseData("Bearer ${token.trim()}")
            if (!response.isSuccessful) {
                throwApiException(response.code())
            }
            response.body() ?: error("Glucose data missing in response")
        }
    }

    suspend fun getMyComprehensiveData(token: String): Result<ComprehensiveDataResponse> {
        return runCatching {
            val api = createApi()
            val response = api.getMyComprehensiveData("Bearer ${token.trim()}")
            if (!response.isSuccessful) {
                throwApiException(response.code())
            }
            response.body() ?: ComprehensiveDataResponse(
                glucose = emptyList(),
                insulin = emptyList(),
                carbs = emptyList()
            )
        }
    }

    suspend fun sendMyTimeseriesData(
        token: String,
        points: List<TimeseriesDataPointRequest>
    ): Result<Unit> {
        return runCatching {
            val api = createApi()
            val response = api.sendMyTimeseriesData(
                authorization = "Bearer ${token.trim()}",
                request = TimeseriesDataRequest(dataPoints = points)
            )
            if (!response.isSuccessful) {
                throwApiException(response.code())
            }
        }
    }

    private fun throwApiException(code: Int): Nothing {
        if (code == 401) {
            throw UnauthorizedException()
        }
        error("Ошибка сервера: HTTP $code")
    }
}

private class UnauthorizedException : IllegalStateException("Сессия истекла. Войдите заново.")

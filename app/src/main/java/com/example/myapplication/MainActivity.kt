package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Log.DEBUG
import android.util.Log.ERROR
import android.util.Log.INFO
import android.util.Log.VERBOSE
import android.util.Log.WARN
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.myapplication.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
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
import java.security.KeyFactory
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

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

private fun currentTimestamp(): String = LocalDateTime.now().format(timestampFormatter)

private data class SavedCredentials(
    val serverIp: String,
    val login: String,
    val password: String
)

private object CredentialsStorage {
    private const val prefsName = "auth_prefs"
    private const val keyServerIp = "server_ip"
    private const val keyLogin = "login"
    private const val keyPassword = "password"

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

    fun load(prefs: SharedPreferences): SavedCredentials = SavedCredentials(
        serverIp = prefs.getString(keyServerIp, "").orEmpty(),
        login = prefs.getString(keyLogin, "").orEmpty(),
        password = prefs.getString(keyPassword, "").orEmpty()
    )

    fun save(prefs: SharedPreferences, serverIp: String, login: String, password: String) {
        prefs.edit()
            .putString(keyServerIp, serverIp)
            .putString(keyLogin, login)
            .putString(keyPassword, password)
            .apply()
    }
}


val cryptoManagerPC: CryptManager = CryptManager()
@Composable
fun MyApplicationApp() {
    val context = LocalContext.current
    val prefs = remember(context) { CredentialsStorage.prefs(context) }
    val savedCredentials = remember(prefs) { CredentialsStorage.load(prefs) }

    var authToken by rememberSaveable { mutableStateOf<String?>(null) }
    var serverIp by rememberSaveable { mutableStateOf(savedCredentials.serverIp) }
    var login by rememberSaveable { mutableStateOf(savedCredentials.login) }
    var password by rememberSaveable { mutableStateOf(savedCredentials.password) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(serverIp, login, password) {
        CredentialsStorage.save(
            prefs = prefs,
            serverIp = serverIp,
            login = login,
            password = password
        )
    }

    if (authToken == null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Авторизация",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = serverIp,
                    onValueChange = { serverIp = it },
                    label = { Text("IP сервера (например 10.0.2.2:8000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = {
                        if (serverIp.isBlank() || login.isBlank() || password.isBlank()) {
                            errorMessage = "Заполните все поля"
                            return@Button
                        }
                        cryptoManagerPC.generateAesKey()
                        val k = cryptoManagerPC.getAesKeyBase64()
                        val pwEncrypt = cryptoManagerPC.encryptWithAES(password)
                        val lgEncrypt = cryptoManagerPC.encryptWithAES(login)
                        Log.d("KEY","{$k}")
                        scope.launch {
                            isLoading = true
                            errorMessage = null
                            authToken = AuthRepository.login(
                                serverIp = serverIp,
                                login = login,
                                password = password,
                                key = k
                            ).onFailure {
                                errorMessage = it.message ?: "Ошибка авторизации"
                            }.getOrNull()
                            isLoading = false
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Войти")
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
        return
    }

    AuthenticatedRoot(
        serverIp = serverIp,
        login = login,
        token = authToken.orEmpty(),
        onLogout = { authToken = null }
    )
}


@Serializable
data class DataWrapper(
    val data_points:List<TimeseriesDataPointRequest>

)
@Composable
private fun AuthenticatedRoot(
    serverIp: String,
    login: String,
    token: String,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var currentSection by rememberSaveable { mutableStateOf(AppSection.Home) }

    var glucoseData by remember { mutableStateOf<GlucoseDataResponse?>(null) }
    var glucoseError by remember { mutableStateOf<String?>(null) }
    var isLoadingGlucose by remember { mutableStateOf(false) }

    val pendingPoints = remember { mutableStateListOf<TimeseriesDataPointRequest>() }
    var sendError by remember { mutableStateOf<String?>(null) }
    var sendSuccess by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    Log.d("aa","${pendingPoints.toList()}")
    var aboba = Json.encodeToString(pendingPoints.toList())
    Log.d("aa",aboba)


    var pendingPointsEncrypted = cryptoManagerPC.encryptWithAES(aboba)
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
                    serverIp = serverIp,
                    token = token,
                    points = aboba
                ).onSuccess {
                    sendSuccess = "Данные успешно отправлены"
                    pendingPoints.clear()
                }.onFailure {
                    sendError = it.message ?: "Ошибка отправки данных"
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (currentSection) {
                AppSection.Home -> HomeScreen(
                    login = login,
                    pendingCount = pendingPoints.size,
                    lastGlucoseCount = glucoseData?.data?.size ?: 0
                )

                AppSection.Charts -> ChartsScreen(
                    glucoseData = glucoseData,
                    glucoseError = glucoseError,
                    isLoadingGlucose = isLoadingGlucose,
                    onLoadGlucose = {
                        scope.launch {
                            isLoadingGlucose = true
                            glucoseError = null
                            glucoseData = AuthRepository.getMyGlucoseData(
                                serverIp = serverIp,
                                token = token
                            ).onFailure {
                                glucoseError = it.message ?: "Ошибка загрузки данных глюкозы"
                            }.getOrNull()
                            isLoadingGlucose = false
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
                    serverIp = serverIp,
                    login = login,
                    token = token,
                    onLogout = onLogout
                )
            }
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
                .height(72.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
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

                    Spacer(modifier = Modifier.width(86.dp))

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
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    containerColor = if (selectedSection == AppSection.Record) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    contentColor = if (selectedSection == AppSection.Record) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onTertiary
                    },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        focusedElevation = 0.dp,
                        hoveredElevation = 0.dp
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
            .width(56.dp)
            .padding(vertical = 4.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun HomeScreen(
    login: String,
    pendingCount: Int,
    lastGlucoseCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Главная",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Здравствуйте, $login")
                Text(text = "Проверьте IP и нажмите «Войти» при необходимости новой сессии.")
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Краткая сводка", color = MaterialTheme.colorScheme.primary)
                Text(text = "Записей ожидает отправки: $pendingCount")
                Text(text = "Последний загруженный график: $lastGlucoseCount точек")
            }
        }
    }
}

@Composable
private fun ChartsScreen(
    glucoseData: GlucoseDataResponse?,
    glucoseError: String?,
    isLoadingGlucose: Boolean,
    onLoadGlucose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Графики",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Button(
            onClick = onLoadGlucose,
            enabled = !isLoadingGlucose,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            if (isLoadingGlucose) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Загрузить данные глюкозы")
            }
        }

        if (glucoseError != null) {
            Text(text = glucoseError, color = MaterialTheme.colorScheme.error)
        }

        glucoseData?.let {
            GlucoseLineChart(glucoseData = it)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Запись",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = pointTimestamp,
            onValueChange = { pointTimestamp = it },
            label = { Text("Timestamp (YYYY-MM-DDTHH:MM:SS)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = "Тип записи", color = MaterialTheme.colorScheme.primary)

        options.forEach { option ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = pointRecordType == option.apiValue,
                    onClick = { pointRecordType = option.apiValue }
                )
                Text(text = option.label)
            }
        }
        if (selectedRecordType.requiresValue) {
            OutlinedTextField(
                value = pointValue,
                onValueChange = { pointValue = it },
                label = { Text("Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        OutlinedTextField(
            value = pointDetails,
            onValueChange = { pointDetails = it },
            label = { Text(selectedRecordType.detailsLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val numericValue = pointValue.toDoubleOrNull()
                if (pointTimestamp.isBlank()) return@Button
                if (selectedRecordType.requiresValue && numericValue == null) return@Button

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
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Добавить запись")
        }

        Button(
            onClick = onSend,
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            if (isSending) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("Отправить врачу")
            }
        }

        Text(text = "Записей в очереди: ${pendingPoints.size}")

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Дневник",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        OutlinedTextField(
            value = pointTimestamp,
            onValueChange = { pointTimestamp = it },
            label = { Text("Timestamp (YYYY-MM-DDTHH:MM:SS)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = pointDetails,
            onValueChange = { pointDetails = it },
            label = { Text("Текст записи дневника") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (pointTimestamp.isBlank() || pointDetails.isBlank()) return@Button
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
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Добавить запись в дневник")
        }

        Button(
            onClick = onSend,
            enabled = !isSending,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            if (isSending) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("Отправить врачу")
            }
        }

        Text(text = "Записей дневника в очереди: ${diaryItems.size}")

        diaryItems.forEachIndexed { index, item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${index + 1}. ${item.timestamp}: ${item.details.orEmpty()}",
                    modifier = Modifier.padding(12.dp)
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
    serverIp: String,
    login: String,
    token: String,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Профиль",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Логин: $login")
                Text(text = "Сервер: $serverIp")
                Text(text = "Токен: ${token.take(24)}...")
            }
        }

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Выйти")
        }
    }
}

@Composable
private fun GlucoseLineChart(
    glucoseData: GlucoseDataResponse,
    modifier: Modifier = Modifier
) {
    val values = glucoseData.data
    if (values.isEmpty()) {
        Text("Нет данных для графика")
        return
    }

    val labels = glucoseData.labels
    val minValue = values.minOrNull() ?: 0.0
    val maxValue = values.maxOrNull() ?: 0.0
    val range = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    val pixelBlue = Color(0xFF5E97F6)
    val pixelMint = Color(0xFF34A853)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
    val chartContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val selectedPointOuterColor = MaterialTheme.colorScheme.surface

    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember(values) { mutableIntStateOf(values.lastIndex.coerceAtLeast(0)) }

    val selectedValue = values[selectedIndex.coerceIn(values.indices)]
    val selectedLabel = labels.getOrNull(selectedIndex).orEmpty()

    val selectPointByX: (Float) -> Unit = { touchX ->
        selectedIndex = findNearestPointIndex(
            touchX = touchX,
            chartWidth = chartSize.width.toFloat(),
            leftPadding = 22f,
            rightPadding = 14f,
            pointsCount = values.size
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, axisColor),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Точка ${selectedIndex + 1} из ${values.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatChartValue(selectedValue)} mmol/L",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (selectedLabel.isNotBlank()) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(chartContainerColor)
                .onSizeChanged { chartSize = it }
                .pointerInput(values, chartSize) {
                    detectTapGestures { tapOffset ->
                        selectPointByX(tapOffset.x)
                    }
                }
                .pointerInput(values, chartSize) {
                    detectDragGestures(
                        onDragStart = { dragOffset -> selectPointByX(dragOffset.x) }
                    ) { change, _ ->
                        selectPointByX(change.position.x)
                    }
                }
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val graphWidth = (size.width - 22f - 14f).coerceAtLeast(1f)
                val graphHeight = (size.height - 14f - 20f).coerceAtLeast(1f)
                val startX = 22f
                val startY = 14f
                val stepX = if (values.size > 1) graphWidth / (values.size - 1) else 0f

                val linePath = Path()
                val points = values.mapIndexed { index, value ->
                    val x = startX + stepX * index
                    val normalized = ((value - minValue) / range).toFloat()
                    val y = startY + graphHeight * (1f - normalized)
                    Offset(x, y)
                }

                points.forEachIndexed { index, point ->
                    if (index == 0) {
                        linePath.moveTo(point.x, point.y)
                    } else {
                        linePath.lineTo(point.x, point.y)
                    }
                }

                val fillPath = Path().apply {
                    addPath(linePath)
                    if (points.isNotEmpty()) {
                        lineTo(points.last().x, startY + graphHeight)
                        lineTo(points.first().x, startY + graphHeight)
                        close()
                    }
                }

                for (step in 0..3) {
                    val y = startY + (graphHeight / 3f) * step
                    drawLine(
                        color = axisColor.copy(alpha = if (step == 3) 0.64f else 0.36f),
                        start = Offset(startX, y),
                        end = Offset(startX + graphWidth, y),
                        strokeWidth = if (step == 3) 2f else 1f
                    )
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            pixelBlue.copy(alpha = 0.28f),
                            pixelMint.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        startY = startY,
                        endY = startY + graphHeight
                    )
                )

                drawPath(
                    path = linePath,
                    color = pixelBlue.copy(alpha = 0.26f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
                drawPath(
                    path = linePath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(pixelBlue, pixelMint),
                        startX = startX,
                        endX = startX + graphWidth
                    ),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )

                val selectedPoint = points[selectedIndex.coerceIn(points.indices)]
                drawLine(
                    color = pixelBlue.copy(alpha = 0.42f),
                    start = Offset(selectedPoint.x, startY),
                    end = Offset(selectedPoint.x, startY + graphHeight),
                    strokeWidth = 2f
                )
                drawCircle(
                    color = selectedPointOuterColor,
                    radius = 8f,
                    center = selectedPoint
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(pixelBlue, pixelMint),
                        center = selectedPoint,
                        radius = 18f
                    ),
                    radius = 6f,
                    center = selectedPoint
                )
            }
        }

        Text(
            text = "Min: ${formatChartValue(minValue)}, Max: ${formatChartValue(maxValue)}, Points: ${values.size}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Коснитесь или проведите по графику для просмотра точки",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun findNearestPointIndex(
    touchX: Float,
    chartWidth: Float,
    leftPadding: Float,
    rightPadding: Float,
    pointsCount: Int
): Int {
    if (pointsCount <= 1 || chartWidth <= leftPadding + rightPadding) return 0
    val graphWidth = (chartWidth - leftPadding - rightPadding).coerceAtLeast(1f)
    val normalizedX = ((touchX - leftPadding) / graphWidth).coerceIn(0f, 1f)
    return (normalizedX * (pointsCount - 1)).roundToInt()
}

private fun formatChartValue(value: Double): String = String.format(Locale.US, "%.1f", value)

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

    @POST("api/patients/me/timeseries_data")
    suspend fun sendMyTimeseriesData(
        @Header("Authorization") authorization: String,
        @Body request: TimeseriesDataRequest
    ): Response<Unit>
}

object AuthRepository {
    private fun normalizeBaseUrl(serverInput: String): String {
        val trimmed = serverInput.trim()
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun createApi(serverIp: String): AuthApi {
        val endpoint = normalizeBaseUrl(serverIp)
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(endpoint)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return retrofit.create(AuthApi::class.java)
    }

    suspend fun login(serverIp: String, login: String, password: String, key: String): Result<String> {
        return runCatching {
            val api = createApi(serverIp)
            val response = api.login(AuthRequest(username = login, password = password, key = key))
            if (!response.isSuccessful) {
                error("Ошибка сервера: HTTP ${response.code()}")
            }
            response.body()?.accessToken ?: error("Token missing in response")
        }
    }

    suspend fun getMyGlucoseData(serverIp: String, token: String): Result<GlucoseDataResponse> {
        return runCatching {
            val api = createApi(serverIp)

            // Обернуть тут
            val response = api.getMyGlucoseData("Bearer ${token.trim()}")
            if (!response.isSuccessful) {
                error("Ошибка сервера: HTTP ${response.code()}")
            }
            response.body() ?: error("Glucose data missing in response")
        }
    }

    suspend fun sendMyTimeseriesData(
        serverIp: String,
        token: String,
        points: String
    ): Result<Unit> {
        return runCatching {
            val api = createApi(serverIp)

            // Обернуть тут
            val response = api.sendMyTimeseriesData(
                authorization = "Bearer ${token.trim()}",
                request = TimeseriesDataRequest(dataPoints = points)
            )
            if (!response.isSuccessful) {
                error("Ошибка сервера: HTTP ${response.code()}")
            }
        }
    }
}





class CryptManager {
    fun logCreator(
        message: String,
        tag: String = "CryptManager",
        level: Int = DEBUG,
    ) {
        when (level) {
            VERBOSE -> Log.v(tag, message)
            DEBUG -> Log.d(tag, message)
            INFO -> Log.i(tag, message)
            WARN -> Log.w(tag, message)
            ERROR -> Log.e(tag, message)
        }
    }

    private val rsaTransformation = "RSA/ECB/PKCS1Padding"
    private val aesTransformation = "AES/CBC/PKCS5Padding"
    private var sessionAesKey: SecretKey? = null

    fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // AESka 256aya
        val key = keyGen.generateKey()
        this.sessionAesKey = key
        return key
    }


    fun getPublicKeyFromPem(pem: String): PublicKey {
        // Чистим ключ от говна лишнего
        val cleanPem = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        val decoded = Base64.decode(cleanPem, Base64.DEFAULT)
        val spec = X509EncodedKeySpec(decoded)
        return KeyFactory.getInstance("RSA").generatePublic(spec)
    }


    // Тут кароче шифр для рсашки, которой мы отправим данные на сервак
    fun encryptWithRSA(data: String, publicKey: PublicKey): String {
        val cipher = Cipher.getInstance(rsaTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    // шифр аески нашей, которую мы записали туда
    fun encryptWithAES(data: String): String {
        val key = sessionAesKey ?: throw IllegalStateException("AES key not generated!")
        val cipher = Cipher.getInstance(aesTransformation)

        val ivBytes = ByteArray(16)
        SecureRandom().nextBytes(ivBytes)
        val iv = IvParameterSpec(ivBytes)

        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val ret_data = ivBytes+encryptedBytes

        return Base64.encodeToString(ret_data, Base64.NO_WRAP)
    }

    fun getAesKeyBase64(): String {
        return Base64.encodeToString(sessionAesKey?.encoded, Base64.NO_WRAP)
    }


    fun decryptWithAES(encryptedPackage: String): String {
        return try {
            val fullPackage = Base64.decode(encryptedPackage, Base64.DEFAULT)
            val iv = fullPackage.copyOfRange(0, 16)
            val ciphertext = fullPackage.copyOfRange(16, fullPackage.size)

            val keySpec = SecretKeySpec(sessionAesKey?.encoded, "AES")
            val ivSpec = IvParameterSpec(iv)


            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)


            val decryptedBytes = cipher.doFinal(ciphertext)

            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            logCreator("Ошибка дешифровки AES: ${e.message}", "CryptManager", ERROR)
            ""

        }

    }

}
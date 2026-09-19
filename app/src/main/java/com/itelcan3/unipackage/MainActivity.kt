package com.itelcan3.unipackage

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

object UniTheme {
    val DarkBackground = Color(0xFF080B08)
    val CardBackground = Color(0xFF111712)
    val PrimaryGreen = Color(0xFF00FF87)
    val SecondaryGreen = Color(0xFF60EF60)
    val DarkGreenAccent = Color(0xFF0B2915)
    val TextMain = Color(0xFFE6F5E9)
    val TextMuted = Color(0xFF8BA38F)
    val MonospaceText = Color(0xFF88D8B0)
    val AlertRed = Color(0xFFFF4D4D)
    val AlertRedBackground = Color(0xFF2B1111)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppsButtonsUnistall()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsButtonsUnistall() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var unusedApps by remember { mutableStateOf<List<LeastUsedApp>>(emptyList()) }
    var showDialogForApp by remember { mutableStateOf<LeastUsedApp?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    val dayOptions = listOf(7, 15, 30, 90, 180, 360)
    var selectedDays by remember { mutableIntStateOf(30) }
    var expanded by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasPermission, selectedDays) {
        if (hasPermission) {
            isScanning = true
            withContext(Dispatchers.IO) {
                try {
                    val reader = LeastUsedAppsReader(context)
                    val apps = reader.getLeastUsedApps(days = selectedDays)
                    withContext(Dispatchers.Main) {
                        unusedApps = apps
                        isScanning = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isScanning = false
                    }
                }
            }
        }
    }

    fun uninstallApp(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UNINSTALL", "Failed to uninstall $packageName", e)
        }
    }

    if (showDialogForApp != null) {
        AlertDialog(
            onDismissRequest = { showDialogForApp = null },
            containerColor = UniTheme.CardBackground,
            titleContentColor = UniTheme.TextMain,
            textContentColor = UniTheme.TextMuted,
            icon = {
                DeleteCanvasIcon(color = UniTheme.AlertRed, modifier = Modifier.size(24.dp))
            },
            title = {
                Text(
                    text = "Uninstall App",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to uninstall ${showDialogForApp?.appName}?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = UniTheme.AlertRed),
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        val packageName = showDialogForApp?.packageName
                        if (!packageName.isNullOrBlank()) {
                            uninstallApp(context, packageName)
                        }
                        showDialogForApp = null
                    }
                ) {
                    Text("Uninstall", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(UniTheme.TextMuted, UniTheme.TextMuted))
                    ),
                    shape = RoundedCornerShape(10.dp),
                    onClick = { showDialogForApp = null }
                ) {
                    Text("Cancel", color = UniTheme.TextMuted)
                }
            }
        )
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = UniTheme.DarkBackground
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
            ) {
                item {
                    HeaderSection()
                    Spacer(modifier = Modifier.height(24.dp))
                }

                item {
                    if (!hasPermission) {
                        PermissionCard {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Unused Apps",
                                color = UniTheme.TextMain,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Box {
                                Surface(
                                    color = UniTheme.DarkGreenAccent,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .border(1.dp, UniTheme.PrimaryGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .clickable { expanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Last $selectedDays days",
                                            color = UniTheme.PrimaryGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        ArrowDownCanvasIcon(color = UniTheme.PrimaryGreen, modifier = Modifier.size(12.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(UniTheme.CardBackground)
                                ) {
                                    dayOptions.forEach { days ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "$days days",
                                                    color = if (days == selectedDays) UniTheme.PrimaryGreen else UniTheme.TextMain
                                                )
                                            },
                                            onClick = {
                                                selectedDays = days
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (hasPermission) {
                    if (isScanning) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = UniTheme.PrimaryGreen)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Scanning for unused apps...", color = UniTheme.TextMuted, fontSize = 14.sp)
                                }
                            }
                        }
                    } else if (unusedApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No unused apps found (try using the device more).",
                                    color = UniTheme.TextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(unusedApps) { app ->
                            AppItemCard(
                                app = app,
                                onUninstallClick = { showDialogForApp = app }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(36.dp))
                    FooterCreditsSection()
                }
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "UniPackage",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.headlineLarge.copy(
                    brush = Brush.horizontalGradient(
                        colors = listOf(UniTheme.PrimaryGreen, UniTheme.SecondaryGreen)
                    )
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Unistaller for Android Packages",
            color = UniTheme.TextMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AppItemCard(
    app: LeastUsedApp,
    onUninstallClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UniTheme.PrimaryGreen.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = UniTheme.CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        color = UniTheme.TextMain,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = app.packageName,
                        color = UniTheme.MonospaceText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onUninstallClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UniTheme.AlertRedBackground,
                        contentColor = UniTheme.AlertRed
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, UniTheme.AlertRed.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    DeleteCanvasIcon(color = UniTheme.AlertRed, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Uninstall", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = Color.White.copy(alpha = 0.05f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageCanvasIcon(color = UniTheme.TextMuted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Size: ${formatFileSize(app.appSize)}",
                    color = UniTheme.TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun PermissionCard(onGrantClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, UniTheme.AlertRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = UniTheme.AlertRedBackground.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShieldCanvasIcon(color = UniTheme.AlertRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Grant Permission",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Usage stats permission is required to find unused apps.",
                color = UniTheme.TextMuted,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = UniTheme.AlertRed),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Grant Permission", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun FooterCreditsSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = UniTheme.DarkGreenAccent.copy(alpha = 0.6f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.border(1.dp, UniTheme.PrimaryGreen.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
        ) {
            Text(
                text = "By 85cs/Itelcan3 (aka. MasterSharp3210)",
                color = UniTheme.PrimaryGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun DeleteCanvasIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.width * 0.1f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.2f, size.height * 0.35f),
            size = Size(size.width * 0.6f, size.height * 0.6f),
            cornerRadius = CornerRadius(size.width * 0.08f),
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.1f, size.height * 0.25f),
            end = Offset(size.width * 0.9f, size.height * 0.25f),
            strokeWidth = strokeWidth
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.12f),
            end = Offset(size.width * 0.65f, size.height * 0.12f),
            strokeWidth = strokeWidth
        )
    }
}

@Composable
fun ShieldCanvasIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.05f)
            lineTo(size.width * 0.9f, size.height * 0.25f)
            lineTo(size.width * 0.9f, size.height * 0.6f)
            cubicTo(
                size.width * 0.9f, size.height * 0.85f,
                size.width * 0.5f, size.height * 0.98f,
                size.width * 0.5f, size.height * 0.98f
            )
            cubicTo(
                size.width * 0.5f, size.height * 0.98f,
                size.width * 0.1f, size.height * 0.85f,
                size.width * 0.1f, size.height * 0.6f
            )
            lineTo(size.width * 0.1f, size.height * 0.25f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = size.width * 0.1f))
    }
}

@Composable
fun ArrowDownCanvasIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.3f)
            lineTo(size.width * 0.5f, size.height * 0.7f)
            lineTo(size.width * 0.9f, size.height * 0.3f)
        }
        drawPath(path = path, color = color, style = Stroke(width = size.width * 0.15f))
    }
}

@Composable
fun StorageCanvasIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.width * 0.1f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.05f, size.height * 0.15f),
            size = Size(size.width * 0.9f, size.height * 0.3f),
            cornerRadius = CornerRadius(size.width * 0.08f),
            style = Stroke(width = strokeWidth)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.05f, size.height * 0.55f),
            size = Size(size.width * 0.9f, size.height * 0.3f),
            cornerRadius = CornerRadius(size.width * 0.08f),
            style = Stroke(width = strokeWidth)
        )
    }
}

fun checkUsageStatsPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", size / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

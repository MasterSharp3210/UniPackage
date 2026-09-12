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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.log10
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppsButtonsUnistall()
        }
    }
}

@Composable
fun AppsButtonsUnistall() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val backgroundColor = Color(0xFF0D0D0D)
    val descriptionColor = Color(0xFFAAAAAA)

    var hasPermission by remember { mutableStateOf(checkUsageStatsPermission(context)) }
    var unusedApps by remember { mutableStateOf<List<LeastUsedApp>>(emptyList()) }
    var showDialogForApp by remember { mutableStateOf<LeastUsedApp?>(null) }
    var isScanning by remember { mutableStateOf(false) }

    // Automatically check permission when the user returns to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = checkUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isScanning = true
            withContext(Dispatchers.IO) {
                try {
                    val reader = LeastUsedAppsReader(context)
                    val apps = reader.getLeastUsedApps()
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
            onDismissRequest = {
                showDialogForApp = null
            },
            title = {
                Text("Uninstall App")
            },
            text = {
                Text(
                    "Are you sure you want to uninstall ${showDialogForApp?.appName}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val packageName = showDialogForApp?.packageName

                        if (!packageName.isNullOrBlank()) {
                            uninstallApp(context, packageName)
                        }

                        showDialogForApp = null
                    }
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDialogForApp = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = "UniPackage",
                color = Color.Green,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Unistaller for Android Packages",
                color = descriptionColor,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(28.dp))

            if (!hasPermission) {
                AppButton(
                    title = "Grant Permission",
                    description = "Usage stats permission is required to find unused apps.",
                    color = Color.Red
                ) {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            } else {
                Text(
                    text = "Unused Apps",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isScanning) {
                    Text(text = "Scanning for unused apps...", color = descriptionColor)
                } else if (unusedApps.isEmpty()) {
                    Text(text = "No unused apps found (try using the device more).", color = descriptionColor)
                }

                unusedApps.forEach { app ->
                    AppButton(
                        title = app.appName,
                        description = "Size: ${formatFileSize(app.appSize)} | Pkg: ${app.packageName}",
                        color = Color.Blue
                    ) {
                        showDialogForApp = app
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Note: Some operations require elevated privileges, ADB, Shizuku, or root (superuser). " +
                        "By 85cs/Itelcan3 (aka. MasterSharp3210)",
                color = Color.Green,
                fontSize = 12.sp
            )
        }
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

@Composable
fun AppButton(
    title: String,
    description: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White
            )
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = description,
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}
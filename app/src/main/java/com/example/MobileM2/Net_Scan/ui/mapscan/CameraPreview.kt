package com.example.MobileM2.Net_Scan.ui.mapscan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation

/**
 * Aperçu de la caméra arrière, sans aucun enregistrement d'image. Temporaire : avec ARCore,
 * la session AR prendra la caméra et cet aperçu affichera son image.
 */
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        permanentlyDenied = !ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (granted) {
            CameraFeed(Modifier.fillMaxSize())
        } else {
            TertiaryButton(
                onClick = {
                    if (permanentlyDenied) openAppSettings(context) else launcher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.padding(Design.spaceExtraSmall),
            ) {
                Text(
                    text = stringResource(if (permanentlyDenied) R.string.camera_settings else R.string.camera_enable),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun CameraFeed(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var failed by remember { mutableStateOf(false) }
    val description = stringResource(R.string.camera_preview_description)

    LaunchedEffect(lifecycleOwner) {
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            try {
                awaitCancellation()
            } finally {
                provider.unbind(preview)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed = true
        }
    }

    val request = surfaceRequest
    when {
        failed -> Text(
            text = stringResource(R.string.camera_unavailable),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(Design.spaceSmall),
        )
        request != null -> CameraXViewfinder(
            surfaceRequest = request,
            modifier = modifier.semantics { contentDescription = description },
        )
    }
}

private fun hasCameraPermission(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

internal fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Rien à ouvrir sur cet appareil : le message à l'écran reste la seule indication.
    }
}

package com.example.MobileM2.Net_Scan.ui.mapscan

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.MobileM2.Net_Scan.R
import com.example.MobileM2.Net_Scan.data.position.ArCoreRenderer
import com.example.MobileM2.Net_Scan.data.position.ArCoreTracker
import com.example.MobileM2.Net_Scan.domain.PositionState
import com.example.MobileM2.Net_Scan.domain.PositioningUnavailable
import com.example.MobileM2.Net_Scan.ui.theme.Design
import com.example.MobileM2.Net_Scan.ui.theme.TertiaryButton

/**
 * Aperçu de ce que filme ARCore. Demande la caméra, installe ARCore au besoin et suit le cycle de
 * vie de l'écran. Aucune image n'est enregistrée : l'image ne sert qu'à l'aperçu et au suivi.
 */
@Composable
fun ArCorePreview(tracker: ArCoreTracker, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        permanentlyDenied = !ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
        if (ok) tracker.retry() else tracker.onCameraPermissionDenied()
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    // Au retour des réglages, la permission a pu changer.
    LifecycleResumeEffect(Unit) {
        val now = hasCameraPermission(context)
        if (now && !granted) tracker.retry()
        if (!now && asked) tracker.onCameraPermissionDenied()
        granted = now
        onPauseOrDispose { }
    }
    val state by tracker.state.collectAsStateWithLifecycle()

    Box(modifier, contentAlignment = Alignment.Center) {
        val unavailable = (state as? PositionState.Unavailable)?.reason
        when {
            !granted -> PreviewAction(
                label = stringResource(if (permanentlyDenied) R.string.camera_settings else R.string.camera_enable),
                onClick = { if (permanentlyDenied) openAppSettings(context) else launcher.launch(Manifest.permission.CAMERA) },
            )
            unavailable == PositioningUnavailable.UNSUPPORTED_DEVICE -> PreviewMessage(stringResource(R.string.arcore_unsupported_short))
            unavailable == PositioningUnavailable.ARCORE_NOT_INSTALLED ->
                PreviewAction(stringResource(R.string.arcore_install), onClick = tracker::approveInstall)
            unavailable != null -> PreviewAction(stringResource(R.string.retry), onClick = tracker::retry)
            else -> ArSurface(tracker, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ArSurface(tracker: ArCoreTracker, modifier: Modifier) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val description = stringResource(R.string.camera_preview_description)
    val view = remember(tracker) {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(ArCoreRenderer(tracker) { display?.rotation ?: Surface.ROTATION_0 })
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }
    LifecycleResumeEffect(view, tracker) {
        activity?.let(tracker::resume)
        view.onResume()
        onPauseOrDispose {
            // Le rendu s'arrête avant la session : plus aucune image n'est demandée à ARCore.
            view.onPause()
            tracker.pause()
        }
    }
    AndroidView(factory = { view }, modifier = modifier.semantics { contentDescription = description })
}

@Composable
private fun PreviewAction(label: String, onClick: () -> Unit) {
    TertiaryButton(onClick = onClick, modifier = Modifier.padding(Design.spaceExtraSmall)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PreviewMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(Design.spaceSmall),
    )
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

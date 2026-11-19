package com.example.MobileM2.Net_Scan.data.position

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.MobileM2.Net_Scan.domain.OccupancyRules
import com.example.MobileM2.Net_Scan.domain.PositionSource
import com.example.MobileM2.Net_Scan.domain.PositionState
import com.example.MobileM2.Net_Scan.domain.PositioningUnavailable
import com.example.MobileM2.Net_Scan.domain.SceneObservation
import com.example.MobileM2.Net_Scan.domain.ScenePoint
import com.example.MobileM2.Net_Scan.domain.TrackingProblem
import com.example.MobileM2.Net_Scan.domain.floorPoseOf
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.CameraIntrinsics
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.nio.ByteOrder
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.max
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Suivi ARCore en marche libre : ouvre la caméra, suit le téléphone hors ligne et publie sa
 * position vue de dessus, ainsi que des points de la scène (profondeur, ou à défaut nuage de
 * points) pour cartographier la pièce. Aucune image n'est conservée : chaque image ne sert qu'à
 * l'aperçu, au suivi et à ces points. Seul le suivi local est utilisé (ni Cloud Anchors, ni Geospatial).
 *
 * Fils d'exécution : [resume], [pause], [retry] et [close] sur le thread principal ;
 * [onSurfaceCreated], [onSurfaceChanged] et [update] sur le thread OpenGL de l'aperçu.
 */
class ArCoreTracker : PositionSource, AutoCloseable {
    private val logger = Logger.getLogger(ArCoreTracker::class.java.name)
    private val lock = Any()
    private val mutableState = MutableStateFlow<PositionState>(PositionState.Starting)
    private val mutableObservations = MutableSharedFlow<SceneObservation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var depthEnabled = false
    private var lastObservationNanos = 0L
    private var session: Session? = null
    private var resumed = false
    private var installRequested = false
    private var installApproved = false
    private var resumeWanted = false
    private val retryHandler = Handler(Looper.getMainLooper())
    private var hasTracked = false
    private var lastPublishedNanos = 0L
    private var cameraTexture = NO_TEXTURE
    private var textureApplied = false
    private var geometry: DisplayGeometry? = null
    private var geometryApplied = false

    val state: StateFlow<PositionState> = mutableState.asStateFlow()
    override val isSimulated = false
    override val position: Flow<PositionState> = state
    override val observations: Flow<SceneObservation> = mutableObservations.asSharedFlow()

    /**
     * Reprise de l'écran, caméra autorisée : démarre le suivi. Si ARCore manque, l'installation
     * n'est proposée que sur demande ([approveInstall]) : ouvrir une visite n'envoie pas sur le Play Store.
     */
    fun resume(activity: Activity) = synchronized(lock) {
        resumeWanted = true
        if (resumed || mutableState.value is PositionState.Unavailable) return@synchronized
        val current = session ?: createSession(activity) ?: return@synchronized
        try {
            current.resume()
            resumed = true
        } catch (error: CameraNotAvailableException) {
            logger.log(Level.WARNING, "Camera unavailable for ARCore", error)
            mutableState.value = PositionState.Unavailable(PositioningUnavailable.CAMERA_ERROR)
        } catch (error: SecurityException) {
            mutableState.value = PositionState.Unavailable(PositioningUnavailable.CAMERA_PERMISSION)
        }
    }

    fun pause() = synchronized(lock) {
        resumeWanted = false
        if (!resumed) return@synchronized
        session?.pause()
        resumed = false
    }

    /** Nouvel essai après une installation refusée, une caméra occupée ou une permission accordée. */
    fun retry() = synchronized(lock) {
        installRequested = false
        if (mutableState.value is PositionState.Unavailable) mutableState.value = startingOrLost()
    }

    /** La personne a demandé à installer ARCore : l'installation sera proposée à la prochaine reprise. */
    fun approveInstall() = synchronized(lock) {
        installApproved = true
        retry()
    }

    fun onCameraPermissionDenied() = synchronized(lock) {
        mutableState.value = PositionState.Unavailable(PositioningUnavailable.CAMERA_PERMISSION)
    }

    override fun close() = synchronized(lock) {
        retryHandler.removeCallbacksAndMessages(null)
        session?.close()
        session = null
        resumed = false
    }

    fun onSurfaceCreated(textureId: Int) = synchronized(lock) {
        cameraTexture = textureId
        textureApplied = false
    }

    fun onSurfaceChanged(rotation: Int, width: Int, height: Int) = synchronized(lock) {
        geometry = DisplayGeometry(rotation, width, height)
        geometryApplied = false
    }

    /** Avance d'une image caméra ; [draw] l'affiche avant que la position ne soit publiée. */
    fun update(draw: (Frame) -> Unit) = synchronized(lock) {
        val current = session ?: return@synchronized
        if (!resumed || cameraTexture == NO_TEXTURE) return@synchronized
        if (!textureApplied) {
            current.setCameraTextureName(cameraTexture)
            textureApplied = true
        }
        geometry?.takeUnless { geometryApplied }?.let {
            current.setDisplayGeometry(it.rotation, it.width, it.height)
            geometryApplied = true
        }
        val frame = try {
            current.update()
        } catch (error: CameraNotAvailableException) {
            logger.log(Level.WARNING, "Camera lost by ARCore", error)
            mutableState.value = PositionState.Unavailable(PositioningUnavailable.CAMERA_ERROR)
            return@synchronized
        } catch (_: SessionPausedException) {
            return@synchronized
        }
        draw(frame)
        publish(frame)
        observe(current, frame)
    }

    private var projectionLogged = false

    private fun createSession(activity: Activity): Session? {
        val unavailable = try {
            val availability = ArCoreApk.getInstance().checkAvailability(activity)
            when {
                // Réponse pas encore connue : on redemande un peu plus tard, tant que l'écran est affiché.
                availability.isTransient -> {
                    retryHandler.postDelayed({ if (resumeWanted) resume(activity) }, AVAILABILITY_RETRY_MILLIS)
                    return null
                }
                availability == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> throw UnavailableDeviceNotCompatibleException()
                availability != ArCoreApk.Availability.SUPPORTED_INSTALLED && !installApproved ->
                    throw UnavailableArcoreNotInstalledException()
            }
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return null
                }
                else -> return Session(activity).also {
                    it.configure(mappingConfig(it))
                    session = it
                    textureApplied = false
                    geometryApplied = false
                }
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            PositioningUnavailable.ARCORE_NOT_INSTALLED
        } catch (_: UnavailableArcoreNotInstalledException) {
            PositioningUnavailable.ARCORE_NOT_INSTALLED
        } catch (_: UnavailableApkTooOldException) {
            PositioningUnavailable.ARCORE_NOT_INSTALLED
        } catch (_: UnavailableDeviceNotCompatibleException) {
            PositioningUnavailable.UNSUPPORTED_DEVICE
        } catch (_: UnavailableSdkTooOldException) {
            PositioningUnavailable.UNSUPPORTED_DEVICE
        } catch (error: Exception) {
            logger.log(Level.WARNING, "Unable to start ARCore", error)
            PositioningUnavailable.ERROR
        }
        mutableState.value = PositionState.Unavailable(unavailable)
        return null
    }

    /**
     * Cartographie : profondeur quand le téléphone la gère (sinon nuage de points), plans horizontaux
     * pour trouver le sol. La lumière reste coupée pour la batterie. La mise au point reste fixe
     * (réglage par défaut d'ARCore) : l'autofocus modifie les paramètres de la caméra et dégrade le suivi.
     */
    private fun mappingConfig(session: Session) = Config(session).apply {
        depthEnabled = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        Log.i(LOG_TAG, "ARCore depth ${if (depthEnabled) "enabled" else "unsupported, using the point cloud"}")
        focusMode = Config.FocusMode.FIXED
        planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
        lightEstimationMode = Config.LightEstimationMode.DISABLED
        depthMode = if (depthEnabled) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
        cloudAnchorMode = Config.CloudAnchorMode.DISABLED
        geospatialMode = Config.GeospatialMode.DISABLED
        updateMode = Config.UpdateMode.BLOCKING
    }

    private fun publish(frame: Frame) {
        val camera = frame.camera
        when (camera.trackingState) {
            TrackingState.TRACKING -> {
                hasTracked = true
                if (frame.timestamp - lastPublishedNanos < PUBLISH_INTERVAL_NANOS) return
                lastPublishedNanos = frame.timestamp
                val pose = camera.displayOrientedPose
                val backward = pose.zAxis
                val forward = floatArrayOf(-backward[0], -backward[1], -backward[2])
                mutableState.value = PositionState.Tracking(floorPoseOf(pose.tx(), pose.tz(), forward, pose.yAxis))
            }
            TrackingState.PAUSED -> mutableState.value = camera.trackingFailureReason.toState()
            else -> mutableState.value = PositionState.Lost(TrackingProblem.UNKNOWN)
        }
    }

    /** Deux fois par seconde, et seulement si quelqu'un écoute (un enregistrement est en cours). */
    private fun observe(session: Session, frame: Frame) {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return
        if (mutableObservations.subscriptionCount.value == 0) return
        if (frame.timestamp - lastObservationNanos < OBSERVATION_INTERVAL_NANOS) return
        lastObservationNanos = frame.timestamp
        val pose = camera.pose
        val points = try {
            if (depthEnabled) depthPoints(frame, camera) else cloudPoints(frame)
        } catch (error: Exception) {
            logger.log(Level.FINE, "Scene points unavailable for this frame", error)
            return
        }
        if (points.isEmpty()) return
        mutableObservations.tryEmit(SceneObservation(ScenePoint(pose.tx(), pose.ty(), pose.tz()), points, floorHeight(session, pose.ty())))
    }

    /**
     * Profondeur ARCore (millimètres, environ 160 × 120) : un pixel sur quelques-uns est projeté en 3D
     * avec les paramètres de l'image (CPU ou texture) dont le cadrage est le sien (voir [intrinsicsForDepth]).
     */
    private fun depthPoints(frame: Frame, camera: Camera): List<ScenePoint> {
        val image = try {
            frame.acquireDepthImage16Bits()
        } catch (_: NotYetAvailableException) {
            return emptyList()
        }
        return image.use { depth ->
            val width = depth.width
            val height = depth.height
            val plane = depth.planes[0]
            val buffer = plane.buffer.order(ByteOrder.nativeOrder())
            val k = intrinsicsForDepth(listOf(camera.imageIntrinsics.toPinhole(), camera.textureIntrinsics.toPinhole()), width, height)
            if (!projectionLogged) {
                Log.i(LOG_TAG, "Depth ${width}x$height projected with fx=${k.fx} fy=${k.fy} cx=${k.cx} cy=${k.cy}")
                projectionLogged = true
            }
            val pose = FloatArray(16)
            camera.pose.toMatrix(pose, 0)
            val step = max(1, width / DEPTH_SAMPLES_PER_ROW)
            val points = ArrayList<ScenePoint>()
            for (v in 0 until height step step) {
                for (u in 0 until width step step) {
                    val millimeters = buffer.getShort(v * plane.rowStride + u * plane.pixelStride).toInt() and 0xFFFF
                    if (millimeters == 0) continue
                    val d = millimeters / 1000f
                    if (d < OccupancyRules.MIN_RANGE || d > OccupancyRules.MAX_RANGE) continue
                    points += unproject(u.toFloat(), v.toFloat(), d, k, pose)
                }
            }
            points
        }
    }

    private fun CameraIntrinsics.toPinhole(): PinholeIntrinsics {
        val dimensions = imageDimensions
        return PinholeIntrinsics(focalLength[0], focalLength[1], principalPoint[0], principalPoint[1], dimensions[0], dimensions[1])
    }

    /** À défaut de profondeur : les points reconnus par ARCore, déjà dans le repère du monde. */
    private fun cloudPoints(frame: Frame): List<ScenePoint> = frame.acquirePointCloud().use { cloud ->
        val buffer = cloud.points
        val points = ArrayList<ScenePoint>()
        while (buffer.remaining() >= 4) {
            val x = buffer.get()
            val y = buffer.get()
            val z = buffer.get()
            val confidence = buffer.get()
            if (confidence >= MIN_POINT_CONFIDENCE) points += ScenePoint(x, y, z)
        }
        points
    }

    /** Le plus bas des plans horizontaux assez grands et nettement sous la caméra : une table n'est pas le sol. */
    private fun floorHeight(session: Session, cameraY: Float): Float? =
        session.getAllTrackables(Plane::class.java)
            .asSequence()
            .filter { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
            .filter { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.extentX * it.extentZ >= MIN_FLOOR_AREA }
            .map { it.centerPose.ty() }
            .filter { it < cameraY - MIN_FLOOR_DROP }
            .minOrNull()

    private fun TrackingFailureReason?.toState(): PositionState = when (this) {
        TrackingFailureReason.EXCESSIVE_MOTION -> PositionState.Lost(TrackingProblem.EXCESSIVE_MOTION)
        TrackingFailureReason.INSUFFICIENT_LIGHT -> PositionState.Lost(TrackingProblem.INSUFFICIENT_LIGHT)
        TrackingFailureReason.INSUFFICIENT_FEATURES -> PositionState.Lost(TrackingProblem.INSUFFICIENT_FEATURES)
        TrackingFailureReason.CAMERA_UNAVAILABLE -> PositionState.Lost(TrackingProblem.CAMERA_UNAVAILABLE)
        else -> startingOrLost()
    }

    private fun startingOrLost(): PositionState =
        if (hasTracked) PositionState.Lost(TrackingProblem.UNKNOWN) else PositionState.Starting

    private data class DisplayGeometry(val rotation: Int, val width: Int, val height: Int)

    private companion object {
        const val NO_TEXTURE = -1

        /** 10 positions par seconde suffisent pour la carte. */
        const val PUBLISH_INTERVAL_NANOS = 100_000_000L

        /** Deux vues de la scène par seconde : la pièce se dessine sans charger le téléphone. */
        const val OBSERVATION_INTERVAL_NANOS = 500_000_000L
        const val AVAILABILITY_RETRY_MILLIS = 200L
        const val DEPTH_SAMPLES_PER_ROW = 40
        const val MIN_POINT_CONFIDENCE = 0.3f
        const val MIN_FLOOR_AREA = 0.5f
        const val MIN_FLOOR_DROP = 0.8f
        const val LOG_TAG = "NetScanMap"
    }
}

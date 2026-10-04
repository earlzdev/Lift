package dev.earlz.holocard.segmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Сегментация через ML Kit Subject Segmentation (модель живёт в Google Play Services). */
class MlKitSubjectSegmenter(context: Context) : SubjectSegmenter {

    private val client = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            // Отдельная маска на каждый объект, а не одна общая на весь передний план
            .enableMultipleSubjects(
                SubjectSegmenterOptions.SubjectResultOptions.Builder()
                    .enableConfidenceMask()
                    .build()
            )
            .build()
    )

    private val moduleInstall = ModuleInstall.getClient(context)

    override suspend fun segment(photo: Bitmap): List<Subject> {
        ensureModelInstalled()
        val result = client.process(InputImage.fromBitmap(photo, 0)).await()
        return result.subjects.mapNotNull { s ->
            val buffer = s.confidenceMask ?: return@mapNotNull null
            val mask = FloatArray(s.width * s.height)
            buffer.rewind()
            buffer.get(mask)
            Subject(
                bounds = Rect(s.startX, s.startY, s.startX + s.width, s.startY + s.height),
                mask = mask,
            )
        }
    }

    /**
     * Модель не входит в APK: её скачивают Play Services. Флаг в манифесте срабатывает
     * только при установке из Play Store, поэтому на всякий случай докачиваем вручную.
     */
    private suspend fun ensureModelInstalled() {
        if (moduleInstall.areModulesAvailable(client).await().areModulesAvailable()) return

        suspendCancellableCoroutine { cont ->
            lateinit var listener: InstallStatusListener
            fun finish(error: Exception?) {
                moduleInstall.unregisterListener(listener)
                if (!cont.isActive) return
                if (error == null) cont.resume(Unit) else cont.resumeWithException(error)
            }
            listener = InstallStatusListener { update ->
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> finish(null)
                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> finish(
                        IllegalStateException("Не удалось скачать модель ML Kit (код ${update.errorCode})")
                    )
                }
            }
            val request = ModuleInstallRequest.newBuilder()
                .addApi(client)
                .setListener(listener)
                .build()
            moduleInstall.installModules(request)
                .addOnSuccessListener { if (it.areModulesAlreadyInstalled()) finish(null) }
                .addOnFailureListener { finish(it) }
            cont.invokeOnCancellation { moduleInstall.unregisterListener(listener) }
        }
    }

    override fun close() = client.close()
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

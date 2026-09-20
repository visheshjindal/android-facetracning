package com.xim.facetracking.infrastructure.storage

import android.content.Context
import com.xim.facetracking.domain.ArtifactId
import com.xim.facetracking.domain.ArtifactStore
import com.xim.facetracking.domain.CaptureClock
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class TemporaryCaptureStore(context: Context, private val clock: CaptureClock, private val io: CoroutineDispatcher) : ArtifactStore {
    private val directory = context.applicationContext.cacheDir
    // File access is infrastructure-only. The domain sees opaque IDs.
    fun allocate(): Pair<ArtifactId, File> {
        val id = ArtifactId(UUID.randomUUID().toString())
        return id to resolve(id)
    }

    fun resolve(id: ArtifactId): File {
        require(id.value.matches(Regex("[a-zA-Z0-9-]+")))
        return File(directory, "facial-capture-${id.value}.mp4")
    }

    override suspend fun exists(id: ArtifactId): Boolean = withContext(io) { resolve(id).isFile }
    override suspend fun delete(id: ArtifactId) = withContext(io) {
        val file = resolve(id)
        check(!file.exists() || file.delete()) { "Unable to remove temporary capture" }
    }
    override suspend fun cleanupExpired() = withContext(io) {
        directory.listFiles()?.filter {
            it.isFile && it.name.startsWith("facial-capture-") && it.extension == "mp4" &&
                clock.wallTimeMs() - it.lastModified() > 86_400_000L
        }?.forEach { check(it.delete()) { "Unable to remove expired capture" } }
        Unit
    }
}

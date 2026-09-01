package com.rommapp.rommp

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

private const val COVER_CACHE_BYTES = 256L * 1024 * 1024

@HiltAndroidApp
class RomMPApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    /**
     * App-wide cover loader: shares the DI OkHttp client and keeps an explicit
     * disk cache (ignoring server cache headers) so covers survive offline.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components { add(SvgDecoder.Factory()) }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(COVER_CACHE_BYTES)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}

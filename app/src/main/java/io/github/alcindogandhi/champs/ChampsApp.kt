package io.github.alcindogandhi.champs

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.alcindogandhi.champs.data.EspnDataSource
import io.github.alcindogandhi.champs.data.FootballDataSource
import io.github.alcindogandhi.champs.data.db.ChampsDatabase
import io.github.alcindogandhi.champs.data.remote.EspnClient
import io.github.alcindogandhi.champs.data.settings.SettingsRepository
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class ChampsApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("crests"))
                    .maxSizeBytes(32L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}

/** Injeção de dependências manual: uma instância por processo, criada na [ChampsApp]. */
class AppContainer(context: Context) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Champs/${BuildConfig.VERSION_NAME} (Android; github.com/alcindogandhi/champs)")
                    .build(),
            )
        }
        .build()

    private val espnCacheDir = File(context.filesDir, "espn")

    val football: FootballDataSource = EspnDataSource(EspnClient(http, espnCacheDir))

    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
    )

    val database: ChampsDatabase = Room.databaseBuilder(context, ChampsDatabase::class.java, "champs.db").build()
}

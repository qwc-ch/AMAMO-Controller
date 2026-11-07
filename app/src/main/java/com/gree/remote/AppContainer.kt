package com.gree.remote

import android.content.Context
import com.gree.remote.data.cloud.CloudSigner
import com.gree.remote.data.cloud.GreeCloudApi
import com.gree.remote.data.cloud.GreeCloudClient
import com.gree.remote.data.lan.LanClientPool
import com.gree.remote.data.repo.AcRepository
import com.gree.remote.data.storage.PrefsStore
import com.gree.remote.data.storage.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/**
 * 手动依赖注入容器（小项目，不引入 Hilt）。在 [GreeRemoteApp] 中单例创建。
 */
class AppContainer(context: Context) {

    val prefs = PrefsStore(context)
    val secure = SecureStorage(context)

    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val okHttp: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val greeApi: GreeCloudApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://grih.gree.com/")
            .client(okHttp)
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(GreeCloudApi::class.java)
    }

    val cloudClient: GreeCloudClient by lazy {
        GreeCloudClient(
            api = greeApi,
            refreshLogin = {
                val user = secure.get(KEY_USER)
                val pwd = secure.get(KEY_PASS)
                requireNotNull(user) { "no saved user for re-login" }
                requireNotNull(pwd) { "no saved password for re-login" }
                cloudClient.login(user, pwd)
            }
        )
    }

    val lanPool: LanClientPool by lazy { LanClientPool(json, context) }

    val repository: AcRepository by lazy {
        AcRepository(lanPool = lanPool, cloudClient = cloudClient)
    }

    /** 保存云端账号凭据（Keystore 加密）。 */
    fun saveCredentials(user: String, password: String) {
        secure.put(KEY_USER, user)
        secure.put(KEY_PASS, password)
    }

    fun hasSavedUser(): Boolean = secure.get(KEY_USER) != null

    /** 登出：清除云端凭据。 */
    fun clearCredentials() {
        secure.remove(KEY_USER)
        secure.remove(KEY_PASS)
    }

    /** 动态取色（Material You）偏好，供设置页开关。 */
    val dynamicColor: Flow<Boolean> = prefs.dynamicColor

    suspend fun setDynamicColor(enabled: Boolean) {
        prefs.setDynamicColor(enabled)
    }

    companion object {
        private const val KEY_USER = "cloud_user"
        private const val KEY_PASS = "cloud_password"
    }
}

/** 便于 UI 层访问容器。 */
interface HasContainer {
    val container: AppContainer
}
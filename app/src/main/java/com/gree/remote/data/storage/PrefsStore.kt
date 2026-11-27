package com.gree.remote.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gree.remote.ui.theme.DisplayPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gree_prefs")

/**
 * DataStore 键值存储：token、设备缓存、通道偏好等非敏感信息。
 * 敏感字段（密码/长期 token）走 [SecureStorage]。
 */
class PrefsStore(private val context: Context) {

    private object Keys {
        val CHANNEL_PREF = stringPreferencesKey("channel_pref") // lan / cloud / auto
        val LAST_MAC = stringPreferencesKey("last_mac")
        val DEVICE_CACHE = stringPreferencesKey("device_cache")
        // 显示偏好（Shirone 风格全部选项）
        val DISPLAY_HUE = intPreferencesKey("display_hue")
        val DISPLAY_STYLE = stringPreferencesKey("display_style")
        val DISPLAY_SPEC_2025 = booleanPreferencesKey("display_spec_2025")
        val DISPLAY_WALLPAPER = stringPreferencesKey("display_wallpaper")
        val DISPLAY_LAYOUT = stringPreferencesKey("display_layout")
        val DISPLAY_TEXTURE = stringPreferencesKey("display_texture")
        val DISPLAY_TEXTURE_OPACITY = floatPreferencesKey("display_texture_opacity")
        val DISPLAY_REDUCE_MOTION = booleanPreferencesKey("display_reduce_motion")
        val DISPLAY_CUSTOM = booleanPreferencesKey("display_custom") // 是否使用自定义配色（关闭则系统动态色）
    }

    val channelPref: Flow<String> = context.dataStore.data.map { it[Keys.CHANNEL_PREF] ?: "auto" }

    suspend fun setChannelPref(channel: String) {
        context.dataStore.edit { it[Keys.CHANNEL_PREF] = channel }
    }

    val lastMac: Flow<String> = context.dataStore.data.map { it[Keys.LAST_MAC] ?: "" }

    suspend fun setLastMac(mac: String) {
        context.dataStore.edit { it[Keys.LAST_MAC] = mac }
    }

    suspend fun saveDeviceCache(json: String) {
        context.dataStore.edit { it[Keys.DEVICE_CACHE] = json }
    }

    val deviceCache: Flow<String> = context.dataStore.data.map { it[Keys.DEVICE_CACHE] ?: "" }

    // ---- 显示偏好（Shirone 风格） ----

    /** 是否使用用户自定义 HCT 配色；false 时跟随系统 Material You（API 31+）。 */
    val displayCustom: Flow<Boolean> = context.dataStore.data.map { it[Keys.DISPLAY_CUSTOM] ?: false }

    suspend fun setDisplayCustom(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISPLAY_CUSTOM] = enabled }
    }

    /** 全部显示偏好，缺失字段回落到 DisplayPrefs 默认值。 */
    val displayPrefs: Flow<DisplayPrefs> = context.dataStore.data.map { p ->
        DisplayPrefs(
            hue = (p[Keys.DISPLAY_HUE] ?: DisplayPrefs.HUE_DEFAULT.toInt()).toFloat(),
            colorStyle = p[Keys.DISPLAY_STYLE] ?: DisplayPrefs.STYLE_DEFAULT,
            spec2025 = p[Keys.DISPLAY_SPEC_2025] ?: DisplayPrefs.SPEC_DEFAULT_2025,
            wallpaperMode = p[Keys.DISPLAY_WALLPAPER] ?: DisplayPrefs.WALLPAPER_DEFAULT,
            layoutMode = p[Keys.DISPLAY_LAYOUT] ?: DisplayPrefs.LAYOUT_DEFAULT,
            texturePreset = p[Keys.DISPLAY_TEXTURE] ?: DisplayPrefs.TEXTURE_DEFAULT,
            textureOpacity = p[Keys.DISPLAY_TEXTURE_OPACITY] ?: DisplayPrefs.TEXTURE_OPACITY_DEFAULT,
            reduceMotion = p[Keys.DISPLAY_REDUCE_MOTION] ?: DisplayPrefs.REDUCE_MOTION_DEFAULT,
        )
    }

    suspend fun setDisplayHue(hue: Float) {
        context.dataStore.edit { it[Keys.DISPLAY_HUE] = hue.toInt() }
    }

    suspend fun setDisplayStyle(style: String) {
        context.dataStore.edit { it[Keys.DISPLAY_STYLE] = style }
    }

    suspend fun setDisplaySpec2025(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISPLAY_SPEC_2025] = enabled }
    }

    suspend fun setDisplayWallpaper(mode: String) {
        context.dataStore.edit { it[Keys.DISPLAY_WALLPAPER] = mode }
    }

    suspend fun setDisplayLayout(mode: String) {
        context.dataStore.edit { it[Keys.DISPLAY_LAYOUT] = mode }
    }

    suspend fun setDisplayTexture(preset: String) {
        context.dataStore.edit { it[Keys.DISPLAY_TEXTURE] = preset }
    }

    suspend fun setDisplayTextureOpacity(opacity: Float) {
        context.dataStore.edit { it[Keys.DISPLAY_TEXTURE_OPACITY] = opacity }
    }

    suspend fun setDisplayReduceMotion(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DISPLAY_REDUCE_MOTION] = enabled }
    }

    /** 一键重置全部显示偏好（含自定义开关）为默认。 */
    suspend fun resetDisplayPrefs() {
        context.dataStore.edit { p ->
            p[Keys.DISPLAY_CUSTOM] = false
            p[Keys.DISPLAY_HUE] = DisplayPrefs.HUE_DEFAULT.toInt()
            p[Keys.DISPLAY_STYLE] = DisplayPrefs.STYLE_DEFAULT
            p[Keys.DISPLAY_SPEC_2025] = DisplayPrefs.SPEC_DEFAULT_2025
            p[Keys.DISPLAY_WALLPAPER] = DisplayPrefs.WALLPAPER_DEFAULT
            p[Keys.DISPLAY_LAYOUT] = DisplayPrefs.LAYOUT_DEFAULT
            p[Keys.DISPLAY_TEXTURE] = DisplayPrefs.TEXTURE_DEFAULT
            p[Keys.DISPLAY_TEXTURE_OPACITY] = DisplayPrefs.TEXTURE_OPACITY_DEFAULT
            p[Keys.DISPLAY_REDUCE_MOTION] = DisplayPrefs.REDUCE_MOTION_DEFAULT
        }
    }
}
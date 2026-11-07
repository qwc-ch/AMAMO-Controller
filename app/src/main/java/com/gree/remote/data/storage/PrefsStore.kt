package com.gree.remote.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color") // Material You 取色开关
    }

    /** 动态取色开关（API 31+ 生效），默认开。 */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC_COLOR] ?: true }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
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
}
package io.github.mateuxlucax.caparao.ui

import android.content.SharedPreferences

/** In-memory SharedPreferences for JVM tests (android.jar only has stubs); listeners hear every change at once. */
class FakePreferences : SharedPreferences {
    private val values = HashMap<String, Any?>()
    private val listeners = LinkedHashSet<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun getAll(): Map<String, *> = values.toMap()
    override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?) = defValues
    override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
    override fun contains(key: String) = key in values
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    private fun set(key: String, value: Any?) {
        if (value == null) values.remove(key) else values[key] = value
        listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
    }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        override fun putString(key: String, value: String?) = apply { set(key, value) }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = apply { set(key, value) }
        override fun putLong(key: String, value: Long) = apply { set(key, value) }
        override fun putFloat(key: String, value: Float) = apply { set(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { set(key, value) }
        override fun remove(key: String) = apply { set(key, null) }
        override fun clear() = apply { values.keys.toList().forEach { set(it, null) } }
        override fun commit() = true
        override fun apply() = Unit
    }
}

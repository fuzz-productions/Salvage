package com.fuzz.android.salvage

import android.os.Bundle
import java.util.*

/**
 * Description: Provides some helper methods to the implementations in generated code.
 *
 * @author Andrew Grosner (fuzz)
 */
abstract class BaseBundlePersister<T> : BundlePersister<T> {

    companion object {

        private const val COUNT = ":count"

        private const val KEY = ":key"

        private const val VALUE = ":value"
    }

    fun persist(obj: T?, bundle: Bundle) = persist(obj, bundle, "")

    fun unpack(`object`: T?, bundle: Bundle) = unpack(`object`, bundle, "")

    override fun persist(obj: T?, bundle: Bundle, uniqueBaseKey: String) {
        throw IllegalAccessError("Tried to access $javaClass.persist() on an argument generated class.")
    }

    protected fun <T : Any> persistList(source: Iterable<T>?, bundle: Bundle, uniqueBaseKey: String,
                                        fieldKey: String, bundlePersister: BundlePersister<T>) {
        if (source != null) {
            var count = 0
            source.forEach {
                bundlePersister.persist(it, bundle, "$uniqueBaseKey$fieldKey$it")
                count++
            }
            bundle.putInt(getCountKey(fieldKey, uniqueBaseKey), count)
        }
    }

    protected fun <T : Any> restoreList(existingList: MutableList<T>?,
                                        bundle: Bundle, uniqueBaseKey: String,
                                        fieldKey: String, bundlePersister: BundlePersister<T>): List<T>? {
        val list = existingList ?: arrayListOf()
        list.clear()
        val count = bundle.getInt(getCountKey(fieldKey, uniqueBaseKey), 0)
        if (count > 0) {
            return (0..count - 1).mapNotNullTo(list, {
                bundlePersister.unpack(null, bundle, "$uniqueBaseKey$fieldKey$it")
            })
        }
        return null
    }

    protected fun <K : Any, V : Any> persistMap(
            map: Map<K, V?>?, bundle: Bundle,
            uniqueBaseKey: String, fieldKey: String,
            keyBundlePersister: BundlePersister<K>,
            variableBundlePersister: BundlePersister<V>): Map<K, V?>? {
        if (map != null) {
            val count = map.size
            bundle.putInt(getCountKey(fieldKey, uniqueBaseKey), count)
            val entries = ArrayList(map.entries)
            (0..count - 1).forEach {
                val (key, value) = entries[it]
                keyBundlePersister.persist(key, bundle, getMapKey(fieldKey, it, uniqueBaseKey))
                variableBundlePersister
                        .persist(value, bundle, getMapValueKey(fieldKey, it, uniqueBaseKey))
            }
        }
        return map
    }

    protected fun <K : Any, V : Any> restoreMap(
            existingMap: MutableMap<K, V?>?,
            bundle: Bundle, uniqueBaseKey: String,
            fieldKey: String,
            keyBundlePersister: BundlePersister<K>,
            variableBundlePersister: BundlePersister<V>): Map<K, V?>? {
        val map = existingMap ?: mutableMapOf()
        map.clear()
        val count = bundle.getInt(getCountKey(fieldKey, uniqueBaseKey), 0)
        if (count > 0) {
            (0..count - 1).forEach {
                val key = keyBundlePersister
                        .unpack(null, bundle, getMapKey(fieldKey, it, uniqueBaseKey))
                if (key != null) {
                    val value = variableBundlePersister
                            .unpack(null, bundle, getMapValueKey(fieldKey, it, uniqueBaseKey))
                    map.put(key, value)
                }
            }
            return map
        }
        return null
    }

    private fun getCountKey(fieldKey: String, uniqueBaseKey: String) =
            "$uniqueBaseKey$fieldKey$COUNT"

    private fun getMapValueKey(fieldKey: String, it: Int, uniqueBaseKey: String) =
            "$uniqueBaseKey$fieldKey$it$VALUE"

    private fun getMapKey(fieldKey: String, it: Int, uniqueBaseKey: String) =
            "$uniqueBaseKey$fieldKey$it$KEY"
}
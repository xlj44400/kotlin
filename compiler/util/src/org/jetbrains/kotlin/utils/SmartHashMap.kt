/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.utils

import java.util.IdentityHashMap

const val ARRAY_UNTIL_SIZE = 10

/**
 * [SmartHashMap] is a Map implementation that uses reference identity for keys.
 * It uses 2 arrays to store keys & values until the number of entries stored is larger than 10.
 * At that point it switches to using an IdentityHashMap and stays that way until [clear] is called.
 * It does not auto convert back if the number of entries decreases through [remove].
 *
 * The implementation of [SmartHashMap] is not synchronized.
 */
class SmartHashMap<K, V>() : MutableMap<K, V> {

    private var keysArray: MutableList<K>? = ArrayList<K>(ARRAY_UNTIL_SIZE)
    private var valuesArray: MutableList<V>? = ArrayList<V>(ARRAY_UNTIL_SIZE)
    private var largeMap: IdentityHashMap<K, V>? = null

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() {
            return keysArray?.let {
                return it.zip(valuesArray!!) { a, b -> Entry(a, b) }.toMutableSet()
            } ?: largeMap!!.entries
        }

    override val keys: MutableSet<K>
        get() = keysArray?.toMutableSet() ?: largeMap!!.keys

    override val size: Int
        get() = keysArray?.size ?: largeMap!!.size

    override val values: MutableCollection<V>
        get() = valuesArray?.toMutableList() ?: largeMap!!.values

    override fun clear() {
        largeMap = null
        keysArray = ArrayList<K>(ARRAY_UNTIL_SIZE)
        valuesArray = ArrayList<V>(ARRAY_UNTIL_SIZE)
    }

    override fun containsKey(key: K): Boolean {
        return keysArray?.let {
            // use any instead of contains to ensure reference identity check
            return it.any { it === key }
        } ?: largeMap!!.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        return valuesArray?.let {
            // use any instead of contains to ensure reference identity check
            return it.any { it === value }
        } ?: largeMap!!.containsValue(value)
    }

    override fun get(key: K): V? {
        return keysArray?.let {
            for ((index, k) in it.withIndex()) {
                if (k === key) {
                    return valuesArray!![index]
                }
            }
            return null
        } ?: largeMap!![key]
    }

    override fun isEmpty(): Boolean = keysArray?.isEmpty() ?: false

    override fun put(key: K, value: V): V? {
        val ka = keysArray
        if (ka != null) {
            val va = valuesArray!!
            // scan for existing keys in array
            for (i in 0 until ka.size) {
                if (ka[i] === key) {
                    val tmp = va[i]
                    va[i] = value
                    return tmp
                }
            }
            // if a new key, and array has room
            if (ka.size < ARRAY_UNTIL_SIZE) {
                ka.add(key)
                va.add(value)
                return null
            }
            convertToHashMap()
        }
        // all other cases, fallback to regular hashmap implementation
        return largeMap!!.put(key, value)
    }

    override fun putAll(from: Map<out K, V>) {
        val ka = keysArray
        if (ka != null) {
            // if the array still has enough room, add all entries
            if (ka.size + from.size < ARRAY_UNTIL_SIZE) {
                for (entry in from) {
                    put(entry.key, entry.value)
                }
                return
            }
            // otherwise convert to hashmap before adding entries
            convertToHashMap()
        }
        largeMap!!.putAll(from)
    }

    override fun remove(key: K): V? {
        val ka = keysArray
        if (ka != null) {
            val va = valuesArray!!
            for (i in 0 until ka.size) {
                if (ka[i] === key) {
                    val tmp = va[i]
                    ka.removeAt(i)
                    va.removeAt(i)
                    return tmp
                }
            }
            return null
        }
        return largeMap!!.remove(key)
    }

    private fun convertToHashMap() {
        val map = IdentityHashMap<K, V>()
        val ka = keysArray!!
        val va = valuesArray!!
        for (i in 0 until ka.size) {
            map.put(ka[i], va[i])
        }
        largeMap = map
        keysArray = null
        valuesArray = null
    }

    private class Entry<K, V>(override val key: K, override val value: V) : MutableMap.MutableEntry<K, V> {
        override fun setValue(newValue: V): V = throw UnsupportedOperationException("This Entry is not mutable.")
    }
}

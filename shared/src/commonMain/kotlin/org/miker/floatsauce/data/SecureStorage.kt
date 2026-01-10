package org.miker.floatsauce.data

interface SecureStorage {
    fun get(key: String): String?
    fun set(key: String, value: String?)
}

package org.miker.floatsauce.data

import platform.Foundation.*
import platform.Security.*
import platform.CoreFoundation.*
import kotlinx.cinterop.*

@OptIn(ExperimentalForeignApi::class)
class AppleSecureStorage : SecureStorage {
    override fun get(key: String): String? {
        val query = CFDictionaryCreateMutable(null, 4, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(key))
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val result = memScoped {
            val resultPtr = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, resultPtr.ptr)
            if (status == errSecSuccess) {
                val data = CFBridgingRelease(resultPtr.value) as? NSData
                data?.let { NSString.create(it, NSUTF8StringEncoding).toString() }
            } else {
                null
            }
        }
        return result
    }

    override fun set(key: String, value: String?) {
        val query = CFDictionaryCreateMutable(null, 2, null, null)
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrAccount, CFBridgingRetain(key))

        if (value == null) {
            SecItemDelete(query)
            return
        }

        val nsValue = value as NSString
        val data = CFBridgingRetain(nsValue.dataUsingEncoding(NSUTF8StringEncoding))
        val status = SecItemCopyMatching(query, null)
        if (status == errSecSuccess) {
            val update = CFDictionaryCreateMutable(null, 1, null, null)
            CFDictionaryAddValue(update, kSecValueData, data)
            SecItemUpdate(query, update)
        } else {
            CFDictionaryAddValue(query, kSecValueData, data)
            SecItemAdd(query, null)
        }
    }
}

// Extension to help with conversion if needed, but the above is a bit simplified.
// In actual Kotlin/Native, we might need more careful handling of CFDictionary.
// Let's refine the toCFDictionary implementation.

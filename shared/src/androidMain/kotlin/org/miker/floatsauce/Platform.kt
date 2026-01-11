package org.miker.floatsauce

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "AndroidTV ${Build.VERSION.SDK_INT}"
    override val version: String = Version.NUMBER
    override val userAgent: String = "FloatSauce $version $name CFNetwork"
}

actual fun getPlatform(): Platform = AndroidPlatform()

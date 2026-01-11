package org.miker.floatsauce

import android.os.Build
import android.content.res.Resources

class AndroidPlatform : Platform {
    override val name: String = "AndroidTV ${Build.VERSION.SDK_INT}"
    override val version: String = Version.NUMBER
    override val userAgent: String = "FloatSauce $version $name CFNetwork"
    override val screenWidth: Int = Resources.getSystem().displayMetrics.widthPixels
    override val screenHeight: Int = Resources.getSystem().displayMetrics.heightPixels
}

actual fun getPlatform(): Platform = AndroidPlatform()

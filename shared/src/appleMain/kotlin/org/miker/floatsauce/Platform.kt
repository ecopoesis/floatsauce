package org.miker.floatsauce

import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents

class ApplePlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val version: String = Version.NUMBER
    override val userAgent: String = "FloatSauce $version $name CFNetwork"
    
    @OptIn(ExperimentalForeignApi::class)
    override val screenWidth: Int = UIScreen.mainScreen.nativeBounds.useContents { size.width.toInt() }
    
    @OptIn(ExperimentalForeignApi::class)
    override val screenHeight: Int = UIScreen.mainScreen.nativeBounds.useContents { size.height.toInt() }
}

actual fun getPlatform(): Platform = ApplePlatform()

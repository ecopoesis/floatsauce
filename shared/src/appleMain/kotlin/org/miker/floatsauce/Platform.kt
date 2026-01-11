package org.miker.floatsauce

import platform.UIKit.UIDevice

class ApplePlatform : Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
    override val version: String = Version.NUMBER
    override val userAgent: String = "FloatSauce $version $name CFNetwork"
}

actual fun getPlatform(): Platform = ApplePlatform()

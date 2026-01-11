package org.miker.floatsauce

interface Platform {
    val name: String
    val version: String
    val userAgent: String
    val screenWidth: Int
    val screenHeight: Int
}

expect fun getPlatform(): Platform

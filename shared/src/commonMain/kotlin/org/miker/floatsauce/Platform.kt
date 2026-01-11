package org.miker.floatsauce

interface Platform {
    val name: String
    val version: String
    val userAgent: String
}

expect fun getPlatform(): Platform

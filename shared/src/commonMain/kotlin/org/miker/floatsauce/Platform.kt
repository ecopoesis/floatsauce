package org.miker.floatsauce

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
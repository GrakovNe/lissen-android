package org.grakovne.lissen.channel.common

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthContextCache @Inject constructor() {

    private var pkce: Pkce = clearPkce()
    private var cookies: String = clearCookies()

    fun storePkce(pkce: Pkce) {
        this.pkce = pkce
    }

    fun readPkce() = pkce

    fun clearPkce(): Pkce {
        pkce = Pkce("", "", "")
        return pkce
    }

    fun storeCookies(cookies: List<String>) {
        this.cookies = cookies.joinToString("; ") { it.substringBefore(";") }
    }

    fun readCookies() = cookies

    fun clearCookies(): String {
        cookies = ""
        return cookies
    }
}

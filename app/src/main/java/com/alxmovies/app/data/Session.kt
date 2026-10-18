package com.alxmovies.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Guarda login, perfil escolhido e sessão de convidado no aparelho. */
class Session(ctx: Context) {
    private val sp = ctx.getSharedPreferences("alx_session", Context.MODE_PRIVATE)

    private val _loggedIn = MutableStateFlow(
        sp.getString("at", null) != null && sp.getString("uid", null) != null
    )
    val loggedInFlow: StateFlow<Boolean> = _loggedIn.asStateFlow()
    val loggedIn: Boolean get() = _loggedIn.value

    var accessToken: String?
        get() = sp.getString("at", null)
        set(v) { sp.edit().putString("at", v).apply() }
    var refreshToken: String?
        get() = sp.getString("rt", null)
        set(v) { sp.edit().putString("rt", v).apply() }
    /** Em segundos (epoch). */
    var expiresAt: Long
        get() = sp.getLong("exp", 0L)
        set(v) { sp.edit().putLong("exp", v).apply() }
    var userId: String?
        get() = sp.getString("uid", null)
        set(v) { sp.edit().putString("uid", v).apply() }
    var email: String?
        get() = sp.getString("email", null)
        set(v) { sp.edit().putString("email", v).apply() }
    var isAnonymous: Boolean
        get() = sp.getBoolean("anon", false)
        set(v) { sp.edit().putBoolean("anon", v).apply() }
    var guestStartedAt: Long
        get() = sp.getLong("guest_at", 0L)
        set(v) { sp.edit().putLong("guest_at", v).apply() }
    var profileId: String?
        get() = sp.getString("pid", null)
        set(v) { sp.edit().putString("pid", v).apply() }
    var tzOffsetMin: Int
        get() = sp.getInt("tz_off", Int.MIN_VALUE)
        set(v) { sp.edit().putInt("tz_off", v).apply() }
    var tzAt: Long
        get() = sp.getLong("tz_at", 0L)
        set(v) { sp.edit().putLong("tz_at", v).apply() }

    /** Preferências simples do app (retomar de onde parou, último episódio, velocidade do player). */
    fun pref(key: String): String? = sp.getString("p_$key", null)
    fun setPref(key: String, value: String) { sp.edit().putString("p_$key", value).apply() }
    fun delPref(key: String) { sp.edit().remove("p_$key").apply() }

    fun markLoggedIn() { _loggedIn.value = accessToken != null && userId != null }

    fun clearAuth() {
        sp.edit()
            .remove("at").remove("rt").remove("exp").remove("uid").remove("email")
            .remove("anon").remove("guest_at").remove("pid")
            .apply()
        _loggedIn.value = false
    }
}

object Graph {
    lateinit var session: Session
        private set
    lateinit var appContext: Context
        private set

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        session = Session(appContext)
    }
}

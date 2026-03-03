package com.fersaiyan.cyanbridge.auth

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户数据管理器 — SharedPreferences 实现。
 *
 * 本地模拟登录/注册，无真实后端。验证码统一为 "888888"。
 */
class UserRepository private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "cyan_user"
        private const val KEY_LOGGED_IN = "logged_in"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_PHONE = "phone"
        private const val KEY_EMAIL = "email"
        private const val KEY_NICKNAME = "nickname"
        private const val KEY_LOGIN_METHOD = "login_method"
        private const val KEY_MEMBER_TIER = "member_tier"
        private const val KEY_MEMBER_EXPIRY = "member_expiry"

        /** 模拟验证码，所有用户统一 */
        const val MOCK_VERIFICATION_CODE = "888888"

        @Volatile private var instance: UserRepository? = null

        fun getInstance(context: Context): UserRepository {
            return instance
                    ?: synchronized(this) {
                        instance
                                ?: UserRepository(context.applicationContext).also { instance = it }
                    }
        }
    }

    private val prefs: SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    val isLoggedIn: Boolean
        get() = _currentUser.value != null

    init {
        if (prefs.getBoolean(KEY_LOGGED_IN, false)) {
            _currentUser.value = loadUserFromPrefs()
        }
    }

    // ── Login / Register ──

    /**
     * 发送验证码（模拟）。
     * @return 始终返回 true
     */
    fun sendVerificationCode(
            @Suppress("UNUSED_PARAMETER") target: String,
            @Suppress("UNUSED_PARAMETER") method: LoginMethod
    ): Boolean {
        return true
    }

    /** 验证并登录。验证码统一为 888888。 */
    fun verifyAndLogin(target: String, code: String, method: LoginMethod): User? {
        if (code != MOCK_VERIFICATION_CODE) return null

        val user =
                User(
                        id = UUID.randomUUID().toString().take(8),
                        phone = if (method == LoginMethod.PHONE) target else null,
                        email = if (method == LoginMethod.EMAIL) target else null,
                        nickname =
                                when (method) {
                                    LoginMethod.PHONE -> "用户${target.takeLast(4)}"
                                    LoginMethod.EMAIL -> target.substringBefore('@')
                                    LoginMethod.WECHAT -> "微信用户"
                                },
                        loginMethod = method,
                        memberTier = MemberTier.FREE,
                )

        saveUser(user)
        return user
    }

    /** 微信登录（模拟）。 */
    fun loginWithWeChat(): User {
        val user =
                User(
                        id = UUID.randomUUID().toString().take(8),
                        nickname = "微信用户",
                        loginMethod = LoginMethod.WECHAT,
                        memberTier = MemberTier.FREE,
                )
        saveUser(user)
        return user
    }

    // ── Membership ──

    /**
     * 模拟购买会员。
     * @return true if purchase is successful (always true for mock)
     */
    fun purchaseMembership(tier: MemberTier): Boolean {
        val user = _currentUser.value ?: return false
        val updated =
                user.copy(
                        memberTier = tier,
                        memberExpiry =
                                if (tier == MemberTier.FREE) 0L
                                else
                                        System.currentTimeMillis() +
                                                30L * 24 * 60 * 60 * 1000, // 30 days
                )
        saveUser(updated)
        return true
    }

    // ── Profile ──

    fun updateNickname(nickname: String) {
        val user = _currentUser.value ?: return
        saveUser(user.copy(nickname = nickname))
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null
    }

    // ── Internal ──

    private fun saveUser(user: User) {
        _currentUser.value = user
        prefs.edit().apply {
            putBoolean(KEY_LOGGED_IN, true)
            putString(KEY_USER_ID, user.id)
            putString(KEY_PHONE, user.phone)
            putString(KEY_EMAIL, user.email)
            putString(KEY_NICKNAME, user.nickname)
            putString(KEY_LOGIN_METHOD, user.loginMethod.name)
            putString(KEY_MEMBER_TIER, user.memberTier.name)
            putLong(KEY_MEMBER_EXPIRY, user.memberExpiry)
            apply()
        }
    }

    private fun loadUserFromPrefs(): User? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        return User(
                id = id,
                phone = prefs.getString(KEY_PHONE, null),
                email = prefs.getString(KEY_EMAIL, null),
                nickname = prefs.getString(KEY_NICKNAME, "用户") ?: "用户",
                loginMethod =
                        try {
                            LoginMethod.valueOf(
                                    prefs.getString(KEY_LOGIN_METHOD, "PHONE") ?: "PHONE"
                            )
                        } catch (_: Exception) {
                            LoginMethod.PHONE
                        },
                memberTier =
                        try {
                            MemberTier.valueOf(prefs.getString(KEY_MEMBER_TIER, "FREE") ?: "FREE")
                        } catch (_: Exception) {
                            MemberTier.FREE
                        },
                memberExpiry = prefs.getLong(KEY_MEMBER_EXPIRY, 0L),
        )
    }
}

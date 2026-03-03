package com.fersaiyan.cyanbridge.auth

/**
 * 用户状态数据模型。
 *
 * 会员等级说明（来自需求.md）：
 * - 普通会员：可以免费使用找物体、识字等功能
 * - 高级会员：+ 人脸识别 + 拍照/录像/录音
 * - 钻石会员：全部功能 + 所有付费功能
 */
data class User(
        val id: String,
        val phone: String? = null,
        val email: String? = null,
        val nickname: String = "用户",
        val avatarUrl: String? = null,
        val memberTier: MemberTier = MemberTier.FREE,
        val memberExpiry: Long = 0L, // ms timestamp, 0 = not a member
        val loginMethod: LoginMethod = LoginMethod.PHONE,
)

/** 会员等级 — 按需求.md 定义 */
enum class MemberTier(
        val displayName: String,
        val emoji: String,
        val monthlyPrice: Int, // ¥ / 月
        val yearlyPrice: Int, // ¥ / 年
        val features: List<String>,
) {
    FREE(
            displayName = "未开通",
            emoji = "🆓",
            monthlyPrice = 0,
            yearlyPrice = 0,
            features = listOf("基础连接", "电量查看"),
    ),
    NORMAL(
            displayName = "普通会员",
            emoji = "⭐",
            monthlyPrice = 9,
            yearlyPrice = 88,
            features = listOf("找物体", "文字识别", "基础连接", "电量查看"),
    ),
    ADVANCED(
            displayName = "高级会员",
            emoji = "🌟",
            monthlyPrice = 29,
            yearlyPrice = 288,
            features = listOf("找物体", "文字识别", "人脸识别", "拍照", "录像", "录音", "媒体下载"),
    ),
    DIAMOND(
            displayName = "钻石会员",
            emoji = "💎",
            monthlyPrice = 59,
            yearlyPrice = 588,
            features =
                    listOf(
                            "找物体",
                            "文字识别",
                            "人脸识别",
                            "表情识别",
                            "导航（地铁/公交）",
                            "拍照",
                            "录像",
                            "录音",
                            "媒体下载",
                            "语音智能对话",
                            "实时翻译",
                            "所有付费功能"
                    ),
    )
}

/** Feature availability matrix for membership comparison table */
data class FeatureRow(
        val name: String,
        val icon: String,
        val free: Boolean,
        val normal: Boolean,
        val advanced: Boolean,
        val diamond: Boolean,
)

val FEATURE_MATRIX: List<FeatureRow> =
        listOf(
                FeatureRow("眼镜连接", "🔗", true, true, true, true),
                FeatureRow("电量查看", "🔋", true, true, true, true),
                FeatureRow("找物体", "🔍", false, true, true, true),
                FeatureRow("文字识别", "📝", false, true, true, true),
                FeatureRow("人脸识别", "👤", false, false, true, true),
                FeatureRow("拍照/录像", "📸", false, false, true, true),
                FeatureRow("录音", "🎙", false, false, true, true),
                FeatureRow("媒体下载", "⬇️", false, false, true, true),
                FeatureRow("表情识别", "😊", false, false, false, true),
                FeatureRow("导航", "🗺", false, false, false, true),
                FeatureRow("语音对话", "🗣", false, false, false, true),
                FeatureRow("实时翻译", "🌐", false, false, false, true),
                FeatureRow("所有付费功能", "🏆", false, false, false, true),
        )

/** 登录方式 */
enum class LoginMethod(val displayName: String) {
    PHONE("手机号"),
    EMAIL("邮箱"),
    WECHAT("微信"),
}

package com.fersaiyan.cyanbridge.vision

/** 视觉指令拦截器：检测用户语音是否包含视觉识别指令, 并生成对应的 Qwen-VL prompt。 */
object VisionInterceptor {

    enum class VisionMode {
        FIND_OBJECT, // 找物体
        READ_TEXT, // 识字
        FACE, // 人脸识别
        EMOTION, // 表情识别
        NAVIGATION // 导航 (找站台/出口)
    }

    // ── 关键词 → 模式映射 ──

    private val FIND_OBJECT_KEYWORDS =
            listOf("找到", "在哪", "帮我找", "什么位置", "放在哪", "东西在", "找一下", "看看有没有", "看到", "能看到", "在哪里")

    private val READ_TEXT_KEYWORDS =
            listOf("读文字", "识字", "读一下", "什么字", "写的什么", "文字", "帮我读", "看看写的", "念一下", "上面写")

    private val FACE_KEYWORDS = listOf("识别人", "这个人", "什么人", "是谁", "认识", "人脸", "谁在", "面前的人")

    private val EMOTION_KEYWORDS =
            listOf("表情", "心情", "开心", "难过", "生气", "高兴", "情绪", "什么表情", "笑", "哭")

    private val NAVIGATION_KEYWORDS = listOf("地铁站", "公交站", "出口", "入口", "站台", "路口", "在哪个方向", "怎么走")

    /**
     * 检测用户语音是否为视觉指令。
     * @return 匹配的 VisionMode，若非视觉指令则返回 null
     */
    fun isVisionCommand(text: String): VisionMode? {
        val t = text.trim()
        return when {
            FIND_OBJECT_KEYWORDS.any { t.contains(it) } -> VisionMode.FIND_OBJECT
            READ_TEXT_KEYWORDS.any { t.contains(it) } -> VisionMode.READ_TEXT
            FACE_KEYWORDS.any { t.contains(it) } -> VisionMode.FACE
            EMOTION_KEYWORDS.any { t.contains(it) } -> VisionMode.EMOTION
            NAVIGATION_KEYWORDS.any { t.contains(it) } -> VisionMode.NAVIGATION
            else -> null
        }
    }

    /** 根据模式和用户原文生成 Qwen-VL 分析 prompt。 */
    fun buildPrompt(mode: VisionMode, userText: String): String =
            when (mode) {
                VisionMode.FIND_OBJECT ->
                        """
            用户是一位盲人，正通过智能眼镜摄像头查看周围。
            用户说："$userText"
            请根据图片描述用户要找的物体在什么位置（左边/右边/前方/上方等），
            并估计距离（如"约50厘米"），用简短口语化的中文回答。
            如果没找到，请诚实告知。
        """.trimIndent()
                VisionMode.READ_TEXT ->
                        """
            用户是一位盲人，正通过智能眼镜摄像头查看前方。
            请识别并读出图片中所有可见的文字内容。
            按从上到下、从左到右的顺序排列，用简洁的中文回答。
        """.trimIndent()
                VisionMode.FACE ->
                        """
            用户是一位盲人，正通过智能眼镜摄像头查看前方的人。
            请描述图片中人物的大致外观特征（性别、年龄段、穿着等），
            用简洁口语化的中文回答。注意保护隐私，不要猜测具体身份。
        """.trimIndent()
                VisionMode.EMOTION ->
                        """
            用户是一位盲人，正通过智能眼镜摄像头查看前方的人。
            请描述图片中人物的面部表情和情绪状态，
            用简洁口语化的中文回答。
        """.trimIndent()
                VisionMode.NAVIGATION ->
                        """
            用户是一位盲人，正通过智能眼镜摄像头查看周围环境。
            用户说："$userText"
            请帮助用户找到目标位置（如地铁站、公交站、出口等），
            描述方向和大致距离，用简短口语化的中文回答。
        """.trimIndent()
            }
}

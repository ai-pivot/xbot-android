package dev.xbot.android.ui

/** 一条消息最多带几个附件。 */
internal const val MAX_ATTACHMENTS = 5

/**
 * 单个附件的上限。
 *
 * 服务端 `POST /api/files/upload` 用 `MaxBytesReader` 卡死 10MB
 * （见 xbot `channel/web/web_file.go: maxFileSize = 10 << 20`），超过会直接 413，
 * 所以这里提前拦，别让用户等半天上传才失败。
 */
internal const val MAX_ATTACHMENT_BYTES = 10L * 1024 * 1024

/** 待校验的候选附件；只保留校验需要的字段，方便纯 JVM 单测。 */
internal data class AttachmentPick(val name: String, val size: Long)

internal data class AttachmentRejection(val pick: AttachmentPick, val reason: String)

/**
 * 一次多选的结果。
 *
 * [acceptedIndices] 指向入参 `picks` 的下标（调用方按它把 Uri 取回来），
 * [rejected] 给出被跳过的文件和原因，用于提示用户。
 */
internal data class AttachmentPlan(
    val acceptedIndices: List<Int>,
    val rejected: List<AttachmentRejection>,
)

/**
 * 决定这一批选中的文件里哪些可以进待发送队列。
 *
 * 规则（顺序即优先级，一个文件只报第一个命中的原因）：
 * 1. 空文件（`size <= 0`，部分 provider 拿不到大小）→ 跳过；
 * 2. 超过 [maxBytes] → 跳过（服务端会 413）；
 * 3. 剩余名额用完（`maxCount - existingCount`）→ 跳过。
 */
internal fun planAttachmentPick(
    existingCount: Int,
    picks: List<AttachmentPick>,
    maxCount: Int = MAX_ATTACHMENTS,
    maxBytes: Long = MAX_ATTACHMENT_BYTES,
): AttachmentPlan {
    var room = (maxCount - existingCount).coerceAtLeast(0)
    val accepted = ArrayList<Int>(minOf(room, picks.size))
    val rejected = ArrayList<AttachmentRejection>()
    picks.forEachIndexed { index, pick ->
        when {
            pick.size <= 0L -> rejected += AttachmentRejection(pick, "空文件")
            pick.size > maxBytes -> rejected += AttachmentRejection(
                pick,
                "超过 ${maxBytes / (1024 * 1024)}MB",
            )
            room <= 0 -> rejected += AttachmentRejection(pick, "最多 $maxCount 个附件")
            else -> {
                accepted += index
                room -= 1
            }
        }
    }
    return AttachmentPlan(acceptedIndices = accepted, rejected = rejected)
}

/** 把被跳过的文件拼成一句可以直接扔 snackbar 的提示；没有被跳过时返回 null。 */
internal fun attachmentRejectionMessage(rejected: List<AttachmentRejection>): String? {
    if (rejected.isEmpty()) return null
    val detail = rejected.joinToString("；") { "${it.pick.name}（${it.reason}）" }
    return "已跳过 ${rejected.size} 个文件：$detail"
}

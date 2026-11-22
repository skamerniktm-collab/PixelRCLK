package com.example.projects

enum class ProjectKind(val label: String) {
    EDITOR("Редактор"),
    RESIZER("Ресайзер"),
    OVERLAY("Наложение")
}

data class AppProjectSummary(
    val id: String,
    val kind: ProjectKind,
    val name: String,
    val width: Int,
    val height: Int,
    val itemCount: Int,
    val detailLabel: String,
    val updatedAtMillis: Long,
    val previewPath: String? = null
)

object ProjectRetentionPolicy {
    const val RETENTION_DAYS = 7L
    const val RETENTION_MILLIS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

    fun isExpired(
        updatedAtMillis: Long,
        lastOpenedAtMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
        retentionMillis: Long = RETENTION_MILLIS
    ): Boolean {
        val lastActivityMillis = maxOf(updatedAtMillis, lastOpenedAtMillis)
        return (nowMillis - lastActivityMillis) >= retentionMillis
    }
}

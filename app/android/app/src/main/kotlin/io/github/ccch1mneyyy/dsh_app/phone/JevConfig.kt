package io.github.ccch1mneyyy.dsh_app.phone

data class JevConfig(
    val enabled: Boolean = false,
    val apiUrl: String = "https://api.typesafe.ai/v1/systemone",
    val apiKey: String = "",
    val confidenceThreshold: Double = 0.85,
    val marginThreshold: Double = 0.30,
) {
    val isUsable: Boolean get() = enabled && apiKey.isNotBlank() && apiUrl.isNotBlank()

    companion object {
        val DISABLED = JevConfig()

        fun fromMap(map: Map<*, *>): JevConfig = JevConfig(
            enabled = map["enabled"] as? Boolean ?: false,
            apiUrl = (map["apiUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?: "https://api.typesafe.ai/v1/systemone",
            apiKey = (map["apiKey"] as? String) ?: "",
            confidenceThreshold = (map["confidenceThreshold"] as? Number)?.toDouble() ?: 0.85,
            marginThreshold = (map["marginThreshold"] as? Number)?.toDouble() ?: 0.30,
        )
    }
}

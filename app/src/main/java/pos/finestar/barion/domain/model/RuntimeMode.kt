package pos.finestar.barion.domain.model

enum class RuntimeMode {
    DAY,
    NIGHT,
    UNKNOWN;

    companion object {
        fun fromRaw(value: String?): RuntimeMode {
            return when (value?.lowercase()) {
                "day" -> DAY
                "night" -> NIGHT
                else -> UNKNOWN
            }
        }
    }
}

package hr.finestar.barion.ui.navigation

object NavRoutes {
    const val AUTH_GATE = "auth_gate"
    const val PIN_LOGIN = "pin_login"
    const val FLOOR_PLAN = "floor_plan"
    const val CHECK = "check"
    const val ADD_ITEMS = "add_items"
    const val PARTIAL_PAYMENT = "partial_payment"

    const val ARG_CHECK_ID = "checkId"
    const val ARG_TABLE_NAME = "tableName"

    val checkRoutePattern = "$CHECK/{$ARG_CHECK_ID}/{$ARG_TABLE_NAME}"
    val addItemsRoutePattern = "$ADD_ITEMS/{$ARG_CHECK_ID}/{$ARG_TABLE_NAME}"
    val partialPaymentRoutePattern = "$PARTIAL_PAYMENT/{$ARG_CHECK_ID}/{$ARG_TABLE_NAME}"

    fun checkRoute(checkId: Long, tableName: String): String {
        val encodedName = java.net.URLEncoder.encode(tableName, Charsets.UTF_8.name())
        return "$CHECK/$checkId/$encodedName"
    }

    fun addItemsRoute(checkId: Long, tableName: String): String {
        val encodedName = java.net.URLEncoder.encode(tableName, Charsets.UTF_8.name())
        return "$ADD_ITEMS/$checkId/$encodedName"
    }

    fun partialPaymentRoute(checkId: Long, tableName: String): String {
        val encodedName = java.net.URLEncoder.encode(tableName, Charsets.UTF_8.name())
        return "$PARTIAL_PAYMENT/$checkId/$encodedName"
    }
}

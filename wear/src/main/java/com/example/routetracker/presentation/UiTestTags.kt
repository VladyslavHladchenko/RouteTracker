package com.example.routetracker.presentation

object UiTestTags {
    const val HEADER_CARD = "header_card"
    const val BOARD_REFRESH_BUTTON = "board_refresh_button"
    const val QUICK_SWITCH_NEW_ROUTE_BUTTON = "quick_switch_new_route_button"
    const val SETTINGS_CLOSE_BUTTON = "settings_close_button"
    const val TRIP_DETAILS_CLOSE_BUTTON = "trip_details_close_button"

    fun departureCard(rowKey: String): String = "departure_card:$rowKey"

    fun departurePlatform(rowKey: String): String = "departure_platform:$rowKey"

    fun departureDelay(rowKey: String): String = "departure_delay:$rowKey"

    fun favoriteRouteCard(stableKey: String): String = "favorite_route:$stableKey"
}

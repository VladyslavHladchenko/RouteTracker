package com.example.routetracker.presentation

object UiTestTags {
    const val HEADER_CARD = "header_card"
    const val BOARD_PULL_REFRESH_CONTAINER = "board_pull_refresh_container"
    const val BOARD_PULL_REFRESH_INDICATOR = "board_pull_refresh_indicator"
    const val BOARD_REFRESH_BUTTON = "board_refresh_button"
    const val QUICK_SWITCH_SWAP_BUTTON = "quick_switch_swap_button"
    const val QUICK_SWITCH_NEW_ROUTE_BUTTON = "quick_switch_new_route_button"
    const val SETTINGS_CLOSE_BUTTON = "settings_close_button"
    const val SETTINGS_API_KEY_BUTTON = "settings_api_key_button"
    const val SETTINGS_API_KEY_SAVE_BUTTON = "settings_api_key_save_button"
    const val SETTINGS_API_KEY_CLEAR_BUTTON = "settings_api_key_clear_button"
    const val TRIP_DETAILS_CLOSE_BUTTON = "trip_details_close_button"

    fun departureCard(rowKey: String): String = "departure_card:$rowKey"

    fun departurePlatform(rowKey: String): String = "departure_platform:$rowKey"

    fun departureDelay(rowKey: String): String = "departure_delay:$rowKey"

    fun favoriteRouteCard(stableKey: String): String = "favorite_route:$stableKey"
}

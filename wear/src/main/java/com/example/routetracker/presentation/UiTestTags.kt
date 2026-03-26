package com.example.routetracker.presentation

object UiTestTags {
    const val BOARD_SUMMARY_CARD = "board_summary_card"
    const val BOARD_CHANGE_ROUTE_BUTTON = "board_change_route_button"
    const val BOARD_REFRESH_BUTTON = "board_refresh_button"
    const val BOARD_SETTINGS_BUTTON = "board_settings_button"
    const val BOARD_AUTO_UPDATES_SWITCH = "board_auto_updates_switch"
    const val QUICK_SWITCH_CURRENT_ROUTE_CARD = "quick_switch_current_route_card"
    const val QUICK_SWITCH_SWAP_BUTTON = "quick_switch_swap_button"
    const val QUICK_SWITCH_EDIT_BUTTON = "quick_switch_edit_button"
    const val QUICK_SWITCH_NEW_ROUTE_BUTTON = "quick_switch_new_route_button"
    const val ROUTE_SETUP_APPLY_BUTTON = "route_setup_apply_button"
    const val SETTINGS_API_KEY_BUTTON = "settings_api_key_button"
    const val SETTINGS_API_KEY_SAVE_BUTTON = "settings_api_key_save_button"
    const val SETTINGS_API_KEY_CLEAR_BUTTON = "settings_api_key_clear_button"
    const val TRIP_DETAILS_REFRESH_BUTTON = "trip_details_refresh_button"

    fun departureCard(rowKey: String): String = "departure_card:$rowKey"

    fun departurePlatform(rowKey: String): String = "departure_platform:$rowKey"

    fun departureDelay(rowKey: String): String = "departure_delay:$rowKey"

    fun favoriteRouteCard(stableKey: String): String = "favorite_route:$stableKey"

    fun favoriteRouteEditAction(stableKey: String): String = "favorite_route_edit:$stableKey"

    fun favoriteRouteDeleteAction(stableKey: String): String = "favorite_route_delete:$stableKey"
}

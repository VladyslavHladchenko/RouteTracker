package com.example.routetracker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.routetracker.data.GolemioApiKeyStore

internal class DebugApiKeyOverrideReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val apiKeyStore = GolemioApiKeyStore(context)

        when (intent.action) {
            ACTION_SET_API_KEY -> {
                val value = intent.getStringExtra(EXTRA_VALUE).orEmpty().trim()
                if (value.isEmpty()) {
                    Log.w(TAG, "Ignoring empty API key override from adb broadcast.")
                    setResultCode(RESULT_MISSING_VALUE)
                    setResultData("Missing non-empty string extra 'value'.")
                    return
                }

                apiKeyStore.setOverride(value)
                Log.d(TAG, "Stored Golemio API key override from adb broadcast.")
                setResultCode(RESULT_SUCCESS)
                setResultData("Saved watch API key override.")
            }

            ACTION_CLEAR_API_KEY -> {
                apiKeyStore.setOverride("")
                Log.d(TAG, "Cleared Golemio API key override from adb broadcast.")
                setResultCode(RESULT_SUCCESS)
                setResultData("Cleared watch API key override.")
            }

            else -> {
                Log.w(TAG, "Ignoring unsupported debug API key action: ${intent.action}")
                setResultCode(RESULT_UNSUPPORTED_ACTION)
                setResultData("Unsupported action '${intent.action}'.")
            }
        }
    }

    private companion object {
        private const val TAG = "DebugApiKeyOverride"
        private const val RESULT_SUCCESS = 1
        private const val RESULT_MISSING_VALUE = 2
        private const val RESULT_UNSUPPORTED_ACTION = 3
    }
}

internal const val ACTION_SET_API_KEY = "com.example.routetracker.debug.SET_API_KEY"
internal const val ACTION_CLEAR_API_KEY = "com.example.routetracker.debug.CLEAR_API_KEY"
internal const val EXTRA_VALUE = "value"

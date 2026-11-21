package com.amurcanov.tgwsproxy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs after the package has been updated (ACTION_MY_PACKAGE_REPLACED).
 * Restores the previous app state: opens MainActivity, restarts the proxy
 * if it was running before the self-update, and cleans up the downloaded apk.
 */
class UpdateCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                SelfUpdateManager.deleteStaleApk(appContext)

                val settingsStore = SettingsStore(appContext)
                val pendingRelaunch = settingsStore.pendingPostUpdateRelaunch.first()
                if (!pendingRelaunch) {
                    Log.i(TAG, "Package replaced, no pending relaunch state")
                    return@launch
                }

                val restoreProxy = settingsStore.restoreProxyRunning.first()
                val selectedTab = settingsStore.restoreSelectedTab.first()

                if (restoreProxy) {
                    val started = ProxyController.startFromSavedSettings(
                        context = appContext,
                        showInvalidPortToast = false
                    )
                    Log.i(TAG, "Package replaced, proxy restore requested: $started")
                }

                val launchIntent = Intent(appContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra(MainActivity.EXTRA_RESTORE_SELECTED_TAB, selectedTab)
                }
                appContext.startActivity(launchIntent)
                Log.i(TAG, "Package replaced, app relaunched (tab=$selectedTab, proxy=$restoreProxy)")

                settingsStore.clearRelaunchState()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore state after package update", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateCompleted"
    }
}

package com.kirimly.app

import android.app.Application
import android.content.Context
import com.kirimly.app.data.db.KirimlyDatabase
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.network.HotspotManager
import com.kirimly.app.network.KirimlyServerManager
import com.kirimly.app.network.NsdDiscoveryManager
import com.kirimly.app.network.PeerTransferClient
import com.kirimly.app.network.TransferTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class KirimlyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container – one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: KirimlyDatabase = KirimlyDatabase.getInstance(appContext)
    val preferences: UserPreferencesRepository = UserPreferencesRepository(appContext)
    val transferTracker: TransferTracker = TransferTracker(appScope)
    val serverManager: KirimlyServerManager =
        KirimlyServerManager(appContext, database.transferDao(), transferTracker, appScope)
    val peerClient: PeerTransferClient =
        PeerTransferClient(appContext, database.transferDao(), transferTracker)
    val nsdManager: NsdDiscoveryManager =
        NsdDiscoveryManager(appContext, appScope) { host, port -> peerClient.fetchInfo(host, port) }
    val hotspotManager: HotspotManager = HotspotManager(appContext)

    init {
        appScope.launch {
            preferences.preferencesFlow.collect { prefs ->
                serverManager.updatePreferences(prefs.deviceNickname, prefs.pinRequired)
                nsdManager.updateLocalName(prefs.deviceNickname)
            }
        }
    }
}

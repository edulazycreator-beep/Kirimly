package com.kirimly.app

import android.app.Application
import android.content.Context
import com.kirimly.app.data.db.KirimlyDatabase
import com.kirimly.app.data.prefs.UserPreferencesRepository
import com.kirimly.app.network.HotspotManager
import com.kirimly.app.network.KirimlyServerManager
import com.kirimly.app.network.NearbyTransferManager
import com.kirimly.app.network.WifiJoiner
import com.kirimly.app.network.NsdDiscoveryManager
import com.kirimly.app.network.PeerTransferClient
import com.kirimly.app.network.SharedFileCache
import com.kirimly.app.network.TransferTracker
import com.kirimly.app.util.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class KirimlyApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        container = AppContainer(this)
    }
}

/** Manual dependency container – one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** True while an Activity is started; set by MainActivity. Used for idle auto-stop. */
    val appInForeground = MutableStateFlow(false)
    val database: KirimlyDatabase = KirimlyDatabase.getInstance(appContext)
    val preferences: UserPreferencesRepository = UserPreferencesRepository(appContext)
    val transferTracker: TransferTracker = TransferTracker(appScope)
    val serverManager: KirimlyServerManager =
        KirimlyServerManager(
            appContext,
            database.transferDao(),
            transferTracker,
            appScope,
            preferences,
            database.uploadSessionDao()
        ) { appInForeground.value }
    val peerClient: PeerTransferClient =
        PeerTransferClient(appContext, database.transferDao(), transferTracker)
    val nsdManager: NsdDiscoveryManager =
        NsdDiscoveryManager(appContext, appScope) { host, port -> peerClient.fetchInfo(host, port) }
    val hotspotManager: HotspotManager = HotspotManager(appContext)
    val nearbyManager: NearbyTransferManager =
        NearbyTransferManager(appContext, appScope, database.transferDao(), transferTracker, serverManager)
    val wifiJoiner: WifiJoiner = WifiJoiner(appContext)

    init {
        appScope.launch(Dispatchers.IO) {
            preferences.ensureDeviceId()
            SharedFileCache.pruneStale(appContext)
        }
        appScope.launch {
            preferences.preferencesFlow.collect { prefs ->
                serverManager.updatePreferences(prefs)
                peerClient.deviceId = prefs.deviceId
                nsdManager.updateLocalName(prefs.deviceNickname)
                val renamed = nearbyManager.localName != prefs.deviceNickname
                nearbyManager.localName = prefs.deviceNickname
                nearbyManager.localDeviceId = prefs.deviceId
                if (renamed && nearbyManager.active.value) nearbyManager.restart()
            }
        }
    }
}

package dev.xoventech.tunnel.vpn.activities

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import app.openconnect.core.OCService
import com.tencent.mmkv.MMKV
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.AngConfigManager
import com.v2ray.ang.util.MessageUtil
import com.v2ray.ang.util.MmkvManager
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.ang.viewmodel.SettingsViewModel
import io.michaelrocks.paranoid.Obfuscate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import dev.xoventech.tunnel.vpn.R
import dev.xoventech.tunnel.vpn.config.ConfigDataBase
import dev.xoventech.tunnel.vpn.config.ConfigUtil
import dev.xoventech.tunnel.vpn.config.SettingsConstants
import dev.xoventech.tunnel.vpn.connectivity.DeviceStateReceiver
import dev.xoventech.tunnel.vpn.connectivity.deviceRebootReceiver
import dev.xoventech.tunnel.vpn.harliesApplication
import dev.xoventech.tunnel.vpn.logger.hLogStatus
import dev.xoventech.tunnel.vpn.service.HarlieService
import dev.xoventech.tunnel.vpn.service.HarlieService.InjectorListener
import dev.xoventech.tunnel.vpn.service.HarlieService.MyBinder
import dev.xoventech.tunnel.vpn.service.OpenVPNService
import dev.xoventech.tunnel.vpn.service.OpenVPNService.ConnectionStats
import dev.xoventech.tunnel.vpn.service.OpenVPNService.EventMsg
import dev.xoventech.tunnel.vpn.service.OpenVPNService.LogMsg
import dev.xoventech.tunnel.vpn.service.OpenVPNService.ProfileList
import dev.xoventech.tunnel.vpn.utils.FileUtils
import dev.xoventech.tunnel.vpn.utils.PrefUtil
import dev.xoventech.tunnel.vpn.utils.util
import dev.xoventech.tunnel.vpn.view.StatisticGraphData
import dev.xoventech.tunnel.vpn.view.StatisticGraphData.DataTransferStats
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.Objects
import java.util.Random


@Obfuscate
abstract class OpenVPNClientBase : AppCompatActivity(), SettingsConstants, InjectorListener, OpenVPNService.EventReceiver {

    private val settingsStorage by lazy { MMKV.mmkvWithID(MmkvManager.ID_SETTING, MMKV.MULTI_PROCESS_MODE) }
    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    protected var pref: SharedPreferences? = null
    protected var editor: SharedPreferences.Editor? = null
    protected var dPrefs: SharedPreferences? = null
    protected var dEditor: SharedPreferences.Editor? = null
    private var injector: HarlieService? = null
    protected var config: ConfigUtil? = null
    private var mBoundService: OpenVPNService? = null
    private var mOCSService: OCService? = null
    protected var serverData: ConfigDataBase? = null
    protected var networkData: ConfigDataBase? = null
    protected var upDateBytes: DataTransferStats? = null
    val isRunning by lazy { MutableLiveData<Boolean>() }
    val updateTestResultAction by lazy { MutableLiveData<String>() }
    private var mDeviceRebootReceiver: deviceRebootReceiver? = null
    private var mDeviceStateReceiver: DeviceStateReceiver? = null

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            this@OpenVPNClientBase.mBoundService = (service as OpenVPNService.LocalBinder).service
            Log.d(TAG, "CLIBASE: onServiceConnected: " + mBoundService.toString())
            mBoundService!!.client_attach(this@OpenVPNClientBase)
            this@OpenVPNClientBase.post_bind()
        }
        override fun onServiceDisconnected(className: ComponentName) {
            Log.d(TAG, "CLIBASE: onServiceDisconnected")
            this@OpenVPNClientBase.mBoundService = null
        }
    }
    private val mInjectorConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, p2: IBinder) {
            injector = (p2 as MyBinder).service
            injector!!.setInjectorListener(this@OpenVPNClientBase)
        }
        override fun onServiceDisconnected(p1: ComponentName) {
            injector = null
        }
    }
    private val mVPNConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(p1: ComponentName, p2: IBinder) {
            mOCSService = (p2 as OCService.LocalBinder).getService()
        }
        override fun onServiceDisconnected(p1: ComponentName) {
            mOCSService = null
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val builder = ThreadPolicy.Builder()
        StrictMode.setThreadPolicy(builder.permitAll().build())
        pref = harliesApplication.getPrivateSharedPreferences()
        editor = pref!!.edit()
        dPrefs = harliesApplication.getDefaultSharedPreferences()
        dEditor = dPrefs!!.edit()
        config = ConfigUtil.getInstance(this@OpenVPNClientBase)
        serverData = ConfigDataBase(this@OpenVPNClientBase, "mServerData")
        networkData = ConfigDataBase(this@OpenVPNClientBase, "mNetwrokData")
        upDateBytes = StatisticGraphData.getStatisticData().dataTransferStats
        window.statusBarColor = ResourcesCompat.getColor(resources,R.color.colorAccent, null)
        window.navigationBarColor = ResourcesCompat.getColor(resources,R.color.colorAccent, null)
    }

    /*protected fun extractZipConfig() {
        try {
            val file: File = FileUtils.zipFile(this@OpenVPNClientBase)
            if (file.exists()) {
                val zip = ZipFile(file)
                if (zip.isEncrypted) {
                    zip.setPassword(util.x)
                }
                zip.extractAll(this@OpenVPNClientBase.filesDir.absolutePath)
                file.delete()
            }
        } catch (e: Exception) {
            util.showToast("ZIP Error!", e.message)
        }
    }*/


    protected fun doBindService() {
        bindService(Intent(this, OpenVPNService::class.java).setAction(OpenVPNService.ACTION_BIND), this.mConnection, BIND_AUTO_CREATE)
        bindService(Intent(this, HarlieService::class.java), mInjectorConnection, BIND_AUTO_CREATE)
        bindService(Intent(this, OCService::class.java).setAction(OCService.START_SERVICE), mVPNConnection, BIND_AUTO_CREATE)
        register_connectivity_receiver()
    }

    protected fun doUnbindService() {
        Log.d(TAG, "CLIBASE: doUnbindService")
        if (this.mBoundService != null) {
            unbindService(this.mConnection)
            this.mBoundService = null
        }
        if (injector != null) {
            unbindService(mInjectorConnection)
            injector = null
        }
        if (mOCSService != null) {
            unbindService(mVPNConnection)
            mOCSService = null
        }
        unregister_connectivity_receiver()
    }

    override fun startOpenVPN() {
        // TODO: Implement this method
    }

    protected fun get_connection_stats(): ConnectionStats? {
        if (this.mBoundService != null) {
            return mBoundService!!._connection_stats
        }
        return null
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @SuppressLint("DefaultLocale")
    protected fun get_gui_version(name: String): String {
        var versionName = "2.9"
        var versionCode = 29
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            versionName = pi.versionName
            versionCode = pi.longVersionCode.toInt()
        } catch (e: java.lang.Exception) {
            addlogInfo("cannot obtain version info: $e")
        }
        return String.format("%s %s-%d", name, versionName, versionCode)
    }


    protected fun submitConnectIntent(
        profile_name: String,
        server: String?,
        vpn_proto: String?,
        ipv6: String?,
        conn_timeout: String?,
        username: String?,
        password: String?,
        cache_password: Boolean,
        pk_password: String?,
        response: String?,
        epki_alias: String?,
        compression_mode: String?,
        proxy_name: String?,
        proxy_username: String?,
        proxy_password: String?,
        proxy_allow_creds_dialog: Boolean,
        gui_version: String?) {
        val prefix = OpenVPNService.INTENT_PREFIX
        val intent = Intent(this, OpenVPNService::class.java).setAction(OpenVPNService.ACTION_CONNECT)
            .putExtra("$prefix.PROFILE", profile_name)
            .putExtra("$prefix.GUI_VERSION", gui_version)
            .putExtra("$prefix.PROXY_NAME", proxy_name)
            .putExtra("$prefix.PROXY_USERNAME", proxy_username)
            .putExtra("$prefix.PROXY_PASSWORD", proxy_password)
            .putExtra("$prefix.PROXY_ALLOW_CREDS_DIALOG", proxy_allow_creds_dialog)
            .putExtra("$prefix.SERVER", server)
            .putExtra("$prefix.PROTO", vpn_proto)
            .putExtra("$prefix.IPv6", ipv6)
            .putExtra("$prefix.CONN_TIMEOUT", conn_timeout)
            .putExtra("$prefix.USERNAME", username)
            .putExtra("$prefix.PASSWORD", password)
            .putExtra("$prefix.CACHE_PASSWORD", cache_password)
            .putExtra("$prefix.PK_PASSWORD", pk_password)
            .putExtra("$prefix.RESPONSE", response).putExtra("$prefix.EPKI_ALIAS", epki_alias)
            .putExtra("$prefix.COMPRESSION_MODE", compression_mode)
        if (this.mBoundService != null) {
            mBoundService!!.client_attach(this)
        }
        startService(intent)
        Log.d(TAG, "CLI: submitConnectIntent: $profile_name")
    }

    protected fun submitDisconnectIntent() {
        startService(Intent(this@OpenVPNClientBase, HarlieService::class.java).setAction(HarlieService.STOP_SERVICE).putExtra("stateSTOP_SERVICE",resString(R.string.state_disconnected)))
    }

    protected fun submitReloadProfileIntent(profile_content:String) {
        val prefix = OpenVPNService.INTENT_PREFIX
        startService(Intent(this@OpenVPNClientBase, OpenVPNService::class.java).setAction(OpenVPNService.ACTION_REFRESH_PROFILE).putExtra("$prefix.CONTENT", profile_content))
    }

    protected fun resolveExternalPkiAlias(prof: OpenVPNService.Profile?, next_action: EpkiPost) {
        if (prof == null || !prof.need_external_pki_alias()) {
            next_action.post_dispatch(null)
        } else {
            next_action.post_dispatch("DISABLE_CLIENT_CERT")
        }
    }

    protected fun addlogInfo(msg: String) {
        hLogStatus.logInfo(msg)
    }
    protected open fun post_bind() {}
    override fun event(ev: EventMsg) {
    }
    override fun log(lm: LogMsg) {
    }
    protected fun resString(res_id: Int): String {
        return resources.getString(res_id)
    }
    interface EpkiPost {
        fun post_dispatch(str: String?)
    }
    protected fun get_last_event(): EventMsg? {
        if (this.mBoundService != null) {
            return mBoundService!!._last_event
        }
        return null
    }
    protected fun get_last_event_prof_manage(): EventMsg? {
        if (this.mBoundService != null) {
            return mBoundService!!._last_event_prof_manage
        }
        return null
    }
    protected fun profile_list(): ProfileList? {
        if (this.mBoundService != null) {
            try {
                return mBoundService!!._profile_list
            } catch (_: IOException) {
            }
        }
        return null
    }


    protected fun loadServerArrayDragaPosition():Boolean {
        try {
            val j1 = JSONArray()
            val j2 = JSONArray()
            val j3 = JSONArray()
            val j4 = JSONArray()
            val j5 = JSONArray()
            val j6 = JSONArray()
            val jarr = JSONArray(serverData!!.data)
            for (i in 0 until jarr.length()) {
                if (jarr.getJSONObject(i).getInt("serverType") == 0) {
                    j1.put(jarr.getJSONObject(i))
                }
                if (jarr.getJSONObject(i).getInt("serverType") == 4) {
                    j2.put(jarr.getJSONObject(i))
                }
                if (jarr.getJSONObject(i).getInt("serverType") == 1) {
                    j3.put(jarr.getJSONObject(i))
                }
                if (jarr.getJSONObject(i).getInt("serverType") == 2) {
                    j4.put(jarr.getJSONObject(i))
                }
                if (jarr.getJSONObject(i).getInt("serverType") == 3) {
                    j5.put(jarr.getJSONObject(i))
                }
                if (jarr.getJSONObject(i).getInt("serverType") == 5) {
                    j6.put(jarr.getJSONObject(i))
                }
            }
            editor!!.putString(SettingsConstants.SERVER_TYPE_V2RAY, j5.toString()).apply()
            editor!!.putString(SettingsConstants.SERVER_TYPE_DNS, j4.toString()).apply()
            editor!!.putString(SettingsConstants.SERVER_TYPE_SSH, j3.toString()).apply()
            editor!!.putString(SettingsConstants.SERVER_TYPE_UDP_HYSTERIA_V1, j2.toString()).apply()
            editor!!.putString(SettingsConstants.SERVER_TYPE_OVPN, j1.toString()).apply()
            editor!!.putString(SettingsConstants.SERVER_TYPE_OPEN_CONNECT, j6.toString()).apply()
            return true
        } catch (e: JSONException) {
            util.showToast("Error!", e.message)
            return false
        }
    }

    protected fun serverArrayDragaPosition() : JSONArray{
        try {
            val jar = JSONArray()
            if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 0) {
                val jarr1 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_OVPN, "[]"))
                for (i in 0 until jarr1.length()) {
                    if (jarr1.getJSONObject(i).getInt("serverType") == 0) {
                        jar.put(jarr1.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 2) {
                val jarr2 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_SSH, "[]"))
                for (i in 0 until jarr2.length()) {
                    if (jarr2.getJSONObject(i).getInt("serverType") == 1) {
                        jar.put(jarr2.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 3) {
                val jarr3 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_DNS, "[]"))
                for (i in 0 until jarr3.length()) {
                    if (jarr3.getJSONObject(i).getInt("serverType") == 2) {
                        jar.put(jarr3.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 1) {
                val jarr4 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_UDP_HYSTERIA_V1, "[]"))
                for (i in 0 until jarr4.length()) {
                    if (jarr4.getJSONObject(i).getInt("serverType") == 4) {
                        jar.put(jarr4.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 4) {
                val jarr5 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_V2RAY, "[]"))
                for (i in 0 until jarr5.length()) {
                    if (jarr5.getJSONObject(i).getInt("serverType") == 3) {
                        jar.put(jarr5.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 5) {
                val jarr6 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_OPEN_CONNECT, "[]"))
                for (i in 0 until jarr6.length()) {
                    if (jarr6.getJSONObject(i).getInt("serverType") == 5) {
                        jar.put(jarr6.getJSONObject(i))
                    }
                }
            }
            if (jar.length() >= 2) {
                editor!!.putBoolean("show_random_layout", true).apply()
            } else {
                editor!!.putBoolean("show_random_layout", false).apply()
            }
            return jar
        } catch (e: JSONException) {
            editor!!.putBoolean("isRandom", false).apply()
            editor!!.putBoolean("show_random_layout", false).apply()
            util.showToast("Error!", e.message)
        }
        return JSONArray("[]")
    }



    protected fun addOrEditedServers() : JSONArray{
        try {
            val jar = JSONArray()
            val jar0 = JSONArray()
            val jarr1 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_OVPN, "[]"))
            val jarr2 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_SSH, "[]"))
            val jarr3 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_DNS, "[]"))
            val jarr4 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_UDP_HYSTERIA_V1, "[]"))
            val jarr5 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_V2RAY, "[]"))
            val jarr6 = JSONArray(pref!!.getString(SettingsConstants.SERVER_TYPE_OPEN_CONNECT, "[]"))
            for (i in 0 until jarr1.length()) {
                jar.put(jarr1.getJSONObject(i))
            }
            for (i in 0 until jarr2.length()) {
                jar.put(jarr2.getJSONObject(i))
            }
            for (i in 0 until jarr3.length()) {
                jar.put(jarr3.getJSONObject(i))
            }
            for (i in 0 until jarr5.length()) {
                jar.put(jarr5.getJSONObject(i))
            }
            for (i in 0 until jarr4.length()) {
                jar.put(jarr4.getJSONObject(i))
            }
            for (i in 0 until jarr6.length()) {
                jar.put(jarr6.getJSONObject(i))
            }
            for (i in 0 until jar.length()) {
                if (jar.getJSONObject(i).has("isAddOrEdited") && jar.getJSONObject(i).getBoolean("isAddOrEdited")){
                    jar0.put(jar.getJSONObject(i))
                }
            }
            return jar0
        } catch (e: JSONException) {
            util.showToast("Error!", e.message)
        }
        return JSONArray("[]")
    }
    protected fun addOrEditedNetwork() : JSONArray{
        try{
            val jar = JSONArray()
            val jar0 = JSONArray()
            val jarr1 = JSONArray(pref!!.getString(SettingsConstants.LOAD_OVPN_SSH_OCS_TWEAKS_KEY, "[]"))
            val jarr2 = JSONArray(pref!!.getString(SettingsConstants.LOAD_UDP_TWEAKS_KEY, "[]"))
            val jarr3 = JSONArray(pref!!.getString(SettingsConstants.LOAD_DNS_TWEAKS_KEY, "[]"))
            val jarr4 = JSONArray(pref!!.getString(SettingsConstants.LOAD_V2RAY_TWEAKS_KEY, "[]"))
            for (i in 0 until jarr1.length()) {
                jar.put(jarr1.getJSONObject(i))
            }
            for (i in 0 until jarr2.length()) {
                jar.put(jarr2.getJSONObject(i))
            }
            for (i in 0 until jarr3.length()) {
                jar.put(jarr3.getJSONObject(i))
            }
            for (i in 0 until jarr4.length()) {
                jar.put(jarr4.getJSONObject(i))
            }
            for (i in 0 until jar.length()) {
                if (jar.getJSONObject(i).has("isAddOrEdited") && jar.getJSONObject(i).getBoolean("isAddOrEdited")){
                    jar0.put(jar.getJSONObject(i))
                }
            }
            return jar0
        } catch (e: JSONException) {
            util.showToast("Error-11!", e.message)
        }
        return JSONArray("[]")
    }

    protected fun loadNetworkArrayDragaPosition():Boolean {
        try {
            val j1 = JSONArray()
            val j2 = JSONArray()
            val j3 = JSONArray()
            val j4 = JSONArray()
            val jarr = JSONArray(networkData!!.data)
            for (i in 0 until jarr.length()) {
                val js = jarr.getJSONObject(i)
                //OVPN - SSH - OCS//
                if (js.getInt("proto_spin") == 0) {
                    j1.put(jarr.getJSONObject(i))
                }
                if (js.getInt("proto_spin") == 3) {
                    j1.put(jarr.getJSONObject(i))
                }
                if (js.getInt("proto_spin") == 4) {
                    j1.put(jarr.getJSONObject(i))
                }
                if (js.getInt("proto_spin") == 5) {
                    j1.put(jarr.getJSONObject(i))
                }
                // UDP //
                if (js.getInt("proto_spin") == 1) {
                    j2.put(jarr.getJSONObject(i))
                }
                // DNS //
                if (js.getInt("proto_spin") == 2) {
                    j3.put(jarr.getJSONObject(i))
                }
                // V2RAY //
                if (js.getInt("proto_spin") == 6){
                    if(js.getInt("V2rayPayloadType") == 0) {
                        j4.put(jarr.getJSONObject(i))
                    }
                }
                if (js.getInt("proto_spin") == 6){
                    if(js.getInt("V2rayPayloadType") == 1) {
                        j4.put(jarr.getJSONObject(i))
                    }
                }
                if (js.getInt("proto_spin") == 6){
                    if(js.getInt("V2rayPayloadType") == 2) {
                        j4.put(jarr.getJSONObject(i))
                    }
                }
            }
            editor!!.putString(SettingsConstants.LOAD_OVPN_SSH_OCS_TWEAKS_KEY, j1.toString()).apply()
            editor!!.putString(SettingsConstants.LOAD_UDP_TWEAKS_KEY, j2.toString()).apply()
            editor!!.putString(SettingsConstants.LOAD_DNS_TWEAKS_KEY, j3.toString()).apply()
            editor!!.putString(SettingsConstants.LOAD_V2RAY_TWEAKS_KEY, j4.toString()).apply()
            return true
        } catch (e: JSONException) {
            util.showToast("Error-12!", e.message)
            return false
        }
    }

    protected fun networkArrayDragaPosition() : JSONArray{
        try {
            val jar = JSONArray()
            if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 0 || pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 2 || pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 5) {
                val jarr = JSONArray(pref!!.getString(SettingsConstants.LOAD_OVPN_SSH_OCS_TWEAKS_KEY, "[]"))
                for (i in 0 until jarr.length()) {
                    val js = jarr.getJSONObject(i)
                    if (js.getInt("proto_spin") == 0) {
                        jar.put(jarr.getJSONObject(i))
                    } else if (js.getInt("proto_spin") == 3) {
                        jar.put(jarr.getJSONObject(i))
                    } else if (js.getInt("proto_spin") == 4) {
                        jar.put(jarr.getJSONObject(i))
                    } else if (js.getInt("proto_spin") == 5) {
                        jar.put(jarr.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 1) {
                val jarr = JSONArray(pref!!.getString(SettingsConstants.LOAD_UDP_TWEAKS_KEY, "[]"))
                for (i in 0 until jarr.length()) {
                    val js = jarr.getJSONObject(i)
                    if (js.getInt("proto_spin") == 1) {
                        jar.put(jarr.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 3) {
                val jarr = JSONArray(pref!!.getString(SettingsConstants.LOAD_DNS_TWEAKS_KEY, "[]"))
                for (i in 0 until jarr.length()) {
                    val js = jarr.getJSONObject(i)
                    if (js.getInt("proto_spin") == 2) {
                        jar.put(jarr.getJSONObject(i))
                    }
                }
            } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 4) {
                val jarr = JSONArray(pref!!.getString(SettingsConstants.LOAD_V2RAY_TWEAKS_KEY, "[]"))
                for (i in 0 until jarr.length()) {
                    val js = jarr.getJSONObject(i)
                    if (js.getInt("proto_spin") == 6) {
                        jar.put(jarr.getJSONObject(i))
                    }
                }
            }
            return jar
        } catch (e: JSONException) {
            util.showToast("Error-13!", e.message)
        }
        return JSONArray("[]")
    }

    private fun getNetworkType(js: JSONObject): String {
        try {
            if (js.getInt("proto_spin") == 0) {
                return "HTTP"
            } else if (js.getInt("proto_spin") == 1) {
                return "UDP"
            } else if (js.getInt("proto_spin") == 2) {
                return "SLOWDNS"
            } else if (js.getInt("proto_spin") == 3) {
                return "SSL"
            } else if (js.getInt("proto_spin") == 4) {
                return "SSL+PAYLOAD"
            } else if (js.getInt("proto_spin") == 5) {
                return "SSL+PROXY"
            } else if (js.getInt("proto_spin") == 6) {
                return "V2ray/Xray";
            }
        } catch (e: Exception) {
            util.showToast("getNetworkType", e.message)
        }
        return ""
    }

    private fun getServerType(sjs: JSONObject, pjs: JSONObject): String? {
        try {
            config!!.socketTYPE = pjs.getString("server_type")
            if (pjs.getString("server_type") == "cf") {
                return sjs.getString("ServerIP")
            } else if (pjs.getString("server_type") == "ws") {
                return sjs.getString("ServerCloudFront")
            } else if (pjs.getString("server_type") == "http") {
                return sjs.getString("ServerHTTP")
            }
            config!!.socketTYPE = "cf"
            util.showToast("Oppss!", "Server error")
            return null
        } catch (e: Exception) {
            util.showToast("getServerType", e.message)
        }
        return null
    }

    private fun t(): String {
        if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 0) {
            return SettingsConstants.SERVER_TYPE_OVPN
        } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 2) {
            return SettingsConstants.SERVER_TYPE_SSH
        } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 3) {
            return SettingsConstants.SERVER_TYPE_DNS
        } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 4) {
            return SettingsConstants.SERVER_TYPE_V2RAY
        } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 1) {
            return SettingsConstants.SERVER_TYPE_UDP_HYSTERIA_V1
        } else if (pref!!.getInt(SettingsConstants.manual_tunnel_radio_key, 0) == 5) {
            return SettingsConstants.SERVER_TYPE_OPEN_CONNECT
        }
        return SettingsConstants.SERVER_TYPE_OVPN
    }

    protected fun reLoad_Configs(): Boolean {
        try {
            val mRandomServerIndex: Int
            val jarr1 = serverArrayDragaPosition()
            val jarr2 = networkArrayDragaPosition()
            if (jarr1.length() == 0 || jarr2.length() == 0) {
                editor!!.putBoolean("isRandom", false).apply()
                return false
            }
            mRandomServerIndex = if (pref!!.getBoolean("isRandom", false)) {
                Random().nextInt(jarr1.length())
            } else {
                pref!!.getInt(SettingsConstants.SERVER_POSITION, 0)
            }
            val s_js = jarr1.getJSONObject(mRandomServerIndex)
            val p_js = jarr2.getJSONObject(pref!!.getInt(SettingsConstants.NETWORK_POSITION, 0))
            val serType = t()
            val netType = getNetworkType(p_js)
            config!!.serverType = serType
            val mHost = getServerType(s_js, p_js)
            config!!.setIsQueryMode(false)
            editor!!.putString("Network_info", "").apply()
            editor!!.putString("Server_message", if (s_js.has("Server_msg")) s_js.getString("Server_msg") else "").apply()
            editor!!.putBoolean(SettingsConstants.CONFIG_EXP_KEY,isConfigXpired(s_js)).apply()
            if (serType == SettingsConstants.SERVER_TYPE_UDP_HYSTERIA_V1) {
                if (mHost!!.isEmpty()) return false
                config!!.serverName = s_js.getString("Name")
                config!!.setServerHost(mHost)
                config!!.setServerPort("20000-50000")
                if(p_js.has("NetworkPayload")) {
                    config!!.udpConfig = p_js.getString("NetworkPayload")
                } else{
                    config!!.udpConfig = ""
                }
                config!!.configIsAutoLogIn = s_js.getBoolean("AutoLogIn")
                if (s_js.getBoolean("AutoLogIn")) {
                    config!!.setUser(s_js.getString("Username"))
                    config!!.setUserPass(s_js.getString("Password"))
                } else {
                    val user = pref!!.getString("_screenUsername_key", "")
                    val pass = pref!!.getString("_screenPassword_key", "")
                    config!!.setUser(if (user!!.isEmpty()) "" else FileUtils.hideJson(user))
                    config!!.setUserPass(if (pass!!.isEmpty()) "" else FileUtils.hideJson(pass))
                }
                editor!!.putString(SettingsConstants.SERVER_WEB_RENEW_KEY,if (s_js.has("server_web_renew")) s_js.getString("server_web_renew") else "").apply()
                editor!!.putString("IPHunter_pName", p_js.getString("Name")).apply()
                if (p_js.has("Info") && p_js.getString("Info").isNotEmpty()) {
                    editor!!.putString("Network_info", p_js.getString("Info")).apply()
                }
                config!!.payloadName = p_js.getString("Name")
                return true
            }
            if (serType == SettingsConstants.SERVER_TYPE_V2RAY) {
                config!!.serverName = s_js.getString("Name")
                config!!.setConfigV2ray(s_js.getString("ServerCloudFront"))
                if (s_js.has("V2rayID")) {
                    config!!.setV2rayID(FileUtils.showJson(s_js.getString("V2rayID")))
                }else{
                    config!!.setV2rayID("")
                }
                if(p_js.has("NetworkPayload")) {
                    editor!!.putString(SettingsConstants.SNI_V2RAY_KEY, p_js.getString("NetworkPayload")).apply()
                } else{
                    editor!!.putString(SettingsConstants.SNI_V2RAY_KEY, "").apply()
                }
                config!!.configIsAutoLogIn = s_js.getBoolean("AutoLogIn")
                if (s_js.getBoolean("AutoLogIn")) {
                    config!!.setUser(s_js.getString("Username"))
                    config!!.setUserPass(s_js.getString("Password"))
                } else {
                    val user = pref!!.getString("_screenUsername_key", "")
                    val pass = pref!!.getString("_screenPassword_key", "")
                    config!!.setUser(if (user!!.isEmpty()) "" else FileUtils.hideJson(user))
                    config!!.setUserPass(if (pass!!.isEmpty()) "" else FileUtils.hideJson(pass))
                }
                config!!.payloadName = p_js.getString("Name")
                editor!!.putString(SettingsConstants.SERVER_WEB_RENEW_KEY,if (s_js.has("server_web_renew")) s_js.getString("server_web_renew") else "").apply()
                return true
            }
            if (serType == SettingsConstants.SERVER_TYPE_DNS) {
                config!!.serverName = s_js.getString("Name")
                config!!.setServerHost(FileUtils.hideJson("127.0.0.1"))
                config!!.setServerPort("2222")
                config!!.setDNSpublicKey(s_js.getString("ServerCloudFront"))
                config!!.setDNSnameServer(s_js.getString("ServerIP"))
                if(p_js.has("NetworkPayload")) {
                    config!!.setDNSaddress(p_js.getString("NetworkPayload"))
                } else{
                    config!!.setDNSaddress("")
                }
                config!!.configIsAutoLogIn = s_js.getBoolean("AutoLogIn")
                if (s_js.getBoolean("AutoLogIn")) {
                    config!!.setUser(s_js.getString("Username"))
                    config!!.setUserPass(s_js.getString("Password"))
                } else {
                    val user = pref!!.getString("_screenUsername_key", "")
                    val pass = pref!!.getString("_screenPassword_key", "")
                    config!!.setUser(if (user!!.isEmpty()) "" else FileUtils.hideJson(user))
                    config!!.setUserPass(if (pass!!.isEmpty()) "" else FileUtils.hideJson(pass))
                }
                editor!!.putString("IPHunter_pName", p_js.getString("Name")).apply()
                if (p_js.has("Info") && p_js.getString("Info").isNotEmpty()) {
                    editor!!.putString("Network_info", p_js.getString("Info")).apply()
                }
                config!!.payloadName = p_js.getString("Name")
                editor!!.putString(SettingsConstants.SERVER_WEB_RENEW_KEY,if (s_js.has("server_web_renew")) s_js.getString("server_web_renew") else "").apply()
                return true
            }
            if (serType == SettingsConstants.SERVER_TYPE_OVPN) {
                if (s_js.has("MultiCert")) {
                    config!!.ovpnCert = if (s_js.getBoolean("MultiCert")) s_js.getString("ovpnCertificate") else pref!!.getString(SettingsConstants.OPEN_VPN_CERT, "")
                } else {
                    config!!.ovpnCert = pref!!.getString(SettingsConstants.OPEN_VPN_CERT, "")
                }
            }
            if (netType == "HTTP") {
                if (mHost!!.isEmpty()) return false
                val useDefProxy: Boolean = p_js.getBoolean("UseDefProxy")
                val serverProxyHost: String = if(generateServerProxy(s_js)) s_js.getString("ProxyHost") else mHost
                val serverProxyPort: String = if(generateServerProxyPort(s_js)) s_js.getString("ProxyPort") else p_js.getString("SquidPort").ifEmpty { "80" }
                val proxy: String = p_js.getString("SquidProxy").ifEmpty { serverProxyHost }
                val SquidProxy: String = if (useDefProxy) serverProxyHost else proxy
                val front_query = p_js.getString("NetworkFrontQuery")
                val back_query = p_js.getString("NetworkBackQuery")
                val port = p_js.getString("SquidPort").ifEmpty { serverProxyPort }
                val SquidPort = if (useDefProxy) serverProxyPort else port
                config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_HTTP_PROXY)
                config!!.autoReplace = if (p_js.has("AutoReplace")) p_js.getBoolean("AutoReplace") else false
                config!!.setServerHost(mHost)
                config!!.setProxyHost(SquidProxy)
                if(p_js.has("NetworkPayload")) {
                    config!!.setPayload(p_js.getString("NetworkPayload"))
                } else{
                    config!!.setPayload("")
                }
                if (front_query.isEmpty() && back_query.isEmpty()) {
                    config!!.setIsQueryMode(false)
                } else if (back_query.isNotEmpty()) {
                    config!!.setIsQueryMode(true)
                    config!!.setBackQuery(back_query)
                    config!!.setFrontQuery("")
                } else if (front_query.isNotEmpty()) {
                    config!!.setIsQueryMode(true)
                    config!!.setFrontQuery(front_query)
                    config!!.setBackQuery("")
                }
                config!!.setProxyPort(SquidPort)
                if (s_js.getString("TcpPort").contains(":")) {
                    val split = s_js.getString("TcpPort").split(":")[0]
                    config!!.setServerPort(split)
                } else {
                    config!!.setServerPort(s_js.getString("TcpPort"))
                }
                if (p_js.getString("Name").contains("Direct") || p_js.getString("Name").contains("direct")) {
                    if (serType == SettingsConstants.SERVER_TYPE_OVPN) {
                        if (p_js.getString("NetworkPayload").isEmpty()) {
                            config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_DIRECT)
                        } else {
                            config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_DIRECT_PAYLOAD)
                        }
                    }
                    if (serType == SettingsConstants.SERVER_TYPE_SSH) {
                        if (p_js.getString("NetworkPayload").isEmpty()) {
                            config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_DIRECT)
                        } else {
                            config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_DIRECT_PAYLOAD)
                            if (s_js.getString("TcpPort").contains(":")) {
                                val split = s_js.getString("TcpPort").split(":")[1]
                                config!!.setServerPort(split)
                            } else {
                                config!!.setServerPort(s_js.getString("TcpPort"))
                            }
                        }
                    }
                }
            }
            if (netType == "SSL") {
                if (mHost!!.isEmpty()) return false
                val sslPort = p_js.getString("SSLPort").ifEmpty { s_js.getString("SSLPort") }
                config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_SSL)
                config!!.setSni(p_js.getString("SSLSNI"))
                config!!.setServerHost(mHost)
                config!!.setServerPort(sslPort)
            }
            if (netType == "SSL+PAYLOAD") {
                if (mHost!!.isEmpty()) return false
                config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_SSL_PAYLOAD)
                val sslPort = p_js.getString("SSLPort").ifEmpty { s_js.getString("SSLPort") }
                config!!.setSni(p_js.getString("SSLSNI"))
                config!!.setPayload(p_js.getString("SSLPayload"))
                config!!.setServerHost(mHost)
                config!!.setServerPort(sslPort)
            }
            if (netType == "SSL+PROXY") {
                if (mHost!!.isEmpty()) return false
                config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_SSL_PROXY)
                val useDefProxy: Boolean = p_js.getBoolean("UseDefProxy")
                val serverProxyHost: String = if(generateServerProxy(s_js)) s_js.getString("ProxyHost") else mHost
                val serverProxyPort: String = if(generateServerProxyPort(s_js)) s_js.getString("ProxyPort") else p_js.getString("SquidPort").ifEmpty { "80" }
                val proxy: String = p_js.getString("SquidProxy").ifEmpty { serverProxyHost }
                val SquidProxy: String = if (useDefProxy) serverProxyHost else proxy
                val port = p_js.getString("SquidPort").ifEmpty { serverProxyPort }
                val SquidPort = if (useDefProxy) serverProxyPort else port
                val sslPort = p_js.getString("SSLPort").ifEmpty { s_js.getString("SSLPort") }
                config!!.setSni(p_js.getString("SSLSNI"))
                config!!.setPayload(p_js.getString("SSLPayload"))
                config!!.setServerHost(mHost)
                config!!.setServerPort(sslPort)
                config!!.setProxyHost(SquidProxy)
                config!!.setProxyPort(SquidPort)
                config!!.autoReplace = if (p_js.has("AutoReplace")) p_js.getBoolean("AutoReplace") else false
            }
            if (serType == SettingsConstants.SERVER_TYPE_OVPN){
                if (config!!.ovpnCert.contains("http-proxy-option")){
                    config!!.setServerPort(if (s_js.getString("TcpPort").contains(":")) s_js.getString("TcpPort").split(":")[0] else s_js.getString("TcpPort"))
                }
                if (config!!.ovpnCert.contains("proto udp")) {
                    if (mHost!!.isEmpty()) return false
                    config!!.setPaylodType(SettingsConstants.PAYLOAD_TYPE_OVPN_UDP)
                    config!!.setServerHost(mHost)
                    if (s_js.getString("TcpPort").contains(":")) {
                        val port = s_js.getString("TcpPort").split(":")[1]
                        config!!.setServerPort(port)
                    } else {
                        config!!.setServerPort("53")
                    }
                }
            }
            config!!.serverName = s_js.getString("Name")
            config!!.payloadName = p_js.getString("Name")
            if (s_js.getBoolean("AutoLogIn")) {
                config!!.setUser(s_js.getString("Username"))
                config!!.setUserPass(s_js.getString("Password"))
            } else {
                val user = pref!!.getString("_screenUsername_key", "")
                val pass = pref!!.getString("_screenPassword_key", "")
                config!!.setUser(if (user!!.isEmpty()) "" else FileUtils.hideJson(user))
                config!!.setUserPass(if (pass!!.isEmpty()) "" else FileUtils.hideJson(pass))
            }
            editor!!.putString(SettingsConstants.SERVER_WEB_RENEW_KEY,if (s_js.has("server_web_renew")) s_js.getString("server_web_renew") else "").apply()
            editor!!.putString("IPHunter_pName", p_js.getString("Name")).apply()
            config!!.configIsAutoLogIn = s_js.getBoolean("AutoLogIn")
            if (p_js.has("Info") && !p_js.getString("Info").isEmpty()) {
                editor!!.putString("Network_info", p_js.getString("Info")).apply()
            }
            return true
        } catch (e: Exception) {
            editor!!.putBoolean("isRandom", false).apply()
            util.showToast("Error!", e.message)
            return false
        }
    }

    private fun generateServerProxy(js:JSONObject):Boolean{
        try {
            if (js.has("ProxyHost")){
                return FileUtils.showJson(js.getString("ProxyHost")).isNotEmpty()
            }
            return false
        }catch (e:Exception){
            return false
        }
    }
    private fun generateServerProxyPort(js:JSONObject):Boolean{
        try {
            if (js.has("ProxyPort")){
                return js.getString("ProxyPort").isNotEmpty()
            }
            return false
        }catch (e:Exception){
            return false
        }
    }

    private fun isConfigXpired(js:JSONObject): Boolean {
        try {
            if (js.has("Server_exp_box")){
                val mValidade: Long = js.getString("Server_exp").split("-")[0].toLong()
                if (mValidade > 0 && isValidadeExpirou(mValidade)) {
                    return true
                }
                return false
            }else{
                return false
            }
        }catch (e:Exception){
            return false
        }
    }

    private fun isValidadeExpirou(validadeDateMillis: Long): Boolean {
        if (validadeDateMillis == 0L) {
            return false
        }
        val date_atual = Calendar.getInstance().time.time
        if (date_atual >= validadeDateMillis) {
            return true
        }
        return false
    }


    private fun importCustomizeConfig(server: String?): Boolean {
        try {
            mainViewModel.appendCustomConfigServer(server!!)
            mainViewModel.reloadServerList()
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private fun importBatchConfig(server: String, subid: String = ""): Boolean {
        val subid2 = if(subid.isEmpty()){
            mainViewModel.subscriptionId
        }else{
            subid
        }
        val append = subid.isEmpty()

        var count = AngConfigManager.importBatchConfig(server, subid2, append)
        if (count <= 0) {
            count = AngConfigManager.importBatchConfig(Utils.decode(server), subid2, append)
        }
        if (count > 0) {
            AngConfigManager.migrateLegacyConfig()
        } else {
            addlogInfo("<font color='red'><b>" + "v2ray config failure!" + "</b>")
            return false
        }
        return true
    }

    protected fun loadV2rayConfig(){
        try {
            var conFigID =""

            if (config!!.getSecureString(SettingsConstants.CONFIG_V2RAY_ID).isNotEmpty()) {
                conFigID = FileUtils.decodeID(FileUtils.showJson(config!!.getSecureString(SettingsConstants.CONFIG_V2RAY_ID))).replace("[v2_id]", conFigID)
            }
            var conFigSNI =""
            if (config!!.getSecureString(SettingsConstants.SNI_V2RAY_KEY).isNotEmpty()) {
                conFigSNI = FileUtils.showJson(config!!.getSecureString(SettingsConstants.SNI_V2RAY_KEY))
            }

            val conFig = FileUtils.showJson(config!!.getSecureString(SettingsConstants.CONFIG_V2RAY)).replace("[v2_id]", conFigID).replace("[v2_host]", conFigSNI)
            //addlogInfo(conFig)
            if (!importCustomizeConfig(conFig)){
                if(!importBatchConfig(conFig)){
                    addlogInfo("<font color='red'><b>" + "V2RAY config failure!" + "</b>")
                    hLogStatus.updateStateString(hLogStatus.VPN_DISCONNECTED, resString(R.string.state_disconnected))
                }
            }
        }catch (e: Exception){
            hLogStatus.updateStateString(hLogStatus.VPN_DISCONNECTED, getString(R.string.state_disconnected))
            addlogInfo(e.toString())
        }
    }

    private fun copyAssets() {
        val extFolder = Utils.userAssetPath(this@OpenVPNClientBase)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val geo = arrayOf("geosite.dat", "geoip.dat")
                assets.list("")
                    ?.filter { geo.contains(it) }
                    ?.filter { !File(extFolder, it).exists() }
                    ?.forEach {
                        val target = File(extFolder, it)
                        assets.open(it).use { input ->
                            FileOutputStream(target).use { output ->
                                input.copyTo(output)
                            }
                        }
                        hLogStatus.logDebug("Copied from apk assets folder to ${target.absolutePath}")
                    }
            } catch (e: Exception) {
                addlogInfo("asset copy failed! "+ e.message)
            }
        }
    }

    protected fun loadV2RaySetups(){
        v2rayRegisterUnregisterReceiver(true)
        settingsViewModel.startListenPreferenceChange()
        copyAssets()
        migrateLegacy()
    }

    protected fun reloadV2RAY(){
        mainViewModel.reloadServerList()
    }

    private fun migrateLegacy() {
        if(config!!.getSecureString(SettingsConstants.CONFIG_V2RAY).isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val result = AngConfigManager.migrateLegacyConfig()
                if (result != null) {
                    launch(Dispatchers.Main) {
                        if (result) {
                            mainViewModel.reloadServerList()
                        } else {
                            util.showToast(resources.getString(R.string.app_name), "Data migration failed!")
                        }
                    }
                }
            }
        }
    }

    protected fun removeServer():Boolean {
        try {
            mainViewModel.clearV2rayServers()
            mainViewModel.reloadServerList()
            return true
        }catch (ex:Exception){
            addlogInfo("<font color = #FF9600>error! $ex")
            return false
        }
    }

    protected fun clearAllTestDelay(){
        if (config?.serverType.equals(SettingsConstants.SERVER_TYPE_V2RAY)){
            mainViewModel.clearAllTestDelay()
        }
    }

    @SuppressLint("NewApi")
    protected fun v2rayRegisterUnregisterReceiver(register:Boolean) {
        when (register) {
            true -> {
                ContextCompat.registerReceiver(this@OpenVPNClientBase, mMsgReceiver, IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY), ContextCompat.RECEIVER_EXPORTED)
                MessageUtil.sendMsg2Service(this@OpenVPNClientBase, AppConfig.MSG_REGISTER_CLIENT, "")
            }
            false -> {
                unregisterReceiver(mMsgReceiver)
            }
        }
    }

    private val mMsgReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING -> {
                    isRunning.value = true
                }
                AppConfig.MSG_STATE_NOT_RUNNING -> {
                    isRunning.value = false
                }
                AppConfig.MSG_STATE_START_SUCCESS -> {
                    hLogStatus.updateStateString(hLogStatus.VPN_CONNECTED, ctx?.resources?.getString(R.string.state_connected))
                    hLogStatus.logInfo("Starting VPN Service")
                    hLogStatus.logInfo("<font color = #68B86B>VPNService Connected")
                    hLogStatus.logInfo("Checking connection.")
                    MessageUtil.sendMsg2Service(this@OpenVPNClientBase, AppConfig.MSG_MEASURE_DELAY, "")
                    isRunning.value = true
                }
                AppConfig.MSG_STATE_START_FAILURE -> {
                    val mServer_type:String = config!!.serverType
                    updateTestResultAction.value = intent.getStringExtra("content")
                    hLogStatus.logInfo(mServer_type +" "+ Objects.toString(updateTestResultAction.value))
                    hLogStatus.logInfo(mServer_type +" "+ ctx?.resources?.getString(R.string.toast_services_failure))
                    startService(Intent(ctx, HarlieService::class.java).setAction(HarlieService.STOP_SERVICE))
                    isRunning.value = false
                }
                AppConfig.MSG_STATE_STOP_SUCCESS -> {
                    if (isRunning.value == true){
                        hLogStatus.logInfo(config!!.serverType+" " + ctx?.resources?.getString(R.string.state_disconnected))
                        hLogStatus.logInfo("<b>VPNService stopped</b>")
                    }
                    isRunning.value = false
                }
                AppConfig.MSG_MEASURE_DELAY_SUCCESS -> {
                    updateTestResultAction.value = intent.getStringExtra("content")
                    val result_value:String = Objects.toString(updateTestResultAction.value)
                    hLogStatus.logInfo("<b>"+config!!.serverType+"</b> "+result_value)
                    if(hLogStatus.isTunnelActive() && isRunning.value == true){
                        if(result_value.contains("Fail to detect internet connection")){
                            startService(Intent(ctx, HarlieService::class.java).setAction("CONNECTION_TEST_FAILD"))
                        }else{
                            if (pref!!.getBoolean("isAutoPinger", false)){
                                startService(Intent(ctx, HarlieService::class.java).setAction("START_PINGER"))
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "OpenVPNClientBase"
    }

    protected fun init_default_preferences(oEditor: PrefUtil) {
        oEditor.set_string("pref_vpn_proxy_address", "127.0.0.1:8989")
        oEditor.set_string("vpn_proto", "adaptive")
        oEditor.set_string("ipv6", "default")
        oEditor.set_string("conn_timeout", "15")
        oEditor.set_string("compression_mode", "yes")
        oEditor.set_string("tls_version_min_override", "default")
        oEditor.set_boolean("google_dns_fallback", true)
        oEditor.set_boolean("autostart_finish_on_connect", true)
    }

    private fun register_connectivity_receiver() {
        try{
            mDeviceRebootReceiver = deviceRebootReceiver()
            mDeviceStateReceiver = DeviceStateReceiver(this@OpenVPNClientBase)
            val filter = IntentFilter()
            filter.addAction("android.intent.action.SCREEN_ON")
            filter.addAction("android.intent.action.SCREEN_OFF")
            ContextCompat.registerReceiver(this@OpenVPNClientBase,mDeviceRebootReceiver,filter,ContextCompat.RECEIVER_EXPORTED)
            mDeviceStateReceiver!!.register()
        }catch (ignored:Exception){}
    }

    private fun unregister_connectivity_receiver() {
        try{
            if (mDeviceRebootReceiver != null) {
                unregisterReceiver(mDeviceRebootReceiver)
            }
            if (mDeviceStateReceiver != null) {
                mDeviceStateReceiver!!.unregister()
            }
        }catch (ignored:Exception){}
    }


}

package dev.xoventech.tunnel.vpn;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import app.openconnect.core.FragCache;
import app.openconnect.core.ProfileManager;
import dev.xoventech.tunnel.vpn.activities.OpenVPNClient;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.utils.util;
import com.tencent.mmkv.MMKV;

public class harliesApplication extends Application
{
    private static SharedPreferences privateSharedPreferences;
    private static SharedPreferences defaultSharedPreferences;
    private static harliesApplication MainApp;
    private static final String PREFS_PRIVATE_KEY = "_PREFS_PRIVATE_KEY";
    private static final String PREFS_DEFAULT_KEY = "_PREFS_DEFAULT_KEY";
    protected boolean showUnSupportToast = true;

    @Override
    public void onCreate() {
        super.onCreate();
        MainApp = harliesApplication.this;
        TopExceptionHandler.init(harliesApplication.this);
        privateSharedPreferences = getSharedPreferences(PREFS_PRIVATE_KEY, MODE_PRIVATE);
        defaultSharedPreferences = getSharedPreferences(PREFS_DEFAULT_KEY, MODE_PRIVATE);
        ConfigUtil.getInstance(harliesApplication.this);
        ConfigUtil.setNotificationActivityClass(OpenVPNClient.class);
        new util(harliesApplication.this).overrideFont("SERIF", "montserrat_medium.ttf");
        MMKV.initialize(harliesApplication.this);
        checkProcessorModel();
        System.loadLibrary("openconnect");
        System.loadLibrary("stoken");
        ProfileManager.init(MainApp);
        FragCache.init();
    }

    public static harliesApplication getApp() {
        return MainApp;
    }

    public static String resString(int res_id) {
        return MainApp.getResources().getString(res_id);
    }

    public static SharedPreferences getDefaultSharedPreferences() {
        return defaultSharedPreferences;
    }
    public static SharedPreferences getPrivateSharedPreferences() {
        return privateSharedPreferences;
    }
    private void checkProcessorModel() {
        if(showUnSupportToast){
            String processorModel = Build.CPU_ABI;
            if (processorModel.equals("x86") || processorModel.equals("x86_64")) {
                Toast.makeText(this, "هشدار! این مدل از پردازنده برای اتصال به سیسکو پشتیبانی نمیشود.", Toast.LENGTH_LONG).show();
            }
        }
    }
        
}

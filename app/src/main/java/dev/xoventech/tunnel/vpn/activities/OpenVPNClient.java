package dev.xoventech.tunnel.vpn.activities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.util.Arrays;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import app.openconnect.OcsVpnProfile;
import app.openconnect.core.OCService;
import app.openconnect.core.OpenConnectManagementThread;
import app.openconnect.core.ProfileManager;
import dev.xoventech.tunnel.vpn.R;
import dev.xoventech.tunnel.vpn.adapter.ConfigSpinnerAdapter;
import dev.xoventech.tunnel.vpn.adapter.LogsAdapter;
import dev.xoventech.tunnel.vpn.config.ConfigDataBase;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.config.SettingsConstants;
import dev.xoventech.tunnel.vpn.core.vpnutils.TunnelUtils;
import dev.xoventech.tunnel.vpn.harliesApplication;
import dev.xoventech.tunnel.vpn.logger.ConnectionStatus;
import dev.xoventech.tunnel.vpn.logger.hLogStatus;
import dev.xoventech.tunnel.vpn.service.HarlieService;
import dev.xoventech.tunnel.vpn.service.OpenVPNService;
import dev.xoventech.tunnel.vpn.service.OpenVPNService.ConnectionStats;
import dev.xoventech.tunnel.vpn.thread.ExpireDate;
import dev.xoventech.tunnel.vpn.thread.checkUpdate;
import dev.xoventech.tunnel.vpn.utils.FileUtils;
import dev.xoventech.tunnel.vpn.utils.PasswordUtil;
import dev.xoventech.tunnel.vpn.utils.PrefUtil;
import dev.xoventech.tunnel.vpn.utils.util;
import dev.xoventech.tunnel.vpn.view.CircleProgressBar;
import dev.xoventech.tunnel.vpn.view.RotateLoading;
import dev.xoventech.tunnel.vpn.view.StatisticGraphData;
import app.openconnect.core.VPNConnector;
import io.michaelrocks.paranoid.Obfuscate;

@Obfuscate
@SuppressLint("NewApi")
public class OpenVPNClient extends OpenVPNClientBase implements NavigationView.OnNavigationItemSelectedListener,hLogStatus.StateListener, SettingsConstants,hLogStatus.ByteCountListener,OnClickListener {

    private VPNConnector mConn;
    private int mConnectionState = OpenConnectManagementThread.STATE_DISCONNECTED;
    private OcsVpnProfile ocsVP;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private ViewPager viewPager;
    private CheckBox mDNS, mVoid;
    private LogsAdapter mAdapter;
    private BottomNavigationView bottomNavigationView;
    private RecyclerView logRecycle;
    private ImageView mDrawerMenu;
    private RelativeLayout serverDialog, networkDialog, _notice;
    private Button btn_connector;
    private RotateLoading mRotateLoading;
    private Animation animation;
    private CircleProgressBar circleProgressBar;
    private TextView tunnel_type, duration_view, byteIn_view, byteOut_view, status_view, Config_vers, s_name, p_name, ac_xp;
    public static final int START_BIND_CALLED = 1;
    private static final int REQUEST_IMPORT_FILE = 2;
    private Handler mHandler;
    private AlertDialog cBuiler;
    private CheckBox pingbox;
    private static boolean isConnected = false;
    private PrefUtil prefs;
    private EditText xUser, xPass;
    private ConfigDataBase exported_config;
    private final Handler stats_timer_handler = new Handler();
    private final Runnable stats_timer_task = new Runnable() {
        public void run() {
            if (hLogStatus.isTunnelActive()) {
                duration_view.setText(getUpDateBytes().isConnected() ? getUpDateBytes().elapsedTimeToDisplay(getUpDateBytes().getElapsedTime()) : "00:00:00");
            }
            OpenVPNClient.this.show_stats();
            OpenVPNClient.this.schedule_stats();
        }
    };

    private void cancel_stats() {
        this.stats_timer_handler.removeCallbacks(this.stats_timer_task);
    }

    private void schedule_stats() {
        cancel_stats();
        this.stats_timer_handler.postDelayed(this.stats_timer_task, 1000);
    }

    private static long m_SentBytes = 0;
    private static long m_ReceivedBytes = 0;

    private boolean isMostrarSenha = false;

    public void show_stats() {
        try {
            if (hLogStatus.isTunnelActive() && !isConnected) {
                if (getConfig().getServerType().equals(SERVER_TYPE_OVPN)) {
                    ConnectionStats stats = get_connection_stats();
                    hLogStatus.updateByteCount(stats.bytes_in, stats.bytes_out);
                } else {
                    m_ReceivedBytes += getUpDateBytes().getBytesReceived();
                    m_SentBytes += getUpDateBytes().getBytesSent();
                    hLogStatus.updateByteCount(m_ReceivedBytes, m_SentBytes);
                }
            }
            if (hLogStatus.isTunnelActive() && isConnected) {
                if (getConfig().getServerType().equals(SERVER_TYPE_OVPN)) {
                    ConnectionStats stats = get_connection_stats();
                    hLogStatus.updateByteCount(stats.bytes_in, stats.bytes_out);
                } else if (getConfig().getServerType().equals(SERVER_TYPE_V2RAY) || getConfig().getServerType().equals(SERVER_TYPE_UDP_HYSTERIA_V1)) {
                    m_ReceivedBytes += getUpDateBytes().getBytesReceived();
                    m_SentBytes += getUpDateBytes().getBytesSent();
                    hLogStatus.updateByteCount(m_ReceivedBytes, m_SentBytes);
                } else {
                    hLogStatus.updateByteCount(getUpDateBytes().getTotalBytesReceived(), getUpDateBytes().getTotalBytesSent());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void doUpdateLayout() {
        boolean isRunning = hLogStatus.isTunnelActive();
        xUser.setEnabled(!isRunning);
        xPass.setEnabled(!isRunning);
        serverDialog.setEnabled(!isRunning);
        networkDialog.setEnabled(!isRunning);
        ((TextView) findViewById(R.id.iplocal)).setText(util.getNetworkType() + TunnelUtils.getLocalIpAddress());
        setupBTNanimation(isRunning);
    }


    @Override
    public void updateState(String state, String logMessage, int localizedResId, ConnectionStatus level) {
        mHandler.post(() -> {
            isConnected = level.equals(ConnectionStatus.LEVEL_CONNECTED);
            status_view.setText(state);
            doUpdateLayout();
            if (isConnected) {
                if (getPref().getInt("loadOnce", 0) == 0) {
                    getEditor().putInt("loadOnce", 1).apply();
                    if (!getPref().getString("Server_message", "").isEmpty()) {
                        addlogInfo("<font color = #68B86B><br/><br/><b>SERVER INFORMATION!</b><br/><br/>" + getPref().getString("Server_message", "").replace("\n", "<br/>") + "<br/><br/>");
                    }
                    showExpireDate();
                    util.showToast(resString(R.string.app_name), "Connected successfully, enjoy 😊");
                }
            } else if (state.equals(resString(R.string.state_reconnecting))) {
                getEditor().putInt("loadOnce", 0).apply();
                if (getPref().getBoolean("isRandom", false)) reLoad_Configs();
            } else if (state.equals(resString(R.string.state_auth_failed))) {
                status_view.setTextColor(Color.RED);
                //accountDialog();
            }
        });
    }


    @Override
    public void updateByteCount(long in, long out, long diffIn, long diffOut) {
        byteIn_view.setText(ConfigUtil.render_bandwidth(in, false));
        byteOut_view.setText(ConfigUtil.render_bandwidth(out, false));
    }


    private static final String[] TABS = {"HOME", "LOGS"};

    private class pagerAdapter extends PagerAdapter {
        private final List<String> title;

        public pagerAdapter(List<String> pageStr) {
            title = pageStr;
        }

        @Override
        public int getCount() {
            return 2;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            int[] tabs = new int[]{R.id.layout1, R.id.layout2};
            return findViewById(tabs[position]);
        }

        @Override
        public boolean isViewFromObject(View p1, Object p2) {
            return p1 == p2;
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return title.get(position);
        }
    }

    private void loadIds() {
        cBuiler = new AlertDialog.Builder(this).create();
        mDrawerMenu = findViewById(R.id.mDrawerMenu);
        loadMainDrawer();
        viewPager = findViewById(R.id.mPager);
        ac_xp = findViewById(R.id.ac_xp);
        xUser = findViewById(R.id.x_username);
        xPass = findViewById(R.id.x_password);
        s_name = findViewById(R.id.select_server_name);
        p_name = findViewById(R.id.tweak_name);
        serverDialog = findViewById(R.id.select_server);
        networkDialog = findViewById(R.id.select_network);
        btn_connector = findViewById(R.id.btn_connect);
        mRotateLoading = findViewById(R.id.mRotateLoading);
        circleProgressBar = findViewById(R.id.circle_progress);
        duration_view = findViewById(R.id.duration);
        byteIn_view = findViewById(R.id.bytes_in);
        byteOut_view = findViewById(R.id.bytes_out);
        status_view = findViewById(R.id.status);
        Config_vers = findViewById(R.id.config_version);
        bottomNavigationView = findViewById(R.id.navigationView);
        tunnel_type = findViewById(R.id.tunnel_type);
        _notice = findViewById(R.id._notice);
        _notice.setOnClickListener(OpenVPNClient.this);
        findViewById(R.id.notice).setOnClickListener(OpenVPNClient.this);
        viewPager.setAdapter(new pagerAdapter(new ArrayList<>(Arrays.asList(TABS))));
        viewPager.setOffscreenPageLimit(2);
        logRecycle = findViewById(R.id.lRecyclerView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mAdapter = new LogsAdapter(layoutManager, this);
        logRecycle.setAdapter(mAdapter);
        logRecycle.setLayoutManager(layoutManager);
        mDrawerMenu.setOnClickListener(v -> open());
        xUser.setText(getPref().getString("_screenUsername_key", ""));
        xUser.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String u = xUser.getText().toString().trim();
                if (!getConfig().getConfigIsAutoLogIn())
                    getEditor().putString("_screenUsername_key", u).apply();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        xPass.setText(getPref().getString("_screenPassword_key", ""));
        xPass.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String u = xPass.getText().toString().trim();
                if (!getConfig().getConfigIsAutoLogIn() && !u.equals("******"))
                    getEditor().putString("_screenPassword_key", u).apply();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        /*findViewById(R.id.password_ic).setOnClickListener(v -> {
            isMostrarSenha = !isMostrarSenha;
            if (isMostrarSenha) {
                xPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ((ImageView) findViewById(R.id.password_ic)).setImageResource(R.drawable.ic_visibility_grey_900_24dp);
            } else {
                xPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ((ImageView) findViewById(R.id.password_ic)).setImageResource(R.drawable.ic_visibility_off_grey_900_24dp);
            }
        });*/

        BottomNavigationView bottomNavigationView = findViewById(R.id.navigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(menuItem -> {
            int item = menuItem.getItemId();
            if (item == R.id.a_update) {
                mUpdate();
                return true;
            } else if (item == R.id.a_tele) {
                mTelegram();
                return true;
            } else if (item == R.id.a_exit) {
                if (cBuiler != null) if (cBuiler.isShowing()) cBuiler.dismiss();
                View inflate = LayoutInflater.from(OpenVPNClient.this).inflate(R.layout.notification_dialog, null);
                cBuiler = new AlertDialog.Builder(OpenVPNClient.this).create();
                ((AppCompatImageView) inflate.findViewById(R.id.notification_icon)).setImageResource(R.drawable.icon_icon);
                ((TextView) inflate.findViewById(R.id.notification_title)).setText("Exit/Minimize");
                ((TextView) inflate.findViewById(R.id.notification_message)).setText("Do you want to minimize or exit?");
                TextView no = inflate.findViewById(R.id.notification_btn_no);
                TextView yes = inflate.findViewById(R.id.notification_btn_yes);
                no.setText("Minimize");
                yes.setText("Exit");
                no.setOnClickListener(p1 -> {
                    Intent startMain = new Intent(Intent.ACTION_MAIN);
                    startMain.addCategory(Intent.CATEGORY_HOME);
                    startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(startMain);
                    cBuiler.dismiss();
                });
                yes.setOnClickListener(p1 -> {
                    if (HarlieService.isVPNRunning()) stopTunnelService();
                    finishAndRemoveTask();
                    System.exit(0);
                    cBuiler.dismiss();
                });
                cBuiler.setView(inflate);
                cBuiler.setCancelable(true);
                cBuiler.show();
            }
            return true;
        });
    }


    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        setContentView(R.layout.main);
        exported_config = new ConfigDataBase(OpenVPNClient.this, "ExportedConfigData");
        new util(OpenVPNClient.this);
        mHandler = new Handler();
        prefs = new PrefUtil(harliesApplication.getDefaultSharedPreferences());
        new PasswordUtil(harliesApplication.getDefaultSharedPreferences());
        doBindService();
        LoadDefaultConfig();
        findViewById(R.id.main_window_bg).setBackgroundColor(getConfig().getMainLayoutBG());
        ocsVP = ProfileManager.create("127.0.0.1");
        animation = AnimationUtils.loadAnimation(this, R.anim.blink);
        loadIds();
        loadV2RaySetups();
        mDNS = findViewById(R.id.dns_forward);
        mDNS.setOnClickListener(view -> getConfig().setVpnDnsForward(mDNS.isChecked()));
        mVoid = findViewById(R.id.udp_forward);
        mVoid.setOnClickListener(view -> getConfig().setVpnUdpForward(mVoid.isChecked()));
        serverDialog.setOnClickListener(OpenVPNClient.this);
        networkDialog.setOnClickListener(OpenVPNClient.this);
        btn_connector.setOnClickListener(OpenVPNClient.this);
        submitReloadProfileIntent(getPref().getString(SERVER_TYPE_OVPN, "[]"));
        /*ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(() -> runOnUiThread(() -> {
            try {
                if (!isCheckUpdateIsRunning) autoUpdate();
                if (isConnected) showExpireDate();
                util.isNetworkAvailable(OpenVPNClient.this);
            } catch (Exception e) {
                hLogStatus.logDebug(e.getMessage());
            }
        }), 0, 120, TimeUnit.SECONDS);*/
        if (ActivityCompat.checkSelfPermission(OpenVPNClient.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1234);
        }
        mFirstNotes();
    }


    private void inboxNotification(int icon, String msg) {
        Notification.Builder mBuilder = new Notification.Builder(OpenVPNClient.this)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.icon_icon))
                .setSmallIcon(icon)
                .setContentTitle("Message Received")
                .setContentText(msg)
                .setAutoCancel(true);
        Notification.BigTextStyle inboxStyle = new Notification.BigTextStyle();
        inboxStyle.setBigContentTitle("New config release");
        inboxStyle.bigText(msg);
        mBuilder.setStyle(inboxStyle);
        Intent intent = getIntent();
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(OpenVPNClient.this);
        stackBuilder.addNextIntent(intent);
        mBuilder.setContentIntent(ConfigUtil.getPendingIntent(OpenVPNClient.this));
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name_userreq);
            NotificationChannel mChannel = new NotificationChannel("openvpn_userreq",name, NotificationManager.IMPORTANCE_HIGH);
            mChannel.setDescription(resString(R.string.channel_description_userreq));
            mChannel.enableVibration(true);
            mChannel.setLightColor(Color.CYAN);
            mBuilder.setChannelId("openvpn_userreq");
            if (mNotificationManager != null) {
                mNotificationManager.createNotificationChannel(mChannel);
            }
        } else {
            mBuilder.setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
        }
        if (mNotificationManager != null) {
            mNotificationManager.notify(3, mBuilder.build());
        }
    }

    private boolean isCheckUpdateIsRunning = false;
    private void autoUpdate(){
        if (!util.isNetworkAvailable(OpenVPNClient.this))return;
        isCheckUpdateIsRunning = true;
        String a = getPref().getString(CONFIG_URL,"");
        new checkUpdate(a, new checkUpdate.Listener() {
            @Override
            public void onError(String config) {
                isCheckUpdateIsRunning = false;
            }
            @Override
            public void onCompleted(final String config)
            {
                isCheckUpdateIsRunning = false;
                String mData = FileUtils.showJson(config);
                try{
                    exported_config.updateData("1", config);
                    JSONArray sjarr = new JSONArray();
                    JSONArray pjarr = new JSONArray();
                    JSONObject obj = new JSONObject(mData);
                    if (getConfig().getVersionCompare(obj.getString("Version"),getPref().getString(CONFIG_VERSION,"0"))){
                        if (addOrEditedServers().length()!=0)for (int i=0;i < addOrEditedServers().length();i++) {
                            sjarr.put(addOrEditedServers().getJSONObject(i));
                        }
                        if (obj.getJSONArray("Servers").length()!=0)for (int i=0;i < obj.getJSONArray("Servers").length();i++) {
                            sjarr.put(obj.getJSONArray("Servers").getJSONObject(i));
                        }
                        if (addOrEditedNetwork().length()!=0)for (int i=0;i < addOrEditedNetwork().length();i++) {
                            pjarr.put(addOrEditedNetwork().getJSONObject(i));
                        }
                        if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                            pjarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                        }
                        if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                            pjarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                        }
                        getServerData().updateData("1", sjarr.toString());
                        getNetworkData().updateData("1", pjarr.toString());
                        loadServerArrayDragaPosition();
                        loadNetworkArrayDragaPosition();
                        getEditor().putInt(SERVER_POSITION,0).apply();
                        getEditor().putInt(NETWORK_POSITION,0).apply();
                        getEditor().putString("CONFIG_FILE_NAME", obj.has("FileName")?obj.getString("FileName"):"Exported Config").apply();
                        getEditor().putString(CONFIG_VERSION, obj.getString("Version")).apply();
                        getEditor().putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                        getEditor().putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                        getEditor().putString(OPEN_VPN_CERT, obj.getString("Ovpn_Cert")).apply();
                        getEditor().putString(CONFIG_URL,FileUtils.showJson(obj.getString("config_url"))).apply();
                        getEditor().putString(CONFIG_API,obj.has("account_api")?FileUtils.showJson(obj.getString("account_api")):"").apply();
                        getEditor().putString(UPLOAD_GET_API,obj.has("upload_get_api")?FileUtils.showJson(obj.getString("upload_get_api")):"").apply();
                        getEditor().putString(UPLOAD_POST_API,obj.has("upload_post_api")?FileUtils.showJson(obj.getString("upload_post_api")):"").apply();
                        getEditor().putString(CONFIG_EDITOR_CODE,obj.has("AppConfPass")?FileUtils.showJson(obj.getString("AppConfPass")):"").apply();
                        if(obj.has("JSONsettings"))getJSONsettings(obj.getJSONArray("JSONsettings").toString());
                        getEditor().putBoolean("isRandom", false).apply();
                        getEditor().putBoolean("isAdminAccept", false).apply();
                        doUpdateLayout();
                        loadConfigurations();
                        Config_vers.setText(obj.getString("Version"));
                        _notice.setVisibility(View.VISIBLE);
                        inboxNotification(R.drawable.icon_icon, obj.getString("ReleaseNotes"));
                        submitReloadProfileIntent(getPref().getString(SERVER_TYPE_OVPN,"[]"));
                    }
                }catch (Exception e){
                    isCheckUpdateIsRunning = false;
                }
            }
        }).start();
    }


    private void mUpdate(){
        new util(OpenVPNClient.this);
        isCheckUpdateIsRunning = true;
        String a = getPref().getString(CONFIG_URL,"");
        Toast.makeText(OpenVPNClient.this, "Checking Updates", Toast.LENGTH_LONG).show();
        new checkUpdate(a, new checkUpdate.Listener() {
            @Override
            public void onError(String config) {
                isCheckUpdateIsRunning = false;
                util.showToast("Oppss...!", config);
            }
            @Override
            public void onCompleted(final String config) {
                isCheckUpdateIsRunning = false;
                String mData = FileUtils.showJson(config);
                try{
                    exported_config.updateData("1", config);
                    JSONArray sjarr = new JSONArray();
                    JSONArray pjarr = new JSONArray();
                    JSONObject obj = new JSONObject(mData);
                    if (getConfig().getVersionCompare(obj.getString("Version"),getPref().getString(CONFIG_VERSION,"0"))){
                        if (addOrEditedServers().length()!=0)for (int i=0;i < addOrEditedServers().length();i++) {
                            sjarr.put(addOrEditedServers().getJSONObject(i));
                        }
                        if (obj.getJSONArray("Servers").length()!=0)for (int i=0;i < obj.getJSONArray("Servers").length();i++) {
                            sjarr.put(obj.getJSONArray("Servers").getJSONObject(i));
                        }
                        if (addOrEditedNetwork().length()!=0)for (int i=0;i < addOrEditedNetwork().length();i++) {
                            pjarr.put(addOrEditedNetwork().getJSONObject(i));
                        }
                        if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                            pjarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                        }
                        if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                            pjarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                        }
                        getServerData().updateData("1", sjarr.toString());
                        getNetworkData().updateData("1", pjarr.toString());
                        loadServerArrayDragaPosition();
                        loadNetworkArrayDragaPosition();
                        getEditor().putInt(SERVER_POSITION,0).apply();
                        getEditor().putInt(NETWORK_POSITION,0).apply();
                        getEditor().putString("CONFIG_FILE_NAME", obj.has("FileName")?obj.getString("FileName"):"Exported Config").apply();
                        getEditor().putString(CONFIG_VERSION, obj.getString("Version")).apply();
                        getEditor().putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                        getEditor().putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                        getEditor().putString(OPEN_VPN_CERT, obj.getString("Ovpn_Cert")).apply();
                        getEditor().putString(CONFIG_URL,FileUtils.showJson(obj.getString("config_url"))).apply();
                        getEditor().putString(CONFIG_API,obj.has("account_api")?FileUtils.showJson(obj.getString("account_api")):"").apply();
                        getEditor().putString(UPLOAD_GET_API,obj.has("upload_get_api")?FileUtils.showJson(obj.getString("upload_get_api")):"").apply();
                        getEditor().putString(UPLOAD_POST_API,obj.has("upload_post_api")?FileUtils.showJson(obj.getString("upload_post_api")):"").apply();
                        getEditor().putString(CONFIG_EDITOR_CODE,obj.has("AppConfPass")?FileUtils.showJson(obj.getString("AppConfPass")):"").apply();
                        if(obj.has("JSONsettings"))getJSONsettings(obj.getJSONArray("JSONsettings").toString());
                        getEditor().putBoolean("isRandom", false).apply();
                        getEditor().putBoolean("isAdminAccept", false).apply();
                        doUpdateLayout();
                        loadConfigurations();
                        Config_vers.setText(obj.getString("Version"));
                        _notice.setVisibility(View.VISIBLE);
                        submitReloadProfileIntent(getPref().getString(SERVER_TYPE_OVPN,"[]"));
                    }else{
                        _notice.setVisibility(View.VISIBLE);
                        Toast.makeText(OpenVPNClient.this, "No Update available!", Toast.LENGTH_LONG).show();
                    }
                }catch (Exception e){
                    isCheckUpdateIsRunning = false;
                    util.showToast("Error...!", e.getMessage());
                }
            }
        }).start();
    }


    private void mImport(){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT_FILE);
    }

    private void LoadDefaultConfig(){
        boolean showFirstTime = getPref().getBoolean("connect_first_time", true);
        if (showFirstTime) {
            init_default_preferences(prefs);
            try {
                String data = FileUtils.readFromAsset(OpenVPNClient.this,"mtk.hs");
                JSONObject obj = new JSONObject(data);
                JSONArray jarr = new JSONArray();
                if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                    jarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                }
                if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                    jarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                }
                if (jarr.length()==0){
                    getNetworkData().insertData("[]");
                }else if (jarr.length()!=0){
                    getNetworkData().insertData(jarr.toString());
                }
                getServerData().insertData(obj.getJSONArray("Servers").toString());
                loadServerArrayDragaPosition();
                loadNetworkArrayDragaPosition();
                exported_config.insertData("[]");
                getEditor().putString("CONFIG_FILE_NAME", obj.has("FileName")?obj.getString("FileName"):"Exported Config").apply();
                getEditor().putString(CONFIG_VERSION, obj.getString("Version")).apply();
                getEditor().putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                getEditor().putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                getEditor().putString(OPEN_VPN_CERT, obj.getString("Ovpn_Cert")).apply();
                getEditor().putString(CONFIG_URL,FileUtils.showJson(obj.getString("config_url"))).apply();
                getEditor().putString(CONFIG_API,obj.has("account_api")?FileUtils.showJson(obj.getString("account_api")):"").apply();
                getEditor().putString(UPLOAD_GET_API,obj.has("upload_get_api")?FileUtils.showJson(obj.getString("upload_get_api")):"").apply();
                getEditor().putString(UPLOAD_POST_API,obj.has("upload_post_api")?FileUtils.showJson(obj.getString("upload_post_api")):"").apply();
                getEditor().putString(CONFIG_EDITOR_CODE,obj.has("AppConfPass")?FileUtils.showJson(obj.getString("AppConfPass")):"").apply();
                if(obj.has("JSONsettings"))getJSONsettings(obj.getJSONArray("JSONsettings").toString());
                getEditor().putBoolean("isRandom", false).apply();
                getEditor().putBoolean("isAdminAccept", false).apply();
                reLoad_Configs();
                getEditor().putBoolean("connect_first_time",false).apply();
            } catch (Exception e) {
                util.showToast("LoadDefaultConfig Error!", e.getMessage());
            }
        }
    }


    private boolean checkConfiguration(){
//        if(!util.isMyApp()){
//            submitDisconnectIntent();
//            util.showToast("Oppss...!", new String(new byte[]{80,108,97,101,115,101,32,105,110,115,116,97,108,108,32,116,104,101,32,111,114,105,103,105,110,97,108,})+" "+resString(R.string.app_name));
//            addlogInfo("<font color = #d50000>"+new String(new byte[]{80,108,97,101,115,101,32,105,110,115,116,97,108,108,32,116,104,101,32,111,114,105,103,105,110,97,108,})+" "+resString(R.string.app_name));
//            return false;
//        }
//        else
            if(!reLoad_Configs()){
            util.showToast("Oppss...!","Config load error!");
            return false;
        }
        else if (!util.isNetworkAvailable(OpenVPNClient.this)) {
            util.showToast("Oppss...!", "Please connect to the internet");
            return false;
        }
        else if (getConfig().getConfigIsAutoLogIn()) {
            if (getConfig().getSecureString(USERNAME_KEY).isEmpty() || getConfig().getSecureString(PASSWORD_KEY).isEmpty()) {
                util.showToast("Oppss...!", "Server Account is empty!");
                //accountDialog();
                return false;
            }
            return true;
        }
        else if (getConfig().getSecureString(USERNAME_KEY).isEmpty() || getConfig().getSecureString(PASSWORD_KEY).isEmpty()) {
            util.showToast("Oppss...!", "Server Account is empty!");
            //accountDialog();
            return false;
        }
        return true;
    }


    private void showConfigInfo(){
        if(cBuiler!=null)if(cBuiler.isShowing())cBuiler.dismiss();
        View inflate = LayoutInflater.from(this).inflate(R.layout.notif2, null);
        cBuiler = new AlertDialog.Builder(this).create();
        RelativeLayout btn = inflate.findViewById(R.id.appButton2);
        TextView title = inflate.findViewById(R.id.notiftext1);
        TextView ms = inflate.findViewById(R.id.confimsg);
        TextView ok = inflate.findViewById(R.id.appButton2txt);
        TextView cancel = inflate.findViewById(R.id.appButton1);
        ms.setTextColor(getConfig().gettextColor());
        cancel.setTextColor(getConfig().getColorAccent());
        inflate.findViewById(R.id.color_bg).setBackgroundColor(getConfig().getColorAccent());
        btn.setBackgroundTintList(ColorStateList.valueOf(getConfig().getColorAccent()));
        title.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        ok.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        title.setText(getConfig().getPayloadName());
        ms.setText(getPref().getString("Network_info",""));
        ok.setText("Continue");
        cancel.setText("Cancel");
        btn.setOnClickListener(p1 -> {
            start_connect();
            cBuiler.dismiss();
        });
        inflate.findViewById(R.id.appButton0).setOnClickListener(p1 -> {
            if (HarlieService.isVPNRunning())stopTunnelService();
            cancel_stats();
            cBuiler.dismiss();
        });
        cBuiler.setView(inflate);
        cBuiler.setCancelable(false);
        cBuiler.getWindow().getAttributes().windowAnimations = R.style.alertDialog;
        cBuiler.show();
    }

    public void stopTunnelService(){
        m_SentBytes = 0;
        m_ReceivedBytes = 0;
        getEditor().putInt("loadOnce",0).apply();
        submitDisconnectIntent();
        if (getConfig().getServerType().equals(SERVER_TYPE_OPEN_CONNECT)){
            mConn.service.stopVPN();
        }
    }

    private void stop_service() {
        hLogStatus.removeStateListener(this);
        hLogStatus.removeByteCountListener(this);
    }

    private void stop() {
        v2rayRegisterUnregisterReceiver(false);
        stop_service();
        doUnbindService();
    }

    @Override
    public void onBackPressed() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    @Override
    protected void onPause() {
        mConn.stopActiveDialog();
        mConn.unbind();
        super.onPause();
    }

    @Override
    protected void onResume() {
        hLogStatus.addStateListener(this);
        hLogStatus.addByteCountListener(this);
        if(hLogStatus.isTunnelActive())schedule_stats();
        if (isDrawerOpen())close();
        mConn = new VPNConnector(this, true) {
            @Override
            public void onUpdate(OCService service) {
                updateUI(service);
            }
        };
        autoUpdate();
        Config_vers.setText(getPref().getString(CONFIG_VERSION,"1.1"));
        doUpdateLayout();
        loadConfigurations();
        if (getConfig().getServerType().equals(SERVER_TYPE_V2RAY))loadV2rayConfig();
        if (getConfig().getServerType().equals(SERVER_TYPE_V2RAY))reloadV2RAY();
        mDNS.setChecked(getConfig().getVpnDnsForward());
        mVoid.setChecked(getConfig().getVpnUdpForward());
        ac_xp.setText(getPref().getString("_AccountXp",date));
        super.onResume();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel_stats();
        stop();
    }

    private void updateUI(OCService service) {
        int newState = service.getConnectionState();
        service.startActiveDialog(this);
        if (mConnectionState != newState) {
            /*if (newState == OpenConnectManagementThread.STATE_DISCONNECTED) {
                mConnectionTab.replace(R.string.vpn_list_title, new VPNProfileList());
            } else if (mConnectionState == OpenConnectManagementThread.STATE_DISCONNECTED) {
                mConnectionTab.replace(R.string.status, new StatusFragment());
            }*/
            mConnectionState = newState;
        }
    }

    public void onClick(View v) {
        int viewid = v.getId();
        if (viewid == R.id._notice) {
            _notice.setVisibility(View.GONE);
            mReleaseNotes(getPref().getString(RELEASE_NOTE,""));
        } else if (viewid == R.id.notice) {
            _notice.setVisibility(View.GONE);
            mReleaseNotes(getPref().getString(RELEASE_NOTE,""));
        } else if (viewid == R.id.btn_connect) {
            startOrStopTunnel();
        } else if (viewid == R.id.select_server) {
            startActivity(new Intent(OpenVPNClient.this, ConfigSpinnerAdapter.class).putExtra("mConfigType","0"));
        } else if (viewid == R.id.select_network) {
            startActivity(new Intent(OpenVPNClient.this, ConfigSpinnerAdapter.class).putExtra("mConfigType","1"));
        }
    }

    @Override
    public void startOpenVPN() {
        if (getConfig().getServerType().equals(SERVER_TYPE_OPEN_CONNECT)){
            ocsVP = ProfileManager.create(getConfig().getSecureString(SERVER_KEY));
            startOCService(ocsVP);
        }else{
            resolve_epki_alias_then_connect();
        }
        super.startOpenVPN();
    }

    private void startOrStopTunnel() {
        getEditor().putInt("loadOnce",0).apply();
        m_SentBytes = 0;
        m_ReceivedBytes = 0;
        if (getConfig().getServerType().equals(SERVER_TYPE_OPEN_CONNECT)){
            ocsVP = ProfileManager.create(getConfig().getSecureString(SERVER_KEY));
        }
        if (hLogStatus.isTunnelActive()){
            stopTunnelService();
            cancel_stats();
        }
        else{
            if (getConfig().getAutoClearLog())mAdapter.clearLog();
            if(checkConfiguration()){
                if (!getPref().getString("Network_info","").isEmpty() && !getConfig().getServerType().equals(SERVER_TYPE_V2RAY)) {
                    showConfigInfo();
                }else{
                    start_connect();
                }
            }
        }
    }


    private void startOCService(OcsVpnProfile profile) {
        Intent intent = new Intent(OpenVPNClient.this, OCService.class);
        intent.putExtra(OCService.EXTRA_UUID, profile.getUUID().toString());
        startService(intent);

        /*Intent intent = new Intent(OpenVPNClient.this, GrantPermissionsActivity.class);
        String pkg = OpenVPNClient.this.getPackageName();
        intent.putExtra(pkg + GrantPermissionsActivity.EXTRA_UUID, profile.getUUID().toString());
        intent.setAction(Intent.ACTION_MAIN);
        startActivity(intent);*/
    }

    private void start_connect() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            try {
                startActivityForResult(intent, START_BIND_CALLED);
                return;
            } catch (ActivityNotFoundException e) {
                // showSnack(getResources().getDrawable(R.drawable.ic_error),"CLI: requesting VPN actor rights failed", e.getMessage());
                return;
            }
        }
        if (getConfig().getServerType().equals(SERVER_TYPE_OPEN_CONNECT)){
            ocsVP = ProfileManager.create(getConfig().getSecureString(SERVER_KEY));
        }
        if(getConfig().getServerType().equals(SERVER_TYPE_V2RAY)&&removeServer()){
            startTunnelService();
            return;
        }
        String mhuntInfo = getPref().getString("IPHunter_pName", "");
        if (mhuntInfo.contains("Hunter") || mhuntInfo.contains("hunter") || mhuntInfo.contains("HUNTER") || mhuntInfo.contains("HUNT") || mhuntInfo.contains("hunt") || mhuntInfo.contains("Hunt")) {
            WarnIphunt();
            return;
        }
        startTunnelService();
    }


    private void startTunnelService(){
        TunnelUtils.restartRotateAndRandom();
        schedule_stats();
        StatisticGraphData.getStatisticData().getDataTransferStats().startConnected();
        showExpireDate();
        if (getConfig().getServerType().equals(SERVER_TYPE_V2RAY))loadV2rayConfig();
        startService(new Intent(OpenVPNClient.this, HarlieService.class).setAction(HarlieService.START_SERVICE));
    }

    private void WarnIphunt(){
        if(cBuiler!=null)if(cBuiler.isShowing())cBuiler.dismiss();
        View inflate = LayoutInflater.from(this).inflate(R.layout.notif2, null);
        final AlertDialog huntBuiler = new AlertDialog.Builder(this).create();
        TextView title = inflate.findViewById(R.id.notiftext1);
        final TextView ms = inflate.findViewById(R.id.confimsg);
        RelativeLayout btn = inflate.findViewById(R.id.appButton2);
        TextView cancel = inflate.findViewById(R.id.appButton1);
        final TextView ok = inflate.findViewById(R.id.appButton2txt);
        ms.setTextColor(getConfig().gettextColor());
        cancel.setTextColor(getConfig().getColorAccent());
        inflate.findViewById(R.id.color_bg).setBackgroundColor(getConfig().getColorAccent());
        btn.setBackgroundTintList(ColorStateList.valueOf(getConfig().getColorAccent()));
        title.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        ok.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        title.setText("GTM IP Hunter");
        ms.setText("To connect to GTM No Load No Blocking, Make sure that you are now in the Magic IP. Click the button to check your IP!");
        ok.setText("Hunt First");
        cancel.setText("Continue");
        btn.setOnClickListener(p1 -> {
            ms.setText("Please wait while we are checking your IP...");
            ok.setEnabled(false);
            ok.setText("Checking...");
            new Handler().postDelayed(() -> {
                try {
                    int l = 0;
                    URL whatismyip = new URL("http://noloadbalance.globe.com.ph");
                    String fail = "🚫 Disconnected. Please Airplane Mode On/Off and Try Again.";
                    try{
                        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("104.16.213.74", 80));
                        HttpURLConnection connection = (HttpURLConnection) whatismyip.openConnection(proxy);
                        connection.setRequestMethod("GET");
                        connection.connect();
                        connection.getContentLength();
                        connection.setConnectTimeout(3000);
                        InputStream in = connection.getInputStream();
                        byte[] buffer = new byte[4096];
                        int countBytesRead;
                        while((countBytesRead = in.read(buffer)) != -1) {
                            l += countBytesRead;
                        }
                        in.markSupported();
                        if (l == 333){
                            huntBuiler.dismiss();
                            startTunnelService();
                            return;
                        }
                        if (connection.getResponseCode() == 200){
                            huntBuiler.dismiss();
                            startTunnelService();
                            return;
                        }
                        in.close();
                        ms.setText(fail);
                        ok.setText("Check Again");
                        ok.setEnabled(true);
                    } catch (IOException e) {
                        ok.setText("Check Again");
                        ok.setEnabled(true);
                        ms.setText(fail);
                    }

                }catch (MalformedURLException ignored) {}}, 1000);
        });
        inflate.findViewById(R.id.appButton0).setOnClickListener(p1 -> {
            huntBuiler.dismiss();
            startTunnelService();
        });
        huntBuiler.setView(inflate);
        huntBuiler.setCancelable(true);
        huntBuiler.getWindow().getAttributes().windowAnimations = R.style.alertDialog;
        huntBuiler.show();
    }


    private OpenVPNService.Profile selected_profile() {
        OpenVPNService.ProfileList proflist = profile_list();
        if (proflist != null) {
            return proflist.get_profile_by_name(getConfig().getServerName());
        }
        return null;
    }
    private void resolve_epki_alias_then_connect() {
        resolveExternalPkiAlias(selected_profile(), OpenVPNClient.this::do_connect);
    }

    private void do_connect(String epki_alias) {
        String app_name = "net.openvpn.connect.android";
        prefs.set_string("n_username", getConfig().getSecureString(USERNAME_KEY));
        String username = getConfig().getSecureString(USERNAME_KEY);
        String password = getConfig().getSecureString(PASSWORD_KEY);
        String proxy_name = null;
        String server = null;
        String pk_password = null;
        String response = null;
        boolean is_auth_pwd_save = false;
        String profile_name = getConfig().getServerName();
        String vpn_proto = prefs.get_string("vpn_proto");
        String conn_timeout = prefs.get_string("conn_timeout");
        String compression_mode = prefs.get_string("compression_mode");
        String ipv6 = this.prefs.get_string("ipv6");
        submitConnectIntent(profile_name, null, vpn_proto, ipv6, conn_timeout, username, password, is_auth_pwd_save, null, null, epki_alias, compression_mode, null, null, null, true, get_gui_version(app_name));
    }


    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        setResult(result);
        switch (request) {
            case START_BIND_CALLED:
                if (result == RESULT_OK) {
                    start_connect();
                    return;
                }
                return;
            case REQUEST_IMPORT_FILE:
                if (result == RESULT_OK) {
                    Uri uri = data.getData();
                    String mData = FileUtils.showJson(FileUtils.readTextUri(OpenVPNClient.this,uri));
                    try{
                        exported_config.updateData("1", FileUtils.readTextUri(OpenVPNClient.this,uri));
                        JSONArray sjarr = new JSONArray();
                        JSONArray pjarr = new JSONArray();
                        JSONObject obj = new JSONObject(mData);
                        if (getConfig().getVersionCompare(obj.getString("Version"),getPref().getString(CONFIG_VERSION,"0"))){
                            if (addOrEditedServers().length()!=0)for (int i=0;i < addOrEditedServers().length();i++) {
                                sjarr.put(addOrEditedServers().getJSONObject(i));
                            }
                            if (obj.getJSONArray("Servers").length()!=0)for (int i=0;i < obj.getJSONArray("Servers").length();i++) {
                                sjarr.put(obj.getJSONArray("Servers").getJSONObject(i));
                            }
                            if (addOrEditedNetwork().length()!=0)for (int i=0;i < addOrEditedNetwork().length();i++) {
                                pjarr.put(addOrEditedNetwork().getJSONObject(i));
                            }
                            if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                                pjarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                            }
                            if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                                pjarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                            }
                            getServerData().updateData("1", sjarr.toString());
                            getNetworkData().updateData("1", pjarr.toString());
                            loadServerArrayDragaPosition();
                            loadNetworkArrayDragaPosition();
                            getEditor().putInt(SERVER_POSITION,0).apply();
                            getEditor().putInt(NETWORK_POSITION,0).apply();
                            getEditor().putString("CONFIG_FILE_NAME", obj.has("FileName")?obj.getString("FileName"):"Exported Config").apply();
                            getEditor().putString(CONFIG_VERSION, obj.getString("Version")).apply();
                            getEditor().putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                            getEditor().putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                            getEditor().putString(OPEN_VPN_CERT, obj.getString("Ovpn_Cert")).apply();
                            getEditor().putString(CONFIG_URL,FileUtils.showJson(obj.getString("config_url"))).apply();
                            getEditor().putString(CONFIG_API,obj.has("account_api")?FileUtils.showJson(obj.getString("account_api")):"").apply();
                            getEditor().putString(UPLOAD_GET_API,obj.has("upload_get_api")?FileUtils.showJson(obj.getString("upload_get_api")):"").apply();
                            getEditor().putString(UPLOAD_POST_API,obj.has("upload_post_api")?FileUtils.showJson(obj.getString("upload_post_api")):"").apply();
                            getEditor().putString(CONFIG_EDITOR_CODE,obj.has("AppConfPass")?FileUtils.showJson(obj.getString("AppConfPass")):"").apply();
                            if(obj.has("JSONsettings"))getJSONsettings(obj.getJSONArray("JSONsettings").toString());
                            getEditor().putBoolean("isRandom", false).apply();
                            getEditor().putBoolean("isAdminAccept", false).apply();
                            Config_vers.setText(getPref().getString(CONFIG_VERSION,"1.1"));
                            loadConfigurations();
                            _notice.setVisibility(View.VISIBLE);
                            submitReloadProfileIntent(getPref().getString(SERVER_TYPE_OVPN,"[]"));
                            if (HarlieService.isVPNRunning())stopTunnelService();
                        }else{
                            Toast.makeText(OpenVPNClient.this, "No Update available!", Toast.LENGTH_LONG).show();
                        }
                    }catch (Exception e){
                        util.showToast("Error...!", e.getMessage());
                    }
                    if (HarlieService.isVPNRunning())stopTunnelService();
                    return;
                }
                recreate();
        }
    }


    private void loadConfigurations() {
        if(reLoad_Configs()){
            if (serverArrayDragaPosition().length()==0){
                serverDialog.setVisibility(View.GONE);
                networkDialog.setVisibility(View.GONE);
            }else if (networkArrayDragaPosition().length()==0){
                networkDialog.setVisibility(View.GONE);
            }else{
                try {
                    JSONObject js1 = serverArrayDragaPosition().getJSONObject(getPref().getInt(SERVER_POSITION, 0));
                    s_name.setText(js1.getString("Name"));
                    TextView stv2 = findViewById(R.id.tvServerInfo);
                    stv2.setText(getServerType(js1));
                    InputStream open1 = getAssets().open("flags/" + "flag_" + js1.getString("FLAG") + ".png");
                    ((ImageView)findViewById(R.id.select_server_icon)).setImageDrawable(Drawable.createFromStream(open1, null));
                    s_name.setTextSize(TypedValue.COMPLEX_UNIT_DIP,10);
                    stv2.setTextSize(TypedValue.COMPLEX_UNIT_DIP,7);
                    if(getPref().getBoolean("isRandom",false) && getPref().getBoolean("show_random_layout", false)){
                        s_name.setText("AUTO SERVER");
                        stv2.setText("Random");
                        ((ImageView)findViewById(R.id.select_server_icon)).setImageResource(R.drawable.ic_server);
                    }

                    JSONObject js2 = networkArrayDragaPosition().getJSONObject(getPref().getInt(NETWORK_POSITION, 0));
                    p_name.setText(js2.getString("Name"));
                    TextView ptv4 = findViewById(R.id.tvNetworkInfo);
                    ptv4.setText(getNetworkType(js2));
                    InputStream open2 = getAssets().open("networks/" + "icon_" + js2.getString("FLAG") + ".png");
                    ((ImageView)findViewById(R.id.tweak_icon)).setImageDrawable(Drawable.createFromStream(open2, null));
                    tunnel_type.setText(getTunnelType(js2));
                    p_name.setTextSize(TypedValue.COMPLEX_UNIT_DIP,10);
                    ptv4.setTextSize(TypedValue.COMPLEX_UNIT_DIP,7);

                    serverDialog.setVisibility(View.VISIBLE);
                    networkDialog.setVisibility(isHidePayloadTweak());
                    open1.close();
                    open2.close();
                } catch (Exception e) {
                    tunnel_type.setTextColor(Color.RED);
                    tunnel_type.setText("Unknown!");
                    serverDialog.setVisibility(View.GONE);
                    networkDialog.setVisibility(View.GONE);
                }
            }
        }else{
            serverDialog.setVisibility(View.GONE);
            networkDialog.setVisibility(View.GONE);
        }
    }


    private String getServerType(JSONObject js) throws JSONException {
        if (js.getInt("Category")==0){
            return "Premium";
        }else if (js.getInt("Category")==1){
            return "VIP";
        } else if (js.getInt("Category")==2){
            return "Private";
        }
        return "Random";
    }

    private int isHidePayloadTweak(){
      if (networkArrayDragaPosition().length()==0){
            return View.GONE;
        }
        return View.VISIBLE;
    }

    private String getTunnelType(JSONObject js) throws JSONException {
        boolean is = (js.getString("Name").contains("Direct")||js.getString("Name").contains("direct"));
        if (js.getInt("proto_spin") == 0) {
            if (is){
                if (js.getString("NetworkPayload").isEmpty()){
                    tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
                    return "Direct";
                }else{
                    tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
                    return "Direct Payload";
                }
            }
            tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
            return "TCP | PROXY";
        } else if (js.getInt("proto_spin") == 1) {
            tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
            return "HYSTERIA";
        } else if (js.getInt("proto_spin") == 2) {
            return "SLOWDNS";
        }else if (js.getInt("proto_spin") == 3) {
            tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
            return "TCP | SSL";
        } else if (js.getInt("proto_spin") == 4) {
            tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
            return "SSL | PAYLOAD";
        } else if (js.getInt("proto_spin") == 5) {
            tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
            return "SSL | PROXY";
        } else if (js.getInt("proto_spin") == 6) {
            tunnel_type.setTextColor(getResources().getColor(R.color.colorPrimary));
            return "V2ray/Xray";
        }
        return "Unknown!";
    }
    private String getNetworkType(JSONObject js) throws JSONException {
        boolean is = (js.getString("Name").contains("Direct")||js.getString("Name").contains("direct"));
        if (js.getInt("proto_spin") == 0) {
            if (is){
                if (js.getString("NetworkPayload").isEmpty()){
                    return "Direct";
                }else{
                    return "Direct Payload";
                }
            }
            return "HTTP PROXY";
        } else if (js.getInt("proto_spin") == 1) {
            return "UDP HYSTERIA";
        } else if (js.getInt("proto_spin") == 2) {
            return "SLOWDNS";
        } else if (js.getInt("proto_spin") == 3) {
            return "SSL/SNI";
        } else if (js.getInt("proto_spin") == 4) {
            return "SSL+PAYLOAD";
        } else if (js.getInt("proto_spin") == 5) {
            return "SSL+PROXY";
        }else if (js.getInt("proto_spin") == 6) {
            return "V2ray/Xray";
        }
        return "Unknown!";
    }
    private void getJSONsettings(String obj){
        try{
            JSONArray jarr = new JSONArray(obj.trim());
            for (int i=0;i < jarr.length();i++) {
                JSONObject js = jarr.getJSONObject(i);
                getConfig().setLocalPort(js.getString("mLocalPort"));
                getConfig().setAutoClearLog(js.getBoolean("mAutoClearLog"));
                getConfig().setDisabledDelaySSH(js.getBoolean("mIsDisabledDelaySSH"));
                getConfig().setCompression(js.getBoolean("mCompression"));
                getConfig().setVpnDnsForward(js.getBoolean("mVpnDnsForward"));
                getConfig().setVpnDnsResolver(js.getString("mVpnDnsResolver"));
                getConfig().setVpnUdpForward(js.getBoolean("mVpnUdpForward"));
                getConfig().setVpnUdpResolver(js.getString("mVpnUdpResolver"));
                getConfig().setPingThread(Integer.parseInt(js.getString("mSSHPinger").isEmpty()?"3":js.getString("mSSHPinger")));
                getConfig().setPingServer(js.getString("mPingServer"));
                getConfig().setProxyAddress(js.getString("mProxyAddress"));
                getConfig().setReconnTime(js.getInt("mReconnTime"));
                getConfig().setTetheringSubnet(js.getBoolean("mIsTetheringSubnet"));
            }
        } catch (JSONException e) {
            util.showToast("getJSONsettings Error!", e.getMessage());
        }
    }


    private void setupBTNanimation(boolean isRunning){
        status_view.setTextColor(isConnected? Color.parseColor("#008000"):Color.BLACK);
        findViewById(R.id.graph_layout).setVisibility(isRunning? View.VISIBLE:View.GONE);
        mRotateLoading.setColor(isConnected?Color.GREEN:Color.RED);
        circleProgressBar.setColor(isConnected?Color.GREEN:Color.RED);
        if (isRunning && !isConnected){
            if (!mRotateLoading.isStart()){
                mRotateLoading.start();
                btn_connector.startAnimation(animation);
                circleProgressBar.setProgressWithAnimation(0);
            }
            btn_connector.setBackgroundResource(R.drawable.connect_button);
        }
        if (isConnected){
            animation.cancel();
            btn_connector.clearAnimation();
            btn_connector.setBackgroundResource(R.drawable.disconnect_button);
            if (mRotateLoading.isStart())mRotateLoading.stop();
            circleProgressBar.setProgressWithAnimation(100);
        }
        if (!isRunning){
            animation.cancel();
            btn_connector.clearAnimation();
            btn_connector.setBackgroundResource(R.drawable.connect_button);
            circleProgressBar.setProgressWithAnimation(0);
            if (mRotateLoading.isStart())mRotateLoading.stop();
        }
        clearAllDataAnim(!isRunning);
    }

    private void clearAllDataAnim(boolean isRunning){
        if (isRunning){
            clearAllTestDelay();
        }
    }

    public void mTelegram(){
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/phapp_devcodes"));
            startActivity(Intent.createChooser(intent, "launch Telegram"));
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Please download the Telegram app!", Toast.LENGTH_LONG).show();
        }
    }

    public DrawerLayout mDrawerLayout;
    private NavigationView drawerNavigationView;

    private void loadMainDrawer() {
        drawerNavigationView = findViewById(R.id.drawerNavigationView);
        mDrawerLayout = findViewById(R.id.drawerLayoutMain);
        View v = drawerNavigationView.getHeaderView(0);
        MenuItem checkbox = drawerNavigationView.getMenu().findItem(R.id.item01).setActionView(new CheckBox(OpenVPNClient.this));
        pingbox = (CheckBox) checkbox.getActionView();
        pingbox.setChecked(getPref().getBoolean("isAutoPinger",false));
        pingbox.setOnCheckedChangeListener((p1, isChecked) -> {
            if (!hLogStatus.isTunnelActive()){
                getEditor().putBoolean("isAutoPinger",isChecked).apply();
            } else {
                pingbox.setChecked(getPref().getBoolean("isAutoPinger",false));
            }
        });
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
                                        }
        );
        drawerNavigationView.setNavigationItemSelectedListener(this);
    }
    public boolean isDrawerOpen(){
        return mDrawerLayout.isDrawerOpen(GravityCompat.START);
    }

    public void close(){
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawers();
        }
    }

    public void open(){
        mDrawerLayout.openDrawer(GravityCompat.START);
    }


    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.options) {
            if (hLogStatus.isTunnelActive()){
                return false;
            }
            close();
            startActivity(new Intent(OpenVPNClient.this, advanceSettings.class));
        }
        else if (id == R.id.item_app) {
            close();
            getConfig().launchMarketDetails();
        }
        else if (id == R.id.item_checkupdates) {
            close();
            mUpdate();
        }else if (id == R.id.item_paste) {
            close();
            mPaste();
        }else if (id == R.id.item_im) {
            close();
            mImport();
        }else if (id == R.id.item_ex) {
            close();
            exportDialog();
        }
        else if (id == R.id.item09) {
            close();
            startActivity(new Intent(OpenVPNClient.this, wifiTethering.class));
        }else if (id == R.id.item05) {
            close();
            mIphunt();
        }else if (id == R.id.item01) {
            close();
            if (!hLogStatus.isTunnelActive()) {
                pingDislog();
            }
        }

        return true;
    }

    private String str = "";
    public String save(String fileName,String content) {
        str = "Ducuments/MTK/"+fileName+".hs";
        executor.execute(() -> {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS+"/MTK");
            dir.mkdirs();
            File file=new File(dir,fileName+".hs");
            try (Writer os = new OutputStreamWriter(new FileOutputStream(file))) {
                os.write(content);
                os.flush();
                os.close();
                str = dir+"/"+fileName+".hs";
            }
            catch (Throwable e) {
                str = e.getMessage();
            }
        });
        return str;
    }

    private void pingDislog(){
        View inflate = LayoutInflater.from(OpenVPNClient.this).inflate(R.layout.ping_dialog, null);
        final AlertDialog clipBuilder = new AlertDialog.Builder(OpenVPNClient.this).create();
        TextInputLayout destination = inflate.findViewById(R.id.destination);
        destination.setBoxStrokeColor(getConfig().getColorAccent());
        final EditText ed_destination = inflate.findViewById(R.id.ed_destination);
        ed_destination.setTextColor(getConfig().gettextColor());
        TextInputLayout timeout = inflate.findViewById(R.id.timeout);
        timeout.setBoxStrokeColor(getConfig().getColorAccent());
        final EditText ed_timeout = inflate.findViewById(R.id.ed_timeout);
        ed_timeout.setTextColor(getConfig().gettextColor());
        TextInputLayout thread = inflate.findViewById(R.id.thread);
        thread.setBoxStrokeColor(getConfig().getColorAccent());
        final EditText ed_thread = inflate.findViewById(R.id.ed_thread);
        inflate.findViewById(R.id.color_bg).setBackgroundColor(getConfig().getColorAccent());
        RelativeLayout save = inflate.findViewById(R.id.save);
        ((TextView)inflate.findViewById(R.id.appButton1)).setTextColor(getConfig().getColorAccent());
        ((TextView)inflate.findViewById(R.id.notiftext1)).setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        ((TextView)inflate.findViewById(R.id.savetv)).setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        ed_destination.setText(getPref().getString("ping_destination","www.google.com"));
        ed_timeout.setText(getPref().getString("ping_timeout","10"));
        ed_thread.setText(String.valueOf(getConfig().getPingThread()));
        save.setBackgroundTintList(ColorStateList.valueOf(getConfig().getColorAccent()));
        save.setOnClickListener(p1 -> {
            String p = ed_thread.getText().toString().trim();
            pingbox.setChecked(true);
            getEditor().putBoolean("isAutoPinger",true).apply();
            getEditor().putString("ping_destination",ed_destination.getText().toString().trim()).apply();
            getEditor().putString("ping_timeout",ed_timeout.getText().toString().trim()).apply();
            getConfig().setPingThread(Integer.parseInt(p.isEmpty()?"3":p));
            clipBuilder.dismiss();
        });
        inflate.findViewById(R.id.appButton0).setOnClickListener(p1 -> {
            pingbox.setChecked(false);
            getEditor().putBoolean("isAutoPinger",false).apply();
            clipBuilder.dismiss();
        });
        clipBuilder.setView(inflate);
        clipBuilder.setCancelable(false);
        clipBuilder.getWindow().getAttributes().windowAnimations = R.style.alertDialog;
        clipBuilder.show();
    }

    private void mIphunt(){
        if(cBuiler!=null)if(cBuiler.isShowing())cBuiler.dismiss();
        View inflate = LayoutInflater.from(this).inflate(R.layout.notif2, null);
        cBuiler = new AlertDialog.Builder(this).create();
        TextView title = inflate.findViewById(R.id.notiftext1);
        final TextView ms = inflate.findViewById(R.id.confimsg);
        RelativeLayout btn = inflate.findViewById(R.id.appButton2);
        TextView cancel = inflate.findViewById(R.id.appButton1);
        final TextView ok = inflate.findViewById(R.id.appButton2txt);
        ms.setTextColor(getConfig().gettextColor());
        cancel.setTextColor(getConfig().getColorAccent());
        inflate.findViewById(R.id.color_bg).setBackgroundColor(getConfig().getColorAccent());
        btn.setBackgroundTintList(ColorStateList.valueOf(getConfig().getColorAccent()));
        title.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        ok.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        title.setText("GTM IP Hunter");
        ms.setText("To connect to GTM No Load No Blocking, Make sure that you are now in the Magic IP. Click the button to check your IP!");
        ok.setText("Check Now");
        cancel.setText("Close");
        btn.setOnClickListener(p1 -> {
            ms.setText("Please wait while we are checking your IP...");
            ok.setEnabled(false);
            ok.setText("Checking...");
            new Handler().postDelayed(() -> {
                try {
                    int l = 0;
                    URL whatismyip = new URL("http://noloadbalance.globe.com.ph");
                    String magic = "✅ Congrats!! You are now connected to MAGIC IP.";
                    String fail = "🚫 Disconnected. Please Airplane Mode On/Off and Try Again.";
                    try{
                        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("104.16.213.74", 80));
                        HttpURLConnection connection = (HttpURLConnection) whatismyip.openConnection(proxy);
                        connection.setRequestMethod("GET");
                        connection.connect();
                        connection.getContentLength();
                        connection.setConnectTimeout(3000);
                        InputStream in = connection.getInputStream();
                        byte[] buffer = new byte[4096];
                        int countBytesRead;
                        while((countBytesRead = in.read(buffer)) != -1) {
                            l += countBytesRead;
                        }
                        in.markSupported();
                        if (l == 333){
                            ms.setText(magic);
                            ok.setText("Check Again");
                            ok.setEnabled(true);
                            return;
                        }
                        if (connection.getResponseCode() == 200){
                            ms.setText(magic);
                            ok.setText("Check Again");
                            ok.setEnabled(true);
                            return;
                        }
                        in.close();
                        ms.setText(fail);
                        ok.setText("Check Again");
                        ok.setEnabled(true);
                    } catch (IOException e) {
                        ok.setText("Check Again");
                        ok.setEnabled(true);
                        ms.setText(fail);
                    }

                }catch (MalformedURLException ignored) {}}, 1000);
        });
        cancel.setOnClickListener(p1 -> {
            cBuiler.dismiss();
        });
        cBuiler.setView(inflate);
        cBuiler.setCancelable(false);
        cBuiler.getWindow().getAttributes().windowAnimations = R.style.alertDialog;
        cBuiler.show();
    }

    private void mPaste(){
        if (cBuiler != null) if (cBuiler.isShowing()) cBuiler.dismiss();
        View inflate = LayoutInflater.from(this).inflate(R.layout.notification_dialog, null);
        cBuiler = new AlertDialog.Builder(this).create();
        ((AppCompatImageView)inflate.findViewById(R.id.notification_icon)).setImageResource(R.drawable.icon_icon);
        ((TextView)inflate.findViewById(R.id.notification_title)).setText(resString(R.string.app_name));
        ((TextView)inflate.findViewById(R.id.notification_message)).setText("This option is Custom Update for add Server or \nNetwork");
        TextView no = inflate.findViewById(R.id.notification_btn_no);
        TextView yes = inflate.findViewById(R.id.notification_btn_yes);
        no.setText("Exit");
        yes.setText("Paste");
        no.setOnClickListener(p1 -> cBuiler.dismiss());
        yes.setOnClickListener(p1 -> {
            String clipData = FileUtils.getClipboard(OpenVPNClient.this);
            if(clipData.isEmpty()){
                util.showToast("Error!", "Config Clipboard is empty!");
                return;
            }
            String mData = FileUtils.showJson(clipData);
            try{
                exported_config.updateData("1", clipData);
                JSONArray sjarr = new JSONArray();
                JSONArray pjarr = new JSONArray();
                JSONObject obj = new JSONObject(mData);
                if (getConfig().getVersionCompare(obj.getString("Version"),getPref().getString(CONFIG_VERSION,"0"))){
                    if (addOrEditedServers().length()!=0)for (int i=0;i < addOrEditedServers().length();i++) {
                        sjarr.put(addOrEditedServers().getJSONObject(i));
                    }
                    if (obj.getJSONArray("Servers").length()!=0)for (int i=0;i < obj.getJSONArray("Servers").length();i++) {
                        sjarr.put(obj.getJSONArray("Servers").getJSONObject(i));
                    }
                    if (addOrEditedNetwork().length()!=0)for (int i=0;i < addOrEditedNetwork().length();i++) {
                        pjarr.put(addOrEditedNetwork().getJSONObject(i));
                    }
                    if (obj.getJSONArray("HTTPNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("HTTPNetworks").length();i++) {
                        pjarr.put(obj.getJSONArray("HTTPNetworks").getJSONObject(i));
                    }
                    if (obj.getJSONArray("SSLNetworks").length()!=0)for (int i=0;i < obj.getJSONArray("SSLNetworks").length();i++) {
                        pjarr.put(obj.getJSONArray("SSLNetworks").getJSONObject(i));
                    }
                    getServerData().updateData("1", sjarr.toString());
                    getNetworkData().updateData("1", pjarr.toString());
                    loadServerArrayDragaPosition();
                    loadNetworkArrayDragaPosition();
                    getEditor().putInt(SERVER_POSITION,0).apply();
                    getEditor().putInt(NETWORK_POSITION,0).apply();
                    getEditor().putString("CONFIG_FILE_NAME", obj.has("FileName")?obj.getString("FileName"):"Exported Config").apply();
                    getEditor().putString(CONFIG_VERSION, obj.getString("Version")).apply();
                    getEditor().putString(RELEASE_NOTE, obj.getString("ReleaseNotes")).apply();
                    getEditor().putString(CONTACT_SUPPORT, obj.getString("contactSupport")).apply();
                    getEditor().putString(OPEN_VPN_CERT, obj.getString("Ovpn_Cert")).apply();
                    getEditor().putString(CONFIG_URL,FileUtils.showJson(obj.getString("config_url"))).apply();
                    getEditor().putString(CONFIG_API,obj.has("account_api")?FileUtils.showJson(obj.getString("account_api")):"").apply();
                    getEditor().putString(UPLOAD_GET_API,obj.has("upload_get_api")?FileUtils.showJson(obj.getString("upload_get_api")):"").apply();
                    getEditor().putString(UPLOAD_POST_API,obj.has("upload_post_api")?FileUtils.showJson(obj.getString("upload_post_api")):"").apply();
                    getEditor().putString(CONFIG_EDITOR_CODE,obj.has("AppConfPass")?FileUtils.showJson(obj.getString("AppConfPass")):"").apply();
                    if(obj.has("JSONsettings"))getJSONsettings(obj.getJSONArray("JSONsettings").toString());
                    getEditor().putBoolean("isRandom", false).apply();
                    getEditor().putBoolean("isAdminAccept", false).apply();
                    loadConfigurations();
                    Config_vers.setText(getPref().getString(CONFIG_VERSION,"1.1"));
                    _notice.setVisibility(View.VISIBLE);
                    submitReloadProfileIntent(getPref().getString(SERVER_TYPE_OVPN,"[]"));
                    if (HarlieService.isVPNRunning())stopTunnelService();
                    cBuiler.dismiss();
                }else{
                    Toast.makeText(OpenVPNClient.this, "No Update available!", Toast.LENGTH_LONG).show();
                    cBuiler.dismiss();
                }
            }catch (Exception e){
                util.showToast("Error...!", e.getMessage());
            }
        });
        cBuiler.setView(inflate);
        cBuiler.setCancelable(false);
        cBuiler.show();
    }


    private void mReleaseNotes(final String message){
        if (cBuiler != null) if (cBuiler.isShowing()) cBuiler.dismiss();
        _notice.setVisibility(View.GONE);
        View inflate = LayoutInflater.from(OpenVPNClient.this).inflate(R.layout.notification_layout, null);
        cBuiler = new AlertDialog.Builder(OpenVPNClient.this).create();
        AppCompatTextView ms1 = inflate.findViewById(R.id.log_title);
        AppCompatTextView ms2 = inflate.findViewById(R.id.log_message);
        inflate.findViewById(R.id.notif_dismiss_btn).setOnClickListener(p1 -> cBuiler.dismiss());
        ms1.setText("NEW UPDATE INFO");
        ms2.setText(message);
        cBuiler.setView(inflate);
        cBuiler.setCancelable(false);
        cBuiler.show();
    }
    private void exportDialog(){
        if (cBuiler != null) if (cBuiler.isShowing()) cBuiler.dismiss();
        View inflate = LayoutInflater.from(this).inflate(R.layout.notification_dialog, null);
        cBuiler = new AlertDialog.Builder(this).create();
        ((AppCompatImageView)inflate.findViewById(R.id.notification_icon)).setImageResource(R.drawable.icon_icon);
        ((TextView)inflate.findViewById(R.id.notification_title)).setText(resString(R.string.app_name));
        ((TextView)inflate.findViewById(R.id.notification_message)).setText("Are you sure you want to File Export?");
        TextView no = inflate.findViewById(R.id.notification_btn_no);
        TextView yes = inflate.findViewById(R.id.notification_btn_yes);
        no.setText("No");
        yes.setText("Yes");
        no.setOnClickListener(p1 -> cBuiler.dismiss());
        yes.setOnClickListener(p1 -> {
            try {
                String saveFile = save(getPref().getString("CONFIG_FILE_NAME",""),exported_config.getData());
                util.showToast(resString(R.string.app_name), "Config save at "+saveFile);
            } catch (Exception e) {
                Toast.makeText(OpenVPNClient.this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            cBuiler.dismiss();
        });
        cBuiler.setView(inflate);
        cBuiler.setCancelable(false);
        cBuiler.show();
    }



    private String date = "Expiry: --/--/-- | 0 Days";
    private void showExpireDate() {
        String api = getPref().getString(CONFIG_API,"");
        String user = getConfig().getSecureString(USERNAME_KEY);
        String pass = getConfig().getSecureString(PASSWORD_KEY);
        ac_xp.setTextColor(getConfig().getColorAccent());
        if (user.isEmpty() || pass.isEmpty()) {
            return;
        }
        ac_xp.setVisibility(View.VISIBLE);
        String model = Build.MODEL;
        String id = getHWID();
        String jsonUrl = api + user + "&password=" + pass + "&device_id=" + id + "&device_model=" + model;
        new ExpireDate(jsonUrl,new ExpireDate.ExpireDateListener() {
            @Override
            public void onExpireDate(String expiry) {
                if (!expiry.equals("none")) {
                    date = "Expiry: " + getExpireDate(expiry) + " | " + getDaysLeft(expiry);
                    getEditor().putString("_AccountXp",date).apply();
                    ac_xp.setText(date);
                    ac_xp.setTextColor(getConfig().getColorAccent());
                }
            }
            @Override
            public void onDeviceNotMatch() {
                getEditor().putString("_AccountXp","This account using other device!").apply();
                ac_xp.setText("This account using other device!");
                ac_xp.setTextColor(Color.RED);
                showXpDialog("This account using other device!");
            }
            @Override
            public void onAuthFailed() {
                getEditor().putString("_AccountXp",resString(R.string.state_auth_failed)).apply();
                ac_xp.setText(resString(R.string.state_auth_failed));
                ac_xp.setTextColor(Color.RED);
                showXpDialog(resString(R.string.state_auth_failed));
            }
            @Override
            public void onError() {
                ac_xp.setText("Expiry date failed to load!");
                ac_xp.setTextColor(Color.RED);
            }
        }).start();
    }
    public static String getHWID() {
        return Objects.requireNonNull(Objects.requireNonNull(md5(Build.SERIAL + Build.BOARD.length() % 5 + Build.BRAND.length() % 5 + Build.DEVICE.length() % 5 + Build.MANUFACTURER.length() % 5 + Build.MODEL.length() % 5 + Build.PRODUCT.length() % 5 + Build.HARDWARE))).toUpperCase(Locale.getDefault());
    }
    public static String md5(String str) {
        try {
            MessageDigest instance = MessageDigest.getInstance("MD5");
            instance.update(str.getBytes());
            byte[] digest = instance.digest();
            StringBuilder stringBuilder = new StringBuilder();
            for (byte b : digest) {
                StringBuilder toHexString = new StringBuilder(Integer.toHexString(b & 255));
                while (toHexString.length() < 2) {
                    toHexString.insert(0, "0");
                }
                stringBuilder.append(toHexString);
            }
            return stringBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
    private String getDaysLeft(String thatDate) {
        if (thatDate.contains(" ")) {
            thatDate = thatDate.split(" ")[0];
        }
        String[] split = thatDate.split("-");
        Calendar instance = Calendar.getInstance();
        instance.set(Integer.parseInt(split[0]), Integer.parseInt(split[1]) - 1, Integer.parseInt(split[2]));
        return String.format("%s Days", (instance.getTimeInMillis() - Calendar.getInstance().getTimeInMillis()) / ((long) 86400000));
    }
    @SuppressLint("SimpleDateFormat")
    private String getExpireDate(String date) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            Date newDate = format.parse(date);
            format = new SimpleDateFormat("MMMM dd, yyyy");
            assert newDate != null;
            return format.format(newDate);
        } catch (ParseException e) {
            return date;
        }
    }

    private void showXpDialog(String str) {
        if (HarlieService.isVPNRunning())stopTunnelService();
        if(cBuiler!=null)if(cBuiler.isShowing())cBuiler.dismiss();
        View inflate = LayoutInflater.from(this).inflate(R.layout.notif2, null);
        cBuiler = new AlertDialog.Builder(this).create();
        TextView title = inflate.findViewById(R.id.notiftext1);
        final TextView ms = inflate.findViewById(R.id.confimsg);
        TextView cancel = inflate.findViewById(R.id.appButton1);
        TextView clear = inflate.findViewById(R.id.appButton2txt);
        RelativeLayout btn = inflate.findViewById(R.id.appButton2);
        cancel.setTextColor(getConfig().getColorAccent());
        inflate.findViewById(R.id.color_bg).setBackgroundColor(getConfig().getColorAccent());
        btn.setBackgroundTintList(ColorStateList.valueOf(getConfig().getColorAccent()));
        ms.setTextColor(getConfig().gettextColor());
        title.setTextColor(getConfig().getAppThemeUtil()? Color.BLACK:Color.WHITE);
        title.setText("Oppss...!");
        ms.setText(str);
        cancel.setText("Close");
        clear.setText("Clear");
        inflate.findViewById(R.id.appButton0).setOnClickListener(p1 -> {
            if (HarlieService.isVPNRunning())stopTunnelService();
            cBuiler.dismiss();
        });

        btn.setOnClickListener(p1 -> {
            xUser.setText("");
            xPass.setText("");
            getEditor().putString("_screenUsername_key","").apply();
            getEditor().putString("_screenPassword_key","").apply();
            getConfig().setUser("");
            getConfig().setUserPass("");
            getEditor().putString("_AccountXp",date).apply();
            if (HarlieService.isVPNRunning())stopTunnelService();
            ac_xp.setText(date);
            cBuiler.dismiss();
        });
        cBuiler.setView(inflate);
        cBuiler.setCancelable(false);
        cBuiler.getWindow().getAttributes().windowAnimations = R.style.alertDialog;
        cBuiler.show();
    }

    private void mFirstNotes(){
        if (cBuiler != null) if (cBuiler.isShowing()) cBuiler.dismiss();
        if (!getDPrefs().getBoolean("join_tele", false)){
            View inflate = LayoutInflater.from(this).inflate(R.layout.notification_dialog, null);
            cBuiler = new AlertDialog.Builder(this).create();
            ((TextView)inflate.findViewById(R.id.notification_message)).setText("We have a Telegram support channel where we post\n" +
                    "and discuss about Settings, new Features, and also\n" +
                    "assist our Users.\n" +
                    "Would you like to join us there?");
            inflate.findViewById(R.id.notification_btn_no).setOnClickListener(p1 -> {
                getDEditor().putBoolean("join_tele",false).apply();
                cBuiler.dismiss();
            });
            inflate.findViewById(R.id.notification_btn_yes).setOnClickListener(p1 -> {
                getDEditor().putBoolean("join_tele",true).apply();
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(getConfig().getContactUrl()));
                    startActivity(Intent.createChooser(intent, "launch Telegram"));
                } catch (Exception e) {
                    util.showToast("Error","Please download the Telegram app");
                }
                cBuiler.dismiss();
            });
            cBuiler.setView(inflate);
            cBuiler.setCancelable(false);
            cBuiler.show();
        }
    }

}

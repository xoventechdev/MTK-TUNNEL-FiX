package dev.xoventech.tunnel.vpn.thread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import android.util.Log;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.DebugLogger;
import com.trilead.ssh2.DynamicPortForwarder;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.InteractiveCallback;
import com.trilead.ssh2.KnownHosts;
import com.trilead.ssh2.LocalPortForwarder;
import com.trilead.ssh2.ProxyData;
import com.trilead.ssh2.ServerHostKeyVerifier;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.concurrent.CountDownLatch;
import dev.xoventech.tunnel.vpn.R;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.config.SettingsConstants;
import dev.xoventech.tunnel.vpn.core.PasswordCache;
import dev.xoventech.tunnel.vpn.logger.hLogStatus;
import dev.xoventech.tunnel.vpn.service.HarlieService;
import dev.xoventech.tunnel.vpn.utils.util;

public class SSHTunnelThread extends Thread implements ConnectionMonitor, InteractiveCallback, ServerHostKeyVerifier, DebugLogger, SettingsConstants
{
    private static final String TAG = SSHTunnelThread.class.getSimpleName();
    private final HarlieService mContext;
    private final ConfigUtil mConfig;
    private boolean mStopping = false, mStarting = false;
    public boolean mReconnecting = false;
    private CountDownLatch mTunnelThreadStopSignal;
    private DNSTunnelThread mDNSTunnelThread;
    private final OnSSHTunnelListener mListener;
    public interface OnSSHTunnelListener {
        void onStop();
    }
    public SSHTunnelThread(HarlieService context,OnSSHTunnelListener mListener) {
        mContext = context;
        mConfig = ConfigUtil.getInstance(context);
        if (mListener == null) {
            throw new NullPointerException();
        }
        this.mListener = mListener;
        new Thread(this::startDNSTunnel).start();
    }

    private void startDNSTunnel() {
        if (mConfig.getServerType().equals(SERVER_TYPE_DNS)&&mDNSTunnelThread != null) {
            mDNSTunnelThread.interrupt();
            mDNSTunnelThread = null;
        }
        if(mConfig.getServerType().equals(SERVER_TYPE_DNS)){
            mDNSTunnelThread = new DNSTunnelThread(mContext, mListener::onStop);
            mDNSTunnelThread.start();
        }
    }

    @Override
    public void run() {
        super.run();
        if (!HarlieService.isVPNRunning()){
            mListener.onStop();
            return;
        }
        mStarting = true;
        mTunnelThreadStopSignal = new CountDownLatch(1);
        int tries = 0;
        while (!mStopping) {
            try {
                if (!util.isNetworkAvailable(mContext)) {
                    try {
                        Thread.sleep(5000);
                    } catch(InterruptedException e2) {
                        mListener.onStop();
                        break;
                    }
                }
                else {
                    if (tries > 0){
                        if(tries==50){
                            addLogInfo("<b>Connection timeout</b>");
                            mListener.onStop();
                            break;
                        }
                    }
                    try {
                        Thread.sleep(500);
                    } catch(InterruptedException e2) {
                        mListener.onStop();
                        break;
                    }
                    startClienteSSH();
                    break;
                }
            } catch(Exception e) {
                new Thread(this::closeSSH).start();
                try {
                    Thread.sleep(500);
                } catch(InterruptedException e2) {
                    mListener.onStop();
                    break;
                }
            }
            tries++;
        }
        mStarting = false;
        if (!mStopping) {
            try {
                mTunnelThreadStopSignal.await();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected void startForwarder(int portaLocal) throws Exception {
        if (!mConnected) {
            throw new Exception();
        }
        startForwarderSocks(portaLocal);
        new Thread(() -> {
            hLogStatus.updateStateString(hLogStatus.VPN_CONNECTED, mContext.getString(R.string.state_connected));
            mContext.VPNTunnel_handler(true);
            while (true) {
                if (!mConnected) break;
                try {
                    Thread.sleep(2000);
                } catch(InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private final static int AUTH_TRIES = 1;
    private final static int RECONNECT_TRIES = 5;
    private static int FIRST_RECONNECT_TRIES = -1;
    private Connection mConnection;
    private boolean mConnected = false;

    protected void startClienteSSH() throws Exception {
        if (FIRST_RECONNECT_TRIES>=1){
            FIRST_RECONNECT_TRIES = 0;
            if (util.isNetworkAvailable(mContext))hLogStatus.clearLog();
        }
        if (!HarlieService.isVPNRunning())return;
        mStopping = false;
        String mHost = mConfig.getSecureString(SERVER_KEY);
        int mPort = Integer.parseInt(mConfig.getSecureString(SERVER_PORT_KEY));
        /*
        String useraccount = getAccount()[0];
        String passaccount = getAccount()[1];
        */
        String useraccount = mConfig.getSecureString(USERNAME_KEY);
        String passaccount = mConfig.getSecureString(PASSWORD_KEY);
        String senha = passaccount.isEmpty() ? PasswordCache.getAuthPassword(null, false) : passaccount;
        String keyPath = mConfig.getSSHKeypath();
        int portaLocal = Integer.parseInt(mConfig.getLocalPort());
        int pType = mConfig.getPayloadType();
        try {
            conectar(mHost, mPort, pType);
            for (; true; ) {
                if (mStopping) {
                    return;
                }
                try {
                    autenticar(useraccount, senha, keyPath);
                    break;
                } catch(IOException e) {
                    throw new IOException("Autenticação falhou");
                }
            }
            startForwarder(portaLocal);
        } catch(Exception e) {
            mConnected = false;
            reconnectSSH();
            throw e;
        }
    }

    public void closeSSH() {
        stopForwarderSocks();
        if (mConnection != null) {
            mConnection.close();
        }
    }

    @SuppressLint({"NewApi", "DefaultLocale"})
    protected void conectar(String serv, int port,int pType) throws Exception {
        if (!mStarting) {
            throw new Exception();
        }
        try {
            mConnection = new Connection(serv, port);
            if (mConfig.getIsDisabledDelaySSH()) {
                mConnection.setTCPNoDelay(true);
            }
            if (mConfig.getCompression()) {
                mConnection.setCompression(true);
            }
            useProxy = addProxy(mConnection,pType);
            mConnection.addConnectionMonitor(this);
            try {
                ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                ProxyInfo proxy = cm.getDefaultProxy();
                if (proxy != null) {
                    addLogInfo("<b>Network Proxy:</b> " + String.format("%s:%d", proxy.getHost(), proxy.getPort()));
                }
            }catch (Exception ignored){}
            FIRST_RECONNECT_TRIES++;
            hLogStatus.updateStateString(hLogStatus.VPN_GET_CONFIG, mContext.getString(R.string.state_assign_ip));
            addLogInfo(mContext.getResources().getString(R.string.state_get_config));
            addLogInfo(mContext.getResources().getString(R.string.state_assign_ip));
            addLogInfo(mContext.getResources().getString(R.string.state_wait));
            mConnection.connect(this, 10*1000, 20*1000);
            mConnected = true;
        } catch(Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String cause = e.getCause().toString();
            if (useProxy && cause.contains("Key exchange was not finished")) {
                addLogInfo("<b>SSH Core: </b>Proxy lost connection");
            }
            else {
                hLogStatus.logDebug("<b>SSH Core: </b>" + cause);
            }
            throw new Exception(e);
        }
    }

    private static final String AUTH_PUBLICKEY = "publickey", AUTH_PASSWORD = "password";
    protected void autenticar(String usuario, String senha, String keyPath) throws IOException {
        if (!mConnected) {
            throw new IOException();
        }
        hLogStatus.updateStateString(hLogStatus.VPN_AUTHENTICATING, mContext.getString(R.string.state_auth));
        try {
            if (mConnection.isAuthMethodAvailable(usuario, AUTH_PASSWORD)) {
                addLogInfo("Authenticate with password");
                if (mConnection.authenticateWithPassword(usuario, senha)) {
                    addLogInfo("<b>" + mContext.getString(R.string.state_auth_success) + "</b>");
                }
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "Connection went away while we were trying to authenticate", e);
        } catch (Exception e) {
            Log.e(TAG, "Problem during handleAuthentication()", e);
        }
        try {
            if (mConnection.isAuthMethodAvailable(usuario, AUTH_PUBLICKEY) && keyPath != null && !keyPath.isEmpty()) {
                File f = new File(keyPath);
                if (f.exists()) {
                    if (senha.equals("")) senha = null;
                    hLogStatus.logDebug("Autenticando com public key");
                    if (mConnection.authenticateWithPublicKey(usuario, f, senha)) {
                        addLogInfo("<b>" + mContext.getString(R.string.state_auth_success) + "</b>");
                    }
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "Host does not support 'Public key' authentication.");
        }
        if (!mConnection.isAuthenticationComplete()) {
            if (AUTH_TRIES_ACCOUNT==1){
                AUTH_TRIES_ACCOUNT = 2;
            }else if (AUTH_TRIES_ACCOUNT==2){
                AUTH_TRIES_ACCOUNT = 3;
            }else if (AUTH_TRIES_ACCOUNT==3){
                AUTH_TRIES_ACCOUNT = 1;
            }
            addLogInfo("<font color = #ff0000>Failed to authenticate, username or password expired");
            if (mStopping){
                hLogStatus.updateStateString(hLogStatus.VPN_AUTH_FAILED, mContext.getString(R.string.state_auth_failed));
                mContext.startService(new Intent(mContext, HarlieService.class).setAction(HarlieService.STOP_SERVICE).putExtra("stateSTOP_SERVICE",mContext.getResources().getString(R.string.state_auth_failed)));
                throw new IOException("Failed to authenticate, username or password expired");
            }else{
                hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, mContext.getResources().getString(R.string.state_reconnecting));
                mContext.startService(new Intent(mContext, HarlieService.class).setAction(HarlieService.RECONNECT_SERVICE));
            }
        }
    }

    private static int AUTH_TRIES_ACCOUNT = 1;
    private String[] getAccount(){
        if (AUTH_TRIES_ACCOUNT==1){
            return new String[]{user1,user1};
        }else if (AUTH_TRIES_ACCOUNT==2){
            return new String[]{user2,user2};
        }else if (AUTH_TRIES_ACCOUNT==3){
            return new String[]{user3,pass3};
        }
        return new String[]{user1,user1};
    }

    @Override
    public String[] replyToChallenge(String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) {
        String passaccount = mConfig.getSecureString(PASSWORD_KEY);//getAccount()[1];
        String[] responses = new String[numPrompts];
        for (int i = 0; i < numPrompts; i++) {
            if (prompt[i].toLowerCase().contains("password"))
                responses[i] = passaccount;
        }
        return responses;
    }

    @Override
    public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) {
        String fingerPrint = KnownHosts.createHexFingerprint(serverHostKeyAlgorithm, serverHostKey);
        addLogInfo("Finger Print: " + fingerPrint);
        return true;
    }

    private boolean useProxy = false;
    private boolean addProxy(Connection conn, int pType) {
        if (pType==PAYLOAD_TYPE_DIRECT||mConfig.getServerType().equals(SERVER_TYPE_DNS)){
            return false;
        }
        else if (pType==PAYLOAD_TYPE_DIRECT_PAYLOAD){
            String hostname = mConfig.getSecureString(SERVER_KEY);
            int port = Integer.parseInt(mConfig.getSecureString(SERVER_PORT_KEY));
            String mCustomPayload = (!mConfig.getSecureString(CUSTOM_PAYLOAD_KEY).isEmpty() ? mConfig.getSecureString(CUSTOM_PAYLOAD_KEY) : null);
            ProxyData proxyData = new HttpProxyCustom(hostname, port,null, null, mCustomPayload, null, true, mContext);
            conn.setProxyData(proxyData);
            return false;
        }
        else if (pType==PAYLOAD_TYPE_HTTP_PROXY || pType==PAYLOAD_TYPE_SSL){
            String[] prxAdrss = mConfig.getProxyAddress().split(":");
            conn.setProxyData(new HTTPProxyData(prxAdrss[0],Integer.parseInt(prxAdrss[1])));
            return true;
        }
        else if (pType==PAYLOAD_TYPE_SSL_PAYLOAD){
            String hostname = mConfig.getSecureString(SERVER_KEY);
            int port = Integer.parseInt(mConfig.getSecureString(SERVER_PORT_KEY));
            String mSni = (!mConfig.getSecureString(SNI_HOST_KEY).isEmpty() ? mConfig.getSecureString(SNI_HOST_KEY) : null);
            String mCustomPayload = (!mConfig.getSecureString(CUSTOM_PAYLOAD_KEY).isEmpty() ? mConfig.getSecureString(CUSTOM_PAYLOAD_KEY) : null);
            ProxyData proxyData = new HttpProxyCustom(hostname, port,null, null, mCustomPayload, mSni, true, mContext);
            conn.setProxyData(proxyData);
            return false;
        }
        else if (pType==PAYLOAD_TYPE_SSL_PROXY){
            String proxyHost = mConfig.getSecureString(PROXY_IP_KEY);
            int proxyPort = Integer.parseInt(mConfig.getSecureString(PROXY_PORT_KEY));
            String mSni = (!mConfig.getSecureString(SNI_HOST_KEY).isEmpty() ? mConfig.getSecureString(SNI_HOST_KEY) : null);
            String mCustomPayload = (!mConfig.getSecureString(CUSTOM_PAYLOAD_KEY).isEmpty() ? mConfig.getSecureString(CUSTOM_PAYLOAD_KEY) : null);
            ProxyData proxyData = new HttpProxyCustom(proxyHost, proxyPort,null, null, mCustomPayload, mSni, true, mContext);
            conn.setProxyData(proxyData);
            return true;
        }
        return false;
    }

    private DynamicPortForwarder dpf;
    private LocalPortForwarder dnsForwarder;
    @SuppressLint("DefaultLocale")
    private void startForwarderSocks(int portaLocal) throws Exception {
        if (!mConnected) {
            throw new Exception();
        }
        String[] pingServ = mConfig.getPingServer().trim().split(":");
        try {
            dnsForwarder = mConnection.createLocalPortForwarder(8053, pingServ[0], Integer.parseInt(pingServ[1]));
            dpf = mConnection.createDynamicPortForwarder(new InetSocketAddress("127.0.0.1",portaLocal));
        } catch (Exception e) {
            hLogStatus.logError("Socks Local: " + e.getCause());
            throw new Exception();
        }
    }

    private void stopForwarderSocks() {
        if (dnsForwarder != null){
            try {
                dnsForwarder.close();
            } catch(IOException ignored){}
            dnsForwarder = null;
        }
        if (dpf != null) {
            try {
                dpf.close();
            } catch(IOException ignored){}
            dpf = null;
        }
    }

    @Override
    public void connectionLost(Throwable reason) {
        if (!HarlieService.isVPNRunning() || mStarting || mStopping || mReconnecting) {
            return;
        }
        mContext.VPNTunnel_handler(false);
        if (reason != null) {
            if (reason.getMessage().contains("There was a problem during connect")) {
                return;
            } else if (reason.getMessage().contains("Closed due to user request")) {
                return;
            } else if (reason.getMessage().contains("The connect timeout expired")) {
                mListener.onStop();
                return;
            }
        } else {
            mListener.onStop();
            return;
        }
        reconnectSSH();
    }

    @Override
    public void onReceiveInfo(int infoId, String infoMsg) {
        if (infoId == SERVER_BANNER) {
            hLogStatus.logInfo(infoMsg);
        }
    }
    @Override
    public void log(int level, String className, String message) {
        hLogStatus.logDebug(String.format("%s: %s", className, message));
    }


    public void reconnectSSH(){
        new Thread(this::closeSSH).start();
        new Thread(this::mReconnectSSH).start();
    }
    private void mReconnectSSH() {
        if (!HarlieService.isVPNRunning() || mStarting || mStopping || mReconnecting) {
            return;
        }
        mContext.VPNTunnel_handler(false);
        mReconnecting = true;
        hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, mContext.getString(R.string.state_reconnecting));
        try {
            Thread.sleep(1000);
        } catch(InterruptedException e) {
            mReconnecting = false;
            return;
        }
        for (int i = 0; i < RECONNECT_TRIES; i++) {
            if (!HarlieService.isVPNRunning() || mStopping) {
                mReconnecting = false;
                return;
            }
            int sleepTime = 5;
            if (!util.isNetworkAvailable(mContext)) {
                hLogStatus.updateStateString(hLogStatus.VPN_PAUSE, mContext.getString(R.string.state_pause));
            }
            else {
                sleepTime = 3;
                mStarting = true;
                hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, mContext.getString(R.string.state_reconnecting));
                try {
                    startClienteSSH();
                    mStarting = false;
                    mReconnecting = false;
                    return;
                } catch(Exception e) {
                    mListener.onStop();
                }
                mStarting = false;
            }
            try {
                Thread.sleep(sleepTime*1000);
                i--;
            } catch(InterruptedException e2){
                mReconnecting = false;
                return;
            }
        }
        mReconnecting = false;
        mListener.onStop();
    }

    @Override
    public void interrupt(){
        super.interrupt();
        FIRST_RECONNECT_TRIES = 0;
        mStopping = true;
        mStarting = false;
        mReconnecting = false;
        new Thread(this::closeSSH).start();
        if (mTunnelThreadStopSignal != null) mTunnelThreadStopSignal.countDown();
        if (mDNSTunnelThread != null) {
            mDNSTunnelThread.interrupt();
            mDNSTunnelThread = null;
        }
    }

    public void addLogInfo(String msg) {
        String hst = mConfig.getSecureString(SERVER_KEY);
        String prx = mConfig.getSecureString(PROXY_IP_KEY);
        if (!msg.contains(hst) || !msg.contains(prx) || !msg.contains("Socket close")){
            if (msg.contains("Connection timed out")) {
                hLogStatus.logInfo("<b>Connection timed out</b>");
                mListener.onStop();
            }else{
                hLogStatus.logInfo(msg);
            }
        }
    }

}


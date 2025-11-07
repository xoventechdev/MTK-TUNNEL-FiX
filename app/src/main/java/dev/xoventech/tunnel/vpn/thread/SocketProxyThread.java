package dev.xoventech.tunnel.vpn.thread;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.ProxyInfo;
import java.io.*;
import java.net.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.*;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import dev.xoventech.tunnel.vpn.R;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.config.SettingsConstants;
import dev.xoventech.tunnel.vpn.core.vpnutils.TunnelUtils;
import dev.xoventech.tunnel.vpn.core.vpnutils.VpnUtils;
import dev.xoventech.tunnel.vpn.harliesApplication;
import dev.xoventech.tunnel.vpn.logger.hLogStatus;
import dev.xoventech.tunnel.vpn.service.HarlieService;
import dev.xoventech.tunnel.vpn.service.VPNTunnelService;
import dev.xoventech.tunnel.vpn.utils.SSLUtil;
import io.michaelrocks.paranoid.Obfuscate;

@Obfuscate
public class SocketProxyThread extends Thread implements SettingsConstants {
    private CountDownLatch mTunnelThreadStopSignal;
    private final HarlieService service;
    public static HttpsURLConnection huc;
    public static BackServer mBackServerThread;
    private ServerSocket ss;
    private Socket client;
    public static Socket server;
    private int repeatCount = 0;
    public static SSLSocket mSSLSocket;
    private static int mPayload_type;
    private final OnWSTunnelListener mListener;
    public interface OnWSTunnelListener {
        void onStop();
    }
    private final int mProxyAddress;
    private final String mBufferSend;
    private final String mBufferReceive;
    private final int tunnel;
    private final ConfigUtil mConfig;

    @SuppressLint({"NewApi", "DefaultLocale"})
    public SocketProxyThread(HarlieService service, OnWSTunnelListener mListener) {
        this.service = service;
        mConfig = ConfigUtil.getInstance(service);
        SharedPreferences mPref = harliesApplication.getPrivateSharedPreferences();
        mProxyAddress = Integer.parseInt(mConfig.getProxyAddress().split(":")[1]);
        mBufferSend = mPref.getString("buffer_send", "16384");
        mBufferReceive = mPref.getString("buffer_receive", "32768");
        mPayload_type = mConfig.getPayloadType();
        tunnel = mConfig.getServerType().equals(SERVER_TYPE_SSH)? service.SSH_DNS:service.OVPN;
        if (mListener == null) {
            throw new NullPointerException();
        }
        this.mListener = mListener;
        try {
            ConnectivityManager cm = (ConnectivityManager) service.getSystemService(Context.CONNECTIVITY_SERVICE);
            ProxyInfo proxy = cm.getDefaultProxy();
            if (proxy != null) {
                addLogInfo("<b>Network Proxy:</b> " + String.format("%s:%d", proxy.getHost(), proxy.getPort()));
            }
        }catch (Exception ignored){}
    }

    private void connectSocket(String host, int port, boolean ssl) throws Exception {
        server = new Socket();
        server.bind(new InetSocketAddress(0));
        server.connect(new InetSocketAddress(host, port));
        doVpnProtect(server);
    }

    private void connectSSL() throws Exception {
        SSLSocketFactory factory = new SSLUtil();
        addLogInfo("<font color = #FF9600>Setting up SNI...");
        String mSni = (mConfig.getSecureString(SNI_HOST_KEY).startsWith("http")) ? mConfig.getSecureString(SNI_HOST_KEY) : "https://" + mConfig.getSecureString(SNI_HOST_KEY);
        URL url = new URL(mSni);
        mSni = url.getHost();
        if (url.getPort() > 0) {
            mSni = mSni + ":" + url.getPort();
        }
        if (!url.getPath().equals("/")) {
            mSni = mSni + url.getPath();
        }
        huc = (HttpsURLConnection) url.openConnection(new Proxy(Proxy.Type.HTTP, mBackServerThread.getLocalSocketAddr()));
        huc.setHostnameVerifier(new HostnameVerifier() {
            @SuppressLint({"BadHostnameVerifier"})
            public boolean verify(String str, SSLSession sSLSession) {
                return true;
            }
        });
        huc.setSSLSocketFactory(factory);
        huc.connect();
    }


    private boolean connectSocket() throws Exception {
        try {
            String readRequest = new BufferedReader(new InputStreamReader(client.getInputStream())).readLine();
            int tunnel_type = mConfig.getPayloadType();
            while (true) {
                String payload = mConfig.getSecureString(CUSTOM_PAYLOAD_KEY);
                String proxy = mConfig.getSecureString(PROXY_IP_KEY);
                int proxyPort = Integer.parseInt(mConfig.getSecureString(PROXY_PORT_KEY));
                String[] hostname = readRequest.split(" ");
                String host = hostname[1].split(":")[0];
                int port = Integer.parseInt(hostname[1].split(":")[1]);
                if (tunnel_type == PAYLOAD_TYPE_DIRECT || tunnel_type == PAYLOAD_TYPE_OVPN_UDP){
                    connectSocket(host, port, false);
                    send200Status(client.getOutputStream());
                }
                else if (tunnel_type == PAYLOAD_TYPE_DIRECT_PAYLOAD){
                    connectSocket(host, port, false);
                    mPayloadInject(payload, server, readRequest);
                    send200Status(client.getOutputStream());
                }
                else if (tunnel_type == PAYLOAD_TYPE_HTTP_PROXY){
                    connectSocket(proxy, proxyPort, false);
                    mPayloadInject(payload, server, readRequest);
                }
                else if (tunnel_type == PAYLOAD_TYPE_SSL){
                    connectSocket(host, port, true);
                    connectSSL();
                    send200Status(client.getOutputStream());
                }
                else if (tunnel_type == PAYLOAD_TYPE_SSL_PAYLOAD){
                    connectSocket(host, port, true);
                    connectSSL();
                    mPayloadInject(payload, mSSLSocket, readRequest);
                }
                else if (tunnel_type == PAYLOAD_TYPE_SSL_PROXY){
                    connectSocket(proxy, proxyPort, true);
                    connectSSL();
                    mPayloadInject(payload, mSSLSocket, readRequest);
                }
                if (mSSLSocket != null) {
                    return !client.isClosed() && server.isConnected() && mSSLSocket.isConnected();
                } else {
                    return !client.isClosed() && server.isConnected();
                }
            }
        } catch (Exception e) {
            addLogInfo("<font color = #FF9600><b>Socket: </b>connection error!");
        }
        return false;
    }

    private void mPayloadInject(String mPayload, Socket socket,String readRequest) throws Exception {
        OutputStream outputStream = socket.getOutputStream();
        int i = 0;
        int pos;
        String[] splitSpace;
        String PORT;
        String IP;
        String reqData;
        Matcher cocok;
        String hostQuery;
        String host = mConfig.getSecureString(SERVER_KEY);
        splitSpace = readRequest.split(" ");
        PORT = "80";
        if (splitSpace[1].startsWith("http") || splitSpace[1].indexOf(":") <= 0) {
            IP = splitSpace[1];
        } else {
            IP = splitSpace[1].split(":")[0];
            PORT = splitSpace[1].split(":")[1];
        }
        pos = mPayload.indexOf("netData");
        if (pos < 0) {
            reqData = mPayload.replace("[METHOD]", splitSpace[0]).replace("[SSH]", splitSpace[1]).replace("[IP_PORT]", splitSpace[1]).replace("[IP]", IP).replace("[PORT]", PORT).replace("[cr]", "\r").replace("[lf]", "\n").replace("[crlf]", "\r\n").replace("[lfcr] ", "\n\r").replace("[protocol]", splitSpace[2]).replace("[host]", IP).replace("[port]", PORT).replace("[host_port]", splitSpace[1]).replace("[ssh]", splitSpace[1]).replace("[ua]", ua()).replace("\\r", "\r").replace("\\n", "\n").replace("[mtk]", host);
        } else if (mPayload.substring(pos + 7, (pos + 7) + 1).equals("@")) {
            cocok = Pattern.compile("\\[.*?@(.*?)\\]").matcher(mPayload);
            hostQuery = "";
            if (cocok.find()) {
                hostQuery = cocok.group(1);
            }
            reqData = mPayload.replace("[netData@" + hostQuery.trim(), splitSpace[0] + " " + splitSpace[1] + "@" + hostQuery.trim() + " " + splitSpace[2]).replace("[METHOD]", splitSpace[0]).replace("[SSH]", splitSpace[1]).replace("[IP_PORT]", splitSpace[1]).replace("[IP]", IP).replace("[PORT]", PORT).replace("\\r", "\r").replace("\\n", "\n").replace("[cr]", "\r").replace("[lf]", "\n").replace("[crlf]", "\r\n").replace("[lfcr] ", "\n\r").replace("[protocol]", splitSpace[2]).replace("[host]", IP).replace("[port]", PORT).replace("[host_port]", splitSpace[1]).replace("[ssh]", splitSpace[1]).replace("[ua]", ua()).replace("]", "").replace("[mtk]", host);
        } else {
            if (pos == 0) {
                pos = 1;
            }
            cocok = Pattern.compile("\\[(.*?)@.*?\\]").matcher(mPayload);
            hostQuery = "";
            if (cocok.find()) {
                hostQuery = cocok.group(1);
            }
            if (mPayload.substring(pos - 1, pos).equals("@")) {
                reqData = mPayload.replace(hostQuery.trim() + "@netData", splitSpace[0] + " " + hostQuery.trim() + "@" + splitSpace[1] + " " + splitSpace[2]).replace("[METHOD]", splitSpace[0]).replace("[SSH]", splitSpace[1]).replace("[IP_PORT]", splitSpace[1]).replace("[IP]", IP).replace("[PORT]", PORT).replace("\\r", "\r").replace("\\n", "\n").replace("[", "").replace("[cr]", "\r").replace("[lf]", "\n").replace("[crlf]", "\r\n").replace("[lfcr] ", "\n\r").replace("[protocol]", splitSpace[2]).replace("[host]", IP).replace("[port]", PORT).replace("[host_port]", splitSpace[1]).replace("[ssh]", splitSpace[1]).replace("[ua]", ua()).replace("]", "").replace("[mtk]", host);
            } else {
                reqData = mPayload.replace("[netData]", readRequest).replace("[METHOD]", splitSpace[0]).replace("[SSH]", splitSpace[1]).replace("[IP_PORT]", splitSpace[1]).replace("[IP]", IP).replace("[PORT]", PORT).replace("[cr]", "\r").replace("[lf]", "\n").replace("[crlf]", "\r\n").replace("[lfcr] ", "\n\r").replace("[protocol]", splitSpace[2]).replace("[host]", IP).replace("[port]", PORT).replace("[host_port]", splitSpace[1]).replace("[ssh]", splitSpace[1]).replace("[ua]", ua()).replace("\\r", "\r").replace("\\n", "\n").replace("[mtk]", host);
            }
        }
        String str = TunnelUtils.parseRandom(TunnelUtils.parseRotate(reqData));
        if (str.contains("[repeat]")) {
            String[] split = str.split(Pattern.quote("[repeat]"));
            str = split[repeatCount];
            repeatCount++;
            if (repeatCount > split.length - 1) {
                repeatCount = 0;
            }
        }
        addLogInfo("<font color = #FF9600>Sending payload");
        addLogInfo("<font color = #FF9600>"+service.getResources().getString(R.string.state_proxy_inject));
        if (str.contains("[split_delay]")) {
            String[] split = str.split(Pattern.quote("[split_delay]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, socket, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(1500);
                }
                i++;
            }
        } else if (str.contains("[split_instant]")) {
            String[] split = str.split(Pattern.quote("[split_instant]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, socket, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(0);
                }
                i++;
            }
        } else if (str.contains("[instant_split]")) {
            String[] split = str.split(Pattern.quote("[instant_split]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, socket, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(0);
                }
                i++;
            }
        } else if (str.contains("[delay_split]")) {
            String[] split = str.split(Pattern.quote("[delay_split]"));
            int length = split.length;
            while (i < length) {
                String str2 = split[i];
                if (a(str2, socket, outputStream)) {
                    outputStream.write(str2.getBytes());
                    outputStream.flush();
                    Thread.sleep(1500);
                }
                i++;
            }
        } else if (a(str, socket, outputStream)) {
            outputStream.write(str.getBytes());
            outputStream.flush();
        }
    }

    private boolean a(String str, Socket socket, OutputStream outputStream) throws Exception {
        if (!str.contains("[split]")) {
            return true;
        }
        for (String str2 : str.split(Pattern.quote("[split]"))) {
            outputStream.write(str2.getBytes());
            outputStream.flush();
        }
        return false;
    }
    private String ua() {
        String property = System.getProperty("http.agent");
        return property == null ? "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36" : property;
    }

    private void send200Status(OutputStream output) throws Exception {
        output.write("HTTP/1.0 200 Connection Established\r\n\r\n".getBytes());
        output.flush();
    }

    @Override
    public void run() {
        super.run();
        mTunnelThreadStopSignal = new CountDownLatch(1);
        try {
            ss = new ServerSocket(mProxyAddress);
            if (mPayload_type == PAYLOAD_TYPE_SSL || mPayload_type == PAYLOAD_TYPE_SSL_PAYLOAD || mPayload_type == PAYLOAD_TYPE_SSL_PROXY) {
                if (mBackServerThread!=null){
                    mBackServerThread.interrupt();
                }
            }
            service.mHandler.sendEmptyMessage(tunnel);
            while (HarlieService.isVPNRunning()) {
                client = ss.accept();
                if (mPayload_type == PAYLOAD_TYPE_SSL || mPayload_type == PAYLOAD_TYPE_SSL_PAYLOAD || mPayload_type == PAYLOAD_TYPE_SSL_PROXY) {
                    mBackServerThread = new BackServer();
                    mBackServerThread.start();
                }
                if (client != null && !client.isClosed() && connectSocket()) {
                    client.setKeepAlive(true);
                    if (mSSLSocket != null && mSSLSocket.isConnected()) {
                        mSSLSocket.setKeepAlive(true);
                        server.setKeepAlive(true);
                        doVpnProtect(mSSLSocket);
                        HTTPInjectorThread.connect(client, mSSLSocket, mBufferSend, mBufferReceive, service);
                    } else if (server != null && server.isConnected()) {
                        server.setKeepAlive(true);
                        doVpnProtect(server);
                        HTTPInjectorThread.connect(client, server, mBufferSend, mBufferReceive, service);
                    }
                }
            }
        } catch (Exception e) {
            String msg = e.toString();
            if (msg.contains("bind failed")) {
                interrupt();
                addLogInfo(e.toString());
                mListener.onStop();
            }
        }
        if (!HarlieService.mStopping) {
            try {
                mTunnelThreadStopSignal.await();
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

     private void doVpnProtect(Socket socket) {
        if (tunnel==service.SSH_DNS){
            new VPNTunnelService().protect(socket);
        }else{
            VpnUtils.isProtected(socket);
        }
    }

    private void addLogInfo(String mLog){
        hLogStatus.logInfo(mLog);
    }


    @Override
    public void interrupt(){
        repeatCount = 0;
        new HTTPInjectorThread().interrupt();
        try {
            if (ss != null) {
                ss.close();
                ss = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (client != null) {
                client.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (server != null) {
                server.close();
                server = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (mSSLSocket != null) {
                mSSLSocket.close();
                mSSLSocket = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (huc != null) {
                huc.disconnect();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mBackServerThread != null) {
                mBackServerThread.interrupt();
                mBackServerThread = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (mSSLSocket != null) {
                mSSLSocket.close();
                mSSLSocket = null;
            }
        } catch (Exception ignored) {
        }
        if (mTunnelThreadStopSignal != null) mTunnelThreadStopSignal.countDown();
        super.interrupt();
    }



}



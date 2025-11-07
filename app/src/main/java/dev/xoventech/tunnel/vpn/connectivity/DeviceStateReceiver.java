package dev.xoventech.tunnel.vpn.connectivity;

import android.content.Context;
import android.content.Intent;
import dev.xoventech.tunnel.vpn.R;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.config.SettingsConstants;
import dev.xoventech.tunnel.vpn.logger.hLogStatus;
import dev.xoventech.tunnel.vpn.service.HarlieService;
import dev.xoventech.tunnel.vpn.service.OpenVPNService;
import dev.xoventech.tunnel.vpn.utils.util;

public class DeviceStateReceiver extends ConnectivityReceiverBase implements SettingsConstants {
    private ConnectionState currentState = getConnectionState();
    public DeviceStateReceiver(Context context) {
        super(context);
    }

    public void onAvailable(Object obj) {
        checkNewState();
    }

    public void onLost(Object obj) {
        checkNewState();
    }

    private void checkNewState() {
        ConnectionState connectionState = getConnectionState();
        if (this.currentState.hasChanged(connectionState)) {
            onStateChange(connectionState);
        }
        this.currentState = connectionState;
    }


    private void onStateChange(ConnectionState connectionState) {
        ConfigUtil config = ConfigUtil.getInstance(context);
        boolean isOVPN = config.getServerType().equals(SERVER_TYPE_OVPN);
        if (this.currentState.isConnected() && connectionState.isDisconnected()) {
            if(hLogStatus.isTunnelActive()){
                hLogStatus.updateStateString(hLogStatus.VPN_PAUSE, context.getResources().getString(R.string.state_pause));
                if (isOVPN) context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_PAUSE));
                else context.startService(new Intent(context, HarlieService.class).setAction(HarlieService.RECONNECT_SERVICE).putExtra("mStateReceiver",context.getResources().getString(R.string.state_pause)));
            }
        } else if (this.currentState.isDisconnected() && connectionState.isConnected()) {
            if(hLogStatus.isTunnelActive()){
                hLogStatus.clearLog();
                hLogStatus.updateStateString(hLogStatus.VPN_RESUME, context.getResources().getString(R.string.state_resume));
                if (isOVPN) context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RESUME));
                else context.startService(new Intent(context, HarlieService.class).setAction(HarlieService.RECONNECT_SERVICE).putExtra("mStateReceiver",context.getResources().getString(R.string.state_resume)));
            }
        } else {
            if(hLogStatus.isTunnelActive() && util.isNetworkAvailable(context)){
                hLogStatus.clearLog();
                hLogStatus.updateStateString(hLogStatus.VPN_RECONNECTING, context.getResources().getString(R.string.state_reconnecting));
                if (isOVPN) context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RECONNECT));
                else context.startService(new Intent(context, HarlieService.class).setAction(HarlieService.RECONNECT_SERVICE).putExtra("mStateReceiver",context.getResources().getString(R.string.state_reconnecting)));
            }
        }
    }
    private ConnectionState getConnectionState() {
        return ConnectionState.getInstance(getManager());
    }

}
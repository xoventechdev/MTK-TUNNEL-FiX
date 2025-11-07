package dev.xoventech.tunnel.vpn.connectivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import dev.xoventech.tunnel.vpn.R;
import dev.xoventech.tunnel.vpn.config.ConfigUtil;
import dev.xoventech.tunnel.vpn.config.SettingsConstants;
import dev.xoventech.tunnel.vpn.logger.hLogStatus;
import dev.xoventech.tunnel.vpn.service.HarlieService;
import dev.xoventech.tunnel.vpn.service.OpenVPNService;
import dev.xoventech.tunnel.vpn.utils.util;

public class deviceRebootReceiver extends BroadcastReceiver implements SettingsConstants {

    public void onReceive(Context context, Intent intent) {
        String act = intent.getAction();
        ConfigUtil config = ConfigUtil.getInstance(context);
        boolean isOVPN = config.getServerType().equals(SERVER_TYPE_OVPN);
        boolean pvbs = config.getPowerSaver();
        if (act!=null){
            if ("android.intent.action.SCREEN_ON".equals(act)) {
                config.setIsScreenOn(true);
            } else if ("android.intent.action.SCREEN_OFF".equals(act)){
                config.setIsScreenOn(false);
            }
        }
        if (hLogStatus.isTunnelActive() && util.isNetworkAvailable(context) && pvbs){
            if (config.getIsScreenOn()) {
                hLogStatus.clearLog();
                hLogStatus.updateStateString(hLogStatus.VPN_RESUME, context.getResources().getString(R.string.state_resume));
                if (isOVPN) context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_RESUME));
                else context.startService(new Intent(context, HarlieService.class).setAction(HarlieService.RECONNECT_SERVICE).putExtra("mStateReceiver",context.getResources().getString(R.string.state_resume)));
            } else {
                hLogStatus.updateStateString(hLogStatus.VPN_WAITING, context.getResources().getString(R.string.state_waiting_connection));
                if (isOVPN) context.startService(new Intent(context, OpenVPNService.class).setAction(OpenVPNService.ACTION_PAUSE));
                else context.startService(new Intent(context, HarlieService.class).setAction(HarlieService.RECONNECT_SERVICE).putExtra("mStateReceiver",context.getResources().getString(R.string.state_waiting_connection)));
            }
        }
    }
}

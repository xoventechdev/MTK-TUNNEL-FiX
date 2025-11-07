package dev.xoventech.tunnel.vpn.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import dev.xoventech.tunnel.vpn.R;
import io.michaelrocks.paranoid.Obfuscate;

@Obfuscate
@SuppressLint("NewApi")
public class SpashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(getResources().getColor(R.color.colorPrimary));
        getWindow().setNavigationBarColor(getResources().getColor(R.color.colorPrimary));
        setContentView(R.layout.splash);
        new Handler().postDelayed(this::launchActivity, 1000);
    }

    void launchActivity() {
        Intent intent = new Intent(this, OpenVPNClient.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
    }
}

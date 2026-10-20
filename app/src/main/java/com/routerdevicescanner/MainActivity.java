package com.routerdevicescanner;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkAddress;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status, results;
    private Button scan;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 28, 24, 24);
        TextView title = new TextView(this);
        title.setText("UNLIMITED VOUCHER\nRouter Device Scanner");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        status = new TextView(this);
        status.setText("Checking active network…");
        status.setTextSize(16);
        root.addView(status);
        scan = new Button(this);
        scan.setText("SCAN LOCAL NETWORK");
        root.addView(scan);
        results = new TextView(this);
        results.setTextSize(14);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        showNetwork();
        scan.setOnClickListener(v -> startScan());
    }
    private void showNetwork() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (lp == null || caps == null) {
            status.setText("No active network. Connect to your authorized Wi-Fi first.");
            return;
        }
        StringBuilder b = new StringBuilder("Active network: ");
        b.append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Other network");
        for (LinkAddress a : lp.getLinkAddresses()) b.append("\nLocal address: ").append(a.getAddress().getHostAddress());
        status.setText(b.toString());
    }
    private void startScan() {
        scan.setEnabled(false);
        results.setText("Scanning a bounded private IPv4 range…\nMAC addresses may be unavailable on Android.");
        worker.execute(() -> {
            StringBuilder out = new StringBuilder();
            try {
                ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                Network n = cm.getActiveNetwork();
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                InetAddress local = null;
                int prefix = -1;
                if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof java.net.Inet4Address) { local=la.getAddress(); prefix=la.getPrefixLength(); break; }
                }
                if (local == null || prefix < 16 || prefix > 30) {
                    out.append("Could not determine a safe IPv4 scan range. Only /16 through /30 networks are supported.");
                } else {
                    byte[] ip=local.getAddress();
                    int me=((ip[0]&255)<<24)|((ip[1]&255)<<16)|((ip[2]&255)<<8)|(ip[3]&255);
                    int mask=(int)(0xffffffffL << (32-prefix));
                    int base=me&mask, count=(1<<(32-prefix))-2;
                    if (count>4094) count=4094;
                    for(int i=1;i<=count;i++) {
                        int value=base+i;
                        if(value==me) continue;
                        byte[] addr={(byte)(value>>>24),(byte)(value>>>16),(byte)(value>>>8),(byte)value};
                        InetAddress host=InetAddress.getByAddress(addr);
                        if(host.isReachable(180)) out.append(host.getHostAddress()).append(" — reachable; MAC: unavailable unless exposed by OS\n");
                    }
                    if(out.length()==0) out.append("No hosts replied to this probe. Devices may block discovery probes.\n");
                    out.append("\nNote: no reply does not mean a device is absent; a reply does not reveal its MAC.");
                }
            } catch(Exception e) { out.append("Scan error: ").append(e.getClass().getSimpleName()).append(": ").append(e.getMessage()); }
            final String text=out.toString();
            runOnUiThread(() -> { results.setText(text); scan.setEnabled(true); });
        });
    }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); }
}

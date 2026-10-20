package com.routerdevicescanner;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status, results, selected;
    private Button scan;
    private Spinner vouchers;
    private final List<String> discovered = new ArrayList<>();
    private String selectedHost = null;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 20);
        TextView title = new TextView(this);
        title.setText("UNLIMITED VOUCHER");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Router Device Scanner");
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);
        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 18, 0, 12);
        root.addView(status);
        scan = new Button(this);
        scan.setText("SCAN CONNECTED NETWORK");
        root.addView(scan);
        TextView hint = new TextView(this);
        hint.setText("Tap a discovered device to select it. MAC addresses are shown only when Android exposes them.");
        hint.setPadding(0, 10, 0, 8);
        root.addView(hint);
        results = new TextView(this);
        results.setTextSize(15);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(results);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        selected = new TextView(this);
        selected.setText("Selected device: none");
        selected.setPadding(0, 10, 0, 6);
        root.addView(selected);
        TextView voucherLabel = new TextView(this);
        voucherLabel.setText("MAC Voucher (local selection only)");
        root.addView(voucherLabel);
        vouchers = new Spinner(this);
        String[] items = {"Select voucher…", "Voucher 01", "Voucher 02", "Voucher 03", "Voucher 04", "Voucher 05", "Voucher 06", "Voucher 07", "Voucher 08", "Voucher 09", "Voucher 10", "Voucher 11", "Voucher 12"};
        vouchers.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        root.addView(vouchers);
        Button openChanger = new Button(this);
        openChanger.setText("OPEN CHANGE MY MAC");
        root.addView(openChanger);
        TextView note = new TextView(this);
        note.setText("This opens the installed MAC utility, if available. It does not transfer a selected remote device's MAC or change it automatically.");
        note.setTextSize(12);
        root.addView(note);
        setContentView(root);
        showNetwork();
        scan.setOnClickListener(v -> startScan());
        openChanger.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("net.xnano.android.changemymac");
            if (launch != null) startActivity(launch);
            else startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=net.xnano.android.changemymac")));
        });
        vouchers.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) status.setText("Voucher selected: " + items[position] + ". Choose OPEN CHANGE MY MAC to use the separate utility.");
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
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
        for (LinkAddress a : lp.getLinkAddresses()) b.append("\\nLocal address: ").append(a.getAddress().getHostAddress());
        status.setText(b.toString());
    }

    private void startScan() {
        scan.setEnabled(false);
        results.setText("Scanning authorized local IPv4 network…");
        discovered.clear();
        worker.execute(() -> {
            List<String> found = new ArrayList<>();
            try {
                ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                Network n = cm.getActiveNetwork();
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                InetAddress local = null;
                int prefix = -1;
                if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof java.net.Inet4Address) { local = la.getAddress(); prefix = la.getPrefixLength(); break; }
                }
                if (local == null || prefix < 16 || prefix > 30) {
                    found.add("Could not determine a safe IPv4 scan range (/16 to /30 only).");
                } else {
                    byte[] ip = local.getAddress();
                    int me = ((ip[0]&255)<<24)|((ip[1]&255)<<16)|((ip[2]&255)<<8)|(ip[3]&255);
                    int mask = (int)(0xffffffffL << (32-prefix));
                    int base = me & mask, count = (1 << (32-prefix)) - 2;
                    if (count > 1022) count = 1022;
                    for (int i=1; i<=count; i++) {
                        int value = base+i;
                        if (value == me) continue;
                        byte[] addr = {(byte)(value>>>24),(byte)(value>>>16),(byte)(value>>>8),(byte)value};
                        InetAddress host = InetAddress.getByAddress(addr);
                        if (host.isReachable(140)) found.add(host.getHostAddress() + "    MAC: unavailable from Android API");
                    }
                    if (found.isEmpty()) found.add("No devices replied. Some devices block discovery probes.");
                }
            } catch (Exception e) { found.add("Scan error: " + e.getClass().getSimpleName() + ": " + e.getMessage()); }
            runOnUiThread(() -> {
                discovered.clear();
                discovered.addAll(found);
                LinearLayout list = new LinearLayout(this);
                list.setOrientation(LinearLayout.VERTICAL);
                results.setText("");
                for (String row : discovered) {
                    Button item = new Button(this);
                    item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    item.setText(row);
                    item.setOnClickListener(v -> {
                        selectedHost = row;
                        selected.setText("Selected device: " + row);
                    });
                    list.addView(item);
                }
                // Render discovered rows as selectable buttons inside the existing result view.
                results.setText(found.isEmpty() ? "No devices found." : android.text.TextUtils.join("\\n", found));
                scan.setEnabled(true);
            });
        });
    }
    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); }
}

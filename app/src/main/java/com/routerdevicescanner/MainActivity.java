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
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.net.Uri;
import java.net.InetAddress;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.security.SecureRandom;
import java.util.Locale;

public class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView status, selected;
    private LinearLayout resultList;
    private Button scan;
    private Spinner vouchers;
    private EditText macInput;
    private final SecureRandom random = new SecureRandom();
    private final List<String> discovered = new ArrayList<>();
    private String selectedHost = null;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        showPasswordGate();
    }

    private void showPasswordGate() {
        LinearLayout gate = new LinearLayout(this);
        gate.setOrientation(LinearLayout.VERTICAL);
        gate.setGravity(Gravity.CENTER);
        gate.setPadding(28, 28, 28, 28);

        TextView title = new TextView(this);
        title.setText("UNLIMITED VOUCHER");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        gate.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView message = new TextView(this);
        message.setText("Enter password to access the app");
        message.setTextSize(16);
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, 18, 0, 18);
        gate.addView(message, new LinearLayout.LayoutParams(-1, -2));

        EditText passwordInput = new EditText(this);
        passwordInput.setSingleLine(true);
        passwordInput.setHint("Password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        gate.addView(passwordInput, new LinearLayout.LayoutParams(-1, -2));

        TextView error = new TextView(this);
        error.setText("");
        error.setGravity(Gravity.CENTER);
        error.setPadding(0, 8, 0, 8);
        gate.addView(error, new LinearLayout.LayoutParams(-1, -2));

        Button unlock = new Button(this);
        unlock.setText("UNLOCK");
        gate.addView(unlock, new LinearLayout.LayoutParams(-1, -2));

        unlock.setOnClickListener(v -> {
            if ("#55555#".equals(passwordInput.getText().toString())) {
                buildMainScreen();
            } else {
                error.setText("Incorrect password. Try again.");
                passwordInput.setText("");
                passwordInput.requestFocus();
            }
        });

        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            unlock.performClick();
            return true;
        });

        setContentView(gate);
    }

    private void buildMainScreen() {
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
        hint.setText("Tap a responding device to select it. Android usually does not expose other devices' MAC addresses to ordinary apps.");
        hint.setPadding(0, 10, 0, 8);
        root.addView(hint);

        resultList = new LinearLayout(this);
        resultList.setOrientation(LinearLayout.VERTICAL);
        // Keep results in the page's main scroll container so controls remain
        // reachable on small phones instead of being pushed off-screen.
        root.addView(resultList, new LinearLayout.LayoutParams(-1, -2));

        selected = new TextView(this);
        selected.setText("Selected device: none");
        selected.setPadding(0, 10, 0, 6);
        root.addView(selected);

        TextView voucherLabel = new TextView(this);
        voucherLabel.setText("PRIVATE MAC ADDRESS • THIS PHONE");
        voucherLabel.setTextSize(16);
        root.addView(voucherLabel);

        macInput = new EditText(this);
        macInput.setSingleLine(true);
        macInput.setHint("02:11:22:33:44:55");
        macInput.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(macInput);

        vouchers = new Spinner(this);
        String[] items = {"Choose a generated private MAC…"};
        vouchers.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        root.addView(vouchers);

        Button generateMac = new Button(this);
        generateMac.setText("GENERATE PRIVATE MAC");
        root.addView(generateMac);

        Button applyMac = new Button(this);
        applyMac.setText("APPLY CUSTOM MAC TO THIS PHONE");
        root.addView(applyMac);

        Button openChanger = new Button(this);
        openChanger.setText("OPEN CHANGE MY MAC");
        root.addView(openChanger);

        TextView note = new TextView(this);
        note.setText("Generates a private, locally administered MAC for your own phone. The app does not copy a detected device's identity or apply changes automatically. Actual changes depend on Android, root permission and Wi-Fi driver support.");
        note.setTextSize(12);
        root.addView(note);

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.addView(root);
        setContentView(pageScroll);
        showNetwork();
        scan.setOnClickListener(v -> startScan());
        openChanger.setOnClickListener(v -> {
            Intent launch = getPackageManager().getLaunchIntentForPackage("net.xnano.android.changemymac");
            if (launch != null) {
                try { startActivity(launch); }
                catch (Exception e) { openStoreListing(); }
            } else {
                openStoreListing();
            }
        });
        generateMac.setOnClickListener(v -> {
            byte[] b = new byte[6];
            random.nextBytes(b);
            b[0] = (byte)((b[0] & 0xFC) | 0x02);
            String mac = String.format(Locale.US, "%02X:%02X:%02X:%02X:%02X:%02X",
                b[0] & 255, b[1] & 255, b[2] & 255, b[3] & 255, b[4] & 255, b[5] & 255);
            macInput.setText(mac);
            status.setText("Private MAC generated. Nothing has been applied yet.");
        });
        applyMac.setOnClickListener(v -> applyCustomMacToThisPhone());
    }

    private void applyCustomMacToThisPhone() {
        String mac = macInput.getText().toString().trim().replace('-', ':').toUpperCase(Locale.US);
        if (!mac.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) {
            status.setText("Enter a valid MAC address, e.g. 02:11:22:33:44:55.");
            return;
        }
        try {
            int first = Integer.parseInt(mac.substring(0, 2), 16);
            if ((first & 0x02) == 0 || (first & 0x01) != 0) {
                status.setText("Use a private MAC: first byte must be locally administered and unicast (e.g. 02:11:22:33:44:55).");
                return;
            }
        } catch (Exception e) {
            status.setText("Invalid MAC address.");
            return;
        }
        status.setText("Requesting root access. Allow the su prompt if your phone is rooted…");
        final String safeMac = mac.toLowerCase(Locale.US);
        worker.execute(() -> {
            String result;
            try {
                String command = "id | grep -q 'uid=0' || { echo ROOT_DENIED; exit 20; }; " +
                    "IFACE=$(ip -o -4 addr show 2>/dev/null | awk 'BEGIN{ } {print $2}' | grep -E '^(wlan0|wlan[0-9]+)
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.xnano.android.changemymac")));
        } catch (Exception e) {
            status.setText("Could not open the MAC utility or its Play Store page.");
        }
    }

    private void showNetwork() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm == null ? null : cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (lp == null || caps == null) {
            status.setText("No active network. Connect to your authorized Wi-Fi first.");
            return;
        }
        StringBuilder b = new StringBuilder("Active network: ");
        b.append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Other network");
        for (LinkAddress a : lp.getLinkAddresses()) {
            if (a.getAddress() instanceof java.net.Inet4Address) {
                b.append("\nLocal IPv4: ").append(a.getAddress().getHostAddress()).append("/").append(a.getPrefixLength());
            }
        }
        status.setText(b.toString());
    }

    private void startScan() {
        scan.setEnabled(false);
        resultList.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Scanning the local IPv4 subnet…");
        resultList.addView(loading);
        discovered.clear();
        selectedHost = null;
        selected.setText("Selected device: none");

        worker.execute(() -> {
            List<String> found = new ArrayList<>();
            try {
                ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                Network n = cm == null ? null : cm.getActiveNetwork();
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                InetAddress local = null;
                int prefix = -1;
                if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof java.net.Inet4Address) {
                        local = la.getAddress();
                        prefix = la.getPrefixLength();
                        break;
                    }
                }

                if (local == null) {
                    found.add("No local IPv4 address found. Connect to Wi-Fi and try again.");
                } else if (prefix < 22 || prefix > 30) {
                    found.add("Subnet is too large or unsupported for this safe scan (/" + prefix + "). This scanner supports IPv4 prefixes /22 through /30.");
                } else {
                    byte[] ip = local.getAddress();
                    int me = ((ip[0]&255)<<24)|((ip[1]&255)<<16)|((ip[2]&255)<<8)|(ip[3]&255);
                    int mask = (int)(0xffffffffL << (32-prefix));
                    int base = me & mask;
                    int count = (1 << (32-prefix)) - 2;
                    ExecutorService probes = Executors.newFixedThreadPool(32);
                    List<Future<String>> tasks = new ArrayList<>();
                    try {
                        for (int i = 1; i <= count; i++) {
                            final int value = base + i;
                            if (value == me) continue;
                            tasks.add(probes.submit(() -> {
                                byte[] addr = {(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
                                InetAddress host = InetAddress.getByAddress(addr);
                                return host.isReachable(350) ? host.getHostAddress() : null;
                            }));
                        }
                        for (Future<String> task : tasks) {
                            try {
                                String result = task.get();
                                if (result != null) found.add(result);
                            } catch (ExecutionException ignored) {
                                // One failed probe should not abort the whole scan.
                            }
                        }
                    } finally {
                        probes.shutdownNow();
                    }
                    if (!found.isEmpty()) {
                        Map<String, String> arp = readArpTable();
                        List<String> rows = new ArrayList<>();
                        for (String ipAddress : found) {
                            String mac = arp.get(ipAddress);
                            rows.add(ipAddress + "    MAC: " + (mac == null ? "unavailable" : mac));
                        }
                        found = rows;
                    } else {
                        found.add("No devices replied to the discovery probe. Some phones/routers block these probes; this does not prove the network is empty.");
                    }
                }
            } catch (Exception e) {
                found.add("Scan error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            }

            final List<String> scanResults = new ArrayList<>(found);
            runOnUiThread(() -> {
                discovered.clear();
                discovered.addAll(scanResults);
                resultList.removeAllViews();
                for (String row : discovered) {
                    TextView item = new TextView(this);
                    item.setText(row);
                    item.setTextSize(15);
                    item.setPadding(14, 14, 14, 14);
                    item.setBackgroundResource(android.R.drawable.list_selector_background);
                    item.setClickable(true);
                    item.setFocusable(true);
                    item.setOnClickListener(v -> {
                        if (row.contains("MAC: ") && row.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
                            selectedHost = row.substring(0, row.indexOf("    "));
                            selected.setText("Selected device: " + row);
                        }
                    });
                    resultList.addView(item, new LinearLayout.LayoutParams(-1, -2));
                }
                scan.setEnabled(true);
            });
        });
    }

    private Map<String, String> readArpTable() {
        Map<String, String> entries = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4 && parts[3].matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
                        && !parts[3].equalsIgnoreCase("00:00:00:00:00:00")) {
                    entries.put(parts[0], parts[3].toUpperCase(java.util.Locale.US));
                }
            }
        } catch (Exception ignored) {
            // Android may restrict access to the ARP table.
        }
        return entries;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}
 | head -n 1); " +
                    "[ -n \"$IFACE\" ] || IFACE=wlan0; " +
                    "ip link show dev \"$IFACE\" >/dev/null 2>&1 || { echo WIFI_INTERFACE_NOT_FOUND; exit 21; }; " +
                    "ip link set dev \"$IFACE\" down || exit 22; " +
                    "if ip link set dev \"$IFACE\" address '" + safeMac + "'; then " +
                    "ip link set dev \"$IFACE\" up; sleep 1; " +
                    "ACTUAL=$(ip link show dev \"$IFACE\" | awk '/link\/ether/ {print tolower($2); exit}'); " +
                    "if [ \"$ACTUAL\" = '" + safeMac + "' ]; then echo MAC_CHANGE_CONFIRMED; else echo MAC_CHANGE_NOT_CONFIRMED; fi; " +
                    "else ip link set dev \"$IFACE\" up >/dev/null 2>&1 || true; echo DRIVER_REJECTED_MAC; exit 23; fi";
                Process p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) output.append(line).append("\\n");
                }
                if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    result = "Timed out waiting for root command.";
                } else {
                    result = output.toString();
                }
            } catch (Exception e) {
                result = "Could not run root command: " + e.getClass().getSimpleName() + ". Root access and device support are required.";
            }
            final String finalResult = result;
            runOnUiThread(() -> status.setText(finalResult.contains("MAC_CHANGE_CONFIRMED")
                ? "MAC change verified on this phone. Wi-Fi may disconnect briefly.\\n" + finalResult
                : "MAC change not confirmed. Android/root/driver may block it; no reboot was performed.\\n" + finalResult));
        });
    }

    private void openStoreListing() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=net.xnano.android.changemymac")));
        } catch (Exception e) {
            status.setText("Could not open the MAC utility or its Play Store page.");
        }
    }

    private void showNetwork() {
        ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        Network n = cm == null ? null : cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (lp == null || caps == null) {
            status.setText("No active network. Connect to your authorized Wi-Fi first.");
            return;
        }
        StringBuilder b = new StringBuilder("Active network: ");
        b.append(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "Wi-Fi" : "Other network");
        for (LinkAddress a : lp.getLinkAddresses()) {
            if (a.getAddress() instanceof java.net.Inet4Address) {
                b.append("\nLocal IPv4: ").append(a.getAddress().getHostAddress()).append("/").append(a.getPrefixLength());
            }
        }
        status.setText(b.toString());
    }

    private void startScan() {
        scan.setEnabled(false);
        resultList.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Scanning the local IPv4 subnet…");
        resultList.addView(loading);
        discovered.clear();
        selectedHost = null;
        selected.setText("Selected device: none");

        worker.execute(() -> {
            List<String> found = new ArrayList<>();
            try {
                ConnectivityManager cm = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
                Network n = cm == null ? null : cm.getActiveNetwork();
                LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
                InetAddress local = null;
                int prefix = -1;
                if (lp != null) for (LinkAddress la : lp.getLinkAddresses()) {
                    if (la.getAddress() instanceof java.net.Inet4Address) {
                        local = la.getAddress();
                        prefix = la.getPrefixLength();
                        break;
                    }
                }

                if (local == null) {
                    found.add("No local IPv4 address found. Connect to Wi-Fi and try again.");
                } else if (prefix < 22 || prefix > 30) {
                    found.add("Subnet is too large or unsupported for this safe scan (/" + prefix + "). This scanner supports IPv4 prefixes /22 through /30.");
                } else {
                    byte[] ip = local.getAddress();
                    int me = ((ip[0]&255)<<24)|((ip[1]&255)<<16)|((ip[2]&255)<<8)|(ip[3]&255);
                    int mask = (int)(0xffffffffL << (32-prefix));
                    int base = me & mask;
                    int count = (1 << (32-prefix)) - 2;
                    ExecutorService probes = Executors.newFixedThreadPool(32);
                    List<Future<String>> tasks = new ArrayList<>();
                    try {
                        for (int i = 1; i <= count; i++) {
                            final int value = base + i;
                            if (value == me) continue;
                            tasks.add(probes.submit(() -> {
                                byte[] addr = {(byte)(value >>> 24), (byte)(value >>> 16), (byte)(value >>> 8), (byte)value};
                                InetAddress host = InetAddress.getByAddress(addr);
                                return host.isReachable(350) ? host.getHostAddress() : null;
                            }));
                        }
                        for (Future<String> task : tasks) {
                            try {
                                String result = task.get();
                                if (result != null) found.add(result);
                            } catch (ExecutionException ignored) {
                                // One failed probe should not abort the whole scan.
                            }
                        }
                    } finally {
                        probes.shutdownNow();
                    }
                    if (!found.isEmpty()) {
                        Map<String, String> arp = readArpTable();
                        List<String> rows = new ArrayList<>();
                        for (String ipAddress : found) {
                            String mac = arp.get(ipAddress);
                            rows.add(ipAddress + "    MAC: " + (mac == null ? "unavailable" : mac));
                        }
                        found = rows;
                    } else {
                        found.add("No devices replied to the discovery probe. Some phones/routers block these probes; this does not prove the network is empty.");
                    }
                }
            } catch (Exception e) {
                found.add("Scan error: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            }

            final List<String> scanResults = new ArrayList<>(found);
            runOnUiThread(() -> {
                discovered.clear();
                discovered.addAll(scanResults);
                resultList.removeAllViews();
                for (String row : discovered) {
                    TextView item = new TextView(this);
                    item.setText(row);
                    item.setTextSize(15);
                    item.setPadding(14, 14, 14, 14);
                    item.setBackgroundResource(android.R.drawable.list_selector_background);
                    item.setClickable(true);
                    item.setFocusable(true);
                    item.setOnClickListener(v -> {
                        if (row.contains("MAC: ") && row.matches(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) {
                            selectedHost = row.substring(0, row.indexOf("    "));
                            selected.setText("Selected device: " + row);
                        }
                    });
                    resultList.addView(item, new LinearLayout.LayoutParams(-1, -2));
                }
                scan.setEnabled(true);
            });
        });
    }

    private Map<String, String> readArpTable() {
        Map<String, String> entries = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/arp"))) {
            String line;
            reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4 && parts[3].matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")
                        && !parts[3].equalsIgnoreCase("00:00:00:00:00:00")) {
                    entries.put(parts[0], parts[3].toUpperCase(java.util.Locale.US));
                }
            }
        } catch (Exception ignored) {
            // Android may restrict access to the ARP table.
        }
        return entries;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.shutdownNow();
    }
}

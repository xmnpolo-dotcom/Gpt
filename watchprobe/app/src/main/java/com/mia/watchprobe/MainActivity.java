package com.mia.watchprobe;

import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    BluetoothAdapter adapter;
    BluetoothLeScanner scanner;
    BluetoothGatt gatt;
    LinearLayout deviceList, logList;
    TextView status, connectionTitle, countLabel, emptyDevices;
    Handler h = new Handler();
    HashSet<String> seen = new HashSet<>();
    boolean scanning = false;
    Runnable scanStopTask;
    final int ink = Color.rgb(35, 43, 48);
    final int muted = Color.rgb(112, 119, 119);
    final int paper = Color.rgb(246, 245, 240);
    final int teal = Color.rgb(54, 112, 105);
    final int white = Color.WHITE;

    int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h)); }
    void gap(LinearLayout parent, int height) { View v = new View(this); parent.addView(v, lp(1, height)); }

    TextView pill(String value) {
        TextView t = text(value, 12, teal, true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        t.setBackground(shape(Color.rgb(229, 238, 233), 30));
        return t;
    }

    LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(17), dp(18), dp(17));
        c.setBackground(shape(white, 20));
        return c;
    }

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildScreen();
        BluetoothManager bm = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = bm.getAdapter();
        if (adapter != null) scanner = adapter.getBluetoothLeScanner();
        requestPerms();
    }

    void buildScreen() {
        ScrollView outer = new ScrollView(this);
        outer.setFillViewport(true);
        outer.setBackgroundColor(paper);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(22), dp(20), dp(28));
        outer.addView(root);
        setContentView(outer);

        LinearLayout top = row();
        LinearLayout brand = column();
        brand.addView(text("MIA", 27, ink, true));
        brand.addView(text("WATCH LAB", 11, muted, true));
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(pill("BLUETOOTH LE"));
        root.addView(top);
        gap(root, 22);

        root.addView(text("Tu reloj,\nsin secretos.", 30, ink, true));
        gap(root, 8);
        TextView intro = text("Conecta el reloj y explora los datos que comparte: servicios, sensores, batería y señales.", 15, muted, false);
        intro.setLineSpacing(dp(3), 1f);
        root.addView(intro);
        gap(root, 22);

        LinearLayout connectCard = card();
        LinearLayout cr = row();
        LinearLayout ctext = column();
        ctext.addView(text("ESTADO DEL RELOJ", 11, muted, true));
        connectionTitle = text("Aún no conectado", 19, ink, true);
        ctext.addView(connectionTitle);
        cr.addView(ctext, new LinearLayout.LayoutParams(0, -2, 1));
        cr.addView(pill("LISTO"));
        connectCard.addView(cr);
        gap(connectCard, 9);
        status = text("Busca dispositivos cercanos para empezar.", 13, muted, false);
        connectCard.addView(status);
        root.addView(connectCard);
        gap(root, 14);

        Button scan = new Button(this);
        scan.setText("BUSCAR MI RELOJ");
        scan.setAllCaps(false);
        scan.setTextSize(16);
        scan.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        scan.setTextColor(white);
        scan.setBackground(shape(teal, 16));
        scan.setPadding(dp(12), dp(7), dp(12), dp(7));
        root.addView(scan, lp(-1, 58));
        scan.setOnClickListener(v -> startScan());
        gap(root, 22);

        LinearLayout devicesCard = card();
        LinearLayout dr = row();
        dr.addView(text("Dispositivos encontrados", 18, ink, true), new LinearLayout.LayoutParams(0, -2, 1));
        countLabel = pill("0");
        dr.addView(countLabel);
        devicesCard.addView(dr);
        gap(devicesCard, 10);
        emptyDevices = text("Todavía no hay dispositivos. Pulsa «Buscar mi reloj».", 13, muted, false);
        devicesCard.addView(emptyDevices);
        deviceList = column();
        devicesCard.addView(deviceList);
        root.addView(devicesCard);
        gap(root, 14);

        LinearLayout dataCard = card();
        dataCard.addView(text("Exploración técnica", 18, ink, true));
        gap(dataCard, 5);
        dataCard.addView(text("Al conectar, aquí aparecerán los servicios GATT, UUID, permisos de lectura y escritura, y los datos recibidos en hexadecimal y texto.", 13, muted, false));
        gap(dataCard, 12);
        logList = column();
        dataCard.addView(logList);
        root.addView(dataCard);
        gap(root, 18);
        TextView foot = text("MIA WATCH LAB  ·  Los datos disponibles dependen del firmware del reloj.", 11, muted, false);
        foot.setGravity(Gravity.CENTER);
        root.addView(foot);
    }

    void log(String s) {
        runOnUiThread(() -> {
            TextView t = text(s, 12, ink, false);
            t.setPadding(dp(10), dp(10), dp(10), dp(10));
            t.setBackground(shape(Color.rgb(241, 242, 238), 10));
            LinearLayout.LayoutParams p = lp(-1, -2);
            p.topMargin = dp(6);
            logList.addView(t, p);
        });
    }

    void requestPerms() {
        if (Build.VERSION.SDK_INT >= 31) {
            ArrayList<String> p = new ArrayList<>();
            for (String s : new String[]{android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT})
                if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) p.add(s);
            if (!p.isEmpty()) requestPermissions(p.toArray(new String[0]), 9);
        } else if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 9);
        }
    }

    boolean allowed() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    void startScan() {
        if (!allowed()) { requestPerms(); status.setText("Concede los permisos Bluetooth para continuar."); return; }
        if (adapter == null || !adapter.isEnabled()) { status.setText("Activa el Bluetooth del móvil."); return; }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) { status.setText("Bluetooth LE no está disponible. Reinicia Bluetooth y prueba otra vez."); return; }
        try {
            if (scanStopTask != null) h.removeCallbacks(scanStopTask);
            if (scanning) scanner.stopScan(callback);
            scanning = false;
        } catch (Exception ignored) {}
        seen.clear();
        deviceList.removeAllViews();
        countLabel.setText("0");
        emptyDevices.setVisibility(View.VISIBLE);
        status.setText("Buscando durante 20 segundos…");
        try {
            scanner.startScan(callback);
            scanning = true;
            scanStopTask = () -> {
                try { if (scanning) scanner.stopScan(callback); } catch (Exception ignored) {}
                scanning = false;
                status.setText("Búsqueda terminada · " + seen.size() + " dispositivos.");
            };
            h.postDelayed(scanStopTask, 20000);
        } catch (Exception e) {
            scanning = false;
            status.setText("No se pudo iniciar el escaneo. Reinicia Bluetooth y vuelve a probar.");
            log("Error al iniciar: " + e);
        }
    }

    ScanCallback callback = new ScanCallback() {
        public void onScanResult(int type, ScanResult r) {
            BluetoothDevice d = r.getDevice();
            String id = d.getAddress();
            if (seen.add(id)) {
                String name = d.getName();
                if (name == null || name.isEmpty()) name = r.getScanRecord() != null ? r.getScanRecord().getDeviceName() : null;
                final String n = name == null || name.isEmpty() ? "Dispositivo sin nombre" : name;
                runOnUiThread(() -> {
                    emptyDevices.setVisibility(View.GONE);
                    countLabel.setText(String.valueOf(seen.size()));
                    LinearLayout item = row();
                    item.setPadding(dp(12), dp(12), dp(12), dp(12));
                    item.setBackground(shape(Color.rgb(241, 242, 238), 12));
                    LinearLayout details = column();
                    details.addView(text(n, 15, ink, true));
                    details.addView(text(id + "   ·   " + r.getRssi() + " dBm", 11, muted, false));
                    item.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
                    TextView connect = text("CONECTAR  ›", 11, teal, true);
                    item.addView(connect);
                    LinearLayout.LayoutParams p = lp(-1, -2);
                    p.topMargin = dp(7);
                    deviceList.addView(item, p);
                    item.setOnClickListener(v -> connect(d));
                });
            }
        }
        public void onScanFailed(int e) {
            scanning = false;
            String msg = e == 1 ? "El escaneo ya estaba iniciado." : e == 2 ? "Demasiadas aplicaciones están escaneando." : e == 3 ? "El escaneo no está registrado." : e == 4 ? "Escaneo no disponible ahora." : "Error desconocido.";
            status.setText("Error Bluetooth " + e + " · " + msg);
            log("Error de escaneo " + e + ": " + msg);
        }
    };

    void connect(BluetoothDevice d) {
        if (!allowed()) { requestPerms(); return; }
        status.setText("Conectando con " + d.getAddress() + "…");
        connectionTitle.setText("Conectando…");
        if (gatt != null) { try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {} }
        gatt = d.connectGatt(this, false, gattCallback);
    }

    BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        public void onConnectionStateChange(BluetoothGatt g, int s, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> { connectionTitle.setText("Reloj conectado"); status.setText("Conectado. Buscando servicios del reloj…"); });
                log("CONECTADO · " + g.getDevice().getAddress());
                g.discoverServices();
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> connectionTitle.setText("Desconectado"));
                log("Desconectado · estado " + s);
            }
        }

        public void onServicesDiscovered(BluetoothGatt g, int s) {
            if (s != BluetoothGatt.GATT_SUCCESS) { log("Error descubriendo servicios: " + s); return; }
            int total = 0;
            for (BluetoothGattService service : g.getServices()) {
                log("SERVICIO: " + service.getUuid());
                for (BluetoothGattCharacteristic c : service.getCharacteristics()) {
                    total++;
                    int p = c.getProperties();
                    log("CARACTERÍSTICA: " + c.getUuid() + " · " + props(p));
                    if ((p & BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        h.postDelayed(() -> { try { g.readCharacteristic(c); } catch (Exception e) { log("No se pudo solicitar lectura: " + c.getUuid()); } }, 700L * total);
                    }
                    if ((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                        g.setCharacteristicNotification(c, true);
                        BluetoothGattDescriptor desc = c.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
                        if (desc != null) {
                            desc.setValue((p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                            h.postDelayed(() -> { try { g.writeDescriptor(desc); } catch (Exception e) { log("No se pudo activar notificación."); } }, 900L * total);
                        }
                    }
                }
            }
            final int n = total;
            runOnUiThread(() -> status.setText("Servicios encontrados: " + g.getServices().size() + " · Características: " + n));
        }

        public void onCharacteristicRead(BluetoothGatt g, BluetoothGattCharacteristic c, int s) {
            if (s == BluetoothGatt.GATT_SUCCESS) showValue(c, "LECTURA"); else log("Lectura fallida " + c.getUuid() + " · " + s);
        }
        public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) { showValue(c, "DATO EN VIVO"); }
        void showValue(BluetoothGattCharacteristic c, String label) {
            byte[] b = c.getValue();
            if (b == null) return;
            StringBuilder hex = new StringBuilder();
            for (byte x : b) hex.append(String.format("%02X ", x));
            log(label + " · " + c.getUuid() + "\nHEX: " + hex + "\nTEXTO: " + new String(b, StandardCharsets.UTF_8));
        }
    };

    String props(int p) {
        ArrayList<String> a = new ArrayList<>();
        if ((p & 2) != 0) a.add("LECTURA");
        if ((p & 8) != 0) a.add("ESCRITURA");
        if ((p & 4) != 0) a.add("ESCRITURA RÁPIDA");
        if ((p & 16) != 0) a.add("NOTIFICACIONES");
        if ((p & 32) != 0) a.add("INDICACIONES");
        return a.isEmpty() ? "Sin permisos estándar" : a.toString();
    }

    protected void onDestroy() {
        super.onDestroy();
        try {
            if (scanStopTask != null) h.removeCallbacks(scanStopTask);
            if (scanner != null && scanning) scanner.stopScan(callback);
            if (gatt != null) gatt.close();
        } catch (Exception ignored) {}
    }
}

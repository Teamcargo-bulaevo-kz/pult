package kz.teamcargo.pult;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;

/** Protocol, address and per-key command codes, kept in SharedPreferences. */
public final class Config {
    private static final String PREFS = "pult";
    private final SharedPreferences sp;

    public Config(Context ctx) {
        sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String protocol() {
        return sp.getString("protocol", IrCodec.NEC);
    }

    public int address() {
        return sp.getInt("address", 0x00);
    }

    public void setProtocol(String p) {
        sp.edit().putString("protocol", p).apply();
    }

    public void setAddress(int a) {
        sp.edit().putInt("address", a & 0xFFFF).apply();
    }

    /** -1 when the key is not set up yet. */
    public int command(String key) {
        return sp.getInt("k_" + key, -1);
    }

    public void setCommand(String key, int cmd) {
        if (cmd < 0) sp.edit().remove("k_" + key).apply();
        else sp.edit().putInt("k_" + key, cmd & 0xFF).apply();
    }

    public int configuredCount() {
        int n = 0;
        for (String k : Keys.ALL.keySet()) if (command(k) >= 0) n++;
        return n;
    }

    /** Text form: one "name=value" per line, easy to share in a messenger. */
    public String export() {
        StringBuilder b = new StringBuilder();
        b.append("# Пульт Телекарта EVO 09\n");
        b.append("protocol=").append(protocol()).append('\n');
        b.append("address=").append(IrCodec.hex(address(), address() > 0xFF ? 4 : 2)).append('\n');
        for (Map.Entry<String, String> e : Keys.ALL.entrySet()) {
            int c = command(e.getKey());
            if (c >= 0) b.append(e.getKey()).append('=').append(IrCodec.hex(c, 2)).append('\n');
        }
        return b.toString();
    }

    /** Returns the number of keys imported, or -1 when the text has no valid lines. */
    public int importText(String text) {
        if (text == null) return -1;
        SharedPreferences.Editor ed = sp.edit();
        int keys = 0;
        boolean any = false;
        for (String raw : text.split("[\\n;,]")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String name = line.substring(0, eq).trim().toLowerCase();
            String val = line.substring(eq + 1).trim();
            if (name.equals("protocol")) {
                String p = val.toUpperCase();
                if (p.equals(IrCodec.NEC) || p.equals(IrCodec.SAMSUNG)) {
                    ed.putString("protocol", p);
                    any = true;
                }
            } else if (name.equals("address")) {
                int a = IrCodec.parseHex(val);
                if (a >= 0) {
                    ed.putInt("address", a);
                    any = true;
                }
            } else if (Keys.ALL.containsKey(name)) {
                int c = IrCodec.parseHex(val);
                if (c >= 0 && c <= 0xFF) {
                    ed.putInt("k_" + name, c);
                    keys++;
                    any = true;
                }
            }
        }
        if (!any) return -1;
        ed.apply();
        return keys;
    }
}

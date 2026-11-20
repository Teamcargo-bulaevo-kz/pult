package kz.teamcargo.pult;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** The remote itself. */
public class MainActivity extends Activity {
    private static final Set<String> REPEAT = new HashSet<>();

    static {
        for (String k : new String[]{"volup", "voldown", "chup", "chdown", "up", "down", "left", "right", "rew", "fwd"}) {
            REPEAT.add(k);
        }
    }

    private Config cfg;
    private Ir ir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<String, Integer> colors = new HashMap<>();
    private TextView status;
    private Runnable repeater;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = new Config(this);
        ir = new Ir(this);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        setContentView(build());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onDestroy() {
        stopRepeat();
        ir.quit();
        super.onDestroy();
    }

    private ScrollView build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);

        LinearLayout head = Ui.row(this);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView t = Ui.text(this, "Телекарта EVO 09", 20, Ui.TEXT);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titles.addView(t);
        status = Ui.text(this, "", 13, Ui.MUTED);
        titles.addView(status);
        Ui.add(head, titles, 2.2f);
        Button setup = Ui.button(this, "⚙ Настройка", Ui.ACCENT);
        setup.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
        Ui.add(head, setup, 1.3f);
        root.addView(head);

        if (!ir.available()) {
            TextView warn = Ui.text(this,
                    "ИК-передатчик не найден. Приложение работает только на телефоне с ИК-портом (как Redmi Note 8).",
                    14, Ui.TEXT);
            warn.setBackground(Ui.round(this, Ui.RED, 10));
            warn.setPadding(pad, pad / 2, pad, pad / 2);
            root.addView(warn);
        }

        root.addView(keys(new String[]{"power", "mute", "tvradio", "sleep"},
                new String[]{"⏻", "🔇", "ТВ/Р", "Сон"}, new int[]{Ui.RED, 0, 0, 0}));

        gap(root);
        root.addView(keys(new String[]{"d1", "d2", "d3"}, null, null));
        root.addView(keys(new String[]{"d4", "d5", "d6"}, null, null));
        root.addView(keys(new String[]{"d7", "d8", "d9"}, null, null));
        root.addView(keys(new String[]{"recall", "d0", "fav"}, new String[]{"↺ Пред.", "0", "★ FAV"}, null));

        gap(root);
        root.addView(keys(new String[]{"menu", "up", "exit"}, new String[]{"Меню", "▲", "Выход"}, null));
        root.addView(keys(new String[]{"left", "ok", "right"}, new String[]{"◀", "OK", "▶"}, new int[]{0, Ui.ACCENT, 0}));
        root.addView(keys(new String[]{"back", "down", "info"}, new String[]{"↩ Назад", "▼", "Инфо"}, null));

        gap(root);
        root.addView(keys(new String[]{"volup", "epg", "chup"}, new String[]{"Гр +", "EPG", "Кан +"}, null));
        root.addView(keys(new String[]{"voldown", "audio", "chdown"}, new String[]{"Гр −", "Аудио", "Кан −"}, null));

        gap(root);
        root.addView(keys(new String[]{"red", "green", "yellow", "blue"}, new String[]{" ", " ", " ", " "},
                new int[]{Ui.RED, Ui.GREEN, Ui.YELLOW, Ui.BLUE}));
        root.addView(keys(new String[]{"sub", "ttx", "rec", "stop"}, new String[]{"Субт.", "TTX", "⏺", "⏹"}, null));
        root.addView(keys(new String[]{"rew", "play", "pause", "fwd"}, new String[]{"⏪", "▶", "⏸", "⏩"}, null));

        TextView hint = Ui.text(this,
                "Серые кнопки ещё не настроены: нажми на любую, и откроется подбор кода для неё.",
                13, Ui.MUTED);
        hint.setPadding(0, pad, 0, 0);
        root.addView(hint);
        return sv;
    }

    private void gap(LinearLayout root) {
        android.view.View v = new android.view.View(this);
        root.addView(v, new LinearLayout.LayoutParams(1, Ui.dp(this, 10)));
    }

    private LinearLayout keys(String[] ids, String[] labels, int[] cols) {
        LinearLayout r = Ui.row(this);
        for (int i = 0; i < ids.length; i++) {
            String id = ids[i];
            String label = labels != null ? labels[i] : Keys.label(id);
            int col = cols != null && cols[i] != 0 ? cols[i] : Ui.KEY;
            Button b = Ui.button(this, label, col);
            b.setTextSize(18);
            buttons.put(id, b);
            colors.put(id, col);
            b.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setAlpha(0.6f);
                        press(id);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(1f);
                        stopRepeat();
                        return true;
                    default:
                        return true;
                }
            });
            Ui.add(r, b, 1f);
        }
        return r;
    }

    private void press(String id) {
        int cmd = cfg.command(id);
        if (cmd < 0) {
            askSetup(id);
            return;
        }
        Button b = buttons.get(id);
        if (b != null) b.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        ir.send(cfg.protocol(), cfg.address(), cmd);
        if (REPEAT.contains(id)) {
            stopRepeat();
            repeater = new Runnable() {
                @Override
                public void run() {
                    ir.send(cfg.protocol(), cfg.address(), cmd);
                    ui.postDelayed(this, 220);
                }
            };
            ui.postDelayed(repeater, 450);
        }
    }

    private void stopRepeat() {
        if (repeater != null) ui.removeCallbacks(repeater);
        repeater = null;
    }

    private void askSetup(String id) {
        new AlertDialog.Builder(this)
                .setTitle(Keys.label(id))
                .setMessage("Эта кнопка ещё не настроена. Подобрать код для неё?")
                .setPositiveButton("Подобрать", (d, w) -> {
                    Intent i = new Intent(this, SetupActivity.class);
                    i.putExtra(SetupActivity.EXTRA_KEY, id);
                    startActivity(i);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void refresh() {
        int n = cfg.configuredCount();
        status.setText("Настроено кнопок: " + n + " из " + Keys.ALL.size()
                + " · " + cfg.protocol() + " " + IrCodec.hex(cfg.address(), cfg.address() > 0xFF ? 4 : 2));
        for (Map.Entry<String, Button> e : buttons.entrySet()) {
            boolean on = cfg.command(e.getKey()) >= 0;
            Integer col = colors.get(e.getKey());
            int base = col == null ? Ui.KEY : col;
            int off = base == Ui.KEY ? Ui.KEY_OFF : (base & 0x00FFFFFF) | 0x55000000;
            e.getValue().setBackground(Ui.round(this, on ? base : off, 14));
            e.getValue().setTextColor(on ? Ui.TEXT : Ui.MUTED);
        }
    }
}

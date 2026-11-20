package kz.teamcargo.pult;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Finds the codes of the set-top box by trying them one after another.
 * The phone can only send IR (it has no receiver), so the user watches
 * the box and says when it reacted.
 */
public class SetupActivity extends Activity {
    public static final String EXTRA_KEY = "key";

    /** Addresses that Chinese DVB receivers use most often go first. */
    private static final int[] LIKELY = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x10,
            0x20, 0x40, 0x80, 0x7F, 0xFE, 0xFF, 0x0A, 0x0E, 0x11, 0x12, 0x18, 0x1C, 0x22, 0x33,
            0x44, 0x4D, 0x55, 0x6E, 0x77, 0x86, 0x88, 0x99, 0xAA, 0xBB, 0xC0, 0xCC, 0xDD, 0xEE};
    private static final int[] ADDR_ORDER = new int[256];

    static {
        boolean[] used = new boolean[256];
        int n = 0;
        for (int a : LIKELY) {
            if (!used[a]) {
                used[a] = true;
                ADDR_ORDER[n++] = a;
            }
        }
        for (int a = 0; a < 256; a++) if (!used[a]) ADDR_ORDER[n++] = a;
    }

    private static final int MODE_ADDR = 0;
    private static final int MODE_CMD = 1;
    private static final long[] SPEEDS = {600, 1200, 2000};
    private static final String[] SPEED_NAMES = {"Быстро 0,6 с", "Средне 1,2 с", "Медленно 2 с"};

    private Config cfg;
    private Ir ir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String targetKey;

    private String protocol;
    private int address;
    private int command;
    private int mode = MODE_CMD;
    private int speed = 0;
    private boolean running;
    private final ArrayDeque<int[]> history = new ArrayDeque<>();

    private TextView addrView, cmdView, stateView;
    private Button necBtn, samBtn, rc5Btn, rc6Btn, modeAddrBtn, modeCmdBtn, speedBtn, startBtn, assignBtn;
    private LinearLayout historyBox, assignedBox;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = new Config(this);
        ir = new Ir(this);
        protocol = cfg.protocol();
        address = cfg.address();
        targetKey = getIntent().getStringExtra(EXTRA_KEY);
        command = 0;
        if (targetKey != null && cfg.command(targetKey) >= 0) command = cfg.command(targetKey);
        mode = MODE_CMD;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        setContentView(build());
        refresh();
    }

    @Override
    protected void onPause() {
        stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ir.quit();
        super.onDestroy();
    }

    // ---------- layout ----------

    private ScrollView build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad * 2);
        sv.addView(root);

        TextView h = Ui.text(this, targetKey == null ? "Настройка пульта" : "Подбор: " + Keys.label(targetKey), 20, Ui.TEXT);
        h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(h);
        root.addView(Ui.text(this,
                "Телефон умеет только передавать ИК-сигнал, принимать его он не может. Поэтому коды подбираются перебором: "
                        + "направь телефон верхним торцом на приставку, запусти перебор и смотри на экран телевизора. "
                        + "Как только приставка что-то сделала, жми большую кнопку «Сработало!».\n\n"
                        + "Шаг 1. «⚡ Найти приставку»: быстрый поиск адреса и протокола (обычно 1–5 минут).\n"
                        + "Шаг 2. Режим «Подбор команд»: команды идут по одной. Увидел реакцию, жми «Сработало!», "
                        + "проверь команду из списка последних и назначь её кнопке.\n\n"
                        + "Перебор может открыть меню приставки или переключить канал. Это не страшно: потом нажми «Выход».",
                14, Ui.MUTED));

        root.addView(Ui.title(this, "Протокол"));
        LinearLayout pr = Ui.row(this);
        necBtn = Ui.button(this, "NEC", Ui.KEY);
        samBtn = Ui.button(this, "Samsung", Ui.KEY);
        rc5Btn = Ui.button(this, "RC5", Ui.KEY);
        rc6Btn = Ui.button(this, "RC6", Ui.KEY);
        necBtn.setOnClickListener(v -> setProtocol(IrCodec.NEC));
        samBtn.setOnClickListener(v -> setProtocol(IrCodec.SAMSUNG));
        rc5Btn.setOnClickListener(v -> setProtocol(IrCodec.RC5));
        rc6Btn.setOnClickListener(v -> setProtocol(IrCodec.RC6));
        Ui.add(pr, necBtn, 1);
        Ui.add(pr, samBtn, 1.3f);
        Ui.add(pr, rc5Btn, 1);
        Ui.add(pr, rc6Btn, 1);
        root.addView(pr);

        root.addView(Ui.title(this, "Адрес приставки"));
        LinearLayout ar = Ui.row(this);
        Button aMinus = Ui.button(this, "−", Ui.KEY);
        Button aPlus = Ui.button(this, "+", Ui.KEY);
        addrView = Ui.text(this, "", 26, Ui.TEXT);
        addrView.setGravity(android.view.Gravity.CENTER);
        Button aEdit = Ui.button(this, "Ввести", Ui.KEY);
        aMinus.setOnClickListener(v -> {
            stop();
            int m = Math.min(IrCodec.maxAddress(protocol), 0xFF);
            address = address <= 0 ? m : Math.min(address - 1, m);
            refresh();
        });
        aPlus.setOnClickListener(v -> {
            stop();
            int m = Math.min(IrCodec.maxAddress(protocol), 0xFF);
            address = address >= m ? 0 : address + 1;
            refresh();
        });
        aEdit.setOnClickListener(v -> askHex("Адрес (hex, 00–FF или 4 знака для расширенного NEC)", address, IrCodec.maxAddress(protocol), val -> {
            address = val;
            refresh();
        }));
        Ui.add(ar, aMinus, 1);
        Ui.add(ar, addrView, 2);
        Ui.add(ar, aPlus, 1);
        Ui.add(ar, aEdit, 1.4f);
        root.addView(ar);

        root.addView(Ui.title(this, "Команда"));
        LinearLayout cr = Ui.row(this);
        Button cMinus = Ui.button(this, "−", Ui.KEY);
        Button cPlus = Ui.button(this, "+", Ui.KEY);
        cmdView = Ui.text(this, "", 26, Ui.TEXT);
        cmdView.setGravity(android.view.Gravity.CENTER);
        Button cEdit = Ui.button(this, "Ввести", Ui.KEY);
        cMinus.setOnClickListener(v -> {
            stop();
            int m = IrCodec.maxCommand(protocol);
            command = command <= 0 ? m : Math.min(command - 1, m);
            sendCurrent();
        });
        cPlus.setOnClickListener(v -> {
            stop();
            int m = IrCodec.maxCommand(protocol);
            command = command >= m ? 0 : command + 1;
            sendCurrent();
        });
        cEdit.setOnClickListener(v -> askHex("Команда (hex)", command, IrCodec.maxCommand(protocol), val -> {
            command = val;
            sendCurrent();
        }));
        Ui.add(cr, cMinus, 1);
        Ui.add(cr, cmdView, 2);
        Ui.add(cr, cPlus, 1);
        Ui.add(cr, cEdit, 1.4f);
        root.addView(cr);
        root.addView(Ui.text(this, "Кнопки − и + сразу отправляют новую команду.", 13, Ui.MUTED));

        LinearLayout sr = Ui.row(this);
        Button send = Ui.button(this, "📡 Отправить ещё раз", Ui.KEY);
        send.setOnClickListener(v -> {
            stop();
            sendCurrent();
        });
        Ui.add(sr, send, 1);
        root.addView(sr);

        root.addView(Ui.title(this, "Перебор"));
        LinearLayout mr = Ui.row(this);
        modeAddrBtn = Ui.button(this, "1. ⚡ Найти приставку", Ui.KEY);
        modeCmdBtn = Ui.button(this, "2. Подбор команд", Ui.KEY);
        modeAddrBtn.setOnClickListener(v -> {
            stop();
            startActivity(new Intent(this, FastSearchActivity.class));
            finish();
        });
        modeCmdBtn.setOnClickListener(v -> {
            stop();
            mode = MODE_CMD;
            refresh();
        });
        Ui.add(mr, modeAddrBtn, 1);
        Ui.add(mr, modeCmdBtn, 1);
        root.addView(mr);

        LinearLayout spr = Ui.row(this);
        speedBtn = Ui.button(this, "", Ui.KEY);
        speedBtn.setOnClickListener(v -> {
            speed = (speed + 1) % SPEEDS.length;
            refresh();
        });
        Ui.add(spr, speedBtn, 1);
        root.addView(spr);

        stateView = Ui.text(this, "", 15, Ui.TEXT);
        stateView.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        root.addView(stateView);

        startBtn = Ui.button(this, "", Ui.ACCENT);
        startBtn.setTextSize(20);
        startBtn.setMinHeight(Ui.dp(this, 72));
        startBtn.setOnClickListener(v -> {
            if (running) reacted();
            else start();
        });
        LinearLayout stRow = Ui.row(this);
        Ui.add(stRow, startBtn, 1);
        root.addView(stRow);

        root.addView(Ui.title(this, "Последние отправленные"));
        root.addView(Ui.text(this,
                "Ты реагируешь с задержкой, поэтому нужная команда обычно одна из последних. "
                        + "Нажми на строку, чтобы отправить её ещё раз и проверить.", 13, Ui.MUTED));
        historyBox = new LinearLayout(this);
        historyBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(historyBox);

        assignBtn = Ui.button(this, "", Ui.GREEN);
        assignBtn.setOnClickListener(v -> assign());
        LinearLayout asRow = Ui.row(this);
        Ui.add(asRow, assignBtn, 1);
        root.addView(asRow);

        root.addView(Ui.title(this, "Настроенные кнопки"));
        assignedBox = new LinearLayout(this);
        assignedBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(assignedBox);

        root.addView(Ui.title(this, "Сохранить или перенести"));
        LinearLayout ex = Ui.row(this);
        Button exp = Ui.button(this, "Экспорт", Ui.KEY);
        Button imp = Ui.button(this, "Импорт", Ui.KEY);
        Button clr = Ui.button(this, "Сбросить", Ui.KEY);
        exp.setOnClickListener(v -> exportConfig());
        imp.setOnClickListener(v -> importConfig());
        clr.setOnClickListener(v -> resetAll());
        Ui.add(ex, exp, 1);
        Ui.add(ex, imp, 1);
        Ui.add(ex, clr, 1);
        root.addView(ex);
        root.addView(Ui.text(this,
                "Экспорт даёт текст с кодами: его можно сохранить в заметках или отправить другу с такой же приставкой. "
                        + "Импорт принимает этот же текст.", 13, Ui.MUTED));
        return sv;
    }

    // ---------- actions ----------

    private void setProtocol(String p) {
        stop();
        protocol = p;
        if (address > IrCodec.maxAddress(p)) address = 0;
        if (command > IrCodec.maxCommand(p)) command = 0;
        cfg.setProtocol(p);
        refresh();
    }

    private void sendCurrent() {
        ir.send(protocol, address, command);
        remember(address, command);
        refresh();
    }

    private void remember(int a, int c) {
        history.addFirst(new int[]{a, c});
        while (history.size() > 8) history.removeLast();
    }

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            ir.send(protocol, address, command);
            remember(address, command);
            refresh();
            long delay;
            if (mode == MODE_ADDR) {
                delay = 150; // 108 ms frame + gap
                if (command == 0xFF) {
                    int idx = addrIndex(address) + 1;
                    if (idx >= ADDR_ORDER.length || address > 0xFF) {
                        finishScan("Все адреса перебраны. Попробуй протокол Samsung или проверь, что телефон смотрит на приставку.");
                        return;
                    }
                    address = ADDR_ORDER[idx];
                    command = 0;
                } else {
                    command++;
                }
            } else {
                delay = SPEEDS[speed];
                if (command >= IrCodec.maxCommand(protocol)) {
                    ui.postDelayed(() -> finishScan("Все команды перебраны."), delay);
                    return;
                }
                command++;
            }
            ui.postDelayed(this, delay);
        }
    };

    private static int addrIndex(int a) {
        for (int i = 0; i < ADDR_ORDER.length; i++) if (ADDR_ORDER[i] == a) return i;
        return -1;
    }

    private void start() {
        if (!ir.available()) {
            Toast.makeText(this, "На этом телефоне нет ИК-передатчика", Toast.LENGTH_LONG).show();
            return;
        }
        if (mode == MODE_ADDR || command >= IrCodec.maxCommand(protocol)) command = 0;
        running = true;
        ui.post(step);
        refresh();
    }

    private void stop() {
        if (!running) return;
        running = false;
        ui.removeCallbacks(step);
        ir.cancelPending();
        refresh();
    }

    private void finishScan(String msg) {
        stop();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /** The user saw the box react: stop and step back to what was just sent. */
    private void reacted() {
        stop();
        int[] last = history.peekFirst();
        if (last != null) {
            address = last[0];
            command = last[1];
        }
        if (mode == MODE_ADDR) {
            cfg.setAddress(address);
            cfg.setProtocol(protocol);
            mode = MODE_CMD;
            new AlertDialog.Builder(this)
                    .setTitle("Адрес найден?")
                    .setMessage("Приставка отреагировала, пока шёл адрес " + IrCodec.hex(address, 2)
                            + ". Он сохранён. Если в списке последних видны два разных адреса, проверь оба.\n\n"
                            + "Дальше режим «Подбор команд»: нажми «Старт» и назначай кнопки.")
                    .setPositiveButton("Понятно", null)
                    .show();
            command = 0;
        }
        refresh();
    }

    private void assign() {
        stop();
        if (targetKey != null) {
            saveKey(targetKey, command);
            return;
        }
        List<String> ids = new ArrayList<>(Keys.ALL.keySet());
        String[] items = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) {
            int c = cfg.command(ids.get(i));
            items[i] = Keys.label(ids.get(i)) + (c >= 0 ? "   (сейчас " + IrCodec.hex(c, 2) + ")" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("Команда " + IrCodec.hex(command, 2) + ": какая это кнопка?")
                .setItems(items, (d, w) -> saveKey(ids.get(w), command))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveKey(String key, int cmd) {
        cfg.setProtocol(protocol);
        cfg.setAddress(address);
        cfg.setCommand(key, cmd);
        Toast.makeText(this, Keys.label(key) + " = " + IrCodec.hex(cmd, 2), Toast.LENGTH_SHORT).show();
        if (targetKey != null) {
            finish();
            return;
        }
        refresh();
    }

    private interface IntCallback {
        void run(int v);
    }

    private void askHex(String title, int current, int max, IntCallback cb) {
        stop();
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        e.setText(Integer.toHexString(current).toUpperCase());
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(e)
                .setPositiveButton("OK", (d, w) -> {
                    int v = IrCodec.parseHex(e.getText().toString());
                    if (v < 0 || v > max) {
                        Toast.makeText(this, "Нужно шестнадцатеричное число, например 1F", Toast.LENGTH_LONG).show();
                    } else {
                        cb.run(v);
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void exportConfig() {
        String text = cfg.export();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("pult", text));
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, text);
        Toast.makeText(this, "Коды скопированы", Toast.LENGTH_SHORT).show();
        startActivity(Intent.createChooser(share, "Отправить коды пульта"));
    }

    private void importConfig() {
        stop();
        EditText e = new EditText(this);
        e.setMinLines(6);
        e.setHint("protocol=NEC\naddress=0x00\npower=0x0A\n…");
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).getText();
            if (clip != null && clip.toString().contains("=")) e.setText(clip);
        }
        new AlertDialog.Builder(this)
                .setTitle("Вставь текст с кодами")
                .setView(e)
                .setPositiveButton("Импорт", (d, w) -> {
                    int n = cfg.importText(e.getText().toString());
                    if (n < 0) {
                        Toast.makeText(this, "Не нашёл в тексте кодов", Toast.LENGTH_LONG).show();
                    } else {
                        protocol = cfg.protocol();
                        address = cfg.address();
                        Toast.makeText(this, "Импортировано кнопок: " + n, Toast.LENGTH_LONG).show();
                        refresh();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void resetAll() {
        new AlertDialog.Builder(this)
                .setTitle("Сбросить все коды?")
                .setMessage("Удалятся все назначенные кнопки.")
                .setPositiveButton("Сбросить", (d, w) -> {
                    for (String k : Keys.ALL.keySet()) cfg.setCommand(k, -1);
                    refresh();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    // ---------- state -> views ----------

    private void refresh() {
        necBtn.setBackground(Ui.round(this, IrCodec.NEC.equals(protocol) ? Ui.ACCENT : Ui.KEY, 14));
        samBtn.setBackground(Ui.round(this, IrCodec.SAMSUNG.equals(protocol) ? Ui.ACCENT : Ui.KEY, 14));
        rc5Btn.setBackground(Ui.round(this, IrCodec.RC5.equals(protocol) ? Ui.ACCENT : Ui.KEY, 14));
        rc6Btn.setBackground(Ui.round(this, IrCodec.RC6.equals(protocol) ? Ui.ACCENT : Ui.KEY, 14));
        modeAddrBtn.setBackground(Ui.round(this, mode == MODE_ADDR ? Ui.ACCENT : Ui.KEY, 14));
        modeCmdBtn.setBackground(Ui.round(this, mode == MODE_CMD ? Ui.ACCENT : Ui.KEY, 14));
        speedBtn.setText("Скорость подбора команд: " + SPEED_NAMES[speed]);
        speedBtn.setEnabled(mode == MODE_CMD);
        speedBtn.setAlpha(mode == MODE_CMD ? 1f : 0.4f);

        addrView.setText(IrCodec.hex(address, address > 0xFF ? 4 : 2));
        cmdView.setText(IrCodec.hex(command, 2));

        if (running) {
            startBtn.setText("✋ Сработало!");
            startBtn.setBackground(Ui.round(this, Ui.RED, 14));
            if (mode == MODE_ADDR) {
                int idx = addrIndex(address) + 1;
                stateView.setText("Ищу адрес: " + IrCodec.hex(address, 2) + " (" + idx + " из 256), команда "
                        + IrCodec.hex(command, 2));
            } else {
                stateView.setText("Перебираю команды: " + IrCodec.hex(command, 2) + " из "
                        + IrCodec.hex(IrCodec.maxCommand(protocol), 2));
            }
        } else {
            startBtn.setText(mode == MODE_ADDR ? "▶ Старт: найти адрес" : "▶ Старт: подбор с " + IrCodec.hex(command, 2));
            startBtn.setBackground(Ui.round(this, Ui.ACCENT, 14));
            stateView.setText("Сохранённый адрес: " + cfg.protocol() + " " + IrCodec.hex(cfg.address(), cfg.address() > 0xFF ? 4 : 2));
        }

        assignBtn.setText(targetKey != null
                ? "✔ Назначить " + IrCodec.hex(command, 2) + " кнопке «" + Keys.label(targetKey) + "»"
                : "✔ Назначить команду " + IrCodec.hex(command, 2) + " кнопке…");

        historyBox.removeAllViews();
        Iterator<int[]> it = history.iterator();
        while (it.hasNext()) {
            int[] h = it.next();
            Button b = Ui.button(this, "адрес " + IrCodec.hex(h[0], h[0] > 0xFF ? 4 : 2) + "   команда " + IrCodec.hex(h[1], 2), Ui.KEY_OFF);
            b.setTextSize(15);
            b.setOnClickListener(v -> {
                stop();
                address = h[0];
                command = h[1];
                ir.send(protocol, address, command);
                refresh();
            });
            LinearLayout r = Ui.row(this);
            Ui.add(r, b, 1);
            historyBox.addView(r);
        }
        if (history.isEmpty()) historyBox.addView(Ui.text(this, "Пока ничего не отправлено.", 13, Ui.MUTED));

        assignedBox.removeAllViews();
        int n = 0;
        for (Map.Entry<String, String> e : Keys.ALL.entrySet()) {
            int c = cfg.command(e.getKey());
            if (c < 0) continue;
            n++;
            String key = e.getKey();
            LinearLayout r = Ui.row(this);
            TextView l = Ui.text(this, e.getValue() + "  ·  " + IrCodec.hex(c, 2), 15, Ui.TEXT);
            Button test = Ui.button(this, "▶", Ui.KEY);
            Button del = Ui.button(this, "✕", Ui.KEY);
            test.setOnClickListener(v -> ir.send(cfg.protocol(), cfg.address(), c));
            del.setOnClickListener(v -> {
                cfg.setCommand(key, -1);
                refresh();
            });
            Ui.add(r, l, 3);
            Ui.add(r, test, 1);
            Ui.add(r, del, 1);
            assignedBox.addView(r);
        }
        if (n == 0) assignedBox.addView(Ui.text(this, "Пока ни одной.", 13, Ui.MUTED));
    }
}

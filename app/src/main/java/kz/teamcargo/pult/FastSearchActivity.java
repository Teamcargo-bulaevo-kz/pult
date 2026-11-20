package kz.teamcargo.pult;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Quick search: for each (protocol, address) pair sends a short burst of
 * commands, then pauses. The user presses one big button when the box reacts.
 */
public class FastSearchActivity extends Activity {
    private static final long FRAME_MS = 115;
    private static final long PAUSE_MS = 900;

    private Config cfg;
    private Ir ir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<FastSearch.Candidate> list = FastSearch.candidates();

    private int index;
    private int cmd;
    private boolean running;
    /** Time the current burst started, to tell whether the reaction belongs to the previous one. */
    private long burstStart;

    private TextView big, small, eta;
    private ProgressBar progress;
    private Button main;
    private LinearLayout verifyBox;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = new Config(this);
        ir = new Ir(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setBackgroundColor(Ui.BG);
        setContentView(build());
        idle();
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

    private ScrollView build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        root.setPadding(pad, pad, pad, pad * 2);
        sv.addView(root);

        TextView h = Ui.text(this, "⚡ Быстрый поиск", 22, Ui.TEXT);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(h);
        root.addView(Ui.text(this,
                "1. Включи приставку, на телевизоре должен идти канал.\n"
                        + "2. Положи телефон верхним краем к приставке, 1–2 метра.\n"
                        + "3. Нажми «Старт» и смотри на ТЕЛЕВИЗОР, а не на телефон.\n"
                        + "4. Как только на экране что-то изменится (канал, цифры, меню, громкость, приставка выключилась), "
                        + "жми большую красную кнопку.\n\n"
                        + "Самые частые варианты идут первыми: обычно находится за 1–5 минут. Весь перебор — до 25 минут.",
                15, Ui.MUTED));

        big = Ui.text(this, "", 30, Ui.TEXT);
        big.setGravity(Gravity.CENTER);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setPadding(0, pad, 0, 0);
        root.addView(big);
        small = Ui.text(this, "", 15, Ui.MUTED);
        small.setGravity(Gravity.CENTER);
        root.addView(small);

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(list.size());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 12));
        plp.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        root.addView(progress, plp);
        eta = Ui.text(this, "", 13, Ui.MUTED);
        eta.setGravity(Gravity.CENTER);
        root.addView(eta);

        main = Ui.button(this, "", Ui.ACCENT);
        main.setTextSize(24);
        main.setMinHeight(Ui.dp(this, 120));
        main.setOnClickListener(v -> {
            if (running) reacted();
            else start();
        });
        LinearLayout mr = Ui.row(this);
        Ui.add(mr, main, 1);
        root.addView(mr);

        verifyBox = new LinearLayout(this);
        verifyBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(verifyBox);

        TextView tip = Ui.text(this,
                "Ничего не находится? Проверь, что ИК-порт светит: открой камеру на ДРУГОМ телефоне, "
                        + "наведи на верх Redmi и нажми «Старт». На экране камеры должна мигать точка. "
                        + "Если не мигает, мешает чехол или ИК-порт неисправен.",
                13, Ui.MUTED);
        tip.setPadding(0, pad, 0, 0);
        root.addView(tip);
        return sv;
    }

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            FastSearch.Candidate c = list.get(index);
            if (cmd == 0) burstStart = System.currentTimeMillis();
            ir.send(c.protocol, c.address, cmd);
            cmd++;
            if (cmd < FastSearch.BURST) {
                ui.postDelayed(this, FRAME_MS);
                return;
            }
            cmd = 0;
            if (index + 1 >= list.size()) {
                ui.postDelayed(() -> {
                    stop();
                    big.setText("Не нашлось");
                    small.setText("Приставка не отреагировала ни на один вариант.");
                    Toast.makeText(FastSearchActivity.this,
                            "Проверь ИК-порт камерой другого телефона (подсказка внизу)", Toast.LENGTH_LONG).show();
                }, PAUSE_MS);
                return;
            }
            index++;
            ui.postDelayed(() -> {
                show();
                ui.post(step);
            }, PAUSE_MS);
        }
    };

    private void start() {
        if (!ir.available()) {
            Toast.makeText(this, "На этом телефоне нет ИК-передатчика", Toast.LENGTH_LONG).show();
            return;
        }
        if (index >= list.size() - 1) index = 0;
        verifyBox.removeAllViews();
        cmd = 0;
        running = true;
        main.setText("✋ ПРИСТАВКА ОТРЕАГИРОВАЛА");
        main.setBackground(Ui.round(this, Ui.RED, 14));
        show();
        ui.post(step);
    }

    private void stop() {
        running = false;
        ui.removeCallbacksAndMessages(null);
        ir.cancelPending();
        main.setText(index > 0 ? "▶ Продолжить поиск" : "▶ Старт");
        main.setBackground(Ui.round(this, Ui.ACCENT, 14));
    }

    private void idle() {
        big.setText("Готов");
        small.setText("Вариантов: " + list.size());
        progress.setProgress(0);
        eta.setText("");
        main.setText("▶ Старт");
        main.setBackground(Ui.round(this, Ui.ACCENT, 14));
    }

    private void show() {
        FastSearch.Candidate c = list.get(index);
        big.setText("Вариант " + (index + 1));
        small.setText(c.name());
        progress.setProgress(index + 1);
        long perCand = FastSearch.BURST * FRAME_MS + PAUSE_MS;
        long left = (list.size() - index) * perCand / 1000;
        eta.setText("из " + list.size() + " · осталось не больше " + (left / 60) + " мин " + (left % 60) + " с");
    }

    /** Stop and let the user check the current and the previous variant. */
    private void reacted() {
        boolean early = System.currentTimeMillis() - burstStart < 1500 && cmd < 12;
        int cur = index;
        stop();
        // Step back so "Продолжить" re-checks from the suspects.
        verifyBox.removeAllViews();
        verifyBox.addView(Ui.title(this, "Какой вариант сработал?"));
        verifyBox.addView(Ui.text(this,
                "Нажми «Повторить» и смотри на телевизор. Тот, после которого приставка снова реагирует, сохрани.",
                14, Ui.MUTED));
        if (early && cur > 0) {
            addVerify(cur - 1);
            addVerify(cur);
        } else {
            addVerify(cur);
            if (cur > 0) addVerify(cur - 1);
        }
        index = Math.max(0, cur - 1);
    }

    private void addVerify(int i) {
        FastSearch.Candidate c = list.get(i);
        LinearLayout r = Ui.row(this);
        TextView l = Ui.text(this, "№" + (i + 1) + "\n" + c.name(), 15, Ui.TEXT);
        Button again = Ui.button(this, "▶ Повторить", Ui.KEY);
        Button save = Ui.button(this, "✔ Это он", Ui.GREEN);
        again.setOnClickListener(v -> burst(c));
        save.setOnClickListener(v -> save(c));
        Ui.add(r, l, 1.2f);
        Ui.add(r, again, 1.4f);
        Ui.add(r, save, 1.2f);
        verifyBox.addView(r);
    }

    private void burst(FastSearch.Candidate c) {
        ui.removeCallbacksAndMessages(null);
        ir.cancelPending();
        for (int i = 0; i < FastSearch.BURST; i++) {
            final int k = i;
            ui.postDelayed(() -> ir.send(c.protocol, c.address, k), i * FRAME_MS);
        }
    }

    private void save(FastSearch.Candidate c) {
        ui.removeCallbacksAndMessages(null);
        ir.cancelPending();
        cfg.setProtocol(c.protocol);
        cfg.setAddress(c.address);
        cfg.setAddressFound(true);
        Toast.makeText(this, "Сохранено: " + c.name() + ". Теперь подберём кнопки.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(this, SetupActivity.class));
        finish();
    }
}

package kz.teamcargo.pult;

import java.util.LinkedHashMap;
import java.util.Map;

/** All buttons of the remote: id -> label. Order is used in lists. */
public final class Keys {
    public static final Map<String, String> ALL = new LinkedHashMap<>();

    static {
        ALL.put("power", "Вкл/Выкл ⏻");
        ALL.put("mute", "Без звука 🔇");
        for (int i = 1; i <= 9; i++) ALL.put("d" + i, String.valueOf(i));
        ALL.put("d0", "0");
        ALL.put("up", "Вверх ▲");
        ALL.put("down", "Вниз ▼");
        ALL.put("left", "Влево ◀");
        ALL.put("right", "Вправо ▶");
        ALL.put("ok", "OK");
        ALL.put("menu", "Меню");
        ALL.put("exit", "Выход");
        ALL.put("back", "Назад ↩");
        ALL.put("volup", "Громкость +");
        ALL.put("voldown", "Громкость −");
        ALL.put("chup", "Канал +");
        ALL.put("chdown", "Канал −");
        ALL.put("info", "Инфо");
        ALL.put("epg", "Телегид EPG");
        ALL.put("fav", "Избранное FAV");
        ALL.put("tvradio", "ТВ/Радио");
        ALL.put("recall", "Пред. канал");
        ALL.put("audio", "Аудио");
        ALL.put("sub", "Субтитры");
        ALL.put("ttx", "Телетекст");
        ALL.put("red", "Красная");
        ALL.put("green", "Зелёная");
        ALL.put("yellow", "Жёлтая");
        ALL.put("blue", "Синяя");
        ALL.put("play", "Пуск ▶");
        ALL.put("pause", "Пауза ⏸");
        ALL.put("stop", "Стоп ⏹");
        ALL.put("rec", "Запись ⏺");
        ALL.put("rew", "Назад ⏪");
        ALL.put("fwd", "Вперёд ⏩");
        ALL.put("sleep", "Сон");
    }

    private Keys() {}

    public static String label(String id) {
        String l = ALL.get(id);
        return l == null ? id : l;
    }
}

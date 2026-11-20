package kz.teamcargo.pult;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds IR timing patterns (microseconds, mark/space alternating) for
 * ConsumerIrManager.transmit(). No Android dependencies, so it is unit tested.
 */
public final class IrCodec {
    public static final int FREQ = 38000;

    public static final String NEC = "NEC";
    public static final String SAMSUNG = "SAMSUNG";
    public static final String RC5 = "RC5";
    public static final String RC6 = "RC6";
    public static final String[] PROTOCOLS = {NEC, SAMSUNG, RC5, RC6};

    public static boolean known(String p) {
        for (String k : PROTOCOLS) if (k.equals(p)) return true;
        return false;
    }

    public static int freq(String protocol) {
        return RC5.equals(protocol) || RC6.equals(protocol) ? 36000 : FREQ;
    }

    /** Highest address the protocol can carry. */
    public static int maxAddress(String protocol) {
        if (RC5.equals(protocol)) return 0x1F;
        if (RC6.equals(protocol)) return 0xFF;
        return 0xFFFF;
    }

    public static int maxCommand(String protocol) {
        return RC5.equals(protocol) ? 0x7F : 0xFF;
    }

    public static int[] pattern(String protocol, int address, int command, boolean toggle) {
        if (RC5.equals(protocol)) return rc5(address, command, toggle);
        if (RC6.equals(protocol)) return rc6(address, command, toggle);
        return pattern(protocol, address, command);
    }

    private static final int RC5_HALF = 889;

    /** Philips RC5: Manchester, bit 1 = space then mark, MSB first. */
    static int[] rc5(int address, int command, boolean toggle) {
        Levels l = new Levels();
        int field = ((command >> 6) & 1) == 0 ? 1 : 0;
        int[] bits = new int[14];
        bits[0] = 1;
        bits[1] = field;
        bits[2] = toggle ? 1 : 0;
        for (int i = 0; i < 5; i++) bits[3 + i] = (address >> (4 - i)) & 1;
        for (int i = 0; i < 6; i++) bits[8 + i] = (command >> (5 - i)) & 1;
        for (int b : bits) {
            if (b == 1) {
                l.add(false, RC5_HALF);
                l.add(true, RC5_HALF);
            } else {
                l.add(true, RC5_HALF);
                l.add(false, RC5_HALF);
            }
        }
        return l.finish(114000);
    }

    private static final int RC6_T = 444;

    /** Philips RC6 mode 0: leader, start 1, mode 000, double-width toggle, 8+8 bits. */
    static int[] rc6(int address, int command, boolean toggle) {
        Levels l = new Levels();
        l.add(true, RC6_T * 6);
        l.add(false, RC6_T * 2);
        rc6Bit(l, 1, 1);
        for (int i = 0; i < 3; i++) rc6Bit(l, 0, 1);
        rc6Bit(l, toggle ? 1 : 0, 2);
        for (int i = 7; i >= 0; i--) rc6Bit(l, (address >> i) & 1, 1);
        for (int i = 7; i >= 0; i--) rc6Bit(l, (command >> i) & 1, 1);
        return l.finish(107000);
    }

    /** RC6 bit 1 = mark then space, bit 0 = space then mark. */
    private static void rc6Bit(Levels l, int bit, int width) {
        l.add(bit == 1, RC6_T * width);
        l.add(bit != 1, RC6_T * width);
    }

    /** Collects mark/space halves, merges neighbours, drops a leading space. */
    private static final class Levels {
        private final List<Integer> out = new ArrayList<>();
        private boolean lastMark = false;

        void add(boolean mark, int us) {
            if (out.isEmpty()) {
                if (!mark) return;
                out.add(us);
                lastMark = true;
                return;
            }
            if (mark == lastMark) {
                out.set(out.size() - 1, out.get(out.size() - 1) + us);
            } else {
                out.add(us);
                lastMark = mark;
            }
        }

        int[] finish(int frameUs) {
            int used = 0;
            for (int v : out) used += v;
            int gap = Math.max(frameUs - used, 20000);
            if (lastMark) out.add(gap);
            else out.set(out.size() - 1, out.get(out.size() - 1) + gap);
            int[] r = new int[out.size()];
            for (int i = 0; i < r.length; i++) r[i] = out.get(i);
            return r;
        }
    }

    private static final int BIT_MARK = 560;
    private static final int ZERO_SPACE = 560;
    private static final int ONE_SPACE = 1690;
    /** Full NEC frame period is 108 ms; the tail space pads up to it. */
    private static final int FRAME_US = 108000;

    private IrCodec() {}

    /**
     * Four bytes sent on air, LSB first.
     * Address up to 0xFF: standard form (addr, ~addr).
     * Address above 0xFF: extended 16-bit form (low byte, high byte).
     * Samsung sends the 8-bit address twice.
     */
    public static int[] frameBytes(String protocol, int address, int command) {
        int c = command & 0xFF;
        int a0, a1;
        if (SAMSUNG.equals(protocol)) {
            a0 = address & 0xFF;
            a1 = address > 0xFF ? (address >> 8) & 0xFF : a0;
        } else if (address > 0xFF) {
            a0 = address & 0xFF;
            a1 = (address >> 8) & 0xFF;
        } else {
            a0 = address & 0xFF;
            a1 = (~address) & 0xFF;
        }
        return new int[]{a0, a1, c, (~c) & 0xFF};
    }

    public static int[] pattern(String protocol, int address, int command) {
        List<Integer> p = new ArrayList<>();
        if (SAMSUNG.equals(protocol)) {
            p.add(4500);
            p.add(4500);
        } else {
            p.add(9000);
            p.add(4500);
        }
        for (int b : frameBytes(protocol, address, command)) {
            for (int i = 0; i < 8; i++) {
                p.add(BIT_MARK);
                p.add(((b >> i) & 1) == 1 ? ONE_SPACE : ZERO_SPACE);
            }
        }
        p.add(BIT_MARK); // stop bit
        int used = 0;
        for (int v : p) used += v;
        p.add(Math.max(FRAME_US - used, 20000));
        int[] out = new int[p.size()];
        for (int i = 0; i < out.length; i++) out[i] = p.get(i);
        return out;
    }

    /** Total pattern duration in microseconds. */
    public static int durationUs(int[] pattern) {
        int s = 0;
        for (int v : pattern) s += v;
        return s;
    }

    public static String hex(int v, int digits) {
        String s = Integer.toHexString(v).toUpperCase();
        while (s.length() < digits) s = "0" + s;
        return "0x" + s;
    }

    /** Parses "0x1A", "1A", "26d" is not supported: hex only. Returns -1 on error. */
    public static int parseHex(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
        if (s.isEmpty() || s.length() > 4) return -1;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

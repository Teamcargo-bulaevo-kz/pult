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

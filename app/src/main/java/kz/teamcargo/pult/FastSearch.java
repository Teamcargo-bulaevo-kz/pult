package kz.teamcargo.pult;

import java.util.ArrayList;
import java.util.List;

/**
 * Order of (protocol, address) pairs tried by the quick search, most likely first,
 * and the short burst of commands sent for each pair. No Android dependencies.
 */
final class FastSearch {
    /** Commands 0x00..0x21 cover digits, power, volume and channel on most remotes. */
    static final int BURST = 0x22;

    static final class Candidate {
        final String protocol;
        final int address;

        Candidate(String protocol, int address) {
            this.protocol = protocol;
            this.address = address;
        }

        String name() {
            return protocol + " " + IrCodec.hex(address, 2);
        }
    }

    private FastSearch() {}

    static List<Candidate> candidates() {
        List<Candidate> out = new ArrayList<>();
        int[] necLikely = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x10, 0x20, 0x40,
                0x80, 0x7F, 0xFE, 0xFF, 0x0A, 0x0E, 0x11, 0x12, 0x18, 0x1C, 0x22, 0x33, 0x44, 0x4D,
                0x55, 0x6E, 0x77, 0x86, 0x88, 0x99, 0xAA, 0xBB, 0xC0, 0xCC, 0xDD, 0xEE};
        boolean[] necUsed = new boolean[256];
        for (int a : necLikely) {
            if (!necUsed[a]) {
                necUsed[a] = true;
                out.add(new Candidate(IrCodec.NEC, a));
            }
        }
        // RC5 system addresses: 8 and 10 are satellite receivers, 0 is TV.
        int[] rc5Likely = {8, 10, 0, 5, 6, 7, 3, 1, 2, 4};
        boolean[] rc5Used = new boolean[32];
        for (int a : rc5Likely) {
            rc5Used[a] = true;
            out.add(new Candidate(IrCodec.RC5, a));
        }
        for (int a = 0; a < 32; a++) if (!rc5Used[a]) out.add(new Candidate(IrCodec.RC5, a));
        for (int a : new int[]{0x00, 0x04, 0x08, 0x0A, 0x0C, 0x10, 0x20, 0x27, 0x80}) {
            out.add(new Candidate(IrCodec.RC6, a));
        }
        for (int a : new int[]{0x07, 0x0E, 0x00, 0x05, 0x08, 0x10, 0x20, 0x40}) {
            out.add(new Candidate(IrCodec.SAMSUNG, a));
        }
        for (int a = 0; a < 256; a++) if (!necUsed[a]) out.add(new Candidate(IrCodec.NEC, a));
        return out;
    }
}

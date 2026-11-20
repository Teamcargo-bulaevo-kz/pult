package kz.teamcargo.pult;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IrCodecTest {
    @Test
    public void standardNecBytes() {
        assertArrayEquals(new int[]{0x00, 0xFF, 0x0A, 0xF5}, IrCodec.frameBytes(IrCodec.NEC, 0x00, 0x0A));
        assertArrayEquals(new int[]{0x80, 0x7F, 0x12, 0xED}, IrCodec.frameBytes(IrCodec.NEC, 0x80, 0x12));
    }

    @Test
    public void extendedNecBytes() {
        assertArrayEquals(new int[]{0x04, 0xFB, 0x08, 0xF7}, IrCodec.frameBytes(IrCodec.NEC, 0xFB04, 0x08));
    }

    @Test
    public void samsungRepeatsAddress() {
        assertArrayEquals(new int[]{0x07, 0x07, 0x02, 0xFD}, IrCodec.frameBytes(IrCodec.SAMSUNG, 0x07, 0x02));
    }

    @Test
    public void necPatternShape() {
        int[] p = IrCodec.pattern(IrCodec.NEC, 0x00, 0x0A);
        // header 2 + 32 bits * 2 + stop + tail
        assertEquals(68, p.length);
        assertEquals(9000, p[0]);
        assertEquals(4500, p[1]);
        assertEquals(108000, IrCodec.durationUs(p));
        // first address bit of 0x00 is 0
        assertEquals(560, p[3]);
        // first command bit of 0x0A (LSB) is 0, second is 1
        assertEquals(560, p[2 + 16 * 2 + 1]);
        assertEquals(1690, p[2 + 17 * 2 + 1]);
        for (int v : p) assertTrue(v > 0);
    }

    @Test
    public void parseHex() {
        assertEquals(0x1F, IrCodec.parseHex("0x1f"));
        assertEquals(0xFB04, IrCodec.parseHex("FB04"));
        assertEquals(-1, IrCodec.parseHex("zz"));
        assertEquals(-1, IrCodec.parseHex(""));
        assertEquals("0x0A", IrCodec.hex(10, 2));
    }

    @Test
    public void rc5Shape() {
        // address 8, command 0x0C (standby), toggle 0: 14 Manchester bits of 1778 us
        int[] p = IrCodec.rc5(8, 0x0C, false);
        assertEquals(889, p[0]); // starts with the mark half of S1
        assertEquals(114000, IrCodec.durationUs(p));
        for (int v : p) assertTrue(v == 889 || v == 1778 || v > 20000);
    }

    @Test
    public void rc6Shape() {
        int[] p = IrCodec.rc6(0x00, 0x0C, false);
        assertEquals(2664, p[0]);
        assertEquals(888, p[1]);
        assertEquals(107000, IrCodec.durationUs(p));
        assertEquals(36000, IrCodec.freq(IrCodec.RC6));
    }

    @Test
    public void fastSearchHasEveryNecAddressOnce() {
        int nec = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (FastSearch.Candidate c : FastSearch.candidates()) {
            assertTrue(seen.add(c.name()));
            if (IrCodec.NEC.equals(c.protocol)) nec++;
        }
        assertEquals(256, nec);
        assertEquals(IrCodec.NEC, FastSearch.candidates().get(0).protocol);
    }
}

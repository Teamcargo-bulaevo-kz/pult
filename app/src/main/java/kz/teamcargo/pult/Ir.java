package kz.teamcargo.pult;

import android.content.Context;
import android.hardware.ConsumerIrManager;
import android.os.Handler;
import android.os.HandlerThread;

/** Sends codes through the phone's IR blaster on a background thread. */
public final class Ir {
    private final ConsumerIrManager ir;
    private final Handler worker;
    private boolean toggle;

    public Ir(Context ctx) {
        ir = (ConsumerIrManager) ctx.getSystemService(Context.CONSUMER_IR_SERVICE);
        HandlerThread t = new HandlerThread("ir");
        t.start();
        worker = new Handler(t.getLooper());
    }

    public boolean available() {
        try {
            return ir != null && ir.hasIrEmitter();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void send(String protocol, int address, int command) {
        if (!available()) return;
        toggle = !toggle; // RC5/RC6 need a new toggle bit for every new press
        final int[] pattern = IrCodec.pattern(protocol, address, command, toggle);
        final int freq = IrCodec.freq(protocol);
        worker.post(() -> {
            try {
                ir.transmit(freq, pattern);
            } catch (RuntimeException ignored) {
                // Some firmwares throw when the emitter is busy; the next press works.
            }
        });
    }

    /** Drops sends that have not started yet (used when a scan is stopped). */
    public void cancelPending() {
        worker.removeCallbacksAndMessages(null);
    }

    public void quit() {
        worker.removeCallbacksAndMessages(null);
        worker.getLooper().quitSafely();
    }
}

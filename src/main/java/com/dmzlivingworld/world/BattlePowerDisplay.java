package com.dmzlivingworld.world;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Same compact M/B/T/Qa/Qi presentation used by DMZ's player statistics screen. */
public final class BattlePowerDisplay {
    private static final DecimalFormat COMPACT = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static final String[] SUFFIXES = {"M", "B", "T", "Qa", "Qi"};
    private static final double[] SCALES = {1e6, 1e9, 1e12, 1e15, 1e18};
    private BattlePowerDisplay() {}

    public static String format(long bp) {
        long safe = Math.max(0L, bp);
        if (safe < 1_000_000L) return Long.toString(safe);
        int i = SCALES.length - 1;
        while (i > 0 && safe < SCALES[i]) i--;
        return COMPACT.format(safe / SCALES[i]) + SUFFIXES[i];
    }
}

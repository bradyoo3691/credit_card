package com.brad.cardwidget;

import java.text.DecimalFormat;

public class Fmt {
    private static final DecimalFormat DF = new DecimalFormat("#,###");

    public static String won(long v) {
        return (v < 0 ? "-" : "") + DF.format(Math.abs(v)) + "원";
    }

    public static String num(long v) {
        return (v < 0 ? "-" : "") + DF.format(Math.abs(v));
    }

    /** 300000 → "30만", 1500000 → "150만", 12345 → "1.2만" */
    public static String man(long v) {
        if (v % 10000 == 0) return (v / 10000) + "만";
        return String.format(java.util.Locale.KOREA, "%.1f만", v / 10000.0);
    }

    /** "202610" → 10 */
    public static int month(String ym) {
        try {
            return Integer.parseInt(ym.substring(4));
        } catch (Exception e) {
            return 0;
        }
    }

    /** "1,234,000" / "123만" / "50" (만원 단위 아님) 등 입력값을 원 단위로. */
    public static long parseAmount(String s) {
        if (s == null) return -1;
        s = s.trim().replace(",", "").replace("원", "").replace(" ", "");
        if (s.isEmpty()) return -1;
        try {
            if (s.endsWith("만")) return Math.round(Double.parseDouble(s.substring(0, s.length() - 1)) * 10000);
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1;
        }
    }
}

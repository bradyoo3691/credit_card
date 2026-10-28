package com.brad.cardwidget;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 결제 내역 / 중복체크 / 미인식 로그 저장소. 모든 데이터는 폰 안에만 저장된다. */
public class Db extends SQLiteOpenHelper {

    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private final Context ctx;

    private Db(Context ctx) {
        super(ctx, "cards.db", null, 1);
        this.ctx = ctx;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tx (id INTEGER PRIMARY KEY AUTOINCREMENT, card TEXT, amount INTEGER, ts INTEGER, ym TEXT, counted INTEGER, raw TEXT, pkg TEXT)");
        db.execSQL("CREATE INDEX tx_card_ym ON tx(card, ym)");
        db.execSQL("CREATE TABLE seen (hash TEXT PRIMARY KEY, ts INTEGER)");
        db.execSQL("CREATE TABLE log (id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER, pkg TEXT, reason TEXT, raw TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {}

    // ---------- 월 키 ----------

    public static String ym(long ts) {
        return new SimpleDateFormat("yyyyMM", Locale.KOREA).format(new Date(ts));
    }

    public static String thisYm() {
        return ym(System.currentTimeMillis());
    }

    public static String prevYm() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, -1);
        return ym(c.getTimeInMillis());
    }

    // ---------- 결제 내역 ----------

    public static class Tx {
        public long id, amount, ts;
        public String card, raw, pkg;
        public boolean counted;
    }

    public long insert(String card, long signedAmount, long ts, boolean counted, String raw, String pkg) {
        ContentValues v = new ContentValues();
        v.put("card", card);
        v.put("amount", signedAmount);
        v.put("ts", ts);
        v.put("ym", ym(ts));
        v.put("counted", counted ? 1 : 0);
        v.put("raw", raw == null ? "" : (raw.length() > 300 ? raw.substring(0, 300) : raw));
        v.put("pkg", pkg == null ? "" : pkg);
        return getWritableDatabase().insert("tx", null, v);
    }

    /** 같은 카드, 같은 금액(부호 포함)이 windowMs 안에 이미 있으면 true (삼성페이+문자 중복 방지). */
    public boolean hasSimilar(String card, long signedAmount, long ts, long windowMs) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tx WHERE card=? AND amount=? AND ts BETWEEN ? AND ?",
                new String[]{card, String.valueOf(signedAmount), String.valueOf(ts - windowMs), String.valueOf(ts + windowMs)});
        try {
            return c.moveToFirst() && c.getLong(0) > 0;
        } finally {
            c.close();
        }
    }

    public void delete(long id) {
        getWritableDatabase().delete("tx", "id=?", new String[]{String.valueOf(id)});
    }

    public void setCounted(long id, boolean counted) {
        ContentValues v = new ContentValues();
        v.put("counted", counted ? 1 : 0);
        getWritableDatabase().update("tx", v, "id=?", new String[]{String.valueOf(id)});
    }

    /** 해당 월 실적 인정 합계 (DB 내역만). */
    public long sumCounted(String card, String ym) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM tx WHERE card=? AND ym=? AND counted=1",
                new String[]{card, ym});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    /** 해당 월 전체 사용 합계 (실적 제외 포함). */
    public long sumAll(String card, String ym) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(amount),0) FROM tx WHERE card=? AND ym=?",
                new String[]{card, ym});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0;
        } finally {
            c.close();
        }
    }

    public List<Tx> recent(int limit) {
        List<Tx> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, card, amount, ts, counted, raw, pkg FROM tx ORDER BY ts DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) {
                Tx t = new Tx();
                t.id = c.getLong(0);
                t.card = c.getString(1);
                t.amount = c.getLong(2);
                t.ts = c.getLong(3);
                t.counted = c.getInt(4) == 1;
                t.raw = c.getString(5);
                t.pkg = c.getString(6);
                list.add(t);
            }
        } finally {
            c.close();
        }
        return list;
    }

    // ---------- 월 합계 (수동 보정 포함) ----------

    private SharedPreferences adj() {
        return ctx.getSharedPreferences("adjust", Context.MODE_PRIVATE);
    }

    /** 앱 설치 전 사용분 등, 그 달 합계에 더할 금액. */
    public long getBase(String card, String ym) {
        return adj().getLong("base_" + card + "_" + ym, 0);
    }

    public void setBase(String card, String ym, long v) {
        adj().edit().putLong("base_" + card + "_" + ym, v).apply();
    }

    /** 그 달 실적을 직접 입력한 값 (-1이면 없음). 지난달 실적 입력용. */
    public long getOverride(String card, String ym) {
        return adj().getLong("ovr_" + card + "_" + ym, -1);
    }

    public void setOverride(String card, String ym, long v) {
        if (v < 0) adj().edit().remove("ovr_" + card + "_" + ym).apply();
        else adj().edit().putLong("ovr_" + card + "_" + ym, v).apply();
    }

    /** 위젯/화면에 쓰는 최종 실적 금액. */
    public long monthTotal(String card, String ym) {
        long o = getOverride(card, ym);
        if (o >= 0) return o;
        return Math.max(0, sumCounted(card, ym) + getBase(card, ym));
    }

    public void saveCumulative(String card, long v, long ts) {
        adj().edit().putLong("cum_" + card, v).putLong("cumts_" + card, ts).apply();
    }

    public long getCumulative(String card) {
        return adj().getLong("cum_" + card, -1);
    }

    public long getCumulativeTs(String card) {
        return adj().getLong("cumts_" + card, 0);
    }

    // ---------- 중복 알림 체크 ----------

    /** 처음 보는 문구면 true를 돌려주고 기록. 같은 문자가 다시 떠도 한 번만 처리. */
    public boolean markSeen(String hash, long now) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM seen WHERE hash=?", new String[]{hash});
        boolean exists;
        try {
            exists = c.moveToFirst();
        } finally {
            c.close();
        }
        if (exists) return false;
        ContentValues v = new ContentValues();
        v.put("hash", hash);
        v.put("ts", now);
        db.insert("seen", null, v);
        if (Math.random() < 0.05) {
            db.delete("seen", "ts < ?", new String[]{String.valueOf(now - 10L * 24 * 3600 * 1000)});
        }
        return true;
    }

    // ---------- 미인식 로그 ----------

    public void log(String pkg, String reason, String raw) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("ts", System.currentTimeMillis());
        v.put("pkg", pkg);
        v.put("reason", reason);
        v.put("raw", raw == null ? "" : (raw.length() > 300 ? raw.substring(0, 300) : raw));
        db.insert("log", null, v);
        db.execSQL("DELETE FROM log WHERE id NOT IN (SELECT id FROM log ORDER BY id DESC LIMIT 100)");
    }

    public List<String[]> logs(int limit) {
        List<String[]> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT ts, pkg, reason, raw FROM log ORDER BY id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) {
                list.add(new String[]{String.valueOf(c.getLong(0)), c.getString(1), c.getString(2), c.getString(3)});
            }
        } finally {
            c.close();
        }
        return list;
    }

    public void clearLogs() {
        getWritableDatabase().delete("log", null, null);
    }
}

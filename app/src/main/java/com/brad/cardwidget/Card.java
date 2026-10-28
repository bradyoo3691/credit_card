package com.brad.cardwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 카드 1장의 설정 (이름, 인식 키워드, 실적 구간, 실적 제외 키워드). */
public class Card {
    public String id;
    public String name;
    public int color;
    public List<String> keywords = new ArrayList<>();
    public long[] tiers = new long[0];
    public List<String> excludes = new ArrayList<>();

    static final String PREF = "cards";

    public Card() {}

    public Card(String id, String name, int color, String[] keywords, long[] tiers, String[] excludes) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.keywords = new ArrayList<>(Arrays.asList(keywords));
        this.tiers = tiers;
        this.excludes = new ArrayList<>(Arrays.asList(excludes));
    }

    // ---- 실적 구간 계산 ----

    /** 달성한 가장 높은 구간 (없으면 -1). */
    public long achievedTier(long total) {
        long best = -1;
        for (long t : tiers) if (total >= t && t > best) best = t;
        return best;
    }

    /** 다음 목표 구간 (모두 달성 시 -1). */
    public long nextTier(long total) {
        long next = -1;
        for (long t : tiers) if (total < t && (next == -1 || t < next)) next = t;
        return next;
    }

    public long maxTier() {
        long m = 0;
        for (long t : tiers) m = Math.max(m, t);
        return m;
    }

    // ---- 저장/불러오기 ----

    JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("color", color);
        o.put("keywords", new JSONArray(keywords));
        JSONArray t = new JSONArray();
        for (long v : tiers) t.put(v);
        o.put("tiers", t);
        o.put("excludes", new JSONArray(excludes));
        return o;
    }

    static Card fromJson(JSONObject o) throws Exception {
        Card c = new Card();
        c.id = o.getString("id");
        c.name = o.getString("name");
        c.color = o.getInt("color");
        JSONArray k = o.getJSONArray("keywords");
        for (int i = 0; i < k.length(); i++) c.keywords.add(k.getString(i));
        JSONArray t = o.getJSONArray("tiers");
        c.tiers = new long[t.length()];
        for (int i = 0; i < t.length(); i++) c.tiers[i] = t.getLong(i);
        Arrays.sort(c.tiers);
        JSONArray e = o.optJSONArray("excludes");
        if (e != null) for (int i = 0; i < e.length(); i++) c.excludes.add(e.getString(i));
        return c;
    }

    public static List<Card> defaults() {
        List<Card> list = new ArrayList<>();
        list.add(new Card("samsung_ev", "삼성 iD EV", 0xFF1D4ED8,
                new String[]{"삼성카드", "삼성", "iD EV", "ID EV"},
                new long[]{300000, 600000},
                new String[]{"관리비", "임대료", "대중교통", "택시", "티머니", "캐시비", "상품권", "선불", "등록금", "학교"}));
        list.add(new Card("woori_jeongseok2", "카드의정석2", 0xFF0EA5E9,
                new String[]{"우리카드", "우리", "카드의정석", "정석"},
                new long[]{500000},
                new String[]{"상품권", "선불", "연회비"}));
        return list;
    }

    public static List<Card> load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String s = p.getString("list", null);
        if (s == null) {
            List<Card> d = defaults();
            save(ctx, d);
            return d;
        }
        List<Card> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) list.add(fromJson(a.getJSONObject(i)));
        } catch (Exception e) {
            return defaults();
        }
        return list;
    }

    public static void save(Context ctx, List<Card> list) {
        try {
            JSONArray a = new JSONArray();
            for (Card c : list) a.put(c.toJson());
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("list", a.toString()).apply();
        } catch (Exception ignored) {
        }
    }
}

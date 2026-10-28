package com.brad.cardwidget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    LinearLayout root;
    static final int[] PALETTE = {0xFF1D4ED8, 0xFF0EA5E9, 0xFFDC2626, 0xFF16A34A, 0xFFF59E0B, 0xFF7C3AED, 0xFF111827};

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("카드실적 위젯");
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(0xFFF3F4F6);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, dp(40));
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        CardWidgetProvider.updateAll(this);
    }

    // =========================================================
    void render() {
        root.removeAllViews();
        List<Card> cards = Card.load(this);
        Db db = Db.get(this);

        // ---- 1. 권한 상태 ----
        boolean listen = listenerEnabled();
        boolean battery = batteryOk();
        if (!listen || !battery) {
            LinearLayout box = section("⚠️ 처음 설정 (필수)");
            if (!listen) {
                box.addView(text("알림 접근 권한이 꺼져 있어서 결제 알림을 읽을 수 없습니다.", 13, 0xFFB91C1C, false));
                box.addView(button("① 알림 접근 허용하기", v ->
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
            }
            if (!battery) {
                box.addView(text("절전 기능 때문에 알림을 놓칠 수 있습니다. '허용'을 눌러주세요.", 13, 0xFFB91C1C, false));
                box.addView(button("② 배터리 최적화 제외하기", v -> requestBattery()));
            }
        } else {
            TextView ok = text("✅ 알림 인식 중 · 홈 화면 길게 누르기 → 위젯 → '카드실적' 추가", 12, 0xFF15803D, false);
            ok.setPadding(0, 0, 0, dp(8));
            root.addView(ok);
        }

        // ---- 2. 카드별 현황 ----
        String ym = Db.thisYm();
        String prevYm = Db.prevYm();
        for (Card c : cards) {
            Summary s = Summary.of(this, c);
            LinearLayout box = section(null);

            LinearLayout head = row();
            TextView dot = new TextView(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(c.color);
            g.setCornerRadius(dp(3));
            dot.setBackground(g);
            head.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(22)));
            TextView name = text("  " + c.name, 16, 0xFF111827, true);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(text(Fmt.won(s.thisMonth), 18, 0xFF111827, true));
            box.addView(head);

            ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(100);
            pb.setProgress(s.progress);
            pb.setProgressTintList(android.content.res.ColorStateList.valueOf(s.next < 0 && c.tiers.length > 0 ? 0xFF16A34A : c.color));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, dp(10));
            pl.setMargins(0, dp(6), 0, dp(4));
            box.addView(pb, pl);

            box.addView(text(s.statusText() + "   |   " + s.prevText(), 13, 0xFF374151, false));

            StringBuilder info = new StringBuilder();
            info.append("실적구간: ");
            for (int i = 0; i < c.tiers.length; i++) info.append(i > 0 ? " / " : "").append(Fmt.man(c.tiers[i]));
            info.append("\n지난달(").append(Fmt.month(prevYm)).append("월) 실적: ").append(Fmt.won(s.prevMonth));
            if (db.getOverride(c.id, prevYm) >= 0) info.append(" (직접입력)");
            long base = db.getBase(c.id, ym);
            if (base != 0) info.append("\n이번달 설치 전 사용분: ").append(Fmt.won(base));
            if (s.thisMonthAll != s.thisMonth) info.append("\n이번달 실적제외 포함 총사용: ").append(Fmt.won(s.thisMonthAll));
            long cum = db.getCumulative(c.id);
            if (cum >= 0) {
                info.append("\n카드사 문자 누적금액(참고): ").append(Fmt.won(cum)).append(" · ")
                        .append(new SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(new Date(db.getCumulativeTs(c.id))));
            }
            TextView it = text(info.toString(), 12, 0xFF6B7280, false);
            it.setPadding(0, dp(6), 0, dp(6));
            box.addView(it);

            LinearLayout btns = row();
            btns.addView(smallButton("+ 직접추가", v -> manualAdd(c)), weight());
            btns.addView(smallButton("설치전 사용분", v -> setBase(c)), weight());
            box.addView(btns);
            LinearLayout btns2 = row();
            btns2.addView(smallButton("지난달 실적 입력", v -> setPrev(c)), weight());
            btns2.addView(smallButton("카드 설정", v -> editCard(c, false)), weight());
            box.addView(btns2);
        }

        root.addView(button("+ 카드 추가", v -> {
            Card n = new Card("card_" + System.currentTimeMillis(), "새 카드", PALETTE[cards.size() % PALETTE.length],
                    new String[]{}, new long[]{300000}, new String[]{"상품권", "선불"});
            editCard(n, true);
        }));

        // ---- 3. 최근 결제 내역 ----
        LinearLayout list = section("최근 인식된 결제 (눌러서 삭제/실적제외)");
        List<Db.Tx> txs = db.recent(40);
        if (txs.isEmpty()) list.addView(text("아직 인식된 결제가 없습니다.", 13, 0xFF6B7280, false));
        SimpleDateFormat df = new SimpleDateFormat("M/d HH:mm", Locale.KOREA);
        for (Db.Tx t : txs) {
            String cn = cardName(cards, t.card);
            String line = df.format(new Date(t.ts)) + "  " + cn + "  " + Fmt.won(t.amount)
                    + (t.counted ? "" : "  [실적제외]") + (t.pkg.equals("manual") ? "  [직접]" : "");
            TextView tv = text(line, 13, t.amount < 0 ? 0xFFDC2626 : 0xFF111827, false);
            tv.setPadding(0, dp(7), 0, dp(7));
            tv.setOnClickListener(v -> txMenu(t, cn));
            list.addView(tv);
            list.addView(divider());
        }

        // ---- 4. 도구 ----
        LinearLayout tools = section("도구");
        tools.addView(button("알림 문구 테스트 (붙여넣어 확인)", v -> testParse()));
        tools.addView(button("미인식 알림 로그 보기", v -> showLogs()));
        tools.addView(button("알림 접근 설정 열기", v ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));

        TextView foot = text("모든 데이터는 이 폰 안에만 저장되며 외부로 전송되지 않습니다.\n실적은 알림 기준 근사치입니다. 정확한 실적은 카드사 앱에서 확인하세요.", 11, 0xFF9CA3AF, false);
        foot.setPadding(0, dp(12), 0, 0);
        root.addView(foot);
    }

    // =========================================================
    // 동작

    void manualAdd(Card c) {
        LinearLayout l = form();
        EditText amt = input(l, "금액 (예: 32000, 취소는 -32000)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        EditText memo = input(l, "메모 (선택)", InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this).setTitle(c.name + " 직접 추가").setView(l)
                .setPositiveButton("추가", (d, w) -> {
                    long v;
                    try { v = Long.parseLong(amt.getText().toString().trim().replace(",", "")); } catch (Exception e) { v = 0; }
                    if (v == 0) { toast("금액을 입력하세요"); return; }
                    Db.get(this).insert(c.id, v, System.currentTimeMillis(), true, "직접입력 " + memo.getText(), "manual");
                    refresh();
                }).setNegativeButton("취소", null).show();
    }

    void setBase(Card c) {
        LinearLayout l = form();
        l.addView(text("앱 설치 전에 이번 달 이미 쓴 금액을 입력하세요.\n카드사 앱의 '이번달 이용금액'을 보고 넣으면 됩니다. (예: 230000 또는 23만)", 13, 0xFF374151, false));
        EditText e = input(l, "금액", InputType.TYPE_CLASS_TEXT);
        long cur = Db.get(this).getBase(c.id, Db.thisYm());
        if (cur != 0) e.setText(String.valueOf(cur));
        new AlertDialog.Builder(this).setTitle(c.name + " · 설치 전 사용분").setView(l)
                .setPositiveButton("저장", (d, w) -> {
                    long v = Fmt.parseAmount(e.getText().toString());
                    Db.get(this).setBase(c.id, Db.thisYm(), Math.max(0, v));
                    refresh();
                }).setNegativeButton("취소", null).show();
    }

    void setPrev(Card c) {
        LinearLayout l = form();
        l.addView(text("지난달(" + Fmt.month(Db.prevYm()) + "월) 실적 총액을 입력하세요. 비우면 앱 집계값을 씁니다.\n(예: 520000 또는 52만)", 13, 0xFF374151, false));
        EditText e = input(l, "지난달 실적", InputType.TYPE_CLASS_TEXT);
        long cur = Db.get(this).getOverride(c.id, Db.prevYm());
        if (cur >= 0) e.setText(String.valueOf(cur));
        new AlertDialog.Builder(this).setTitle(c.name + " · 지난달 실적").setView(l)
                .setPositiveButton("저장", (d, w) -> {
                    Db.get(this).setOverride(c.id, Db.prevYm(), Fmt.parseAmount(e.getText().toString()));
                    refresh();
                }).setNegativeButton("취소", null).show();
    }

    void editCard(Card c, boolean isNew) {
        LinearLayout l = form();
        EditText name = input(l, "카드 이름 (위젯 표시)", InputType.TYPE_CLASS_TEXT);
        name.setText(c.name);
        l.addView(text("인식 키워드 (쉼표로 구분) — 알림에 이 단어가 있으면 이 카드로 인식", 12, 0xFF6B7280, false));
        EditText kw = input(l, "예: 삼성카드, 삼성, iD EV", InputType.TYPE_CLASS_TEXT);
        kw.setText(join(c.keywords));
        l.addView(text("실적 구간 (쉼표로 구분, 원 또는 만)", 12, 0xFF6B7280, false));
        EditText tiers = input(l, "예: 30만, 60만", InputType.TYPE_CLASS_TEXT);
        StringBuilder ts = new StringBuilder();
        for (int i = 0; i < c.tiers.length; i++) ts.append(i > 0 ? ", " : "").append(Fmt.man(c.tiers[i]));
        tiers.setText(ts.toString());
        l.addView(text("실적 제외 키워드 (이 단어가 있으면 실적에서 제외)", 12, 0xFF6B7280, false));
        EditText ex = input(l, "예: 관리비, 상품권", InputType.TYPE_CLASS_TEXT);
        ex.setText(join(c.excludes));
        l.addView(text("색상", 12, 0xFF6B7280, false));
        final int[] chosen = {c.color};
        LinearLayout colors = row();
        List<TextView> swatches = new ArrayList<>();
        for (int col : PALETTE) {
            TextView sw = new TextView(this);
            swatches.add(sw);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
            lp.setMargins(0, dp(4), dp(8), dp(4));
            colors.addView(sw, lp);
            sw.setOnClickListener(v -> {
                chosen[0] = col;
                paintSwatches(swatches, chosen[0]);
            });
        }
        paintSwatches(swatches, chosen[0]);
        l.addView(colors);

        AlertDialog.Builder bld = new AlertDialog.Builder(this).setTitle(isNew ? "카드 추가" : "카드 설정").setView(l)
                .setPositiveButton("저장", (d, w) -> {
                    c.name = name.getText().toString().trim().isEmpty() ? "카드" : name.getText().toString().trim();
                    c.keywords = split(kw.getText().toString());
                    c.excludes = split(ex.getText().toString());
                    List<Long> tl = new ArrayList<>();
                    for (String s : split(tiers.getText().toString())) {
                        long v = Fmt.parseAmount(s);
                        if (v > 0) tl.add(v);
                    }
                    c.tiers = new long[tl.size()];
                    for (int i = 0; i < tl.size(); i++) c.tiers[i] = tl.get(i);
                    Arrays.sort(c.tiers);
                    c.color = chosen[0];
                    List<Card> all = Card.load(this);
                    boolean replaced = false;
                    for (int i = 0; i < all.size(); i++) {
                        if (all.get(i).id.equals(c.id)) { all.set(i, c); replaced = true; }
                    }
                    if (!replaced) all.add(c);
                    Card.save(this, all);
                    refresh();
                }).setNegativeButton("취소", null);
        if (!isNew) {
            bld.setNeutralButton("카드 삭제", (d, w) -> new AlertDialog.Builder(this)
                    .setMessage(c.name + " 카드를 목록에서 삭제할까요?")
                    .setPositiveButton("삭제", (d2, w2) -> {
                        List<Card> all = Card.load(this);
                        List<Card> keep = new ArrayList<>();
                        for (Card x : all) if (!x.id.equals(c.id)) keep.add(x);
                        Card.save(this, keep);
                        refresh();
                    }).setNegativeButton("취소", null).show());
        }
        bld.show();
    }

    void txMenu(Db.Tx t, String cardName) {
        String[] items = {t.counted ? "실적에서 제외" : "실적에 포함", "이 내역 삭제", "원문 보기"};
        new AlertDialog.Builder(this).setTitle(cardName + " " + Fmt.won(t.amount))
                .setItems(items, (d, which) -> {
                    Db db = Db.get(this);
                    if (which == 0) { db.setCounted(t.id, !t.counted); refresh(); }
                    else if (which == 1) { db.delete(t.id); refresh(); }
                    else new AlertDialog.Builder(this).setTitle("원문 (" + t.pkg + ")").setMessage(t.raw)
                                .setPositiveButton("닫기", null).show();
                }).show();
    }

    void testParse() {
        LinearLayout l = form();
        EditText e = input(l, "카드 알림/문자 내용을 붙여넣으세요", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(4);
        new AlertDialog.Builder(this).setTitle("알림 문구 테스트").setView(l)
                .setPositiveButton("분석", (d, w) -> {
                    String s = e.getText().toString();
                    Parser.Result r = Parser.parse(s, Card.load(this));
                    String msg;
                    if (r == null) msg = "❌ 인식 실패\n사유: " + Parser.lastReason;
                    else msg = "✅ 인식 성공\n카드: " + r.cardName + "\n금액: " + Fmt.won(r.amount)
                            + (r.cancel ? "\n승인취소 (차감)" : "")
                            + (r.excluded ? "\n실적제외 (" + r.excludeWord + ")" : "")
                            + (r.cumulative >= 0 ? "\n카드사 누적: " + Fmt.won(r.cumulative) : "");
                    AlertDialog.Builder res = new AlertDialog.Builder(this).setTitle("분석 결과").setMessage(msg)
                            .setNegativeButton("닫기", null);
                    if (r != null) {
                        res.setPositiveButton("이 내역 추가", (d2, w2) -> {
                            Db.get(this).insert(r.cardId, r.cancel ? -r.amount : r.amount,
                                    System.currentTimeMillis(), !r.excluded, s, "manual");
                            refresh();
                        });
                    }
                    res.show();
                }).setNegativeButton("취소", null).show();
    }

    void showLogs() {
        List<String[]> logs = Db.get(this).logs(50);
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat df = new SimpleDateFormat("M/d HH:mm", Locale.KOREA);
        if (logs.isEmpty()) sb.append("기록 없음");
        for (String[] l : logs) {
            sb.append(df.format(new Date(Long.parseLong(l[0])))).append("  [").append(l[2]).append("]\n")
                    .append(l[1]).append("\n").append(l[3]).append("\n\n");
        }
        final String all = sb.toString();
        new AlertDialog.Builder(this).setTitle("미인식/참고 로그").setMessage(all)
                .setPositiveButton("닫기", null)
                .setNeutralButton("복사", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("log", all));
                    toast("복사됨 — Claude에게 붙여넣어 보내주세요");
                })
                .setNegativeButton("로그 지우기", (d, w) -> Db.get(this).clearLogs())
                .show();
    }

    void refresh() {
        render();
        CardWidgetProvider.updateAll(this);
    }

    // =========================================================
    // 권한

    boolean listenerEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    boolean batteryOk() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    void requestBattery() {
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    // =========================================================
    // UI 헬퍼

    LinearLayout section(String title) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        box.setBackground(bg);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, dp(12));
        root.addView(box, lp);
        if (title != null) {
            TextView t = text(title, 14, 0xFF111827, true);
            t.setPadding(0, 0, 0, dp(8));
            box.addView(t);
        }
        return box;
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    Button smallButton(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    View divider() {
        View v = new View(this);
        v.setBackgroundColor(0xFFE5E7EB);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        return v;
    }

    LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        return l;
    }

    EditText input(LinearLayout parent, String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        parent.addView(e);
        return e;
    }

    void paintSwatches(List<TextView> sw, int chosen) {
        for (int i = 0; i < sw.size(); i++) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(PALETTE[i]);
            if (PALETTE[i] == chosen) g.setStroke(dp(3), 0xFF111827);
            sw.get(i).setBackground(g);
        }
    }

    String cardName(List<Card> cards, String id) {
        for (Card c : cards) if (c.id.equals(id)) return c.name;
        return "(삭제된 카드)";
    }

    static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) sb.append(i > 0 ? ", " : "").append(l.get(i));
        return sb.toString();
    }

    static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split("[,，]")) if (!p.trim().isEmpty()) out.add(p.trim());
        return out;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}

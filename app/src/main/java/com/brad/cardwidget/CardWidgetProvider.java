package com.brad.cardwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CardWidgetProvider extends AppWidgetProvider {

    static final int[] ROW = {R.id.row1, R.id.row2, R.id.row3};
    static final int[] BAR = {R.id.bar1, R.id.bar2, R.id.bar3};
    static final int[] NAME = {R.id.name1, R.id.name2, R.id.name3};
    static final int[] AMOUNT = {R.id.amount1, R.id.amount2, R.id.amount3};
    static final int[] PROGRESS = {R.id.progress1, R.id.progress2, R.id.progress3};
    static final int[] STATUS = {R.id.status1, R.id.status2, R.id.status3};
    static final int[] PREV = {R.id.prev1, R.id.prev2, R.id.prev3};

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        RemoteViews v = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, v);
    }

    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, CardWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        RemoteViews v = build(ctx);
        for (int id : ids) mgr.updateAppWidget(id, v);
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        List<Card> cards = Card.load(ctx);

        v.setTextViewText(R.id.title, Fmt.month(Db.thisYm()) + "월 카드실적");
        v.setTextViewText(R.id.updated, new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date()) + " 갱신");
        v.setViewVisibility(R.id.empty, cards.isEmpty() ? View.VISIBLE : View.GONE);

        for (int i = 0; i < ROW.length; i++) {
            if (i >= cards.size()) {
                v.setViewVisibility(ROW[i], View.GONE);
                continue;
            }
            Summary s = Summary.of(ctx, cards.get(i));
            v.setViewVisibility(ROW[i], View.VISIBLE);
            v.setInt(BAR[i], "setBackgroundColor", s.card.color);
            v.setTextViewText(NAME[i], s.card.name);
            v.setTextViewText(AMOUNT[i], Fmt.won(s.thisMonth));
            v.setProgressBar(PROGRESS[i], 100, s.progress, false);
            v.setTextViewText(STATUS[i], s.statusText());
            v.setTextViewText(PREV[i], s.prevText());
            v.setTextColor(PREV[i], s.prevAchieved > 0 ? 0xFF16A34A : 0xFF9CA3AF);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                int barColor = s.next < 0 && s.card.tiers.length > 0 ? 0xFF16A34A : s.card.color;
                v.setColorStateList(PROGRESS[i], "setProgressTintList", ColorStateList.valueOf(barColor));
            }
        }

        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.root, pi);
        return v;
    }
}

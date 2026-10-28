package com.brad.cardwidget;

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** 폰에 뜨는 모든 알림(문자, 카드사앱, 삼성월렛, 토스 등)을 받아 카드 결제만 골라 누적한다. */
public class CardListenerService extends NotificationListenerService {

    static final long DUP_WINDOW_MS = 3 * 60 * 1000L; // 같은 결제가 여러 경로로 올 때 합치는 시간

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(this, sbn);
        } catch (Throwable ignored) {
        }
    }

    static void handle(Context ctx, StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(ctx.getPackageName())) return;
        Notification n = sbn.getNotification();
        if (n == null || n.extras == null) return;
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        Bundle ex = n.extras;
        String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
        String conv = str(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        String head = (conv.isEmpty() ? title : conv + " " + title).trim();

        // 하나의 알림에 문자 여러 개가 묶여 오는 경우(메시지 앱)를 개별 문구로 쪼갠다
        List<String> segs = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0) {
            for (Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                String t = str(b.getCharSequence("text"));
                String sender = str(b.getCharSequence("sender"));
                if (t.isEmpty()) continue;
                segs.add((head + " " + sender + " " + t).trim());
                long ts = b.getLong("time", 0);
                times.add(ts > 0 ? ts : sbn.getPostTime());
            }
        }
        if (segs.isEmpty()) {
            CharSequence[] lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            String big = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
            if (!big.isEmpty() || !text.isEmpty()) {
                segs.add((head + " " + (big.isEmpty() ? text : big)).trim());
                times.add(sbn.getPostTime());
            } else if (lines != null) {
                for (CharSequence l : lines) {
                    segs.add((head + " " + str(l)).trim());
                    times.add(sbn.getPostTime());
                }
            }
        }

        boolean changed = false;
        for (int i = 0; i < segs.size(); i++) {
            changed |= process(ctx, pkg, segs.get(i), times.get(i));
        }
        if (changed) CardWidgetProvider.updateAll(ctx);
    }

    /** @return 결제 내역이 추가됐으면 true */
    static boolean process(Context ctx, String pkg, String seg, long ts) {
        if (seg.isEmpty() || !seg.contains("원")) return false;
        if (!seg.matches("(?s).*[0-9].*")) return false;

        Db db = Db.get(ctx);
        // 같은 문구 재표시(알림 갱신, 문자 묶음 재게시) 방지
        if (!db.markSeen(sha1(pkg + "|" + seg), System.currentTimeMillis())) return false;

        // 앱 설치 전/오래된 문자가 다시 떠서 집계되는 것 방지 (2일 이전 메시지 무시)
        if (System.currentTimeMillis() - ts > 2L * 24 * 3600 * 1000) return false;

        List<Card> cards = Card.load(ctx);
        Parser.Result r = Parser.parse(seg, cards);
        if (r == null) {
            if (looksFinancial(seg)) db.log(pkg, Parser.lastReason, seg);
            return false;
        }

        if (r.cumulative >= 0) db.saveCumulative(r.cardId, r.cumulative, ts);

        long signed = r.cancel ? -r.amount : r.amount;
        if (db.hasSimilar(r.cardId, signed, ts, DUP_WINDOW_MS)) {
            db.log(pkg, "중복(합침) " + r.cardName + " " + Fmt.won(signed), seg);
            return false;
        }
        db.insert(r.cardId, signed, ts, !r.excluded, seg, pkg);
        if (r.excluded) db.log(pkg, "실적제외로 기록(" + r.excludeWord + ")", seg);
        return true;
    }

    static boolean looksFinancial(String s) {
        return s.contains("카드") || s.contains("승인") || s.contains("결제") || s.contains("페이");
    }

    static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}

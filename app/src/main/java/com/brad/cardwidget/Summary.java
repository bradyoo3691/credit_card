package com.brad.cardwidget;

import android.content.Context;

/** 카드 1장의 이번 달 / 지난달 실적 요약. 위젯과 앱 화면이 같이 쓴다. */
public class Summary {
    public Card card;
    public long thisMonth;     // 이번 달 실적 인정 금액
    public long thisMonthAll;  // 이번 달 전체 사용 (실적 제외 포함, 보정 포함)
    public long prevMonth;     // 지난달 실적
    public long achieved;      // 이번 달 달성 구간 (-1 없음)
    public long next;          // 다음 목표 구간 (-1 = 모두 달성)
    public long prevAchieved;  // 지난달 달성 구간 (-1 = 미달)
    public int progress;       // 0~100, 다음 목표 기준

    public static Summary of(Context ctx, Card c) {
        Db db = Db.get(ctx);
        String ym = Db.thisYm();
        String prev = Db.prevYm();
        Summary s = new Summary();
        s.card = c;
        s.thisMonth = db.monthTotal(c.id, ym);
        s.thisMonthAll = Math.max(0, db.sumAll(c.id, ym) + db.getBase(c.id, ym));
        s.prevMonth = db.monthTotal(c.id, prev);
        s.achieved = c.achievedTier(s.thisMonth);
        s.next = c.nextTier(s.thisMonth);
        s.prevAchieved = c.achievedTier(s.prevMonth);
        if (s.next > 0) s.progress = (int) Math.min(100, s.thisMonth * 100 / s.next);
        else s.progress = 100;
        return s;
    }

    /** 위젯 2번째 줄 왼쪽: "60만까지 187,700원" / "✅ 60만 달성!" */
    public String statusText() {
        if (card.tiers.length == 0) return "실적 조건 없음";
        if (next < 0) return "✅ " + Fmt.man(achieved) + " 달성!";
        String head = achieved > 0 ? "✅" + Fmt.man(achieved) + " · " : "";
        return head + Fmt.man(next) + "까지 " + Fmt.won(next - thisMonth);
    }

    /** 위젯 2번째 줄 오른쪽: 지난달 실적 결과 → 이번 달 혜택 구간 */
    public String prevText() {
        if (card.tiers.length == 0) return "";
        if (prevAchieved > 0) return "전월 " + Fmt.man(prevAchieved) + " 달성!";
        return "전월 미달";
    }
}

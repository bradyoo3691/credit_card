package com.brad.cardwidget;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 카드 승인 알림/문자 문구를 해석해서 카드·금액·취소 여부를 뽑아낸다. */
public class Parser {

    public static class Result {
        public String cardId;
        public String cardName;
        public long amount;        // 양수
        public boolean cancel;     // 승인취소
        public long cumulative = -1; // 문자에 찍힌 카드사 누적금액 (참고용)
        public boolean excluded;   // 실적 제외 항목
        public String excludeWord;
    }

    /** 실패 사유 (로그용). parse()가 null을 돌려줄 때 채워진다. */
    public static String lastReason = "";

    // 결제 내역이 아닌 안내/광고/명세서 알림 — 금액이 있어도 무시
    static final String[] IGNORE = {
            "결제예정", "결제 예정", "예정금액", "청구금액", "청구 금액", "청구예정", "명세서",
            "이벤트", "혜택", "쿠폰", "(광고)", "[광고]", "광고)", "캐시백 지급", "적립 안내",
            "한도초과", "한도 초과", "거절", "실패", "잔액부족", "잔액 부족", "오류",
            "결제일 안내", "납부안내", "납부 안내", "연체", "리볼빙", "카드론", "현금서비스 한도",
            // 은행 계좌 거래(이체/입금)는 카드 실적이 아님
            "은행", "입금", "계좌", "송금"
    };

    // 결제를 의미하는 단어 — 하나는 있어야 함
    static final String[] PAY_WORDS = {
            "승인", "결제", "사용", "이용", "자동납부", "자동이체", "정기", "일시불", "할부"
    };

    // 카드 판별 전에 지워버릴 단어 (삼성페이로 우리카드 결제 시 '삼성'으로 오인 방지)
    static final String[] STRIP = {
            "삼성페이", "삼성 페이", "Samsung Pay", "SamsungPay", "Samsung Wallet",
            "삼성월렛", "삼성 월렛", "삼성 Wallet", "Samsung pay"
    };

    static final Pattern AMOUNT = Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원");

    public static Result parse(String raw, List<Card> cards) {
        lastReason = "";
        if (raw == null) { lastReason = "빈 알림"; return null; }
        String text = raw.replace('\n', ' ').replace('\r', ' ');

        for (String w : IGNORE) {
            if (text.contains(w)) { lastReason = "안내/광고 문구(" + w + ")"; return null; }
        }

        boolean payWord = false;
        for (String w : PAY_WORDS) if (text.contains(w)) { payWord = true; break; }
        if (!payWord) { lastReason = "결제 단어 없음"; return null; }

        // 카드 판별
        String forCard = text;
        for (String s : STRIP) forCard = forCard.replace(s, " ");
        String lower = forCard.toLowerCase(Locale.ROOT);
        Card found = null;
        int foundPos = Integer.MAX_VALUE;
        for (Card c : cards) {
            for (String k : c.keywords) {
                if (k == null || k.trim().isEmpty()) continue;
                int p = lower.indexOf(k.trim().toLowerCase(Locale.ROOT));
                if (p >= 0 && p < foundPos) { found = c; foundPos = p; }
            }
        }
        if (found == null) { lastReason = "카드 판별 불가"; return null; }

        // 금액 판별: '누적' 앞에 붙은 금액은 누적금액, 잔액/한도/포인트 등은 무시
        Result r = new Result();
        long amount = -1;
        Matcher m = AMOUNT.matcher(text);
        while (m.find()) {
            long v;
            try { v = Long.parseLong(m.group(1).replace(",", "")); } catch (Exception e) { continue; }
            String before = text.substring(Math.max(0, m.start() - 10), m.start());
            if (before.contains("누적")) {
                if (r.cumulative < 0) r.cumulative = v;
                continue;
            }
            if (before.contains("잔액") || before.contains("한도") || before.contains("포인트")
                    || before.contains("적립") || before.contains("할인") || before.contains("캐시백")
                    || before.contains("수수료")) {
                continue;
            }
            if (amount < 0) amount = v;
        }
        if (amount <= 0) { lastReason = "금액 없음"; return null; }
        if (amount > 50_000_000L) { lastReason = "금액 비정상"; return null; }

        r.cardId = found.id;
        r.cardName = found.name;
        r.amount = amount;
        r.cancel = text.contains("취소");
        for (String ex : found.excludes) {
            if (ex != null && !ex.trim().isEmpty() && text.contains(ex.trim())) {
                r.excluded = true;
                r.excludeWord = ex.trim();
                break;
            }
        }
        return r;
    }
}

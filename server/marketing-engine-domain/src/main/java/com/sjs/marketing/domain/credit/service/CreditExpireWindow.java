package com.sjs.marketing.domain.credit.service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 过期扫描的时间窗口切分（纯函数，不依赖 Spring / DB，可直接单测）。
 *
 * <p>为什么按天切？到期批次的索引是 {@code (state, expires_at)}，
 * 一次扫「一个月」和扫「一天」，回表的行数差一个数量级。任务每小时跑一次，
 * 每次只需要扫「今天 + 最近几天（补跑漏掉的）」这一小段，而不是全表。</p>
 *
 * <p>窗口左端从「今天零点 - lookbackDays 天」开始，右端就是 {@code now}——
 * 未来到期的批次不参与回收，扫了也是浪费。</p>
 *
 * @author marketing-engine contributors
 */
public final class CreditExpireWindow {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private CreditExpireWindow() {
    }

    /**
     * 一个时间槽：[from, to)。
     *
     * @param from 左闭
     * @param to   右开
     */
    public record Slot(Date from, Date to) {
        public boolean isEmpty() {
            return from == null || to == null || !to.after(from);
        }
    }

    /**
     * 把 [今天零点 - lookbackDays 天, now) 按自然日切成若干槽。
     *
     * <p>最后一个槽的右端被截断到 {@code now}，保证不会扫到未来到期的批次。</p>
     *
     * @param now          当前时间
     * @param lookbackDays 回看天数（0 表示只扫今天）
     * @return 时间槽列表，按时间升序、首尾相接
     */
    public static List<Slot> slots(Date now, int lookbackDays) {
        List<Slot> slots = new ArrayList<>();
        if (now == null) {
            return slots;
        }
        int days = Math.max(0, lookbackDays);
        long end = now.getTime();
        long cursor = startOfDay(now) - days * DAY_MS;
        // 防御：切分逻辑本身是有限循环，这里再加一层保险，避免时间被改乱时死循环
        int guard = days + 2;
        while (cursor < end && slots.size() < guard) {
            long dayEnd = startOfDay(new Date(cursor)) + DAY_MS;
            long next = Math.min(dayEnd, end);
            if (next <= cursor) {
                next = Math.min(cursor + DAY_MS, end);
            }
            slots.add(new Slot(new Date(cursor), new Date(next)));
            cursor = next;
        }
        return slots;
    }

    /** 当天零点（本地时区） */
    static long startOfDay(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}

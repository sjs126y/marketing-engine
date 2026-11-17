package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.credit.service.CreditExpireWindow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 过期扫描时间窗口的切分验证。
 *
 * <p>这是纯函数：任务把「最近 N 天 + 今天」切成若干段 SQL，切片切错了会漏扫或重复扫。
 * 断言三件事：<b>首尾相接、覆盖到 now 为止、不碰未来</b>。</p>
 *
 * @author marketing-engine contributors
 */
class CreditExpireWindowTest {

    private static Date at(String isoLocalDateTime) {
        return Date.from(LocalDateTime.parse(isoLocalDateTime)
                .atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    @DisplayName("回看 2 天：切成 3 段，首尾相接，最后一段截断到 now")
    void shouldSplitByNaturalDay() {
        Date now = at("2026-11-17T17:30:00");
        List<CreditExpireWindow.Slot> slots = CreditExpireWindow.slots(now, 2);

        assertThat(slots).hasSize(3);
        // 第一段从 11-15 00:00 开始
        assertThat(slots.get(0).from()).isEqualTo(at("2026-11-15T00:00:00"));
        // 相邻两段首尾严格相接
        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).from()).isEqualTo(slots.get(i - 1).to());
        }
        // 最后一段的右端就是 now，不能把未来到期的批次算进来
        assertThat(slots.get(slots.size() - 1).to()).isEqualTo(now);
        // 没有任何一段是空窗口
        assertThat(slots).allSatisfy(slot -> assertThat(slot.isEmpty()).isFalse());
    }

    @Test
    @DisplayName("不回看：只有今天一段 [今天零点, now)")
    void shouldOnlyCoverTodayWhenLookbackZero() {
        Date now = at("2026-11-17T09:15:00");
        List<CreditExpireWindow.Slot> slots = CreditExpireWindow.slots(now, 0);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).from()).isEqualTo(at("2026-11-17T00:00:00"));
        assertThat(slots.get(0).to()).isEqualTo(now);
    }

    @Test
    @DisplayName("刚过零点：今天的窗口非空，不会因为除以整点而退化成空段")
    void shouldHandleMidnightEdge() {
        Date now = at("2026-11-17T00:00:03");
        List<CreditExpireWindow.Slot> slots = CreditExpireWindow.slots(now, 1);

        assertThat(slots).isNotEmpty();
        assertThat(slots.get(slots.size() - 1).to()).isEqualTo(now);
        assertThat(slots).allSatisfy(slot -> assertThat(slot.isEmpty()).isFalse());
    }

    @Test
    @DisplayName("入参为空或回看天数为负：安全退化为空/今天")
    void shouldBeSafeOnBadInput() {
        assertThat(CreditExpireWindow.slots(null, 3)).isEmpty();

        Date now = at("2026-11-17T12:00:00");
        List<CreditExpireWindow.Slot> slots = CreditExpireWindow.slots(now, -5);
        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).from()).isEqualTo(LocalDate.of(2026, 11, 17).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }
}

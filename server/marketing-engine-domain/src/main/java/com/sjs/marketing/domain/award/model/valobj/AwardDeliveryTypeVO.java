package com.sjs.marketing.domain.award.model.valobj;

import java.util.Optional;

/**
 * 奖品交付类型。
 *
 * <p>中台不持有积分/次数这些业务资产，但它需要告诉上游「这是一笔什么奖励」，
 * 于是把 {@code award_key} 归一成有限的几种交付类型。</p>
 *
 * @author marketing-engine contributors
 */
public enum AwardDeliveryTypeVO {

    /** 积分 */
    CREDIT("CREDIT", "积分"),
    /** 对话额度 */
    LLM_QUOTA("LLM_QUOTA", "对话次数"),
    /** 权益解锁（模型等持有型资产） */
    ENTITLEMENT("ENTITLEMENT", "权益解锁"),
    ;

    private final String code;

    private final String desc;

    AwardDeliveryTypeVO(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 按 award_key 推断交付类型。
     *
     * <p>种子数据里的约定：{@code user_credit_random} 是积分、
     * {@code openai_use_count} 是对话次数、{@code openai_model} 是模型解锁。
     * 认不出来的按权益解锁处理，至少不会错发成钱。</p>
     */
    public static AwardDeliveryTypeVO of(String awardKey) {
        if (awardKey == null) {
            return ENTITLEMENT;
        }
        String key = awardKey.toLowerCase();
        if (key.contains("credit") || key.contains("integral") || key.contains("point")) {
            return CREDIT;
        }
        if (key.contains("use_count") || key.contains("quota") || key.contains("token")) {
            return LLM_QUOTA;
        }
        return ENTITLEMENT;
    }

    /**
     * 从 award_config 解析交付数量。
     *
     * <p>支持两种写法：单个数字（{@code "5"}）与区间（{@code "1,100"}，取区间随机值）。
     * 解析不出来时返回 1，保证回调里总有一个合法值。</p>
     */
    public static int resolveAmount(String awardConfig) {
        if (awardConfig == null || awardConfig.isBlank()) {
            return 1;
        }
        String text = awardConfig.trim();
        try {
            if (text.contains(",")) {
                String[] parts = text.split(",");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max <= min) {
                    return Math.max(min, 1);
                }
                return min + (int) (Math.random() * (max - min + 1));
            }
            return Math.max(Integer.parseInt(text), 1);
        } catch (NumberFormatException e) {
            // 非数字配置（例如模型名 gpt-4）：数量按 1 处理，原文放在 payload 的 config 字段里
            return 1;
        }
    }

    /**
     * 解析失败时保留原文，让上游知道具体是哪个权益。
     */
    public static Optional<String> rawConfig(String awardConfig) {
        return Optional.ofNullable(awardConfig).filter(s -> !s.isBlank());
    }

}

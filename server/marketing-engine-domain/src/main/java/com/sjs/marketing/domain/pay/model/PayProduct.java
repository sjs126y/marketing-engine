package com.sjs.marketing.domain.pay.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品目录（纯函数，可单测）。
 *
 * <p>只做积分包：对话次数还没迁进权益中心（P2 之后的 v8 任务），
 * 现在卖「对话次数」就得回到旧的 sku 充值链路，语义会乱。
 * 目录里预留了 {@code quota} 类型，等 v8 表结构落地后直接补 SKU 即可。</p>
 *
 * @author marketing-engine contributors
 */
public final class PayProduct {

    /** 积分包 */
    public static final String TYPE_CREDIT = "credit";
    /** 次数包（v8 之后启用） */
    public static final String TYPE_QUOTA = "quota";

    private static final Map<String, PayProduct> CATALOG;

    static {
        Map<String, PayProduct> catalog = new LinkedHashMap<>();
        catalog.put("credit_100", new PayProduct("credit_100", TYPE_CREDIT, 100, 100));
        catalog.put("credit_500", new PayProduct("credit_500", TYPE_CREDIT, 500, 400));
        catalog.put("credit_1000", new PayProduct("credit_1000", TYPE_CREDIT, 1000, 700));
        CATALOG = Collections.unmodifiableMap(catalog);
    }

    /** SKU 编码 */
    private final String sku;
    /** 商品类型 */
    private final String type;
    /** 发放数量 */
    private final int amount;
    /** 应付金额（分） */
    private final int amountCent;

    private PayProduct(String sku, String type, int amount, int amountCent) {
        this.sku = sku;
        this.type = type;
        this.amount = amount;
        this.amountCent = amountCent;
    }

    public String getSku() {
        return sku;
    }

    public String getType() {
        return type;
    }

    public int getAmount() {
        return amount;
    }

    public int getAmountCent() {
        return amountCent;
    }

    /** 按 SKU 查商品，未登记返回 null（由调用方转成参数错误） */
    public static PayProduct of(String sku) {
        return sku == null ? null : CATALOG.get(sku.trim());
    }

    /** 全部商品，供页面展示 */
    public static Map<String, PayProduct> catalog() {
        return CATALOG;
    }
}

package com.sjs.marketing.test.unit;

import com.sjs.marketing.domain.pay.model.PayProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商品目录验证。
 *
 * <p>价格和发放数量是「钱」的事，配错一个 SKU 就是资损，所以做成纯函数单独测：
 * 只认登记过的编码，未登记一律返回 null 由调用方转参数错误。</p>
 *
 * @author marketing-engine contributors
 */
class PayProductTest {

    @Test
    @DisplayName("登记的 SKU 能查到数量与价格")
    void shouldFindRegisteredSku() {
        PayProduct product = PayProduct.of("credit_100");

        assertThat(product).isNotNull();
        assertThat(product.getType()).isEqualTo(PayProduct.TYPE_CREDIT);
        assertThat(product.getAmount()).isEqualTo(100);
        assertThat(product.getAmountCent()).isEqualTo(100);
    }

    @Test
    @DisplayName("未登记 / 空 SKU 返回 null，不允许「默认商品」这种兜底")
    void shouldRejectUnknownSku() {
        assertThat(PayProduct.of("credit_999999")).isNull();
        assertThat(PayProduct.of("")).isNull();
        assertThat(PayProduct.of(null)).isNull();
    }

    @Test
    @DisplayName("目录非空、价格为正、数量为正（防止配成 0 元购）")
    void catalogShouldBeSane() {
        assertThat(PayProduct.catalog()).isNotEmpty();
        PayProduct.catalog().values().forEach(product -> {
            assertThat(product.getAmount()).isPositive();
            assertThat(product.getAmountCent()).isPositive();
            assertThat(product.getType()).isIn(PayProduct.TYPE_CREDIT, PayProduct.TYPE_QUOTA);
            assertThat(product.getSku()).isEqualTo(PayProduct.of(product.getSku()).getSku());
        });
    }

    @Test
    @DisplayName("SKU 前后空格能容错")
    void shouldTrimSku() {
        assertThat(PayProduct.of("  credit_500  ")).isNotNull();
        assertThat(PayProduct.of("  credit_500  ").getAmount()).isEqualTo(500);
    }
}

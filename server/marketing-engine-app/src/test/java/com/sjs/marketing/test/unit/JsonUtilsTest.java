package com.sjs.marketing.test.unit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sjs.marketing.domain.award.event.SendAwardMessageEvent;
import com.sjs.marketing.types.event.BaseEvent;
import com.sjs.marketing.types.util.JsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSON 序列化验证（fastjson → Jackson 迁移的回归保障）。
 *
 * <p>消息体是带泛型的包装对象 {@code EventMessage<T>}，fastjson 用
 * {@code TypeReference.getType()}，Jackson 用 {@code new TypeReference<T>() {}}，
 * 这层泛型信息在迁移中最容易丢，所以单独覆盖。</p>
 *
 * @author marketing-engine contributors
 */
class JsonUtilsTest {

    @Test
    @DisplayName("带泛型的消息包装可以完整往返")
    void shouldRoundTripGenericEventMessage() {
        SendAwardMessageEvent.SendAwardMessage data = SendAwardMessageEvent.SendAwardMessage.builder()
                .userId("demo-user")
                .awardId(102)
                .awardTitle("OpenAI 会员卡")
                .build();

        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> message =
                BaseEvent.EventMessage.<SendAwardMessageEvent.SendAwardMessage>builder()
                        .id("12345678901")
                        .timestamp(new Date(1700000000000L))
                        .data(data)
                        .build();

        String json = JsonUtils.toJson(message);
        assertThat(json).contains("\"id\":\"12345678901\"").contains("OpenAI 会员卡");

        BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage> parsed =
                JsonUtils.parse(json, new TypeReference<BaseEvent.EventMessage<SendAwardMessageEvent.SendAwardMessage>>() {
                });

        assertThat(parsed).isNotNull();
        assertThat(parsed.getId()).isEqualTo("12345678901");
        assertThat(parsed.getTimestamp()).isEqualTo(new Date(1700000000000L));
        assertThat(parsed.getData().getUserId()).isEqualTo("demo-user");
        assertThat(parsed.getData().getAwardId()).isEqualTo(102);
        assertThat(parsed.getData().getAwardTitle()).isEqualTo("OpenAI 会员卡");
    }

    @Test
    @DisplayName("多出未知字段时不报错，兼容上游加字段")
    void shouldIgnoreUnknownFields() {
        String json = "{\"userId\":\"u1\",\"sku\":9011,\"extraField\":\"ignored\"}";

        Dummy parsed = JsonUtils.parse(json, Dummy.class);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getUserId()).isEqualTo("u1");
        assertThat(parsed.getSku()).isEqualTo(9011L);
    }

    @Test
    @DisplayName("空输入返回 null 而不是抛异常")
    void shouldReturnNullForEmptyInput() {
        assertThat(JsonUtils.toJson(null)).isNull();
        assertThat(JsonUtils.parse(null, Dummy.class)).isNull();
        assertThat(JsonUtils.parse("", Dummy.class)).isNull();
    }

    @lombok.Data
    public static class Dummy {
        private String userId;
        private Long sku;
    }

}

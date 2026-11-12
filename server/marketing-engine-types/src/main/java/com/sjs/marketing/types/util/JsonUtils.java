package com.sjs.marketing.types.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 统一 JSON 工具，替代原先混用的 fastjson 1.x / 2.x。
 *
 * <p>选 Jackson 的理由：Spring Boot 自带、无额外依赖、默认不开启多态反序列化，
 * 比 fastjson 的 autoType 机制安全。</p>
 *
 * <p>注意：Redis 的对象编解码由 Redisson 的 {@code JsonJacksonCodec} 负责（带类型信息），
 * 这里只用于日志、消息体与普通序列化。</p>
 *
 * @author marketing-engine contributors
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtils() {
    }

    /**
     * 对象转 JSON 字符串。
     */
    public static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 JSON 失败", e);
        }
    }

    /**
     * JSON 字符串转对象。
     */
    public static <T> T parse(String json, Class<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 JSON 失败", e);
        }
    }

    /**
     * JSON 字符串转带泛型的对象。
     */
    public static <T> T parse(String json, TypeReference<T> type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化 JSON 失败", e);
        }
    }

}

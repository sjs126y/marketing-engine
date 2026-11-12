package com.sjs.marketing.domain.strategy.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 配置校验发现的一条问题。
 *
 * @author marketing-engine contributors
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigViolation {

    /** 问题编码，便于前端分类展示 */
    private String code;
    /** 严重级别：ERROR 阻止发布，WARN 仅提示 */
    private String severity;
    /** 定位信息，例如 strategyId=100006 / tree=tree_lock_3 / award=105 */
    private String target;
    /** 人类可读的说明 */
    private String message;

    public boolean isError() {
        return "ERROR".equals(severity);
    }

    public static ConfigViolation error(String code, String target, String message) {
        return ConfigViolation.builder().code(code).severity("ERROR").target(target).message(message).build();
    }

    public static ConfigViolation warn(String code, String target, String message) {
        return ConfigViolation.builder().code(code).severity("WARN").target(target).message(message).build();
    }

}

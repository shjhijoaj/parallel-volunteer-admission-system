package org.enroll.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "enroll.login")
public class LoginProperties {

    /** 是否开启登录校验。关闭后所有接口无需登录即可访问，仅建议本地调试时使用。 */
    private boolean enabled = true;

    private String adminName;

    private String adminPass;
}

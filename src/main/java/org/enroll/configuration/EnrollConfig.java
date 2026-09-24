package org.enroll.configuration;

import org.enroll.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册登录拦截器与跨域规则。
 *
 * <p>建表脚本不再通过 {@code DataSourceInitializer} 自动执行。
 * 旧实现会在每次启动时执行带 DROP TABLE 的 schema.sql，
 * 重启一次就清空一次数据；现在改由 {@code spring.sql.init.mode} 控制，
 * 默认值 neutral（never），只有显式设置 SQL_INIT_MODE=always 才会初始化。</p>
 */
@Configuration
public class EnrollConfig implements WebMvcConfigurer {

    @Autowired
    LoginInterceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        InterceptorRegistration registration = registry.addInterceptor(interceptor);
        registration.addPathPatterns("/**");
        registration.excludePathPatterns(
                "/login/**",
                "/admission/**",
                "/index.html",
                "/favicon.ico",
                "/css/**",
                "/js/**",
                "/img/**",
                "/fonts/**",
                "/error");
    }

    /**
     * 允许本地前端开发服务器跨域访问。
     *
     * <p>由于开启了 {@code allowCredentials}，这里只能使用具体来源，不能写成 {@code *}。</p>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("http://localhost:8000")
                .allowedMethods("GET","HEAD","POST","PUT","DELETE","OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

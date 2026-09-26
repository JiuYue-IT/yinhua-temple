package com.lifebranch.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 开发便利：允许本机任意端口的页面直接调用 /api（例如前端没配 Vite 代理时）。
 * 推荐仍用 Vite 代理或由后端托管 web/dist，同源访问不需要 CORS。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type");
    }
}

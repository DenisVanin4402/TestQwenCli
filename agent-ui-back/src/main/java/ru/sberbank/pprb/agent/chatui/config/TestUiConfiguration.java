package ru.sberbank.pprb.agent.chatui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Раздаёт ресурсы отдельного agent-ui только в локальном тестовом профиле. */
@Configuration(proxyBeanMethods = false)
@Profile("poc-local")
public class TestUiConfiguration implements WebMvcConfigurer {
    /** Файлы лежат вне стандартного static, чтобы другие профили не публиковали тестовый экран. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/test-ui/**").addResourceLocations("classpath:/test-ui/");
    }

    /** Открывает стартовый документ с корректными относительными ссылками на ресурсы. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/test-ui", "/test-ui/");
        registry.addViewController("/test-ui/").setViewName("forward:/test-ui/index.html");
    }
}

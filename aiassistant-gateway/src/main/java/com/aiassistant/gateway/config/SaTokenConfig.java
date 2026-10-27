package com.aiassistant.gateway.config;

import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.stp.StpUtil;
import com.aiassistant.common.result.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class SaTokenConfig {

    @Bean
    public SaReactorFilter saReactorFilter(ObjectMapper objectMapper) {
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/actuator/**"
                )
                .setAuth(obj -> {
                    StpUtil.checkLogin();
                })
                .setError(e -> {
                    int code = ErrorCode.UNAUTHORIZED.getCode();
                    String msg = ErrorCode.UNAUTHORIZED.getMessage();

                    if (e instanceof cn.dev33.satoken.exception.NotLoginException) {
                        msg = "未登录或登录已过期";
                    }

                    try {
                        return objectMapper.writeValueAsString(Map.of(
                                "code", code,
                                "message", msg,
                                "data", (Object) ""
                        ));
                    } catch (JsonProcessingException ex) {
                        return "{\"code\":401,\"message\":\"未登录\",\"data\":\"\"}";
                    }
                });
    }
}

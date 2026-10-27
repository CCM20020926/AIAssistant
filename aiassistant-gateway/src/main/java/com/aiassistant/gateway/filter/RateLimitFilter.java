package com.aiassistant.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final int CHAT_MAX_REQUESTS = 10;
    private static final Duration CHAT_WINDOW = Duration.ofMinutes(1);

    private static final int DEFAULT_MAX_REQUESTS = 60;
    private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(1);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        String userIdHeader = request.getHeaders().getFirst("X-User-Id");
        if (userIdHeader == null) {
            return chain.filter(exchange);
        }

        int maxRequests;
        Duration window;
        if (path.startsWith("/api/chat/stream")) {
            maxRequests = CHAT_MAX_REQUESTS;
            window = CHAT_WINDOW;
        } else {
            maxRequests = DEFAULT_MAX_REQUESTS;
            window = DEFAULT_WINDOW;
        }

        String key = "rate_limit:" + userIdHeader + ":" + pathPrefix(path);

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, window).then(doFilter(exchange, chain));
                    }
                    if (count > maxRequests) {
                        return reject(exchange);
                    }
                    return doFilter(exchange, chain);
                });
    }

    private Mono<Void> doFilter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = "{\"code\":429,\"message\":\"对不起，系统繁忙，请稍后重试\",\"data\":\"\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String pathPrefix(String path) {
        String[] parts = path.split("/");
        if (parts.length >= 3) {
            return parts[1] + "/" + parts[2];
        }
        return path;
    }

    @Override
    public int getOrder() {
        return -150;
    }
}

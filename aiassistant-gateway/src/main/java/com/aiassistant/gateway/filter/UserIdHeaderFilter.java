package com.aiassistant.gateway.filter;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Injects the X-User-Id header for every authenticated request.
 *
 * Implemented as a WebFilter (not a Gateway GlobalFilter) so it also applies to
 * gateway-local controllers (document / knowledge-base / notifications), which are
 * served by WebFlux and bypass the Gateway filter chain. The login id is resolved
 * from the bearer token via StpUtil.getLoginIdByToken, a storage lookup that does not
 * depend on the reactive Sa-Token request context.
 */
@Slf4j
@Component
@Order(0)
public class UserIdHeaderFilter implements WebFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String auth = exchange.getRequest().getHeaders().getFirst(AUTH_HEADER);
        if (auth != null && auth.startsWith(TOKEN_PREFIX)) {
            String token = auth.substring(TOKEN_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                try {
                    Object loginId = StpUtil.getLoginIdByToken(token);
                    if (loginId != null) {
                        ServerHttpRequest mutated = exchange.getRequest().mutate()
                                .header("X-User-Id", String.valueOf(loginId))
                                .build();
                        exchange = exchange.mutate().request(mutated).build();
                    }
                } catch (Exception e) {
                    log.debug("Failed to resolve login id from token", e);
                }
            }
        }
        return chain.filter(exchange);
    }
}

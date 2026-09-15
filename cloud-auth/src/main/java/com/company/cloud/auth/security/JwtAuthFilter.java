package com.company.cloud.auth.security;

import com.company.cloud.auth.entity.User;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（对标任务书 AuthGuard）
 * 职责：
 *   1. 从 Authorization: Bearer <token> 提取 token
 *   2. 校验 JWT（过期/非法 → 40103）
 *   3. 校验吊销列表（登出/改密/禁用 → 即时失效，40103）
 *   4. 注入 req.user（CurrentUser）到 SecurityContext
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final RevocationService revocationService;
    private final LoginThrottle loginThrottle; // 预留：可用于全局请求纬度兜底，当前以登录接口限流为主

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER)) {
            String token = header.substring(BEARER.length());
            try {
                Claims claims = jwtService.parse(token);
                // 只接受 access token；refresh token 不能用于业务请求
                if (jwtService.isAccessToken(claims)) {
                    String jti = jwtService.getJti(claims);
                    if (!revocationService.isRevoked(jti)) {
                        Long userId = Long.valueOf(claims.getSubject());
                        String username = claims.get("username", String.class);
                        String role = claims.get("role", String.class);
                        CurrentUser cu = new CurrentUser(userId, username, role);
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        cu, null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                    // 已吊销 → 不认证，进入后续 Security 触发 401
                }
            } catch (Exception e) {
                log.debug("JWT 校验失败: {}", e.getMessage());
                // 非法/过期 token → 不认证，由 Security 入口统一返回 40103
            }
        }
        chain.doFilter(request, response);
    }
}
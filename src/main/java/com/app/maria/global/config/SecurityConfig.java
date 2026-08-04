package com.app.maria.global.config;

import com.app.maria.domain.member.type.MemberRole;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

@Configuration
public class SecurityConfig {

  private static final String[] PUBLIC_URLS = {
      "/",
      "/favicon.ico",

      // 정적 리소스와 SPA 진입점
      "/css/**",
      "/js/**",
      "/images/**",

      // JWT 인증 API
      "/api/auth/admin/login",

      // Swagger
      "/swagger-ui/**",
      "/swagger-ui.html",
      "/api-docs/**"
  };

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http
  ) throws Exception {
    http
        // JWT는 서버 세션을 사용하지 않는다.
        .sessionManagement(session -> session
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )

        // Access Token을 Authorization 헤더로 전달하는 Stateless API 기준 설정이다.
        .csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)

        .authorizeHttpRequests(auth -> auth
            .requestMatchers(PUBLIC_URLS)
            .permitAll()

            .requestMatchers("/api/admin/**")
            .hasRole(MemberRole.ADMIN.name())

            .requestMatchers("/api/manager/**")
            .hasAnyRole(
                MemberRole.MANAGER.name(),
                MemberRole.ADMIN.name()
            )

//            .requestMatchers("/api/**")
//            .authenticated()

            // 화면 전환은 SPA가 담당하고 실제 데이터 접근은 API에서 검증한다.
            .anyRequest()
            .permitAll()
        )

        .exceptionHandling(exception -> exception
            .authenticationEntryPoint((request, response, ex) ->
                writeJsonError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHORIZED",
                    "로그인이 필요합니다."
                )
            )
            .accessDeniedHandler((request, response, ex) ->
                writeJsonError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "FORBIDDEN",
                    "접근 권한이 없습니다."
                )
            )
        );

    // JwtAuthenticationFilter 구현 후 UsernamePasswordAuthenticationFilter 앞에 등록한다.
    return http.build();
  }

  private static void writeJsonError(
      HttpServletResponse response,
      int status,
      String code,
      String message
  ) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getWriter().write("""
        {
          "code": "%s",
          "message": "%s"
        }
        """.formatted(code, message));
  }
}

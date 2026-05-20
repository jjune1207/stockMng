package com.example.stockchart.config;

import com.example.stockchart.auth.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        String uri = request.getRequestURI();

        if (session == null || session.getAttribute(AuthController.SESSION_OWNER) == null) {
            if (uri.startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "로그인이 필요합니다.");
                return false;
            }
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        boolean mustChangePw = Boolean.TRUE.equals(session.getAttribute(AuthController.SESSION_MUST_CHANGE_PW));
        if (mustChangePw && !uri.equals("/change-password")) {
            if (uri.startsWith("/api/")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "비밀번호 변경이 필요합니다.");
                return false;
            }
            response.sendRedirect(request.getContextPath() + "/change-password");
            return false;
        }

        return true;
    }
}

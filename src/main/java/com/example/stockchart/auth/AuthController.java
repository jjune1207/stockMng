package com.example.stockchart.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    public static final String SESSION_OWNER = "SESSION_OWNER";
    public static final String SESSION_IS_ADMIN = "SESSION_IS_ADMIN";
    public static final String SESSION_MUST_CHANGE_PW = "SESSION_MUST_CHANGE_PW";

    private final OwnerAuthService ownerAuthService;

    @GetMapping("/login")
    public String loginPage(Model model) {
        return "login";
    }

    @PostMapping("/login")
    public String login(
        @RequestParam("name") String name,
        @RequestParam("password") String password,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        if (ownerAuthService.isAdminCredentials(name, password)) {
            session.setAttribute(SESSION_OWNER, name);
            session.setAttribute(SESSION_IS_ADMIN, true);
            log.info("admin 로그인: {}", name);
            return "redirect:/";
        }
        if (ownerAuthService.authenticate(name, password)) {
            session.setAttribute(SESSION_OWNER, name);
            session.setAttribute(SESSION_IS_ADMIN, false);
            log.info("소유자 로그인: {}", name);
            if (ownerAuthService.isMustChangePassword(name)) {
                session.setAttribute(SESSION_MUST_CHANGE_PW, true);
                return "redirect:/change-password";
            }
            return "redirect:/";
        }
        redirectAttributes.addFlashAttribute("error", "이름 또는 비밀번호가 올바르지 않습니다.");
        return "redirect:/login";
    }

    @GetMapping("/change-password")
    public String changePasswordPage(HttpSession session, Model model) {
        if (session.getAttribute(SESSION_OWNER) == null) {
            return "redirect:/login";
        }
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
        @RequestParam("newPassword") String newPassword,
        @RequestParam("confirmPassword") String confirmPassword,
        HttpSession session,
        RedirectAttributes redirectAttributes
    ) {
        String owner = (String) session.getAttribute(SESSION_OWNER);
        if (owner == null) {
            return "redirect:/login";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "비밀번호가 일치하지 않습니다.");
            return "redirect:/change-password";
        }
        try {
            ownerAuthService.changePassword(owner, newPassword);
            session.removeAttribute(SESSION_MUST_CHANGE_PW);
            log.info("비밀번호 변경: {}", owner);
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/change-password";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}

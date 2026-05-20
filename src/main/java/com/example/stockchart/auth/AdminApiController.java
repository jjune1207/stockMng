package com.example.stockchart.auth;

import com.example.stockchart.service.StockDataFacade;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {

    private final OwnerAuthService ownerAuthService;
    private final StockDataFacade stockDataFacade;

    @GetMapping("/accounts")
    public ResponseEntity<List<String>> getAccounts(HttpSession session) {
        assertAdmin(session);
        return ResponseEntity.ok(ownerAuthService.getAccountNames());
    }

    @PostMapping("/accounts")
    public ResponseEntity<Map<String, String>> createAccount(
        @RequestBody Map<String, String> body,
        HttpSession session
    ) {
        assertAdmin(session);
        String name = body.get("name");
        try {
            ownerAuthService.adminCreate(name);
            log.info("어드민이 계정 생성: {}", name);
            return ResponseEntity.ok(Map.of(
                "message", name + " 계정이 생성되었습니다. 초기 비밀번호: " + OwnerAuthService.DEFAULT_ADMIN_PASSWORD
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/accounts/{name}")
    public ResponseEntity<Void> deleteAccount(@PathVariable String name, HttpSession session) {
        assertAdmin(session);
        ownerAuthService.deleteAccount(name);
        stockDataFacade.deleteOwner(name);
        log.info("어드민이 계정 삭제: {}", name);
        return ResponseEntity.noContent().build();
    }

    private void assertAdmin(HttpSession session) {
        if (!Boolean.TRUE.equals(session.getAttribute(AuthController.SESSION_IS_ADMIN))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 접근 가능합니다.");
        }
    }
}

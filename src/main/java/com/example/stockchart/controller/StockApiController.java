package com.example.stockchart.controller;

import com.example.stockchart.auth.AuthController;
import com.example.stockchart.auth.SubOwnerMappingService;
import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.MarketIndicatorDto;
import com.example.stockchart.dto.StockPriceDto;
import com.example.stockchart.dto.StockSearchDto;
import com.example.stockchart.dto.UsNewsDto;
import com.example.stockchart.dto.WatchlistItemDto;
import com.example.stockchart.dto.WatchlistRequestDto;
import com.example.stockchart.service.StockDataFacade;
import com.example.stockchart.util.IndicatorUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockApiController {

    private final StockDataFacade stockDataFacade;
    private final SubOwnerMappingService subOwnerMappingService;

    @GetMapping("/{symbol}/price")
    public ResponseEntity<StockPriceDto> getPrice(@PathVariable("symbol") String symbol) {
        log.info("REST 현재가 요청: {}", symbol);
        StockPriceDto price = stockDataFacade.getCurrentPrice(symbol);
        return ResponseEntity.ok(price);
    }

    @GetMapping("/{symbol}/candle")
    public ResponseEntity<Map<String, Object>> getCandle(
            @PathVariable("symbol") String symbol,
            @RequestParam(name = "timeframe", defaultValue = "day") String timeframe) {
        log.info("REST 캔들 요청: symbol={}, timeframe={}", symbol, timeframe);

        List<CandleDto> candles;
        switch (timeframe) {
            case "1":
                candles = stockDataFacade.getMinuteCandles(symbol, 1);
                break;
            case "3":
                candles = stockDataFacade.getMinuteCandles(symbol, 3);
                break;
            case "10":
                candles = stockDataFacade.getMinuteCandles(symbol, 10);
                break;
            default:
                candles = stockDataFacade.getDailyCandles(symbol);
                break;
        }

        List<Double> ma5 = IndicatorUtil.calculateMA(candles, 5);
        List<Double> ma20 = IndicatorUtil.calculateMA(candles, 20);
        List<Double> ma50 = IndicatorUtil.calculateMA(candles, 50);
        List<Double> ma100 = IndicatorUtil.calculateMA(candles, 100);
        List<Double> ma200 = IndicatorUtil.calculateMA(candles, 200);

        IndicatorUtil.BollingerBands bb = IndicatorUtil.calculateBollingerBands(candles, 20, 2.0);
        List<Double> rsi = IndicatorUtil.calculateRSI(candles, 14);
        IndicatorUtil.MacdResult macd = IndicatorUtil.calculateMACD(candles, 12, 26, 9);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeframe", timeframe);
        result.put("candles", candles);
        result.put("ma5", ma5);
        result.put("ma20", ma20);
        result.put("ma50", ma50);
        result.put("ma100", ma100);
        result.put("ma200", ma200);
        result.put("bollingerUpper", bb.getUpper());
        result.put("bollingerMiddle", bb.getMiddle());
        result.put("bollingerLower", bb.getLower());
        result.put("rsi", rsi);
        result.put("macdLine", macd.getMacdLine());
        result.put("macdSignal", macd.getSignalLine());
        result.put("macdHistogram", macd.getHistogram());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    public ResponseEntity<List<StockSearchDto>> searchStock(
        @RequestParam(name = "keyword", defaultValue = "") String keyword) {
        log.info("REST 종목 검색 요청: keyword={}", keyword);
        List<StockSearchDto> results = stockDataFacade.searchStock(keyword);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/market-indicators")
    public ResponseEntity<List<MarketIndicatorDto>> getMarketIndicators() {
        log.info("REST 주요 시장 지표 요청");
        return ResponseEntity.ok(stockDataFacade.getMarketIndicators());
    }

    @GetMapping("/news")
    public ResponseEntity<List<UsNewsDto>> getUsNews(
        @RequestParam(name = "limit", defaultValue = "10") int limit,
        @RequestParam(name = "keywords", required = false, defaultValue = "") String keywordsParam,
        @RequestParam(name = "owner", required = false, defaultValue = "") String ownerParam,
        HttpSession session) {
        List<String> keywords = keywordsParam.isBlank()
            ? List.of()
            : List.of(keywordsParam.split(",")).stream()
                .map(String::trim).filter(k -> !k.isBlank())
                .toList();
        String owner = resolveNewsOwner(ownerParam, session);
        log.info("REST 주요 뉴스 요청: limit={}, keywords={}, owner={}", limit, keywords, owner);
        return ResponseEntity.ok(stockDataFacade.getUsNews(limit, keywords, owner));
    }

    @GetMapping("/news-keywords")
    public ResponseEntity<List<String>> getNewsKeywords(
        @RequestParam(name = "owner", required = false, defaultValue = "") String ownerParam,
        HttpSession session) {
        String owner = resolveNewsOwner(ownerParam, session);
        log.info("REST 뉴스 키워드 조회 요청: owner={}", owner);
        return ResponseEntity.ok(stockDataFacade.getNewsKeywords(owner));
    }

    @PutMapping("/news-keywords")
    public ResponseEntity<List<String>> updateNewsKeywords(
        @RequestBody List<String> keywords,
        @RequestParam(name = "owner", required = false, defaultValue = "") String ownerParam,
        HttpSession session) {
        String owner = resolveNewsOwner(ownerParam, session);
        log.info("REST 뉴스 키워드 업데이트 요청: owner={}, {}개", owner, keywords.size());
        return ResponseEntity.ok(stockDataFacade.updateNewsKeywords(owner, keywords));
    }

    @GetMapping("/usdkrw-rate")
    public ResponseEntity<Double> getUsdKrwRate() {
        List<MarketIndicatorDto> indicators = stockDataFacade.getMarketIndicators();
        double rate = indicators.stream()
            .filter(i -> "USDKRW".equals(i.getId()))
            .mapToDouble(MarketIndicatorDto::getCurrentValue)
            .findFirst()
            .orElse(0.0);
        return ResponseEntity.ok(rate);
    }

    @GetMapping("/top")
    public ResponseEntity<List<StockSearchDto>> getTopByVolume(
        @RequestParam(name = "type", defaultValue = "stock") String type,
        @RequestParam(name = "limit", defaultValue = "10") int limit) {
        if (!"stock".equalsIgnoreCase(type) && !"etf".equalsIgnoreCase(type)
            && !"us_stock".equalsIgnoreCase(type) && !"us_etf".equalsIgnoreCase(type)) {
            throw new IllegalArgumentException("type은 stock, etf, us_stock, us_etf만 허용됩니다.");
        }
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException("limit은 1~20 범위로 입력해 주세요.");
        }

        return ResponseEntity.ok(stockDataFacade.getTopByVolume(type, limit));
    }

    @GetMapping("/watchlist")
    public ResponseEntity<List<WatchlistItemDto>> getWatchlist(HttpSession session) {
        List<WatchlistItemDto> all = stockDataFacade.getWatchlist();
        return ResponseEntity.ok(filterForSession(all, session));
    }

    @PostMapping("/watchlist")
    public ResponseEntity<List<WatchlistItemDto>> addWatchlist(
            @RequestBody WatchlistRequestDto request, HttpSession session) {
        String sessionUser = sessionOwner(session);
        String owner;
        if (isAdmin(session)) {
            owner = request.getOwner() != null && !request.getOwner().isBlank()
                ? request.getOwner()
                : sessionUser;
        } else {
            String requested = request.getOwner();
            if (requested != null && !requested.isBlank() && !requested.equals(sessionUser)) {
                if (!subOwnerMappingService.isSubOwnerOf(requested, sessionUser)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "해당 소유자를 사용할 수 없습니다.");
                }
                owner = requested;
            } else {
                owner = sessionUser;
            }
        }

        WatchlistItemDto item = WatchlistItemDto.builder()
            .symbol(request.getSymbol())
            .name(request.getName())
            .market(request.getMarket())
            .type(request.getType())
            .group(request.getGroup())
            .owner(owner)
            .build();

        List<WatchlistItemDto> result = stockDataFacade.addWatchlistItem(item);
        return ResponseEntity.ok(filterForSession(result, session));
    }

    @DeleteMapping("/watchlist/{symbol}")
    public ResponseEntity<List<WatchlistItemDto>> removeWatchlist(
            @PathVariable("symbol") String symbol, HttpSession session) {
        if (!isAdmin(session)) {
            assertOwnership(symbol, sessionOwner(session));
        }
        List<WatchlistItemDto> result = stockDataFacade.removeWatchlistItem(symbol);
        return ResponseEntity.ok(filterForSession(result, session));
    }

    @GetMapping("/watchlist/groups")
    public ResponseEntity<List<String>> getWatchlistGroups(HttpSession session) {
        if (isAdmin(session)) {
            return ResponseEntity.ok(stockDataFacade.getWatchlistGroups());
        }
        String owner = sessionOwner(session);
        List<String> groups = stockDataFacade.getWatchlist().stream()
            .filter(i -> owner.equals(i.getOwner()))
            .map(WatchlistItemDto::getGroup)
            .filter(g -> g != null && !g.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
        return ResponseEntity.ok(groups);
    }

    @PutMapping("/watchlist/{symbol}/group")
    public ResponseEntity<List<WatchlistItemDto>> moveToGroup(
            @PathVariable("symbol") String symbol,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        if (!isAdmin(session)) {
            assertOwnership(symbol, sessionOwner(session));
        }
        List<WatchlistItemDto> result = stockDataFacade.moveToGroup(symbol, body.get("group"));
        return ResponseEntity.ok(filterForSession(result, session));
    }

    @DeleteMapping("/watchlist/groups/{groupName}")
    public ResponseEntity<List<WatchlistItemDto>> deleteGroup(
            @PathVariable("groupName") String groupName,
            @RequestParam(name = "owner", defaultValue = "") String owner,
            HttpSession session) {
        String effectiveOwner = isAdmin(session) && !owner.isBlank() ? owner : sessionOwner(session);
        List<WatchlistItemDto> result = stockDataFacade.deleteWatchlistGroup(effectiveOwner, groupName);
        return ResponseEntity.ok(filterForSession(result, session));
    }

    @PutMapping("/watchlist/groups/{groupName}")
    public ResponseEntity<List<WatchlistItemDto>> renameGroup(
            @PathVariable("groupName") String groupName,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        String effectiveOwner = isAdmin(session) && body.containsKey("owner") && !body.get("owner").isBlank()
            ? body.get("owner")
            : sessionOwner(session);
        List<WatchlistItemDto> result = stockDataFacade.renameWatchlistGroup(effectiveOwner, groupName, body.get("newName"));
        return ResponseEntity.ok(filterForSession(result, session));
    }

    @GetMapping("/watchlist/owners")
    public ResponseEntity<List<String>> getOwners(HttpSession session) {
        if (isAdmin(session)) {
            return ResponseEntity.ok(stockDataFacade.getOwners());
        }
        String owner = sessionOwner(session);
        List<String> result = new ArrayList<>();
        result.add(owner);
        result.addAll(subOwnerMappingService.getSubOwnersForAccount(owner));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/watchlist/owners")
    public ResponseEntity<Map<String, String>> addSubOwner(
            @RequestBody Map<String, String> body, HttpSession session) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "소유자명을 입력해 주세요."));
        }
        String trimmed = name.trim();
        if (!trimmed.matches("^[\\w가-힣\\s]{1,20}$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "소유자명은 한글/영문/숫자 1~20자로 입력해 주세요."));
        }
        String accountName = sessionOwner(session);
        if (subOwnerMappingService.isKnownSubOwner(trimmed) && !subOwnerMappingService.isSubOwnerOf(trimmed, accountName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "이미 다른 계정에 등록된 소유자명입니다."));
        }
        subOwnerMappingService.register(trimmed, accountName);
        return ResponseEntity.ok(Map.of("name", trimmed, "account", accountName));
    }

    @PutMapping("/watchlist/owners/{ownerName}")
    public ResponseEntity<List<WatchlistItemDto>> renameOwner(
            @PathVariable("ownerName") String ownerName,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        if (!isAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "소유자 이름 변경은 관리자만 가능합니다.");
        }
        return ResponseEntity.ok(stockDataFacade.renameOwner(ownerName, body.get("newName")));
    }

    @DeleteMapping("/watchlist/owners/{ownerName}")
    public ResponseEntity<List<WatchlistItemDto>> deleteOwner(
            @PathVariable("ownerName") String ownerName, HttpSession session) {
        if (!isAdmin(session)) {
            String sessionUser = sessionOwner(session);
            if (ownerName.equals(sessionUser)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "자신의 계정 소유자는 삭제할 수 없습니다.");
            }
            if (!subOwnerMappingService.isSubOwnerOf(ownerName, sessionUser)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "소유자 삭제는 관리자만 가능합니다.");
            }
        }
        subOwnerMappingService.delete(ownerName);
        return ResponseEntity.ok(filterForSession(stockDataFacade.deleteOwner(ownerName), session));
    }

    @PutMapping("/watchlist/{symbol}/portfolio")
    public ResponseEntity<List<WatchlistItemDto>> updatePortfolio(
            @PathVariable("symbol") String symbol,
            @RequestBody Map<String, Double> body,
            HttpSession session) {
        if (!isAdmin(session)) {
            assertOwnership(symbol, sessionOwner(session));
        }
        List<WatchlistItemDto> result = stockDataFacade.updateWatchlistPortfolio(
            symbol, body.get("quantity"), body.get("purchasePrice"));
        return ResponseEntity.ok(filterForSession(result, session));
    }

    // --- 세션 헬퍼 ---

    /** 뉴스 키워드 owner 결정: 어드민은 ?owner= 파라미터 우선, 일반 사용자는 세션 owner 고정 */
    private String resolveNewsOwner(String ownerParam, HttpSession session) {
        if (isAdmin(session) && ownerParam != null && !ownerParam.isBlank()) {
            return ownerParam.trim();
        }
        String sessionUser = sessionOwner(session);
        return sessionUser != null ? sessionUser : "";
    }

    private String sessionOwner(HttpSession session) {
        return (String) session.getAttribute(AuthController.SESSION_OWNER);
    }

    private boolean isAdmin(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(AuthController.SESSION_IS_ADMIN));
    }

    private List<WatchlistItemDto> filterForSession(List<WatchlistItemDto> items, HttpSession session) {
        if (isAdmin(session)) return items;
        String owner = sessionOwner(session);
        Set<String> ownersForSession = new HashSet<>();
        ownersForSession.add(owner);
        ownersForSession.addAll(subOwnerMappingService.getSubOwnersForAccount(owner));
        return items.stream().filter(i -> ownersForSession.contains(i.getOwner())).toList();
    }

    private void assertOwnership(String compositeKey, String owner) {
        String[] parts = compositeKey.split("\\|", -1);
        if (parts.length == 3 && !parts[1].equals(owner)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자의 데이터를 수정할 수 없습니다.");
        }
    }
}

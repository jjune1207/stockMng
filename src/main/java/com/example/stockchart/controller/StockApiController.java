package com.example.stockchart.controller;

import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.MarketIndicatorDto;
import com.example.stockchart.dto.StockPriceDto;
import com.example.stockchart.dto.StockSearchDto;
import com.example.stockchart.dto.UsNewsDto;
import com.example.stockchart.dto.WatchlistItemDto;
import com.example.stockchart.dto.WatchlistRequestDto;
import com.example.stockchart.service.StockDataFacade;
import com.example.stockchart.util.IndicatorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockApiController {

    private final StockDataFacade stockDataFacade;

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
        @RequestParam(name = "keywords", required = false, defaultValue = "") String keywordsParam) {
        List<String> keywords = keywordsParam.isBlank()
            ? List.of()
            : List.of(keywordsParam.split(",")).stream()
                .map(String::trim).filter(k -> !k.isBlank())
                .toList();
        log.info("REST 주요 뉴스 요청: limit={}, keywords={}", limit, keywords);
        return ResponseEntity.ok(stockDataFacade.getUsNews(limit, keywords));
    }

    @GetMapping("/news-keywords")
    public ResponseEntity<List<String>> getNewsKeywords() {
        log.info("REST 뉴스 키워드 조회 요청");
        return ResponseEntity.ok(stockDataFacade.getNewsKeywords());
    }

    @PutMapping("/news-keywords")
    public ResponseEntity<List<String>> updateNewsKeywords(@RequestBody List<String> keywords) {
        log.info("REST 뉴스 키워드 업데이트 요청: {}개", keywords.size());
        return ResponseEntity.ok(stockDataFacade.updateNewsKeywords(keywords));
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
    public ResponseEntity<List<WatchlistItemDto>> getWatchlist() {
        return ResponseEntity.ok(stockDataFacade.getWatchlist());
    }

    @PostMapping("/watchlist")
    public ResponseEntity<List<WatchlistItemDto>> addWatchlist(@RequestBody WatchlistRequestDto request) {
        WatchlistItemDto item = WatchlistItemDto.builder()
            .symbol(request.getSymbol())
            .name(request.getName())
            .market(request.getMarket())
            .type(request.getType())
            .group(request.getGroup())
            .build();

        return ResponseEntity.ok(stockDataFacade.addWatchlistItem(item));
    }

    @DeleteMapping("/watchlist/{symbol}")
    public ResponseEntity<List<WatchlistItemDto>> removeWatchlist(@PathVariable("symbol") String symbol) {
        return ResponseEntity.ok(stockDataFacade.removeWatchlistItem(symbol));
    }

    @GetMapping("/watchlist/groups")
    public ResponseEntity<List<String>> getWatchlistGroups() {
        return ResponseEntity.ok(stockDataFacade.getWatchlistGroups());
    }

    @PutMapping("/watchlist/{symbol}/group")
    public ResponseEntity<List<WatchlistItemDto>> moveToGroup(
        @PathVariable("symbol") String symbol,
        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(stockDataFacade.moveToGroup(symbol, body.get("group")));
    }

    @DeleteMapping("/watchlist/groups/{groupName}")
    public ResponseEntity<List<WatchlistItemDto>> deleteGroup(@PathVariable("groupName") String groupName) {
        return ResponseEntity.ok(stockDataFacade.deleteWatchlistGroup(groupName));
    }

    @PutMapping("/watchlist/groups/{groupName}")
    public ResponseEntity<List<WatchlistItemDto>> renameGroup(
        @PathVariable("groupName") String groupName,
        @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(stockDataFacade.renameWatchlistGroup(groupName, body.get("newName")));
    }

    @PutMapping("/watchlist/{symbol}/portfolio")
    public ResponseEntity<List<WatchlistItemDto>> updatePortfolio(
        @PathVariable("symbol") String symbol,
        @RequestBody Map<String, Double> body) {
        return ResponseEntity.ok(stockDataFacade.updateWatchlistPortfolio(
            symbol, body.get("quantity"), body.get("purchasePrice")));
    }
}

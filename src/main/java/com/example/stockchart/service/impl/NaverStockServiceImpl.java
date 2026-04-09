package com.example.stockchart.service.impl;

import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.MarketIndicatorDto;
import com.example.stockchart.dto.StockPriceDto;
import com.example.stockchart.dto.StockSearchDto;
import com.example.stockchart.exception.StockApiException;
import com.example.stockchart.service.NaverStockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NaverStockServiceImpl implements NaverStockService {

    private final WebClient naverWebClient;
    private final WebClient yahooWebClient;
    private final ObjectMapper objectMapper;

    public NaverStockServiceImpl(
            @Qualifier("naverWebClient") WebClient naverWebClient,
            @Qualifier("yahooWebClient") WebClient yahooWebClient,
            ObjectMapper objectMapper) {
        this.naverWebClient = naverWebClient;
        this.yahooWebClient = yahooWebClient;
        this.objectMapper = objectMapper;
    }

    @Value("${naver.stock-basic-url}")
    private String stockBasicUrl;

    @Value("${naver.etf-basic-url}")
    private String etfBasicUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile(
        "<tr[^>]*>.*?<td[^>]*class=[\"']no[\"'][^>]*>\\s*\\d+\\s*</td>\\s*<td>\\s*<a[^>]*?/item/main\\.naver\\?code=([^\"'&>]+)[^>]*>(.*?)</a>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern ETF_NAME_PATTERN = Pattern.compile(
        "(KODEX|TIGER|KOSEF|ARIRANG|ACE|RISE|SOL|HANARO|KBSTAR|ETF|ETN|INVERS|LEVERAGE|FUTURE|S&P|NASDAQ|TOP10)",
        Pattern.CASE_INSENSITIVE
    );

    /** siseJson 응답 행 패턴: ["20230102", 55500, 56100, 55200, 55500, 10031448, 49.67] */
    private static final Pattern ROW_PATTERN = Pattern.compile(
        "\\[\"(\\d{8})\"\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)\\s*,\\s*([\\d.]+)"
    );

    /** 해외 종목 여부 판단 (국내 종목: 6자리 숫자) */
    private boolean isOverseasSymbol(String symbol) {
        return symbol != null && !symbol.matches("^[0-9]{6}$");
    }

    @Override
    @Cacheable(value = "stockCandle", key = "#p0")
    public List<CandleDto> getDailyCandles(String symbol) {
        if (isOverseasSymbol(symbol)) {
            log.info("해외 종목 일봉 조회 (Yahoo Finance): symbol={}", symbol);
            return fetchYahooDailyCandles(symbol);
        }
        log.info("일봉 캔들 데이터 조회 시작: symbol={}", symbol);

        String endTime = LocalDate.now().format(DATE_FMT);
        String startTime = LocalDate.now().minusYears(3).format(DATE_FMT);

        try {
            String rawResponse = naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("fchart.stock.naver.com")
                    .path("/siseJson.nhn")
                    .queryParam("symbol", symbol)
                    .queryParam("requestType", "1")
                    .queryParam("startTime", startTime)
                    .queryParam("endTime", endTime)
                    .queryParam("timeframe", "day")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("일봉 캔들 응답이 비어 있습니다: symbol={}", symbol);
                return Collections.emptyList();
            }

            List<CandleDto> candles = parseSiseJsonResponse(rawResponse);

            candles.sort((a, b) -> a.getDate().compareTo(b.getDate()));
            log.info("일봉 캔들 데이터 조회 완료: symbol={}, count={}", symbol, candles.size());

            return candles;

        } catch (WebClientResponseException e) {
            log.error("일봉 캔들 조회 HTTP 오류 [HTTP {}] symbol={}: {}",
                e.getStatusCode(), symbol, e.getMessage());
            throw new StockApiException(
                "일봉 캔들 데이터 조회 실패: " + e.getMessage(), e.getStatusCode().value(), e);
        } catch (StockApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("일봉 캔들 조회 중 예외 발생: symbol={}: {}", symbol, e.getMessage(), e);
            throw new StockApiException("일봉 캔들 데이터 조회 오류: " + e.getMessage(), e);
        }
    }

    @Override
    @Cacheable(value = "stockMinuteCandle", key = "#p0 + '_' + #p1")
    public List<CandleDto> getMinuteCandles(String symbol, int intervalMinutes) {
        if (isOverseasSymbol(symbol)) {
            log.info("해외 종목 분봉 조회 (Yahoo Finance): symbol={}, interval={}분", symbol, intervalMinutes);
            List<CandleDto> raw = fetchYahooMinuteCandles1M(symbol);
            if (intervalMinutes <= 1) return raw;
            return aggregateMinuteCandles(raw, intervalMinutes);
        }
        log.info("분봉 캔들 데이터 조회 시작: symbol={}, interval={}분", symbol, intervalMinutes);

        // 최근 5일치 데이터를 가져오기 위해 startDateTime 설정
        String startDateTime = LocalDate.now().minusDays(5).format(DATE_FMT) + "090000";

        try {
            String rawResponse = naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("api.stock.naver.com")
                    .path("/chart/domestic/item/{symbol}/minute")
                    .queryParam("periodType", intervalMinutes)
                    .queryParam("startDateTime", startDateTime)
                    .build(symbol))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (rawResponse == null || rawResponse.isBlank()) {
                log.warn("분봉 캔들 응답이 비어 있습니다: symbol={}", symbol);
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(rawResponse);
            if (!root.isArray()) {
                return Collections.emptyList();
            }

            // 1분봉 원본 데이터 파싱
            List<CandleDto> rawCandles = new ArrayList<>();
            for (JsonNode node : root) {
                String dateTime = node.path("localDateTime").asText("");
                if (dateTime.length() < 12) continue;
                rawCandles.add(CandleDto.builder()
                    .date(dateTime) // yyyyMMddHHmmss 형식 유지
                    .open(node.path("openPrice").asDouble())
                    .high(node.path("highPrice").asDouble())
                    .low(node.path("lowPrice").asDouble())
                    .close(node.path("currentPrice").asDouble())
                    .volume(node.path("accumulatedTradingVolume").asLong())
                    .build());
            }

            // 1분봉이면 그대로 반환, 아니면 집계
            List<CandleDto> result;
            if (intervalMinutes <= 1) {
                result = rawCandles;
            } else {
                result = aggregateMinuteCandles(rawCandles, intervalMinutes);
            }

            log.info("분봉 캔들 데이터 조회 완료: symbol={}, interval={}분, count={}",
                symbol, intervalMinutes, result.size());
            return result;

        } catch (WebClientResponseException e) {
            log.error("분봉 캔들 조회 HTTP 오류 [HTTP {}] symbol={}: {}",
                e.getStatusCode(), symbol, e.getMessage());
            throw new StockApiException(
                "분봉 캔들 데이터 조회 실패: " + e.getMessage(), e.getStatusCode().value(), e);
        } catch (Exception e) {
            log.error("분봉 캔들 조회 중 예외 발생: symbol={}: {}", symbol, e.getMessage(), e);
            throw new StockApiException("분봉 캔들 데이터 조회 오류: " + e.getMessage(), e);
        }
    }

    /** 1분봉을 N분봉으로 집계 */
    private List<CandleDto> aggregateMinuteCandles(List<CandleDto> oneMinCandles, int intervalMinutes) {
        if (oneMinCandles.isEmpty()) return Collections.emptyList();

        List<CandleDto> result = new ArrayList<>();
        int i = 0;
        while (i < oneMinCandles.size()) {
            CandleDto first = oneMinCandles.get(i);
            String baseDate = first.getDate().substring(0, 8); // yyyyMMdd
            int baseHour = Integer.parseInt(first.getDate().substring(8, 10));
            int baseMin = Integer.parseInt(first.getDate().substring(10, 12));
            int slotStart = (baseMin / intervalMinutes) * intervalMinutes;

            double open = first.getOpen();
            double high = first.getHigh();
            double low = first.getLow();
            double close = first.getClose();
            long volume = first.getVolume();
            String slotDate = String.format("%s%02d%02d00", baseDate, baseHour, slotStart);

            i++;
            while (i < oneMinCandles.size()) {
                CandleDto c = oneMinCandles.get(i);
                String cDate = c.getDate().substring(0, 8);
                int cHour = Integer.parseInt(c.getDate().substring(8, 10));
                int cMin = Integer.parseInt(c.getDate().substring(10, 12));
                int cSlotStart = (cMin / intervalMinutes) * intervalMinutes;

                if (!cDate.equals(baseDate) || cHour != baseHour || cSlotStart != slotStart) {
                    break;
                }
                high = Math.max(high, c.getHigh());
                low = Math.min(low, c.getLow());
                close = c.getClose();
                volume += c.getVolume();
                i++;
            }

            result.add(CandleDto.builder()
                .date(slotDate)
                .open(open).high(high).low(low).close(close)
                .volume(volume)
                .build());
        }
        return result;
    }

    @Override
    @Cacheable(value = "stockPrice", key = "#p0")
    public StockPriceDto getCurrentPrice(String symbol) {
        log.info("현재가 정보 조회 시작: symbol={}", symbol);

        if (isOverseasSymbol(symbol)) {
            log.info("해외 종목 현재가 조회 (Yahoo Finance): symbol={}", symbol);
            StockPriceDto price = fetchYahooCurrentPrice(symbol);
            if (price != null) return price;
            throw new StockApiException("해외 종목 현재가 조회 실패: " + symbol, 404);
        }

        try {
            // 주식(stock) API 시도
            StockPriceDto price = fetchBasicInfo(symbol, stockBasicUrl + "/" + symbol + "/basic");
            if (price != null) return ensureVolume(symbol, price);
        } catch (Exception e) {
            log.debug("주식 API 실패, ETF API 시도: symbol={}", symbol);
        }

        try {
            // ETF API 시도
            StockPriceDto price = fetchBasicInfo(symbol, etfBasicUrl + "/" + symbol + "/basic");
            if (price != null) return ensureVolume(symbol, price);
        } catch (Exception e) {
            log.debug("ETF API도 실패: symbol={}", symbol);
        }

        return fallbackPriceFromCandles(symbol);
    }

    @Override
    @Cacheable(value = "stockSearch", key = "#p0", condition = "#p0 != null && !#p0.isBlank()")
    public List<StockSearchDto> searchStock(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }

        log.info("종목 검색 요청: keyword={}", keyword);

        try {
            String response = naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("ac.stock.naver.com")
                    .path("/ac")
                    .queryParam("q", keyword)
                    .queryParam("target", "stock,etf")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) {
                return Collections.emptyList();
            }

            return parseSearchResponse(response);

        } catch (Exception e) {
            log.error("검색 API 호출 실패: keyword={}, {}", keyword, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    @Cacheable(value = "topRanking", key = "#p0 + ':' + #p1")
    public List<StockSearchDto> getTopByVolume(String type, int limit) {
        String normalizedType = "etf".equalsIgnoreCase(type) ? "etf" : "stock";
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        int candidateSize = normalizedType.equals("etf") ? 50 : 30;

        List<StockSearchDto> candidates = fetchVolumeCandidates(normalizedType, candidateSize);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        return candidates.stream()
            .limit(safeLimit)
            .toList();
    }

    @Override
    @Cacheable(value = "usPopular", key = "#p0 + ':' + #p1")
    public List<StockSearchDto> getUsPopular(String type, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        boolean isEtf = "us_etf".equalsIgnoreCase(type);

        List<String[]> list = isEtf
            ? List.of(
                new String[]{"SPY",  "SPDR S&P 500 ETF Trust"},
                new String[]{"QQQ",  "Invesco QQQ Trust"},
                new String[]{"TQQQ", "ProShares UltraPro QQQ"},
                new String[]{"SQQQ", "ProShares UltraPro Short QQQ"},
                new String[]{"SOXL", "Direxion Daily Semiconductor Bull 3X"},
                new String[]{"SOXS", "Direxion Daily Semiconductor Bear 3X"},
                new String[]{"IWM",  "iShares Russell 2000 ETF"},
                new String[]{"GLD",  "SPDR Gold Shares"},
                new String[]{"TLT",  "iShares 20+ Year Treasury Bond ETF"},
                new String[]{"VTI",  "Vanguard Total Stock Market ETF"}
            )
            : List.of(
                new String[]{"NVDA",  "NVIDIA Corporation"},
                new String[]{"TSLA",  "Tesla Inc"},
                new String[]{"AAPL",  "Apple Inc"},
                new String[]{"AMZN",  "Amazon.com Inc"},
                new String[]{"AMD",   "Advanced Micro Devices"},
                new String[]{"META",  "Meta Platforms Inc"},
                new String[]{"MSFT",  "Microsoft Corporation"},
                new String[]{"PLTR",  "Palantir Technologies"},
                new String[]{"GOOGL", "Alphabet Inc"},
                new String[]{"NFLX",  "Netflix Inc"}
            );

        String normalizedType = isEtf ? "etf" : "stock";
        return list.stream()
            .limit(safeLimit)
            .map(arr -> StockSearchDto.builder()
                .symbol(arr[0])
                .name(arr[1])
                .market("NASDAQ")
                .type(normalizedType)
                .build())
            .toList();
    }

    @Override
    @Cacheable(value = "marketIndicators", key = "'all'")
    public List<MarketIndicatorDto> getMarketIndicators() {
        log.info("주요 시장 지표 조회 시작");

        List<MarketIndicatorDto> result = new ArrayList<>();
        result.add(fetchDomesticIndexIndicator("KOSPI", "KOSPI", "코스피"));
        result.add(fetchDomesticIndexIndicator("KOSDAQ", "KOSDAQ", "코스닥"));
        result.add(fetchNaverMarketIndexIndicator("exchange", "FX_USDKRW", "USDKRW", "환율 (USD/KRW)"));
        result.add(fetchNaverMarketIndexIndicator("energy", "CLcv1", "WTI", "WTI 유가"));
        result.add(fetchNaverForeignIndexIndicator(".INX", "SP500", "S&P 500", "^GSPC"));
        result.add(fetchNaverForeignIndexIndicator(".IXIC", "NASDAQ", "나스닥", "^IXIC"));
        result.add(fetchNaverForeignIndexIndicator(".DJI", "DJI", "다우지수", "^DJI"));

        return result.stream().filter(Objects::nonNull).toList();
    }

    /** 국내 지수 (코스피/코스닥) 지표 조회 */
    private MarketIndicatorDto fetchDomesticIndexIndicator(String code, String id, String name) {
        try {
            String response = naverWebClient.get()
                .uri("https://m.stock.naver.com/api/index/" + code + "/basic")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) {
                return buildEmptyIndicator(id, name);
            }

            JsonNode root = objectMapper.readTree(response);
            double price = parseDouble(getTextOrDefault(root, "closePrice", "0").replace(",", ""));
            double change = parseDouble(getTextOrDefault(root, "compareToPreviousClosePrice", "0").replace(",", ""));
            double changeRate = parseDouble(getTextOrDefault(root, "fluctuationsRatio", "0"));

            JsonNode compareNode = root.get("compareToPreviousPrice");
            if (compareNode != null) {
                String dirCode = getTextOrDefault(compareNode, "code", "3");
                // code: 1=상한가(UPPER_LIMIT), 2=상승(RISING), 3=보합, 4=하한가(LOWER_LIMIT), 5=하락(FALLING)
                if ("4".equals(dirCode) || "5".equals(dirCode)) {
                    change = -Math.abs(change);
                    changeRate = -Math.abs(changeRate);
                } else {
                    change = Math.abs(change);
                    changeRate = Math.abs(changeRate);
                }
            }

            List<Double> history = fetchDomesticHistory(code);

            return MarketIndicatorDto.builder()
                .id(id).name(name)
                .currentValue(price).change(change).changeRate(changeRate)
                .history(history)
                .build();

        } catch (Exception e) {
            log.warn("국내 지수 지표 조회 실패: code={}, error={}", code, e.getMessage());
            return buildEmptyIndicator(id, name);
        }
    }

    /** 해외 지수 (S&P 500: .INX, 나스닥: .IXIC, 다우: .DJI) 지표 조회 + Yahoo Finance 히스토리 */
    private MarketIndicatorDto fetchNaverForeignIndexIndicator(String symbol, String id, String name, String yahooSymbol) {
        try {
            String response = naverWebClient.get()
                .uri("https://api.stock.naver.com/index/" + symbol + "/basic")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) {
                return buildEmptyIndicator(id, name);
            }

            JsonNode root = objectMapper.readTree(response);
            double price = parseDouble(getTextOrDefault(root, "closePrice", "0").replace(",", ""));
            double change = parseDouble(getTextOrDefault(root, "compareToPreviousClosePrice", "0").replace(",", ""));
            double changeRate = parseDouble(getTextOrDefault(root, "fluctuationsRatio", "0"));

            JsonNode compareNode = root.get("compareToPreviousPrice");
            if (compareNode != null) {
                String dirCode = getTextOrDefault(compareNode, "code", "3");
                if ("4".equals(dirCode) || "5".equals(dirCode)) {
                    change = -Math.abs(change);
                    changeRate = -Math.abs(changeRate);
                } else {
                    change = Math.abs(change);
                    changeRate = Math.abs(changeRate);
                }
            }

            List<Double> history = fetchYahooIndexHistory(yahooSymbol);

            return MarketIndicatorDto.builder()
                .id(id).name(name)
                .currentValue(price).change(change).changeRate(changeRate)
                .history(history)
                .build();

        } catch (Exception e) {
            log.warn("해외 지수 지표 조회 실패: symbol={}, error={}", symbol, e.getMessage());
            return buildEmptyIndicator(id, name);
        }
    }

    /** Yahoo Finance 지수 히스토리 조회 (스파크라인용, 30일치) */
    private List<Double> fetchYahooIndexHistory(String yahooSymbol) {
        try {
            String response = yahooWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("query1.finance.yahoo.com")
                    .path("/v8/finance/chart/{symbol}")
                    .queryParam("interval", "1d")
                    .queryParam("range", "2mo")
                    .build(yahooSymbol))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return Collections.emptyList();

            JsonNode closes = result.get(0).path("indicators").path("quote").get(0).path("close");
            if (!closes.isArray()) return Collections.emptyList();

            List<Double> history = new ArrayList<>();
            for (JsonNode node : closes) {
                if (!node.isNull()) history.add(node.asDouble());
            }
            return history;

        } catch (Exception e) {
            log.debug("Yahoo 지수 히스토리 조회 실패: symbol={}, error={}", yahooSymbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 네이버 증권 front-api를 통한 환율/원자재 지표 조회 */
    private MarketIndicatorDto fetchNaverMarketIndexIndicator(String category, String reutersCode, String id, String name) {
        try {
            String response = naverWebClient.get()
                .uri("https://m.stock.naver.com/front-api/marketIndex/productDetail?category=" + category + "&reutersCode=" + reutersCode)
                .header("Referer", "https://m.stock.naver.com/")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) {
                return buildEmptyIndicator(id, name);
            }

            JsonNode root = objectMapper.readTree(response);
            if (!root.path("isSuccess").asBoolean(false)) {
                return buildEmptyIndicator(id, name);
            }

            JsonNode result = root.path("result");
            String closePriceStr = result.path("closePrice").asText("0").replace(",", "");
            double price = parseDouble(closePriceStr);
            double change = result.path("fluctuations").asDouble(0);
            double changeRate = result.path("fluctuationsRatio").asDouble(0);
            List<Double> history = fetchNaverMarketIndexHistory(category, reutersCode);

            return MarketIndicatorDto.builder()
                .id(id).name(name)
                .currentValue(price).change(change).changeRate(changeRate)
                .history(history)
                .build();

        } catch (Exception e) {
            log.warn("네이버 시장 지표 조회 실패: category={}, reutersCode={}, error={}", category, reutersCode, e.getMessage());
            return buildEmptyIndicator(id, name);
        }
    }

    /** 환율/원자재 히스토리 조회 (api.stock.naver.com/marketindex/{category}/{code}/prices) */
    private List<Double> fetchNaverMarketIndexHistory(String category, String reutersCode) {
        try {
            String response = naverWebClient.get()
                .uri("https://api.stock.naver.com/marketindex/" + category + "/" + reutersCode + "/prices?period=1M")
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) return Collections.emptyList();

            JsonNode arr = objectMapper.readTree(response);
            if (!arr.isArray()) return Collections.emptyList();

            List<Double> history = new ArrayList<>();
            for (JsonNode node : arr) {
                String cp = node.path("closePrice").asText("0").replace(",", "");
                double v = parseDouble(cp);
                if (v > 0) history.add(v);
            }
            Collections.reverse(history); // 최신→과거 → 과거→최신 순으로
            return history;

        } catch (Exception e) {
            log.debug("시장 지표 히스토리 조회 실패: category={}, reutersCode={}", category, reutersCode);
            return Collections.emptyList();
        }
    }

    /** 국내 지수 히스토리 조회 — fchart siseJson 직접 호출 (getDailyCandles는 isOverseasSymbol 라우팅이 있어 사용 불가) */
    private List<Double> fetchDomesticHistory(String code) {
        try {
            String endTime = LocalDate.now().format(DATE_FMT);
            String startTime = LocalDate.now().minusMonths(2).format(DATE_FMT);

            String raw = naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("fchart.stock.naver.com")
                    .path("/siseJson.nhn")
                    .queryParam("symbol", code)
                    .queryParam("requestType", "1")
                    .queryParam("startTime", startTime)
                    .queryParam("endTime", endTime)
                    .queryParam("timeframe", "day")
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (raw == null || raw.isBlank()) return Collections.emptyList();

            List<CandleDto> candles = parseSiseJsonResponse(raw);
            candles.sort((a, b) -> a.getDate().compareTo(b.getDate()));
            int from = Math.max(0, candles.size() - 30);
            return candles.subList(from, candles.size()).stream()
                .map(CandleDto::getClose)
                .toList();
        } catch (Exception e) {
            log.debug("국내 지수 히스토리 조회 실패: code={}, error={}", code, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** 빈 지표 DTO 생성 (API 실패 시 fallback) */
    private MarketIndicatorDto buildEmptyIndicator(String id, String name) {
        return MarketIndicatorDto.builder()
            .id(id).name(name)
            .currentValue(0).change(0).changeRate(0)
            .history(Collections.emptyList())
            .build();
    }

    /** siseJson 응답 문자열을 파싱하여 캔들 목록으로 변환 */
    private List<CandleDto> parseSiseJsonResponse(String raw) {
        List<CandleDto> candles = new ArrayList<>();
        Matcher matcher = ROW_PATTERN.matcher(raw);

        while (matcher.find()) {
            try {
                candles.add(CandleDto.builder()
                    .date(matcher.group(1))
                    .open(parseDouble(matcher.group(2)))
                    .high(parseDouble(matcher.group(3)))
                    .low(parseDouble(matcher.group(4)))
                    .close(parseDouble(matcher.group(5)))
                    .volume(parseLong(matcher.group(6)))
                    .build());
            } catch (NumberFormatException e) {
                log.debug("캔들 행 파싱 건너뜀: {}", e.getMessage());
            }
        }

        return candles;
    }

    /** 네이버 주식/ETF 기본 API에서 현재가 정보를 추출하는 메서드 */
    private StockPriceDto fetchBasicInfo(String symbol, String url) {
        String response = naverWebClient.get()
            .uri(url)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        if (response == null || response.isBlank()) return null;

        try {
            JsonNode root = objectMapper.readTree(response);

            String name = getTextOrDefault(root, "stockName", symbol);
            long closePrice = parseFormattedNumber(getTextOrDefault(root, "closePrice", "0"));
            long changeAmount = parseFormattedNumber(getTextOrDefault(root, "compareToPreviousClosePrice", "0"));
            double changeRate = parseDouble(getTextOrDefault(root, "fluctuationsRatio", "0"));

            JsonNode compareNode = root.get("compareToPreviousPrice");
            if (compareNode != null) {
                String code = getTextOrDefault(compareNode, "code", "3");
                // code: 1=상한가(UPPER_LIMIT), 2=상승(RISING), 3=보합, 4=하한가(LOWER_LIMIT), 5=하락(FALLING)
                if ("4".equals(code) || "5".equals(code)) {
                    changeAmount = -Math.abs(changeAmount);
                    changeRate = -Math.abs(changeRate);
                } else {
                    changeAmount = Math.abs(changeAmount);
                    changeRate = Math.abs(changeRate);
                }
            }

            long highPrice = parseFormattedNumber(getTextOrDefault(root, "highPrice", "0"));
            long lowPrice = parseFormattedNumber(getTextOrDefault(root, "lowPrice", "0"));
            long openPrice = parseFormattedNumber(getTextOrDefault(root, "openPrice", "0"));
            long volume = parseFormattedNumber(getTextOrDefault(root, "accumulatedTradingVolume", "0"));
            if (volume <= 0) {
                volume = parseFormattedNumber(getTextOrDefault(root, "tradingVolume", "0"));
            }
            if (volume <= 0) {
                volume = parseFormattedNumber(getTextOrDefault(root, "volume", "0"));
            }

            return StockPriceDto.builder()
                .symbol(symbol)
                .name(name)
                .currentPrice(closePrice)
                .priceChange(changeAmount)
                .changeRate(changeRate)
                .high(highPrice)
                .low(lowPrice)
                .open(openPrice)
                .volume(volume)
                .build();

        } catch (Exception e) {
            log.debug("현재가 JSON 파싱 실패: symbol={}, {}", symbol, e.getMessage());
            return null;
        }
    }

    /** 기본 API 실패 시 캔들 데이터에서 현재가를 추출하는 fallback 메서드 */
    private StockPriceDto fallbackPriceFromCandles(String symbol) {
        try {
            List<CandleDto> candles = getDailyCandles(symbol);
            if (candles.isEmpty()) {
                throw new StockApiException("종목 데이터를 찾을 수 없습니다: " + symbol, 404);
            }

            CandleDto latest = candles.get(candles.size() - 1);
            CandleDto prev = candles.size() > 1 ? candles.get(candles.size() - 2) : latest;

            long change = (long) (latest.getClose() - prev.getClose());
            double rate = prev.getClose() != 0
                ? Math.round((change / prev.getClose()) * 10000.0) / 100.0
                : 0.0;

            return StockPriceDto.builder()
                .symbol(symbol)
                .name(symbol)
                .currentPrice((long) latest.getClose())
                .priceChange(change)
                .changeRate(rate)
                .high((long) latest.getHigh())
                .low((long) latest.getLow())
                .open((long) latest.getOpen())
                .volume(latest.getVolume())
                .build();

        } catch (StockApiException e) {
            throw e;
        } catch (Exception e) {
            throw new StockApiException("현재가 조회 실패: " + symbol, e);
        }
    }

    /** 네이버 자동완성 API 응답 파싱 (중첩 배열 구조 처리) */
    private List<StockSearchDto> parseSearchResponse(String response) {
        List<StockSearchDto> results = new ArrayList<>();

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.get("items");

            if (items == null || !items.isArray()) {
                return results;
            }

            for (JsonNode categoryGroup : items) {
                if (categoryGroup.isArray()) {
                    for (JsonNode item : categoryGroup) {
                        parseAndAddSearchItem(item, results);
                    }
                } else if (categoryGroup.isObject()) {
                    parseAndAddSearchItem(categoryGroup, results);
                }
            }

        } catch (Exception e) {
            log.error("검색 응답 파싱 실패: {}", e.getMessage());
        }

        return results;
    }

    private void parseAndAddSearchItem(JsonNode item, List<StockSearchDto> results) {
        String code = getTextOrDefault(item, "code", "");
        String name = getTextOrDefault(item, "name", "");
        String typeName = getTextOrDefault(item, "typeName", "");
        String typeCode = getTextOrDefault(item, "typeCode", "");
        String category = getTextOrDefault(item, "category", "stock");

        if (code.isEmpty() || name.isEmpty()) return;

        boolean isEtf = "etf".equalsIgnoreCase(category)
            || "ETF".equalsIgnoreCase(typeCode)
            || "ET".equalsIgnoreCase(typeCode);
        String type = isEtf ? "etf" : "stock";

        results.add(StockSearchDto.builder()
            .symbol(code)
            .name(name)
            .market(typeName)
            .type(type)
            .build());
    }

    /** 거래량 상위 종목 후보를 네이버 금융 페이지에서 스크래핑 */
    private List<StockSearchDto> fetchVolumeCandidates(String type, int candidateSize) {
        String path = "etf".equals(type) ? "/sise/etf.naver" : "/sise/sise_quant.naver";
        try {
            byte[] responseBytes = naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("finance.naver.com")
                    .path(path)
                    .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
            String response = decodeEucKr(responseBytes);

            int linkCount = countMatches(response, "/item/main.naver?code=");
            log.debug("거래량 상위 원본 수신: type={}, path={}, len={}, links={}", type, path,
                response == null ? 0 : response.length(), linkCount);
            List<StockSearchDto> parsed = parseTopRows(response, type, candidateSize);
            log.debug("거래량 상위 파싱 결과: type={}, count={}", type, parsed.size());
            if (!parsed.isEmpty()) {
                return parsed;
            }
            if ("etf".equals(type)) {
                return fetchVolumeCandidatesFromQuant(type, candidateSize);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("거래량 상위 목록 조회 실패: type={}, error={}", type, e.getMessage());
            if ("etf".equals(type)) {
                return fetchVolumeCandidatesFromQuant(type, candidateSize);
            }
            return Collections.emptyList();
        }
    }

    /** ETF 전용 fallback: 거래량 상위(quant) 페이지에서 후보 조회 */
    private List<StockSearchDto> fetchVolumeCandidatesFromQuant(String type, int candidateSize) {
        try {
            byte[] responseBytes = naverWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("finance.naver.com")
                    .path("/sise/sise_quant.naver")
                    .build())
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
            String response = decodeEucKr(responseBytes);
            return parseTopRows(response, type, candidateSize);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /** HTML 테이블 행에서 종목 코드와 이름을 추출 */
    private List<StockSearchDto> parseTopRows(String response, String type, int candidateSize) {
        if (response == null || response.isBlank()) {
            return Collections.emptyList();
        }

        Map<String, StockSearchDto> deduped = new LinkedHashMap<>();
        Matcher rowMatcher = TABLE_ROW_PATTERN.matcher(response);
        while (rowMatcher.find()) {
            String symbol = rowMatcher.group(1);
            String name = stripHtml(rowMatcher.group(2));
            if (symbol == null || symbol.isBlank() || name == null || name.isBlank()) {
                continue;
            }

            boolean etfLike = ETF_NAME_PATTERN.matcher(name).find();
            if ("etf".equals(type) && !etfLike) {
                continue;
            }
            if ("stock".equals(type) && etfLike) {
                continue;
            }

            deduped.putIfAbsent(symbol, StockSearchDto.builder()
                .symbol(symbol)
                .name(name)
                .market("")
                .type(type)
                .build());

            if (deduped.size() >= candidateSize) {
                break;
            }
        }

        return new ArrayList<>(deduped.values());
    }

    /** 거래량이 0인 경우 캔들 데이터에서 보충 */
    private StockPriceDto ensureVolume(String symbol, StockPriceDto price) {
        if (price == null || price.getVolume() > 0) {
            return price;
        }
        try {
            List<CandleDto> candles = getDailyCandles(symbol);
            if (!candles.isEmpty()) {
                CandleDto latest = candles.get(candles.size() - 1);
                price.setVolume(latest.getVolume());
            }
        } catch (Exception e) {
            log.debug("거래량 보충 실패: symbol={}, {}", symbol, e.getMessage());
        }
        return price;
    }

    /** HTML 태그를 제거하고 텍스트만 추출 */
    private String stripHtml(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replaceAll("\\s+", " ")
            .trim();
    }

    /** 문자열 내 특정 토큰의 출현 횟수를 반환 */
    private int countMatches(String text, String token) {
        if (text == null || text.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    /** 응답 바이트를 UTF-8 또는 EUC-KR로 디코딩 */
    private String decodeEucKr(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        if (utf8.contains("거래상위") || utf8.contains("종목") || utf8.contains("코스")) {
            return utf8;
        }
        return new String(bytes, Charset.forName("EUC-KR"));
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        if (node == null) return defaultValue;
        JsonNode child = node.get(field);
        return (child != null && !child.isNull()) ? child.asText() : defaultValue;
    }

    private long parseFormattedNumber(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value.replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(value.replace(",", "").replace(" ", "").trim());
            } catch (NumberFormatException e2) {
                return 0L;
            }
        }
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) return 0.0;
        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return (long) Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // ── Yahoo Finance 해외 종목 조회 ──────────────────────────────────────

    /** Yahoo Finance에서 해외 종목 현재가 조회 */
    private StockPriceDto fetchYahooCurrentPrice(String symbol) {
        try {
            String response = yahooWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("query1.finance.yahoo.com")
                    .path("/v8/finance/chart/{symbol}")
                    .queryParam("interval", "1d")
                    .queryParam("range", "1d")
                    .build(symbol))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            if (response == null || response.isBlank()) return null;

            JsonNode root = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return null;

            JsonNode resultNode = result.get(0);
            JsonNode meta = resultNode.path("meta");
            double price    = meta.path("regularMarketPrice").asDouble(0);
            double change   = meta.path("regularMarketChange").asDouble(0);
            double rate     = meta.path("regularMarketChangePercent").asDouble(0);
            long   volume   = meta.path("regularMarketVolume").asLong(0);

            // meta의 Day 필드는 장 마감 후 모두 동일한 값으로 반환되는 문제가 있음
            // indicators.quote (실제 캔들 데이터)에서 OHLCV 추출해 우선 사용
            double high = meta.path("regularMarketDayHigh").asDouble(0);
            double low  = meta.path("regularMarketDayLow").asDouble(0);
            double open = meta.path("regularMarketOpen").asDouble(0);

            JsonNode quoteArr = resultNode.path("indicators").path("quote");
            if (!quoteArr.isEmpty()) {
                JsonNode quote = quoteArr.get(0);
                double candleHigh = lastValidDouble(quote.path("high"));
                double candleLow  = lastValidDouble(quote.path("low"));
                double candleOpen = firstValidDouble(quote.path("open"));
                if (candleHigh > 0) high = candleHigh;
                if (candleLow  > 0) low  = candleLow;
                if (candleOpen > 0) open = candleOpen;
            }

            // 장 외 시간에 change/rate가 0으로 오는 경우 previousClose로 직접 계산
            if ((change == 0 || rate == 0) && price > 0) {
                double prevClose = meta.path("chartPreviousClose").asDouble(0);
                if (prevClose <= 0) prevClose = meta.path("previousClose").asDouble(0);
                if (prevClose > 0) {
                    change = price - prevClose;
                    rate   = (change / prevClose) * 100.0;
                }
            }

            String name = meta.path("longName").asText("");
            if (name.isBlank()) name = meta.path("shortName").asText(symbol);

            return StockPriceDto.builder()
                .symbol(symbol)
                .name(name)
                .currentPrice(Math.round(price * 100))
                .priceChange(Math.round(change * 100))
                .changeRate(Math.round(rate * 100.0) / 100.0)
                .high(Math.round(high * 100))
                .low(Math.round(low * 100))
                .open(Math.round(open * 100))
                .volume(volume)
                .currency("USD")
                .build();

        } catch (Exception e) {
            log.error("Yahoo 현재가 조회 실패: symbol={}, {}", symbol, e.getMessage());
            return null;
        }
    }

    /** JsonNode 배열에서 마지막으로 유효한(non-null, non-NaN) double 값 반환 */
    private double lastValidDouble(JsonNode arr) {
        if (arr == null || !arr.isArray()) return 0;
        double result = 0;
        for (JsonNode node : arr) {
            if (!node.isNull() && node.isNumber()) {
                double v = node.asDouble();
                if (!Double.isNaN(v) && v > 0) result = v;
            }
        }
        return result;
    }

    /** JsonNode 배열에서 첫 번째로 유효한(non-null, non-NaN) double 값 반환 */
    private double firstValidDouble(JsonNode arr) {
        if (arr == null || !arr.isArray()) return 0;
        for (JsonNode node : arr) {
            if (!node.isNull() && node.isNumber()) {
                double v = node.asDouble();
                if (!Double.isNaN(v) && v > 0) return v;
            }
        }
        return 0;
    }

    /** Yahoo Finance에서 해외 종목 일봉 조회 */
    private List<CandleDto> fetchYahooDailyCandles(String symbol) {
        try {
            String response = yahooWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("query1.finance.yahoo.com")
                    .path("/v8/finance/chart/{symbol}")
                    .queryParam("interval", "1d")
                    .queryParam("range", "3y")
                    .build(symbol))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseYahooCandleResponse(response, symbol, false);
        } catch (Exception e) {
            log.error("Yahoo 일봉 조회 실패: symbol={}, {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Yahoo Finance에서 해외 종목 1분봉 조회 (집계 전 원본) */
    private List<CandleDto> fetchYahooMinuteCandles1M(String symbol) {
        try {
            String response = yahooWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .scheme("https")
                    .host("query1.finance.yahoo.com")
                    .path("/v8/finance/chart/{symbol}")
                    .queryParam("interval", "1m")
                    .queryParam("range", "5d")
                    .build(symbol))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseYahooCandleResponse(response, symbol, true);
        } catch (Exception e) {
            log.error("Yahoo 1분봉 조회 실패: symbol={}, {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Yahoo Finance 캔들 응답 파싱 */
    private List<CandleDto> parseYahooCandleResponse(String response, String symbol, boolean isMinute) {
        if (response == null || response.isBlank()) return Collections.emptyList();

        try {
            JsonNode root   = objectMapper.readTree(response);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return Collections.emptyList();

            JsonNode item       = result.get(0);
            JsonNode timestamps = item.path("timestamp");
            JsonNode quoteArr   = item.path("indicators").path("quote");
            if (!timestamps.isArray() || quoteArr.isEmpty()) return Collections.emptyList();

            JsonNode quote   = quoteArr.get(0);
            JsonNode opens   = quote.path("open");
            JsonNode highs   = quote.path("high");
            JsonNode lows    = quote.path("low");
            JsonNode closes  = quote.path("close");
            JsonNode volumes = quote.path("volume");

            // 미국 장 시간대 (ET)
            ZoneId et = ZoneId.of("America/New_York");
            DateTimeFormatter dtf = isMinute
                ? DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                : DateTimeFormatter.ofPattern("yyyyMMdd");

            List<CandleDto> candles = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                double close = closes.get(i).asDouble(Double.NaN);
                if (Double.isNaN(close) || close <= 0) continue;

                long ts = timestamps.get(i).asLong();
                ZonedDateTime zdt = Instant.ofEpochSecond(ts).atZone(et);
                String date = zdt.format(dtf);

                candles.add(CandleDto.builder()
                    .date(date)
                    .open(opens.get(i).asDouble(close))
                    .high(highs.get(i).asDouble(close))
                    .low(lows.get(i).asDouble(close))
                    .close(close)
                    .volume(volumes.get(i).asLong(0))
                    .build());
            }

            candles.sort((a, b) -> a.getDate().compareTo(b.getDate()));
            log.info("Yahoo 캔들 파싱 완료: symbol={}, isMinute={}, count={}", symbol, isMinute, candles.size());
            return candles;

        } catch (Exception e) {
            log.error("Yahoo 캔들 파싱 실패: symbol={}, {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }
}

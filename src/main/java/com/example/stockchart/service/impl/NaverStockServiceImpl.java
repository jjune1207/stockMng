package com.example.stockchart.service.impl;

import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.StockPriceDto;
import com.example.stockchart.dto.StockSearchDto;
import com.example.stockchart.exception.StockApiException;
import com.example.stockchart.service.NaverStockService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaverStockServiceImpl implements NaverStockService {

    @Qualifier("naverWebClient")
    private final WebClient naverWebClient;

    private final ObjectMapper objectMapper;

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

    @Override
    @Cacheable(value = "stockCandle", key = "#p0")
    public List<CandleDto> getDailyCandles(String symbol) {
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
    @Cacheable(value = "stockPrice", key = "#p0")
    public StockPriceDto getCurrentPrice(String symbol) {
        log.info("현재가 정보 조회 시작: symbol={}", symbol);

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
                // code: 1=하한가, 2=하락, 3=보합/변동없음, 4=상승, 5=상한가
                if ("5".equals(code) || "1".equals(code)) {
                    changeAmount = -Math.abs(changeAmount);
                    changeRate = -Math.abs(changeRate);
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
}

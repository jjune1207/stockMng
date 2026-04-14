# CLAUDE.md

## 프로젝트 개요

Java 17 + Spring Boot 3.2.5 기반 주식/ETF 분석 차트 웹앱.
DB 없이 네이버 증권 API를 활용하여 거래량 상위 종목 조회, 관심 종목 관리, 기술적 분석(MA, 볼린저밴드), 종합 매매 분석(5개 지표), 주요 시장 지표(코스피/코스닥/환율/WTI/해외지수)를 제공한다. 분봉(1/3/10분봉) 및 일봉 타임프레임 지원.

## 빌드 및 실행 명령어

```cmd
# 개발 서버 실행 (http://localhost:8080)
gradlew.bat bootRun

# 빌드
gradlew.bat build

# JAR 실행
java -jar build\libs\stock-chart-0.0.1-SNAPSHOT.jar

# 테스트
gradlew.bat test

# 테스트 보고서: build/reports/tests/test/index.html
```

## 아키텍처

### 레이어 구조

`StockApiController` → `StockDataFacade` → 서비스 인터페이스(`NaverStockService`, `WatchlistService`) → 구현체(`NaverStockServiceImpl`, `InMemoryWatchlistService`)

- **Controller**: REST API 엔드포인트(`/api/stock/**`) + Thymeleaf 뷰 라우팅
- **StockDataFacade**: 모든 서비스 호출을 하나로 묶는 퍼사드 패턴. Controller는 이 퍼사드만 의존
- **NaverStockServiceImpl**: 네이버 증권 API(시세, 검색, 거래량 랭킹, 분봉/일봉, 시장 지표)를 WebClient로 호출. 해외 종목(6자리 숫자 아닌 심볼)은 Yahoo Finance로 라우팅(`yahooWebClient` Bean 별도 분리). `INDEX_YAHOO_SYMBOLS` 맵으로 대표지수 ID(KOSPI/KOSDAQ/SP500/NASDAQ/DJI) → Yahoo 심볼(^KS11/^KQ11 등) 변환. `US_ETF_DESCRIPTIONS` 맵으로 미국 ETF 한글 설명 제공. Caffeine 캐시 적용
- **InMemoryWatchlistService**: 관심 종목을 인메모리로 관리하고 `data/watchlist.json`에 JSON 영속화. 복합키(`symbol|group`) 기반 그룹별 분류. 서버 시작 시 `type` 필드 누락 항목을 종목명 패턴으로 자동 마이그레이션(ETF 판별)
- **InMemoryNewsKeywordsService**: 뉴스 필터 키워드를 `data/news-keywords.json`에 영속화. `@PostConstruct`로 파일 로드, `NewsKeywordsService` 인터페이스를 통해 Facade에서 호출
- **IndicatorUtil**: 기술적 지표 계산 유틸리티 (MA, 볼린저밴드, RSI, MACD). Controller에서 캔들 응답에 지표를 합성할 때 직접 호출

### 프론트엔드

- Thymeleaf SSR 템플릿(`index.html`, `chart.html`) + TradingView Lightweight Charts v4
- `static/js/chart.js`: 차트 렌더링, 타임프레임 전환, 종합 매매 분석 로직
- `static/css/style.css`: 다크/라이트 테마 (토글 지원)

### 데이터 흐름

- 외부 API: 네이버 증권 (시세, 차트, 검색, 랭킹), Yahoo Finance (해외종목·시장지표·금/은), 한국 경제 RSS 6종 (구글뉴스/다음/한국경제/연합뉴스/매일경제/이데일리 — 소스당 1~2건, 최대 10건 수집 후 중복제거)
- `StockPriceDto`: `currency`(KRW/USD), `description`(미국 ETF 한글 설명, optional). 캐시: Caffeine (현재가 30초, 캔들 10분, 분봉 1분, 검색 60분, 랭킹 10분, 시장지표 5분, 뉴스 30분, 미국인기종목 30분)
- 프론트 자동 갱신: 주식/시장지표 5분, 뉴스 30분 (별도 타이머)
- 영속화: `data/watchlist.json`, `data/news-keywords.json` (`.gitignore`에 포함)

## 주요 의존성

- Spring Boot Web + WebFlux(WebClient), Thymeleaf, Caffeine Cache, Lombok
- 테스트: JUnit 5 (spring-boot-starter-test), reactor-test

## API 경로 규칙

모든 REST API는 `/api/stock` 하위. 캔들: `/{symbol}/candle?timeframe=1|3|10|day`. 시장 지표: `/market-indicators` (코스피→코스닥→S&P500→나스닥→다우→환율→WTI→금→은). 환율: `/usdkrw-rate`. 뉴스: `/news?limit=N&keywords=k1,k2` (기본값·최대 10). 뉴스 키워드: `GET /news-keywords`, `PUT /news-keywords`. 거래량 상위/미국 종목: `/top?type=stock|etf|us_stock|us_etf&limit=1~20`. 관심 종목 삭제: 복합키(`symbol|group`) 또는 symbol 단독. 대표지수(KOSPI/KOSDAQ/SP500/NASDAQ/DJI) 클릭 시 `/chart/{id}` 이동 (WTI/USDKRW는 클릭 없음).

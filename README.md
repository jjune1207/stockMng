# 주식 분석 차트 시스템

Java 17 + Spring Boot 3.2.5 기반 주식/ETF 분석 차트 웹앱.  
DB 없이 네이버 증권 API와 Yahoo Finance를 활용하여 거래량 상위 종목 조회, 관심 종목 관리, 기술적 분석, 시장 지표, 해외 종목 분석을 제공합니다.

---

## 주요 기능

- **거래량 상위 10개** 주식/ETF 목록 (10분 단위 자동 갱신)
- **종목 검색** (네이버 자동완성 API — 주식 + ETF 통합 검색)
- **관심 종목 관리** (그룹별 분류, 같은 종목 여러 그룹 중복 등록, JSON 파일 영속화)
- **그룹 관리**: 그룹 추가/삭제/이름 변경, 드래그&드롭 탭 순서 변경, 빈 그룹 유지
- **캔들스틱 차트** (TradingView Lightweight Charts v4)
- **타임프레임**: 1분봉 / 3분봉 / 10분봉 (최근 5영업일) / 일봉 (최대 3년)
- **기술적 지표**: MA(5/20/50/100/200), 볼린저 밴드
- **종합 매매 분석**: 5개 지표 종합 점수 기반 매수/매도 판정 및 코멘트
- **주요 시장 지표**: 코스피, 코스닥, 환율(USD/KRW), WTI, 해외 지수(S&P 500, 나스닥, 다우 등) 실시간 표시
- **해외 종목 지원**: Yahoo Finance 연동으로 미국 주식 시세·차트 조회, 원화/달러 가격 토글
- **다크/라이트 모드 전환**: 상단 토글 버튼으로 테마 변경 (localStorage 저장, 메인/차트 페이지 모두 지원)

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.2.5, Gradle 8.7 |
| HTTP Client | WebClient (Spring WebFlux) |
| 캐시 | Caffeine Cache (인메모리) |
| 데이터 영속화 | JSON 파일 (관심 종목: `data/watchlist.json`) |
| Frontend | Thymeleaf, TradingView Lightweight Charts v4, Bootstrap 5 |
| 데이터 소스 | 네이버 증권 API (시세, 검색, 거래량 랭킹), Yahoo Finance (해외 종목, 환율, WTI) |

---

## 사전 요구사항

- **Java 17** 이상
- **Gradle 8.x** (또는 Gradle Wrapper 사용)

```cmd
java -version
```

---

## 빌드 및 실행

### Gradle Wrapper 사용 (권장)

```cmd
gradlew.bat bootRun
```

### JAR 빌드 후 실행

```cmd
gradlew.bat build
java -jar build\libs\stock-chart-0.0.1-SNAPSHOT.jar
```

### 브라우저 접속

```
http://localhost:8080
```

---

## 화면 구성

### 메인 화면 (`/`)

- **검색창**: 종목명 또는 코드 입력 (디바운스 자동완성, 코드 입력 시 차트 직행)
- **주요 시장 지표 바**: 코스피, 코스닥, 환율(USD/KRW), WTI, 해외 주요 지수 실시간 표시
- **주요 주식 탭**: 거래량 상위 주식 10개 (현재가, 등락률, 거래량)
- **주요 ETF 탭**: 거래량 상위 ETF 10개
- **관심 종목 탭**: 사용자가 추가한 종목/ETF 목록 (그룹별 분류)
  - **그룹 필터**: 그룹 탭 클릭으로 필터링, 드래그&드롭으로 순서 변경 (localStorage 저장)
  - **그룹 관리**: 그룹 추가(인라인 입력) / 삭제 / 이름 변경
  - **중복 등록**: 같은 종목을 여러 그룹에 등록 가능 (복합키 `symbol|group`)
  - **전체 탭**: 중복 종목은 1건만 표시, 소속 그룹을 뱃지로 모두 표시
  - **마우스 그룹 선택**: 관심추가 시 드롭다운 팝업에서 그룹 선택 (키보드 입력 불필요)
  - **빈 그룹 유지**: 종목이 없어도 그룹이 삭제되지 않음 (localStorage 영속)
  - **정렬**: 이름, 현재가, 등락률, 거래량 헤더 클릭으로 정렬
- **자동 갱신**: 10분 단위 + 카운트다운 타이머 + 수동 새로고침 버튼
- 각 종목에 **차트** / **관심추가** 버튼 제공 (이미 등록된 종목도 다른 그룹에 추가 가능)

### 차트 화면 (`/chart/{종목코드}`)

- **캔들스틱 차트** + 거래량 히스토그램 (시간축 동기화)
- **타임프레임 선택**: 1분봉 / 3분봉 / 10분봉 / 일봉
  - 분봉: 네이버 분봉 API 활용, 최근 5영업일 데이터 (3분/10분은 백엔드에서 집계)
  - 일봉: 최대 3년 데이터
- **기간 탭** (일봉 전용): 1개월 / 3개월(기본) / 6개월 / 1년 / 2년 / 전체
- **오버레이 지표**: MA 5 / MA 20 / MA 50 / MA 100 / MA 200 / 볼린저 밴드
- **우측 정보 패널**: 현재가, 시가/고가/저가/종가, 거래량, MA 현재값 및 이격률
- **종합 매매 분석 패널**: 5개 지표 종합 점수 기반 판정
  - MA 배열 / 골든·데드 크로스 / 볼린저밴드 / 거래량 / 5일 모멘텀
  - 강력 매수 ~ 강력 매도 7단계 판정 + 상세 코멘트
- **이동평균선 분석 패널**: 크로스 시그널, 정배열/역배열 분석
- **USD/KRW 가격 토글**: 해외 종목(미국 주식 등) 차트에서 원화·달러 금액 전환
- **빠른 검색**: 차트 페이지 내에서 다른 종목 즉시 검색·이동

---

## REST API 엔드포인트

| 메서드 | URL | 설명 |
|--------|-----|------|
| GET | `/api/stock/{symbol}/price` | 현재가 (30초 캐시) |
| GET | `/api/stock/{symbol}/candle?timeframe=day` | 캔들 OHLCV + MA + 볼린저 (`timeframe`: `day`/`1`/`3`/`10`) |
| GET | `/api/stock/search?keyword=삼성` | 종목/ETF 검색 (60분 캐시) |
| GET | `/api/stock/top?type=stock&limit=10` | 거래량 상위 목록 (10분 캐시) |
| GET | `/api/stock/market-indicators` | 주요 시장 지표 (코스피/코스닥/환율/WTI/해외지수, 5분 캐시) |
| GET | `/api/stock/usdkrw-rate` | USD/KRW 환율 조회 |
| GET | `/api/stock/watchlist` | 관심 종목 목록 조회 |
| POST | `/api/stock/watchlist` | 관심 종목 추가 (`group` 필드 필수) |
| DELETE | `/api/stock/watchlist/{symbol\|group}` | 관심 종목 삭제 (composite key 또는 symbol) |
| GET | `/api/stock/watchlist/groups` | 그룹 목록 조회 |
| PUT | `/api/stock/watchlist/{symbol\|group}/group` | 그룹 이동 |
| DELETE | `/api/stock/watchlist/groups/{groupName}` | 그룹 삭제 (소속 종목도 삭제) |
| PUT | `/api/stock/watchlist/groups/{groupName}` | 그룹 이름 변경 |

### 응답 예시 — 현재가

```json
{
  "symbol": "005930",
  "name": "삼성전자",
  "currentPrice": 73400,
  "priceChange": -200,
  "changeRate": -0.27,
  "volume": 10234567,
  "high": 74200,
  "low": 72800,
  "open": 73600,
  "currency": "KRW"
}
```

### 요청 예시 — 관심 종목 추가

```json
POST /api/stock/watchlist
{
  "symbol": "005930",
  "name": "삼성전자",
  "market": "코스피",
  "type": "stock",
  "group": "성장주"
}
```

---

## 관심 종목 영속화

- 관심 종목은 `data/watchlist.json` 파일에 자동 저장됩니다.
- 서버 재시작 시 파일에서 자동 로드하여 복원합니다.
- 최대 100개까지 등록 가능, 4~12자 영숫자 종목 코드 형식 검증 적용.
- 같은 종목을 다른 그룹에 중복 등록 가능 (복합키: `symbol|group`).
- "전체" 탭에서는 symbol 기준 중복 제거하여 1건만 표시.
- 그룹 삭제 시 소속 종목도 함께 삭제됩니다.
- 빈 그룹(종목 없음)도 localStorage에 유지되어 새로고침 시 사라지지 않습니다.
- `data/` 디렉토리는 `.gitignore`에 포함되어 있습니다.

---

## 캐시 설정

| 캐시 이름 | TTL | 최대 항목 | 설명 |
|-----------|-----|-----------|------|
| `stockPrice` | 30초 | 100 | 현재가 정보 |
| `stockCandle` | 10분 | 200 | 일봉 캔들 데이터 |
| `stockMinuteCandle` | 1분 | 100 | 분봉 캔들 데이터 (장중 실시간성) |
| `stockSearch` | 60분 | 500 | 종목 검색 결과 |
| `topRanking` | 10분 | 20 | 거래량 상위 목록 |
| `marketIndicators` | 5분 | 10 | 시장 지표 (코스피/코스닥/환율/WTI/해외지수) |

`application.yml`에서 변경 가능:
```yaml
cache:
  candle-ttl-minutes: 10
  price-ttl-seconds: 30
```

---

## 테스트 실행

```cmd
gradlew.bat test
```

테스트 보고서: `build/reports/tests/test/index.html`

---

## 프로젝트 구조

```
stockMng/
├── src/main/java/com/example/stockchart/
│   ├── StockChartApplication.java
│   ├── config/
│   │   ├── WebClientConfig.java          # 네이버/야후 API용 WebClient Bean
│   │   └── CacheConfig.java              # Caffeine 캐시 설정
│   ├── controller/
│   │   ├── ChartViewController.java      # Thymeleaf 페이지 라우팅 + favicon
│   │   └── StockApiController.java       # REST API 엔드포인트
│   ├── service/
│   │   ├── NaverStockService.java        # 네이버 조회 인터페이스
│   │   ├── WatchlistService.java         # 관심 종목 인터페이스
│   │   ├── StockDataFacade.java          # 서비스 퍼사드
│   │   └── impl/
│   │       ├── NaverStockServiceImpl.java    # 네이버/야후 증권 API 구현
│   │       └── InMemoryWatchlistService.java # 관심 종목 (그룹별 분류, JSON 영속화)
│   ├── dto/
│   │   ├── CandleDto.java
│   │   ├── StockPriceDto.java            # currency(KRW/USD) 필드 포함
│   │   ├── StockSearchDto.java
│   │   ├── MarketIndicatorDto.java       # 시장 지표 DTO
│   │   ├── WatchlistItemDto.java
│   │   └── WatchlistRequestDto.java
│   ├── exception/
│   │   ├── StockApiException.java
│   │   └── GlobalExceptionHandler.java
│   └── util/
│       └── IndicatorUtil.java            # MA / 볼린저 / RSI / MACD 계산
├── src/main/resources/
│   ├── templates/
│   │   ├── index.html                    # 메인 페이지 (탭 + 검색 + 그룹 관리 + 시장 지표)
│   │   └── chart.html                    # 차트 페이지 (지표 + 매매 분석 + USD/KRW 토글)
│   ├── static/
│   │   ├── js/chart.js                   # TradingView 렌더링 + 타임프레임 + 종합 분석
│   │   └── css/style.css                 # 다크/라이트 테마 스타일
│   └── application.yml
├── src/test/java/com/example/stockchart/
│   ├── service/impl/InMemoryWatchlistServiceTest.java
│   └── util/IndicatorUtilTest.java
├── data/
│   └── watchlist.json                    # 관심 종목 저장 파일 (자동 생성)
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 데이터 소스

| 소스 | 용도 |
|------|------|
| 네이버 증권 API | 국내 주식·ETF 시세, 검색, 거래량 랭킹, 분봉/일봉 차트 |
| Yahoo Finance (`query1.finance.yahoo.com`) | 해외 종목(미국 주식 등) 시세·차트, 환율(USD/KRW), WTI, 해외 대표 지수 |

---

## 제외 항목

| 항목 | 사유 |
|------|------|
| DB (H2 포함) | Caffeine 캐시 + JSON 파일로 충분 |
| 로그인/인증 | 시세 조회 전용 앱, 인증 불필요 |
| 실거래 주문 | 시세 조회 및 분석만 제공 |
| Docker | JAR 직접 실행으로 충분 |

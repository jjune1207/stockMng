# 주식 분석 차트 시스템

Java 17 + Spring Boot 3.2.5 기반 주식/ETF 분석 차트 웹앱.  
DB 없이 네이버 증권 API와 Yahoo Finance를 활용하여 거래량 상위 종목 조회, 관심 종목 관리, 기술적 분석, 시장 지표, 해외 종목 분석, 경제 뉴스를 제공합니다.

---

## 주요 기능

- **거래량 상위 10개** 주식/ETF 목록 (5분 단위 자동 갱신)
- **종목 검색** (네이버 자동완성 API — 주식 + ETF 통합 검색)
- **관심 종목 관리** (그룹별 분류, 같은 종목 여러 그룹 중복 등록, JSON 파일 영속화)
- **포트폴리오 관리**: 보유수량/평균단가 입력, 매입금액·평가금액·수익금·수익률 자동 계산, 포트폴리오 비중 표시
- **포트폴리오 요약 바**: 관심 종목 탭 상단에 전체(또는 그룹별) 매입금액·평가금액·평가손익·수익률 요약 표시 (USD/KRW 토글 연동)
- **그룹 관리**: 그룹 추가/삭제/이름 변경, 드래그&드롭 탭 순서 변경, 빈 그룹 유지
- **캔들스틱 차트** (TradingView Lightweight Charts v4)
- **타임프레임**: 1분봉 / 3분봉 / 10분봉 (최근 5영업일) / 일봉 (최대 3년)
- **기술적 지표**: MA(5/20/50/100/200), 볼린저 밴드
- **종합 매매 분석**: 5개 지표 종합 점수 기반 매수/매도 판정 및 코멘트
- **주요 시장 지표**: 코스피, 코스닥, S&P 500, 나스닥, 다우지수, 환율(USD/KRW), WTI, 금(Gold), 은(Silver) 실시간 표시. 코스피/코스닥/S&P500/나스닥/다우 클릭 시 차트 상세 페이지 이동
- **해외 종목 지원**: Yahoo Finance 연동으로 미국 주식 시세·차트 조회, 원화/달러 가격 토글. 미국 ETF는 한글 설명 표시
- **주요뉴스**: 8개 RSS 소스(구글뉴스/다음/한국경제/연합뉴스/매일경제/이데일리/JTBC/YTN)에서 설정 키워드 필터링, 36시간 이내 뉴스, 중복 제거 후 최대 10건 수집 (30분 자동 갱신)
- **뉴스 키워드 설정**: 필터 키워드를 서버(`data/news-keywords.json`)에 영속화, 설정 화면에서 편집 가능
- **다크/라이트 모드 전환**: 상단 토글 버튼으로 테마 변경 (localStorage 저장, 메인/차트 페이지 모두 지원)

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Java 17, Spring Boot 3.2.5, Gradle 8.7 |
| HTTP Client | WebClient (Spring WebFlux) |
| 캐시 | Caffeine Cache (인메모리) |
| 데이터 영속화 | JSON 파일 (`data/watchlist.json`, `data/news-keywords.json`) |
| Frontend | Thymeleaf, TradingView Lightweight Charts v4, Bootstrap 5 |
| 데이터 소스 | 네이버 증권 API (시세, 검색, 거래량 랭킹), Yahoo Finance (해외 종목, 시장지표, 금·은) |

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
- **주요 시장 지표 바**: 코스피, 코스닥, S&P500, 나스닥, 다우지수, 환율(USD/KRW), WTI, 금, 은 실시간 표시. 5개 지수 카드 클릭 시 차트 상세 페이지 이동
- **주요뉴스 섹션**: 8개 RSS 소스에서 수집한 뉴스 가로 스크롤 카드 표시 (30분 갱신). 좌우 네비게이션 버튼 제공. 키워드 설정으로 필터링
- **관심 종목 탭**: 사용자가 추가한 종목/ETF 목록 (기본 활성 탭, 그룹별 분류, KRW/USD 토글, 미국 ETF 한글 설명 표시)
  - **포트폴리오 요약 바**: 탭 상단에 매입금액·평가금액·평가손익·수익률 요약 표시. 통화 토글·그룹 필터 변경 시 자동 갱신
- **주요 주식 탭**: 코스피 시총 상위 10개 (현재가, 등락률, 거래량)
- **주요 ETF 탭**: 인기 ETF 16개 (고정 큐레이션 목록)
- **미국 주식 탭**: S&P500 상위 10개 (KRW/USD 토글, 기본값 원화)
- **미국 ETF 탭**: 주요 ETF 15개 (KRW/USD 토글, 기본값 원화, 한글 설명 표시)
  - **그룹 필터**: 그룹 탭 클릭으로 필터링, 드래그&드롭으로 순서 변경 (localStorage 저장)
  - **그룹 관리**: 그룹 추가(인라인 입력) / 삭제 / 이름 변경
  - **중복 등록**: 같은 종목을 여러 그룹에 등록 가능 (복합키 `symbol|group`)
  - **전체 탭**: 중복 종목은 1건만 표시, 소속 그룹을 뱃지로 모두 표시
  - **마우스 그룹 선택**: 관심추가 시 드롭다운 팝업에서 그룹 선택
  - **빈 그룹 유지**: 종목이 없어도 그룹이 삭제되지 않음 (localStorage 영속)
  - **정렬**: 이름, 비중, 수익률, 현재가, 등락률, 거래량 헤더 클릭으로 정렬
  - **포트폴리오 모달**: 종목별 보유수량/평균단가 입력 → 매입금액·평가금액·수익금·수익률 실시간 계산, 다크/라이트 테마 지원
- **자동 갱신**: 주식/시장지표 5분, 뉴스 30분 별도 타이머 + 카운트다운 표시 + 수동 새로고침 버튼 (클릭 시 스피너 피드백)
- **더블클릭 진입**: 종목 행 더블클릭으로 차트 상세 페이지 즉시 이동 (버튼/링크 클릭은 기존 동작 유지)
- 각 종목에 **차트** / **관심추가** 버튼 제공 (이미 등록된 종목도 다른 그룹에 추가 가능)

### 차트 화면 (`/chart/{종목코드}`)

- **캔들스틱 차트** + 거래량 히스토그램 (시간축 동기화)
- **종목명 아래 한글 설명**: 미국 ETF인 경우 자동 표시
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
| GET | `/api/stock/{symbol}/price` | 현재가 (30초 캐시). 미국 ETF는 `description` 필드 포함 |
| GET | `/api/stock/{symbol}/candle?timeframe=day` | 캔들 OHLCV + MA + 볼린저 (`timeframe`: `day`/`1`/`3`/`10`) |
| GET | `/api/stock/search?keyword=삼성` | 종목/ETF 검색 (60분 캐시) |
| GET | `/api/stock/top?type=stock&limit=10` | 거래량 상위 목록 (`type`: `stock`/`etf`/`us_stock`/`us_etf`, 10분 캐시) |
| GET | `/api/stock/market-indicators` | 주요 시장 지표 (코스피/코스닥/S&P500/나스닥/다우/환율/WTI/금/은, 5분 캐시) |
| GET | `/api/stock/usdkrw-rate` | USD/KRW 환율 조회 |
| GET | `/api/stock/news?limit=10&keywords=미국,나스닥` | 주요 뉴스 (8개 RSS 소스, 30분 캐시, 기본값·최대 10건) |
| GET | `/api/stock/news-keywords` | 뉴스 필터 키워드 목록 조회 |
| PUT | `/api/stock/news-keywords` | 뉴스 필터 키워드 업데이트 (서버 영속화) |
| GET | `/api/stock/watchlist` | 관심 종목 목록 조회 |
| POST | `/api/stock/watchlist` | 관심 종목 추가 (`group` 필드 필수) |
| DELETE | `/api/stock/watchlist/{symbol\|group}` | 관심 종목 삭제 (composite key 또는 symbol) |
| GET | `/api/stock/watchlist/groups` | 그룹 목록 조회 |
| PUT | `/api/stock/watchlist/{symbol\|group}/group` | 그룹 이동 |
| DELETE | `/api/stock/watchlist/groups/{groupName}` | 그룹 삭제 (소속 종목도 삭제) |
| PUT | `/api/stock/watchlist/groups/{groupName}` | 그룹 이름 변경 |
| PUT | `/api/stock/watchlist/{symbol}/portfolio` | 보유수량/평균단가 업데이트 (`quantity`, `purchasePrice`) |

### 응답 예시 — 현재가 (미국 ETF)

```json
{
  "symbol": "QQQ",
  "name": "Invesco QQQ Trust",
  "currentPrice": 450,
  "priceChange": 3,
  "changeRate": 0.67,
  "volume": 35000000,
  "high": 452,
  "low": 447,
  "open": 448,
  "currency": "USD",
  "description": "나스닥 기술주 로켓 100"
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

### 요청 예시 — 뉴스 키워드 업데이트

```json
PUT /api/stock/news-keywords
["미국", "트럼프", "나스닥", "S&P", "다우", "NASDAQ", "코스피"]
```

---

## 데이터 영속화

### 관심 종목 (`data/watchlist.json`)

- 서버 재시작 시 파일에서 자동 로드하여 복원
- 최대 100개까지 등록 가능, 4~12자 영숫자 종목 코드 형식 검증 적용
- 같은 종목을 다른 그룹에 중복 등록 가능 (복합키: `symbol|group`)
- "전체" 탭에서는 symbol 기준 중복 제거하여 1건만 표시
- 그룹 삭제 시 소속 종목도 함께 삭제
- 종목별 `quantity`(보유수량), `purchasePrice`(평균단가) 저장 — 포트폴리오 수익률 계산에 활용

### 뉴스 키워드 (`data/news-keywords.json`)

- 뉴스 필터 키워드를 서버 파일로 영속화 (`NewsKeywordsService`)
- 파일 없을 경우 기본 키워드로 자동 초기화
- 설정 화면에서 추가/삭제 후 즉시 서버에 반영

`data/` 디렉토리는 `.gitignore`에 포함되어 있습니다.

---

## 캐시 설정

| 캐시 이름 | TTL | 최대 항목 | 설명 |
|-----------|-----|-----------|------|
| `stockPrice` | 30초 | 100 | 현재가 정보 |
| `stockCandle` | 10분 | 200 | 일봉 캔들 데이터 |
| `stockMinuteCandle` | 1분 | 100 | 분봉 캔들 데이터 (장중 실시간성) |
| `stockSearch` | 60분 | 500 | 종목 검색 결과 |
| `topRanking` | 10분 | 20 | 거래량 상위 목록 |
| `marketIndicators` | 5분 | 10 | 시장 지표 (코스피/코스닥/환율/WTI/금/은/해외지수) |
| `usPopular` | 30분 | 20 | 미국 인기 종목/ETF 목록 (고정 리스트) |
| `usNews` | 30분 | 10 | 주요 뉴스 (8개 RSS 소스, 키워드 필터 + 중복 제거) |
| `domesticPopular` | 60분 | 10 | 국내 인기 종목/ETF 고정 리스트 |

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
│   │   ├── WebClientConfig.java              # 네이버/야후 API용 WebClient Bean
│   │   └── CacheConfig.java                  # Caffeine 캐시 설정
│   ├── controller/
│   │   ├── ChartViewController.java          # Thymeleaf 페이지 라우팅 + favicon
│   │   └── StockApiController.java           # REST API 엔드포인트
│   ├── service/
│   │   ├── NaverStockService.java            # 네이버 조회 인터페이스
│   │   ├── WatchlistService.java             # 관심 종목 인터페이스
│   │   ├── NewsKeywordsService.java          # 뉴스 키워드 인터페이스
│   │   ├── StockDataFacade.java              # 서비스 퍼사드
│   │   └── impl/
│   │       ├── NaverStockServiceImpl.java        # 네이버/야후 증권 API 구현
│   │       ├── InMemoryWatchlistService.java     # 관심 종목 (그룹별 분류, JSON 영속화)
│   │       └── InMemoryNewsKeywordsService.java  # 뉴스 키워드 (JSON 영속화)
│   ├── dto/
│   │   ├── CandleDto.java
│   │   ├── StockPriceDto.java                # currency(KRW/USD), description(ETF 한글 설명) 포함
│   │   ├── StockSearchDto.java               # description(미국 ETF 전용) 포함
│   │   ├── MarketIndicatorDto.java           # 시장 지표 DTO
│   │   ├── UsNewsDto.java                    # 뉴스 DTO
│   │   ├── WatchlistItemDto.java
│   │   └── WatchlistRequestDto.java
│   ├── exception/
│   │   ├── StockApiException.java
│   │   └── GlobalExceptionHandler.java
│   └── util/
│       └── IndicatorUtil.java                # MA / 볼린저 / RSI / MACD 계산
├── src/main/resources/
│   ├── templates/
│   │   ├── index.html                        # 메인 페이지 (탭 + 검색 + 그룹 관리 + 시장 지표 + 뉴스)
│   │   └── chart.html                        # 차트 페이지 (지표 + 매매 분석 + USD/KRW 토글)
│   ├── static/
│   │   ├── js/chart.js                       # TradingView 렌더링 + 타임프레임 + 종합 분석
│   │   └── css/style.css                     # 다크/라이트 테마 스타일
│   └── application.yml
├── src/test/java/com/example/stockchart/
│   ├── service/impl/InMemoryWatchlistServiceTest.java
│   ├── service/impl/InMemoryNewsKeywordsServiceTest.java
│   └── util/IndicatorUtilTest.java
├── data/
│   ├── watchlist.json                        # 관심 종목 저장 파일 (자동 생성)
│   └── news-keywords.json                    # 뉴스 필터 키워드 (자동 생성)
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 데이터 소스

| 소스 | 용도 |
|------|------|
| 네이버 증권 API | 국내 주식·ETF 시세, 검색, 거래량 랭킹, 분봉/일봉 차트 |
| Yahoo Finance (`query1.finance.yahoo.com`) | 해외 종목(미국 주식 등) 시세·차트, 환율(USD/KRW), WTI, 금, 은, 해외 대표 지수. 통화 자동 감지(KRW/USD) |
| Google News RSS (구글뉴스 / JTBC / YTN) | 뉴스 키워드 필터링 한국어 뉴스 |
| 다음뉴스 / 한국경제 / 연합뉴스 / 매일경제 / 이데일리 RSS | 한국 경제 뉴스 (키워드 필터링) |

---

## 제외 항목

| 항목 | 사유 |
|------|------|
| DB (H2 포함) | Caffeine 캐시 + JSON 파일로 충분 |
| 로그인/인증 | 시세 조회 전용 앱, 인증 불필요 |
| 실거래 주문 | 시세 조회 및 분석만 제공 |
| Docker | JAR 직접 실행으로 충분 |

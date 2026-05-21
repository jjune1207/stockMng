# CLAUDE.md

## 프로젝트 개요

Java 17 + Spring Boot 3.2.5 기반 주식/ETF 분석 차트 웹앱.
네이버 증권 + Yahoo Finance API로 시세·차트·시장지표 제공. DB 없음, JSON 파일 영속화.

## 빌드 및 실행

```cmd
gradlew.bat bootRun   # 개발 서버 (http://localhost:8080)
gradlew.bat build
gradlew.bat test
```

## 아키텍처

`StockApiController` → `StockDataFacade` → 서비스 인터페이스 → 구현체

- **StockDataFacade**: 모든 서비스 단일 진입점. Controller는 이것만 의존
- **NaverStockServiceImpl**: 네이버(국내) + Yahoo Finance(해외·지수·금·은). Caffeine 캐시 적용
- **InMemoryWatchlistService**: `data/watchlist.json` 영속화. 복합키 `symbol|owner|group`
- **InMemoryNewsKeywordsService**: `data/news-keywords.json` 영속화. 소유자별 독립 키워드 관리
- **SubOwnerMappingService**: `data/sub-owner-mapping.json` 영속화. 서브소유자↔계정 매핑
- **IndicatorUtil**: MA / 볼린저 / RSI / MACD 계산

### 프론트엔드

Thymeleaf SSR (`index.html`, `chart.html`) + TradingView Lightweight Charts v4 + Bootstrap 5

- `index.html`: 관심 종목 관리 (소유자·그룹·종목구분 필터, 포트폴리오, 알림), 시장지표, 뉴스
- `chart.js`: 차트 렌더링, 타임프레임, 종합 매매 분석, 시장지수 현황 분석
- `style.css`: 다크/라이트 테마

## 배포

Railway 배포 지원 (`Dockerfile` + `railway.json`).
- Dockerfile: eclipse-temurin:17-jdk-alpine 멀티스테이지 빌드
- `PORT` 환경변수로 포트 자동 감지

## 주요 의존성

Spring Boot Web + WebFlux(WebClient), Thymeleaf, Caffeine Cache, Lombok, JUnit 5

## API 경로

모든 REST: `/api/stock/**`

| 경로 | 설명 |
|------|------|
| `/{symbol}/candle?timeframe=1\|3\|10\|day` | 캔들 + 지표 |
| `/market-indicators` | 코스피·코스닥·S&P500·나스닥·다우·SOX·VIX·WTI·환율·금·은 |
| `/top?type=stock\|etf\|us_stock\|us_etf` | 인기 종목 |
| `/news?owner=` | 뉴스 조회 (keywords 비면 세션owner 키워드 자동 적용) |
| `/news-keywords?owner=` | 소유자별 키워드 조회·수정 (어드민: 전체, 일반: 자신·서브소유자만) |
| `/watchlist` (CRUD) | 복합키 `symbol\|owner\|group`, 포트폴리오 PUT |
| `/watchlist/owners/**`, `/watchlist/groups/**` | 소유자·그룹 관리 |

어드민 전용: `/api/admin/**` (계정 목록, 소유자 계층, 계정 생성·삭제)

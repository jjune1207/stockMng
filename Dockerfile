# ── 1단계: 빌드 ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 의존성 캐시 레이어: 소스 변경 시 재다운로드 방지
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew
RUN ./gradlew dependencies --no-daemon -q

# 소스 복사 후 빌드 (테스트 제외)
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── 2단계: 실행 ──────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드 산출물만 복사
COPY --from=builder /app/build/libs/stockMng-*.jar app.jar

# 초기 데이터 복사 (재배포 시 리셋됨 - 볼륨 미사용 시)
COPY data/ ./data/

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

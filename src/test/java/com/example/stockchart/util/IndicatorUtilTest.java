package com.example.stockchart.util;

import com.example.stockchart.dto.CandleDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * IndicatorUtil 단위 테스트
 * JUnit5 + AssertJ
 */
class IndicatorUtilTest {

    private List<CandleDto> candles;

    @BeforeEach
    void setUp() {
        // 30개 캔들 데이터 (종가: 100, 102, 104, ... 패턴)
        candles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            double close = 100.0 + i * 2;
            candles.add(CandleDto.builder()
                .date("202401" + String.format("%02d", i + 1))
                .open(close - 1)
                .high(close + 2)
                .low(close - 2)
                .close(close)
                .volume(1_000_000L + i * 10_000)
                .build());
        }
    }

    // ──────────────────────────────────────────
    // MA 테스트
    // ──────────────────────────────────────────

    @Test
    @DisplayName("MA5 계산: 리스트 크기가 캔들 수와 동일해야 함")
    void calculateMA_size() {
        List<Double> ma = IndicatorUtil.calculateMA(candles, 5);
        assertThat(ma).hasSize(candles.size());
    }

    @Test
    @DisplayName("MA5 계산: period-1 이전 인덱스는 null이어야 함")
    void calculateMA_nullPrefix() {
        int period = 5;
        List<Double> ma = IndicatorUtil.calculateMA(candles, period);
        for (int i = 0; i < period - 1; i++) {
            assertThat(ma.get(i)).isNull();
        }
        assertThat(ma.get(period - 1)).isNotNull();
    }

    @Test
    @DisplayName("MA5 첫 번째 유효값: (100+102+104+106+108) / 5 = 104")
    void calculateMA_firstValidValue() {
        List<Double> ma = IndicatorUtil.calculateMA(candles, 5);
        // index 4: close = 100 + 4*2 = 108, 이전 4개: 100,102,104,106,108 → 평균 104
        assertThat(ma.get(4)).isEqualTo(104.0);
    }

    @Test
    @DisplayName("MA 빈 리스트 처리: 빈 결과 반환")
    void calculateMA_emptyInput() {
        List<Double> ma = IndicatorUtil.calculateMA(new ArrayList<>(), 5);
        assertThat(ma).isEmpty();
    }

    @Test
    @DisplayName("MA period가 데이터 수보다 크면: 모두 null")
    void calculateMA_periodLargerThanData() {
        List<Double> ma = IndicatorUtil.calculateMA(candles, 100);
        assertThat(ma).hasSize(candles.size());
        assertThat(ma).allMatch(v -> v == null);
    }

    // ──────────────────────────────────────────
    // 볼린저 밴드 테스트
    // ──────────────────────────────────────────

    @Test
    @DisplayName("볼린저 밴드: upper >= middle >= lower 항상 성립")
    void calculateBollingerBands_upperGteMiddleGteLower() {
        IndicatorUtil.BollingerBands bb = IndicatorUtil.calculateBollingerBands(candles, 20, 2.0);

        for (int i = 0; i < candles.size(); i++) {
            Double upper  = bb.getUpper().get(i);
            Double middle = bb.getMiddle().get(i);
            Double lower  = bb.getLower().get(i);

            if (upper != null) {
                assertThat(upper).isGreaterThanOrEqualTo(middle);
                assertThat(lower).isLessThanOrEqualTo(middle);
            }
        }
    }

    @Test
    @DisplayName("볼린저 밴드: 세 리스트 크기가 모두 캔들 수와 동일")
    void calculateBollingerBands_size() {
        IndicatorUtil.BollingerBands bb = IndicatorUtil.calculateBollingerBands(candles, 20, 2.0);
        assertThat(bb.getUpper()).hasSize(candles.size());
        assertThat(bb.getMiddle()).hasSize(candles.size());
        assertThat(bb.getLower()).hasSize(candles.size());
    }

    @Test
    @DisplayName("볼린저 밴드: period-1 이전은 null")
    void calculateBollingerBands_nullPrefix() {
        int period = 20;
        IndicatorUtil.BollingerBands bb = IndicatorUtil.calculateBollingerBands(candles, period, 2.0);
        for (int i = 0; i < period - 1; i++) {
            assertThat(bb.getUpper().get(i)).isNull();
            assertThat(bb.getLower().get(i)).isNull();
        }
    }

    // ──────────────────────────────────────────
    // RSI 테스트
    // ──────────────────────────────────────────

    @Test
    @DisplayName("RSI: 리스트 크기가 캔들 수와 동일")
    void calculateRSI_size() {
        List<Double> rsi = IndicatorUtil.calculateRSI(candles, 14);
        assertThat(rsi).hasSize(candles.size());
    }

    @Test
    @DisplayName("RSI: 0 이상 100 이하 범위 이내")
    void calculateRSI_range() {
        List<Double> rsi = IndicatorUtil.calculateRSI(candles, 14);
        rsi.stream()
            .filter(v -> v != null)
            .forEach(v -> assertThat(v).isBetween(0.0, 100.0));
    }

    @Test
    @DisplayName("RSI: 단조 상승 데이터에서 RSI = 100.0 예상")
    void calculateRSI_allUp() {
        // 지속 상승 데이터 (손실 없음)
        List<CandleDto> upCandles = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            upCandles.add(CandleDto.builder()
                .date("20240101")
                .open(100.0 + i)
                .high(102.0 + i)
                .low(99.0 + i)
                .close(101.0 + i)
                .volume(1_000_000L)
                .build());
        }
        List<Double> rsi = IndicatorUtil.calculateRSI(upCandles, 14);
        Double lastRsi = rsi.get(rsi.size() - 1);
        assertThat(lastRsi).isEqualTo(100.0);
    }

    @Test
    @DisplayName("RSI: period-1 이전은 null")
    void calculateRSI_nullPrefix() {
        int period = 14;
        List<Double> rsi = IndicatorUtil.calculateRSI(candles, period);
        for (int i = 0; i < period; i++) {
            assertThat(rsi.get(i)).isNull();
        }
        assertThat(rsi.get(period)).isNotNull();
    }

    // ──────────────────────────────────────────
    // MACD 테스트
    // ──────────────────────────────────────────

    @Test
    @DisplayName("MACD: 세 리스트 크기가 모두 캔들 수와 동일")
    void calculateMACD_size() {
        IndicatorUtil.MacdResult macd = IndicatorUtil.calculateMACD(candles, 12, 26, 9);
        assertThat(macd.getMacdLine()).hasSize(candles.size());
        assertThat(macd.getSignalLine()).hasSize(candles.size());
        assertThat(macd.getHistogram()).hasSize(candles.size());
    }

    @Test
    @DisplayName("MACD: histogram = macdLine - signalLine (null 제외)")
    void calculateMACD_histogramEquality() {
        IndicatorUtil.MacdResult macd = IndicatorUtil.calculateMACD(candles, 12, 26, 9);

        for (int i = 0; i < candles.size(); i++) {
            Double m = macd.getMacdLine().get(i);
            Double s = macd.getSignalLine().get(i);
            Double h = macd.getHistogram().get(i);

            if (m != null && s != null && h != null) {
                assertThat(h).isCloseTo(m - s, within(0.01));
            }
        }
    }

    @Test
    @DisplayName("MACD: 단조 상승 시 MACD 라인은 양수")
    void calculateMACD_uptrend_positive() {
        IndicatorUtil.MacdResult macd = IndicatorUtil.calculateMACD(candles, 12, 26, 9);
        // 26일 이후 값들은 단조 상승이므로 단기 EMA > 장기 EMA → MACD > 0
        List<Double> macdLine = macd.getMacdLine();
        for (int i = 26; i < candles.size(); i++) {
            if (macdLine.get(i) != null) {
                assertThat(macdLine.get(i)).isGreaterThan(0.0);
            }
        }
    }

    // ──────────────────────────────────────────
    // EMA 테스트
    // ──────────────────────────────────────────

    @Test
    @DisplayName("EMA: period-1 이전은 null")
    void calculateEMA_nullPrefix() {
        int period = 12;
        List<Double> ema = IndicatorUtil.calculateEMA(candles, period);
        for (int i = 0; i < period - 1; i++) {
            assertThat(ema.get(i)).isNull();
        }
        assertThat(ema.get(period - 1)).isNotNull();
    }

    @Test
    @DisplayName("EMA: 리스트 크기가 캔들 수와 동일")
    void calculateEMA_size() {
        List<Double> ema = IndicatorUtil.calculateEMA(candles, 12);
        assertThat(ema).hasSize(candles.size());
    }
}

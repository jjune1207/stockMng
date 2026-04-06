package com.example.stockchart.util;

import com.example.stockchart.dto.CandleDto;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * 주식 기술적 지표 계산 유틸리티
 * - MA (이동평균선)
 * - 볼린저 밴드
 * - RSI (Relative Strength Index)
 * - MACD (Moving Average Convergence Divergence)
 */
public final class IndicatorUtil {

    private IndicatorUtil() {}

    // ────────────────────────────────────────────────────────────
    // MA (단순 이동평균)
    // ────────────────────────────────────────────────────────────

    /**
     * 단순 이동평균 계산
     *
     * @param candles 캔들 리스트 (날짜 오름차순)
     * @param period  기간
     * @return 이동평균 리스트 (period-1 이전 인덱스는 null)
     */
    public static List<Double> calculateMA(List<CandleDto> candles, int period) {
        List<Double> result = new ArrayList<>();
        if (candles == null || candles.isEmpty() || period <= 0) {
            return result;
        }

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                result.add(null);
            } else {
                double sum = 0.0;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += candles.get(j).getClose();
                }
                result.add(round(sum / period));
            }
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────
    // 볼린저 밴드
    // ────────────────────────────────────────────────────────────

    /**
     * 볼린저 밴드 계산 (중간선 = MA, 상단/하단 = MA ± sigma * 표준편차)
     *
     * @param candles 캔들 리스트
     * @param period  기간 (일반적으로 20)
     * @param sigma   표준편차 배수 (일반적으로 2.0)
     * @return BollingerBands (upper, middle, lower)
     */
    public static BollingerBands calculateBollingerBands(
            List<CandleDto> candles, int period, double sigma) {

        List<Double> middle = calculateMA(candles, period);
        List<Double> upper  = new ArrayList<>();
        List<Double> lower  = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                upper.add(null);
                lower.add(null);
            } else {
                double avg = middle.get(i);
                double sumSq = 0.0;
                for (int j = i - period + 1; j <= i; j++) {
                    double diff = candles.get(j).getClose() - avg;
                    sumSq += diff * diff;
                }
                double std = Math.sqrt(sumSq / period);
                upper.add(round(avg + sigma * std));
                lower.add(round(avg - sigma * std));
            }
        }

        return new BollingerBands(upper, middle, lower);
    }

    // ────────────────────────────────────────────────────────────
    // RSI (Wilder's Smoothing Method)
    // ────────────────────────────────────────────────────────────

    /**
     * RSI 계산 (Wilder's Exponential Smoothing 방식)
     *
     * @param candles 캔들 리스트
     * @param period  기간 (일반적으로 14)
     * @return RSI 리스트 (period 이전 인덱스는 null)
     */
    public static List<Double> calculateRSI(List<CandleDto> candles, int period) {
        List<Double> result = new ArrayList<>();
        if (candles == null || candles.size() <= period || period <= 0) {
            return result;
        }

        // 초기 평균 이득/손실 계산
        double avgGain = 0.0;
        double avgLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        // period개 이전 인덱스는 null
        for (int i = 0; i < period; i++) {
            result.add(null);
        }

        // period 인덱스의 첫 번째 RSI
        result.add(calcRsiValue(avgGain, avgLoss));

        // Wilder's smoothing으로 이후 RSI 계산
        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose() - candles.get(i - 1).getClose();
            double gain   = change > 0 ? change : 0.0;
            double loss   = change < 0 ? Math.abs(change) : 0.0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;

            result.add(calcRsiValue(avgGain, avgLoss));
        }

        return result;
    }

    private static double calcRsiValue(double avgGain, double avgLoss) {
        if (avgLoss == 0.0) return 100.0;
        double rs = avgGain / avgLoss;
        return round(100.0 - (100.0 / (1.0 + rs)));
    }

    // ────────────────────────────────────────────────────────────
    // MACD
    // ────────────────────────────────────────────────────────────

    /**
     * MACD 계산
     *
     * @param candles 캔들 리스트
     * @param fast    단기 EMA 기간 (일반적으로 12)
     * @param slow    장기 EMA 기간 (일반적으로 26)
     * @param signal  시그널 EMA 기간 (일반적으로 9)
     * @return MacdResult (macdLine, signalLine, histogram)
     */
    public static MacdResult calculateMACD(
            List<CandleDto> candles, int fast, int slow, int signal) {

        List<Double> emaFast = calculateEMA(candles, fast);
        List<Double> emaSlow = calculateEMA(candles, slow);

        // MACD 라인 = 단기 EMA - 장기 EMA
        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Double f = emaFast.get(i);
            Double s = emaSlow.get(i);
            macdLine.add((f != null && s != null) ? round(f - s) : null);
        }

        // 시그널 라인 = MACD 라인의 EMA
        List<Double> signalLine = calculateEMAFromValues(macdLine, signal);

        // 히스토그램 = MACD - 시그널
        List<Double> histogram = new ArrayList<>();
        for (int i = 0; i < macdLine.size(); i++) {
            Double m = macdLine.get(i);
            Double s = signalLine.get(i);
            histogram.add((m != null && s != null) ? round(m - s) : null);
        }

        return new MacdResult(macdLine, signalLine, histogram);
    }

    /**
     * 캔들 종가 기반 EMA 계산
     */
    public static List<Double> calculateEMA(List<CandleDto> candles, int period) {
        List<Double> result = new ArrayList<>();
        if (candles == null || candles.isEmpty() || period <= 0) return result;

        double multiplier = 2.0 / (period + 1);
        Double prevEma = null;

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                result.add(null);
            } else if (i == period - 1) {
                // 초기값: 단순 평균
                double sum = 0.0;
                for (int j = 0; j < period; j++) sum += candles.get(j).getClose();
                prevEma = sum / period;
                result.add(round(prevEma));
            } else {
                prevEma = (candles.get(i).getClose() - prevEma) * multiplier + prevEma;
                result.add(round(prevEma));
            }
        }
        return result;
    }

    /**
     * 값 리스트 기반 EMA 계산 (MACD 시그널 라인용)
     */
    private static List<Double> calculateEMAFromValues(List<Double> values, int period) {
        List<Double> result = new ArrayList<>();
        if (values == null || values.isEmpty() || period <= 0) return result;

        double multiplier = 2.0 / (period + 1);
        Double prevEma = null;
        int validCount = 0;
        double initSum = 0.0;

        for (Double value : values) {
            if (value == null) {
                result.add(null);
                continue;
            }

            if (prevEma == null) {
                validCount++;
                initSum += value;
                if (validCount == period) {
                    prevEma = initSum / period;
                    result.add(round(prevEma));
                } else {
                    result.add(null);
                }
            } else {
                prevEma = (value - prevEma) * multiplier + prevEma;
                result.add(round(prevEma));
            }
        }
        return result;
    }

    // ────────────────────────────────────────────────────────────
    // 내부 결과 클래스
    // ────────────────────────────────────────────────────────────

    @Getter
    public static class BollingerBands {
        private final List<Double> upper;
        private final List<Double> middle;
        private final List<Double> lower;

        public BollingerBands(List<Double> upper, List<Double> middle, List<Double> lower) {
            this.upper  = upper;
            this.middle = middle;
            this.lower  = lower;
        }
    }

    @Getter
    public static class MacdResult {
        private final List<Double> macdLine;
        private final List<Double> signalLine;
        private final List<Double> histogram;

        public MacdResult(List<Double> macdLine, List<Double> signalLine, List<Double> histogram) {
            this.macdLine   = macdLine;
            this.signalLine = signalLine;
            this.histogram  = histogram;
        }
    }

    /** 소수점 둘째 자리에서 반올림 */
    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}

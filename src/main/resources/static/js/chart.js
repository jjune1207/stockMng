/**
 * TradingView Lightweight Charts v4 기반 차트 렌더링
 * MA50 / MA100 / MA200 이동평균선 + 볼린저 밴드 + RSI + MACD
 */

let mainChart = null, volumeChart = null, rsiChart = null, macdChart = null;
let candleSeries = null, volumeSeries = null;
let ma5Series = null, ma20Series = null, ma50Series = null, ma100Series = null, ma200Series = null;
let bbUpperSeries = null, bbMiddleSeries = null, bbLowerSeries = null;
let rsiSeries = null, macdLineSeries = null, macdSigSeries = null, macdHistSeries = null;

let currentSymbol = '';
let currentData = null;
let visibleDays = 132; // 기본 6개월

var CHART_BG = '#131722';
var GRID_COLOR = '#1e222d';
var BORDER_COLOR = '#2a2e39';
var TEXT_COLOR = '#d1d4dc';

function initChart(symbol) {
    currentSymbol = symbol;

    createMainChart();
    createVolumeChart();
    createRsiChart();
    createMacdChart();

    // 기간 탭
    document.querySelectorAll('.period-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.period-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            visibleDays = parseInt(btn.dataset.days, 10);
            if (currentData) applyVisibleRange();
        });
    });

    // 이동평균선 토글
    setupCheckbox('chkMA5', ma5Series);
    setupCheckbox('chkMA20', ma20Series);
    setupCheckbox('chkMA50', ma50Series);
    setupCheckbox('chkMA100', ma100Series);
    setupCheckbox('chkMA200', ma200Series);

    // 볼린저 밴드 토글
    var chkBB = document.getElementById('chkBollinger');
    if (chkBB) {
        chkBB.addEventListener('change', function(e) {
            toggleSeries(bbUpperSeries, e.target.checked);
            toggleSeries(bbMiddleSeries, e.target.checked);
            toggleSeries(bbLowerSeries, e.target.checked);
        });
    }

    // RSI 토글
    var chkRSI = document.getElementById('chkRSI');
    if (chkRSI) {
        chkRSI.addEventListener('change', function(e) {
            document.getElementById('rsiPanel').style.display = e.target.checked ? 'block' : 'none';
            if (currentData) renderRsi(currentData);
        });
    }

    // MACD 토글
    var chkMACD = document.getElementById('chkMACD');
    if (chkMACD) {
        chkMACD.addEventListener('change', function(e) {
            document.getElementById('macdPanel').style.display = e.target.checked ? 'block' : 'none';
            if (currentData) renderMacd(currentData);
        });
    }

    // 빠른 검색
    var quickSearch = document.getElementById('quickSearch');
    if (quickSearch) {
        var timer = null;
        quickSearch.addEventListener('input', function() {
            clearTimeout(timer);
            var kw = this.value.trim();
            if (kw.length < 1) { document.getElementById('quickSearchResults').style.display = 'none'; return; }
            timer = setTimeout(function() { quickSearchDo(kw); }, 300);
        });
        quickSearch.addEventListener('keydown', function(e) {
            if (e.key === 'Enter') {
                var kw = this.value.trim();
                if (/^\d{6}/.test(kw)) { window.location.href = '/chart/' + kw; return; }
                clearTimeout(timer);
                quickSearchDo(kw);
            }
        });
        document.addEventListener('click', function(e) {
            if (!e.target.closest('#quickSearch') && !e.target.closest('#quickSearchResults')) {
                document.getElementById('quickSearchResults').style.display = 'none';
            }
        });
    }

    loadChartData(symbol);
    loadPriceInfo(symbol);
}

function setupCheckbox(id, series) {
    var el = document.getElementById(id);
    if (el) {
        el.addEventListener('change', function(e) { toggleSeries(series, e.target.checked); });
    }
}

// ── 차트 생성 ──

function createMainChart() {
    var container = document.getElementById('mainChart');
    mainChart = LightweightCharts.createChart(container, {
        width: container.clientWidth, height: 480,
        layout: { background: { color: CHART_BG }, textColor: TEXT_COLOR },
        grid: { vertLines: { color: GRID_COLOR }, horzLines: { color: GRID_COLOR } },
        crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
        rightPriceScale: { borderColor: BORDER_COLOR },
        timeScale: { borderColor: BORDER_COLOR, timeVisible: true, secondsVisible: false },
    });

    candleSeries = mainChart.addCandlestickSeries({
        upColor: '#ef5350', downColor: '#42a5f5',
        borderUpColor: '#ef5350', borderDownColor: '#42a5f5',
        wickUpColor: '#ef5350', wickDownColor: '#42a5f5',
    });

    ma5Series   = mainChart.addLineSeries({ color: '#ffeb3b', lineWidth: 1, priceLineVisible: false, visible: false });
    ma20Series  = mainChart.addLineSeries({ color: '#4fc3f7', lineWidth: 1, priceLineVisible: false, visible: false });
    ma50Series  = mainChart.addLineSeries({ color: '#ff9800', lineWidth: 1, priceLineVisible: false, visible: true });
    ma100Series = mainChart.addLineSeries({ color: '#2196f3', lineWidth: 1, priceLineVisible: false, visible: true });
    ma200Series = mainChart.addLineSeries({ color: '#e91e63', lineWidth: 2, priceLineVisible: false, visible: true });

    bbUpperSeries  = mainChart.addLineSeries({ color: 'rgba(76,175,80,0.7)', lineWidth: 1, lineStyle: 2, priceLineVisible: false, visible: false });
    bbMiddleSeries = mainChart.addLineSeries({ color: 'rgba(76,175,80,0.4)', lineWidth: 1, lineStyle: 2, priceLineVisible: false, visible: false });
    bbLowerSeries  = mainChart.addLineSeries({ color: 'rgba(76,175,80,0.7)', lineWidth: 1, lineStyle: 2, priceLineVisible: false, visible: false });

    mainChart.subscribeCrosshairMove(function(param) {
        if (!param.time || !currentData) return;
        var idx = currentData.candles.findIndex(function(c) { return dateToTimestamp(c.date) === param.time; });
        if (idx < 0) return;
        updateInfoPanel(currentData.candles[idx]);
    });

    window.addEventListener('resize', function() {
        mainChart.applyOptions({ width: document.getElementById('mainChart').clientWidth });
        if (volumeChart) volumeChart.applyOptions({ width: document.getElementById('volumeChart').clientWidth });
        if (rsiChart) rsiChart.applyOptions({ width: document.getElementById('rsiChart').clientWidth });
        if (macdChart) macdChart.applyOptions({ width: document.getElementById('macdChart').clientWidth });
    });
}

function createVolumeChart() {
    var container = document.getElementById('volumeChart');
    volumeChart = LightweightCharts.createChart(container, {
        width: container.clientWidth, height: 100,
        layout: { background: { color: CHART_BG }, textColor: TEXT_COLOR },
        grid: { vertLines: { color: GRID_COLOR }, horzLines: { visible: false } },
        rightPriceScale: { borderColor: BORDER_COLOR, scaleMargins: { top: 0.1, bottom: 0 } },
        timeScale: { borderColor: BORDER_COLOR, visible: false },
    });
    volumeSeries = volumeChart.addHistogramSeries({ priceFormat: { type: 'volume' }, priceScaleId: '' });
    volumeChart.priceScale('').applyOptions({ scaleMargins: { top: 0.1, bottom: 0 } });
}

function createRsiChart() {
    var container = document.getElementById('rsiChart');
    rsiChart = LightweightCharts.createChart(container, {
        width: container.clientWidth, height: 120,
        layout: { background: { color: CHART_BG }, textColor: TEXT_COLOR },
        grid: { vertLines: { color: GRID_COLOR }, horzLines: { color: GRID_COLOR } },
        rightPriceScale: { borderColor: BORDER_COLOR, scaleMargins: { top: 0.1, bottom: 0.1 } },
        timeScale: { borderColor: BORDER_COLOR, visible: false },
    });
    rsiSeries = rsiChart.addLineSeries({ color: '#e91e63', lineWidth: 1, priceLineVisible: false });
}

function createMacdChart() {
    var container = document.getElementById('macdChart');
    macdChart = LightweightCharts.createChart(container, {
        width: container.clientWidth, height: 120,
        layout: { background: { color: CHART_BG }, textColor: TEXT_COLOR },
        grid: { vertLines: { color: GRID_COLOR }, horzLines: { color: GRID_COLOR } },
        rightPriceScale: { borderColor: BORDER_COLOR },
        timeScale: { borderColor: BORDER_COLOR, visible: false },
    });
    macdLineSeries = macdChart.addLineSeries({ color: '#2196f3', lineWidth: 1, priceLineVisible: false });
    macdSigSeries  = macdChart.addLineSeries({ color: '#ff9800', lineWidth: 1, priceLineVisible: false });
    macdHistSeries = macdChart.addHistogramSeries({ priceLineVisible: false });
}

// ── 데이터 로드 ──

async function loadChartData(symbol) {
    showLoading(true);
    try {
        var res = await fetch('/api/stock/' + symbol + '/candle');
        if (!res.ok) throw new Error('HTTP ' + res.status);
        currentData = await res.json();

        renderCandles(currentData);
        renderVolume(currentData);
        renderOverlays(currentData);

        if (document.getElementById('chkRSI').checked) renderRsi(currentData);
        if (document.getElementById('chkMACD').checked) renderMacd(currentData);

        var count = (currentData.candles || []).length;
        document.getElementById('infoCandleCount').textContent = '캔들 ' + count + '개 (최대 3년)';

        applyVisibleRange();
        syncTimeScales();
        updateMAValues();
        analyzeMA();
        analyzeTradingSignal();

    } catch (err) {
        showError('차트 데이터를 불러올 수 없습니다: ' + err.message);
    } finally {
        showLoading(false);
    }
}

function renderCandles(data) {
    var candles = (data.candles || []).map(function(c) {
        return { time: dateToTimestamp(c.date), open: c.open, high: c.high, low: c.low, close: c.close };
    });
    candleSeries.setData(candles);

    if (data.candles && data.candles.length > 0) {
        updateInfoPanel(data.candles[data.candles.length - 1]);
    }
}

function renderVolume(data) {
    var volumes = (data.candles || []).map(function(c) {
        return {
            time: dateToTimestamp(c.date), value: c.volume,
            color: c.close >= c.open ? 'rgba(239,83,80,0.5)' : 'rgba(66,165,245,0.5)',
        };
    });
    volumeSeries.setData(volumes);
}

function renderOverlays(data) {
    setLineData(ma5Series, data.ma5, data.candles);
    setLineData(ma20Series, data.ma20, data.candles);
    setLineData(ma50Series, data.ma50, data.candles);
    setLineData(ma100Series, data.ma100, data.candles);
    setLineData(ma200Series, data.ma200, data.candles);
    setLineData(bbUpperSeries, data.bollingerUpper, data.candles);
    setLineData(bbMiddleSeries, data.bollingerMiddle, data.candles);
    setLineData(bbLowerSeries, data.bollingerLower, data.candles);
}

function renderRsi(data) { setLineData(rsiSeries, data.rsi, data.candles); }

function renderMacd(data) {
    setLineData(macdLineSeries, data.macdLine, data.candles);
    setLineData(macdSigSeries, data.macdSignal, data.candles);
    var histData = (data.macdHistogram || [])
        .map(function(v, i) {
            if (v == null) return null;
            return { time: dateToTimestamp(data.candles[i].date), value: v,
                     color: v >= 0 ? 'rgba(239,83,80,0.7)' : 'rgba(66,165,245,0.7)' };
        }).filter(Boolean);
    macdHistSeries.setData(histData);
}

function setLineData(series, values, candles) {
    if (!series || !values || !candles) return;
    var data = [];
    for (var i = 0; i < values.length && i < candles.length; i++) {
        if (values[i] != null) {
            data.push({ time: dateToTimestamp(candles[i].date), value: values[i] });
        }
    }
    series.setData(data);
}

function applyVisibleRange() {
    if (!currentData || !currentData.candles || currentData.candles.length === 0) return;
    var total = currentData.candles.length;

    if (visibleDays === 0 || visibleDays >= total) {
        mainChart.timeScale().fitContent();
        return;
    }

    var from = Math.max(0, total - visibleDays);
    mainChart.timeScale().setVisibleLogicalRange({ from: from, to: total - 1 });
}

function syncTimeScales() {
    mainChart.timeScale().subscribeVisibleLogicalRangeChange(function(range) {
        if (!range) return;
        if (volumeChart) volumeChart.timeScale().setVisibleLogicalRange(range);
        if (rsiChart) rsiChart.timeScale().setVisibleLogicalRange(range);
        if (macdChart) macdChart.timeScale().setVisibleLogicalRange(range);
    });
}

// ── 현재가 정보 ──

async function loadPriceInfo(symbol) {
    try {
        var res = await fetch('/api/stock/' + symbol + '/price');
        if (!res.ok) return;
        var data = await res.json();

        document.getElementById('stockName').textContent = data.name || symbol;

        var currentPriceEl = document.getElementById('infoCurrentPrice');
        var changeEl = document.getElementById('infoPriceChange');

        currentPriceEl.textContent = formatNumber(data.currentPrice) + '원';

        var isUp = data.priceChange > 0;
        var isDown = data.priceChange < 0;
        var sign = isUp ? '+' : '';
        currentPriceEl.className = 'info-value fs-4 ' + (isUp ? 'price-up' : isDown ? 'price-down' : 'price-flat');
        changeEl.innerHTML = '<span class="' + (isUp ? 'price-up' : isDown ? 'price-down' : 'price-flat') + '">' +
            sign + formatNumber(data.priceChange) + ' (' + sign + (data.changeRate || 0).toFixed(2) + '%)</span>';

        document.getElementById('infoOpen').textContent = formatNumber(data.open) + '원';
        document.getElementById('infoHigh').textContent = formatNumber(data.high) + '원';
        document.getElementById('infoLow').textContent = formatNumber(data.low) + '원';
        document.getElementById('infoVolume').textContent = formatNumber(data.volume);

    } catch (err) {
        document.getElementById('stockName').textContent =
            document.getElementById('stockSymbol').textContent || symbol;
    }
}

function updateInfoPanel(candle) {
    if (!candle) return;
    document.getElementById('infoOpen').textContent = formatNumber(candle.open) + '원';
    document.getElementById('infoHigh').textContent = formatNumber(candle.high) + '원';
    document.getElementById('infoLow').textContent = formatNumber(candle.low) + '원';
    document.getElementById('infoClose').textContent = formatNumber(candle.close) + '원';
    document.getElementById('infoVolume').textContent = formatNumber(candle.volume);
}

// ── MA 현재값 표시 ──

function updateMAValues() {
    var panel = document.getElementById('maValues');
    if (!currentData || !currentData.candles || currentData.candles.length === 0) {
        panel.innerHTML = '<div class="text-secondary small">데이터 없음</div>';
        return;
    }

    var last = currentData.candles.length - 1;
    var lastPrice = currentData.candles[last].close;

    var maList = [
        { name: 'MA 5', values: currentData.ma5, color: '#ffeb3b' },
        { name: 'MA 20', values: currentData.ma20, color: '#4fc3f7' },
        { name: 'MA 50', values: currentData.ma50, color: '#ff9800' },
        { name: 'MA 100', values: currentData.ma100, color: '#2196f3' },
        { name: 'MA 200', values: currentData.ma200, color: '#e91e63' },
    ];

    var html = '';
    maList.forEach(function(ma) {
        var val = (ma.values && ma.values[last]) ? ma.values[last] : null;
        if (val == null) {
            html += '<div class="d-flex justify-content-between align-items-center mb-1">' +
                '<span class="small" style="color:' + ma.color + '">' + ma.name + '</span>' +
                '<span class="small text-secondary">데이터 부족</span></div>';
        } else {
            var diff = ((lastPrice - val) / val * 100).toFixed(2);
            var diffClass = diff > 0 ? 'price-up' : diff < 0 ? 'price-down' : 'text-light';
            var diffSign = diff > 0 ? '+' : '';
            html += '<div class="d-flex justify-content-between align-items-center mb-1">' +
                '<span class="small" style="color:' + ma.color + '">' + ma.name + '</span>' +
                '<span class="small text-light">' + formatNumber(val) + '</span>' +
                '<span class="small ' + diffClass + '">' + diffSign + diff + '%</span></div>';
        }
    });

    panel.innerHTML = html;
}

// ── MA 분석 ──

function analyzeMA() {
    var panel = document.getElementById('analysisPanel');
    var content = document.getElementById('analysisContent');
    if (!currentData || !currentData.candles || currentData.candles.length < 200) {
        panel.style.display = 'none';
        return;
    }

    var last = currentData.candles.length - 1;
    var price = currentData.candles[last].close;
    var ma50Val = currentData.ma50 ? currentData.ma50[last] : null;
    var ma100Val = currentData.ma100 ? currentData.ma100[last] : null;
    var ma200Val = currentData.ma200 ? currentData.ma200[last] : null;

    var signals = [];

    // 가격 vs MA 위치 분석
    if (ma50Val != null) {
        if (price > ma50Val) signals.push({ type: 'bullish', text: '현재가가 MA50(' + formatNumber(ma50Val) + ') 위에 위치 — 단기 상승 추세' });
        else signals.push({ type: 'bearish', text: '현재가가 MA50(' + formatNumber(ma50Val) + ') 아래 위치 — 단기 하락 추세' });
    }
    if (ma200Val != null) {
        if (price > ma200Val) signals.push({ type: 'bullish', text: '현재가가 MA200(' + formatNumber(ma200Val) + ') 위에 위치 — 장기 상승 추세' });
        else signals.push({ type: 'bearish', text: '현재가가 MA200(' + formatNumber(ma200Val) + ') 아래 위치 — 장기 하락 추세' });
    }

    // 골든크로스 / 데드크로스 확인 (최근 5일)
    if (ma50Val != null && ma200Val != null && last >= 5) {
        for (var i = last - 4; i <= last; i++) {
            var prev50 = currentData.ma50[i - 1];
            var prev200 = currentData.ma200[i - 1];
            var cur50 = currentData.ma50[i];
            var cur200 = currentData.ma200[i];
            if (prev50 != null && prev200 != null && cur50 != null && cur200 != null) {
                if (prev50 <= prev200 && cur50 > cur200) {
                    signals.push({ type: 'golden', text: '골든크로스 발생! (MA50이 MA200을 상향 돌파) — 강력한 매수 시그널' });
                }
                if (prev50 >= prev200 && cur50 < cur200) {
                    signals.push({ type: 'death', text: '데드크로스 발생! (MA50이 MA200을 하향 돌파) — 강력한 매도 시그널' });
                }
            }
        }
    }

    // MA 배열 분석
    if (ma50Val != null && ma100Val != null && ma200Val != null) {
        if (ma50Val > ma100Val && ma100Val > ma200Val) {
            signals.push({ type: 'bullish', text: 'MA 정배열 (MA50 > MA100 > MA200) — 상승 추세 지속 가능' });
        } else if (ma50Val < ma100Val && ma100Val < ma200Val) {
            signals.push({ type: 'bearish', text: 'MA 역배열 (MA50 < MA100 < MA200) — 하락 추세 지속 가능' });
        }
    }

    if (signals.length === 0) {
        panel.style.display = 'none';
        return;
    }

    var html = '';
    signals.forEach(function(s) {
        var icon, cls;
        if (s.type === 'golden') { icon = '&#9733;'; cls = 'text-warning'; }
        else if (s.type === 'death') { icon = '&#9888;'; cls = 'text-danger'; }
        else if (s.type === 'bullish') { icon = '&#9650;'; cls = 'text-danger'; }
        else { icon = '&#9660;'; cls = 'text-primary'; }
        html += '<div class="mb-2 ' + cls + ' small">' + icon + ' ' + s.text + '</div>';
    });

    content.innerHTML = html;
    panel.style.display = 'block';
}

// ── 종합 매매 분석 ──

function analyzeTradingSignal() {
    var panel = document.getElementById('tradingAnalysisPanel');
    var content = document.getElementById('tradingAnalysisContent');
    var verdictEl = document.getElementById('tradingVerdict');
    if (!currentData || !currentData.candles || currentData.candles.length < 30) {
        content.innerHTML = '<div class="text-secondary small">분석에 필요한 데이터가 부족합니다 (최소 30일 필요).</div>';
        verdictEl.textContent = '';
        return;
    }

    var last = currentData.candles.length - 1;
    var price = currentData.candles[last].close;
    var prevPrice = currentData.candles[last - 1].close;
    var volume = currentData.candles[last].volume;

    // 최근 20일 평균 거래량
    var volSum = 0;
    var volCount = Math.min(20, last);
    for (var vi = last - volCount; vi < last; vi++) {
        volSum += currentData.candles[vi].volume;
    }
    var avgVolume = volSum / volCount;

    var scores = []; // { name, score(-2~+2), reason }

    // 1) MA 배열 분석
    var ma5Val = safeVal(currentData.ma5, last);
    var ma20Val = safeVal(currentData.ma20, last);
    var ma50Val = safeVal(currentData.ma50, last);
    var ma100Val = safeVal(currentData.ma100, last);
    var ma200Val = safeVal(currentData.ma200, last);

    if (ma50Val && ma200Val) {
        if (ma50Val > ma200Val && price > ma50Val) {
            scores.push({ name: 'MA 배열', score: 2, reason: '정배열 + 가격이 MA50 위 (강한 상승 추세)' });
        } else if (ma50Val > ma200Val && price < ma50Val) {
            scores.push({ name: 'MA 배열', score: 1, reason: '정배열이나 가격이 MA50 하회 (추세 약화 주의)' });
        } else if (ma50Val < ma200Val && price < ma50Val) {
            scores.push({ name: 'MA 배열', score: -2, reason: '역배열 + 가격이 MA50 아래 (강한 하락 추세)' });
        } else if (ma50Val < ma200Val && price > ma50Val) {
            scores.push({ name: 'MA 배열', score: -1, reason: '역배열이나 가격이 MA50 상회 (반등 시도)' });
        }
    }

    // 2) 골든크로스 / 데드크로스 (최근 10일)
    if (ma50Val && ma200Val && last >= 10) {
        var crossFound = false;
        for (var ci = last - 9; ci <= last; ci++) {
            var p50 = safeVal(currentData.ma50, ci - 1);
            var p200 = safeVal(currentData.ma200, ci - 1);
            var c50 = safeVal(currentData.ma50, ci);
            var c200 = safeVal(currentData.ma200, ci);
            if (p50 && p200 && c50 && c200) {
                if (p50 <= p200 && c50 > c200) {
                    scores.push({ name: '크로스', score: 2, reason: '최근 골든크로스 발생 (MA50이 MA200 상향 돌파)' });
                    crossFound = true; break;
                }
                if (p50 >= p200 && c50 < c200) {
                    scores.push({ name: '크로스', score: -2, reason: '최근 데드크로스 발생 (MA50이 MA200 하향 돌파)' });
                    crossFound = true; break;
                }
            }
        }
        if (!crossFound) {
            scores.push({ name: '크로스', score: 0, reason: '최근 10일 내 크로스 시그널 없음' });
        }
    }

    // 3) RSI 분석
    var rsiVal = safeVal(currentData.rsi, last);
    if (rsiVal) {
        if (rsiVal >= 70) {
            scores.push({ name: 'RSI(' + rsiVal.toFixed(1) + ')', score: -2, reason: '과매수 구간 (70 이상) — 단기 조정 가능성' });
        } else if (rsiVal >= 60) {
            scores.push({ name: 'RSI(' + rsiVal.toFixed(1) + ')', score: 1, reason: '매수 우위 구간 (60~70) — 상승 모멘텀 유지' });
        } else if (rsiVal <= 30) {
            scores.push({ name: 'RSI(' + rsiVal.toFixed(1) + ')', score: 2, reason: '과매도 구간 (30 이하) — 반등 가능성' });
        } else if (rsiVal <= 40) {
            scores.push({ name: 'RSI(' + rsiVal.toFixed(1) + ')', score: -1, reason: '매도 우위 구간 (30~40) — 하락 모멘텀' });
        } else {
            scores.push({ name: 'RSI(' + rsiVal.toFixed(1) + ')', score: 0, reason: '중립 구간 (40~60)' });
        }
    }

    // 4) MACD 분석
    var macdVal = safeVal(currentData.macdLine, last);
    var macdSig = safeVal(currentData.macdSignal, last);
    var macdHist = safeVal(currentData.macdHistogram, last);
    var prevHist = safeVal(currentData.macdHistogram, last - 1);
    if (macdVal != null && macdSig != null) {
        if (macdVal > macdSig && macdHist > 0) {
            var rising = prevHist != null && macdHist > prevHist;
            scores.push({ name: 'MACD', score: rising ? 2 : 1, reason: 'MACD가 시그널 위 (매수 신호)' + (rising ? ' + 히스토그램 확대 중' : '') });
        } else if (macdVal < macdSig && macdHist < 0) {
            var falling = prevHist != null && macdHist < prevHist;
            scores.push({ name: 'MACD', score: falling ? -2 : -1, reason: 'MACD가 시그널 아래 (매도 신호)' + (falling ? ' + 히스토그램 확대 중' : '') });
        } else {
            scores.push({ name: 'MACD', score: 0, reason: 'MACD와 시그널이 수렴 중 (방향 탐색)' });
        }
    }

    // 5) 볼린저 밴드 분석
    var bbUpper = safeVal(currentData.bollingerUpper, last);
    var bbLower = safeVal(currentData.bollingerLower, last);
    var bbMiddle = safeVal(currentData.bollingerMiddle, last);
    if (bbUpper && bbLower && bbMiddle) {
        var bbWidth = bbUpper - bbLower;
        var bbPos = (price - bbLower) / bbWidth; // 0~1 범위
        if (price >= bbUpper) {
            scores.push({ name: '볼린저', score: -1, reason: '상단 밴드 도달/돌파 (' + formatNumber(bbUpper) + ') — 과열 주의' });
        } else if (price <= bbLower) {
            scores.push({ name: '볼린저', score: 1, reason: '하단 밴드 도달/돌파 (' + formatNumber(bbLower) + ') — 반등 가능' });
        } else if (bbPos > 0.5) {
            scores.push({ name: '볼린저', score: 0, reason: '밴드 상단부 위치 (중심선 위)' });
        } else {
            scores.push({ name: '볼린저', score: 0, reason: '밴드 하단부 위치 (중심선 아래)' });
        }
    }

    // 6) 거래량 분석
    if (avgVolume > 0) {
        var volRatio = volume / avgVolume;
        if (volRatio >= 2.0 && price > prevPrice) {
            scores.push({ name: '거래량', score: 2, reason: '거래량 급증(' + volRatio.toFixed(1) + '배) + 가격 상승 — 강한 매수세' });
        } else if (volRatio >= 2.0 && price < prevPrice) {
            scores.push({ name: '거래량', score: -2, reason: '거래량 급증(' + volRatio.toFixed(1) + '배) + 가격 하락 — 강한 매도세' });
        } else if (volRatio >= 1.5) {
            scores.push({ name: '거래량', score: price > prevPrice ? 1 : -1, reason: '거래량 증가(' + volRatio.toFixed(1) + '배) — 관심 집중' });
        } else {
            scores.push({ name: '거래량', score: 0, reason: '거래량 보통 수준 (' + volRatio.toFixed(1) + '배)' });
        }
    }

    // 7) 단기 모멘텀 (5일 수익률)
    if (last >= 5) {
        var price5ago = currentData.candles[last - 5].close;
        var momentum5 = ((price - price5ago) / price5ago * 100).toFixed(2);
        if (momentum5 > 5) {
            scores.push({ name: '5일 모멘텀', score: 1, reason: '5일간 +' + momentum5 + '% 상승 — 단기 강세' });
        } else if (momentum5 < -5) {
            scores.push({ name: '5일 모멘텀', score: -1, reason: '5일간 ' + momentum5 + '% 하락 — 단기 약세' });
        } else {
            scores.push({ name: '5일 모멘텀', score: 0, reason: '5일간 ' + (momentum5 > 0 ? '+' : '') + momentum5 + '% — 횡보' });
        }
    }

    // 종합 점수 계산
    var totalScore = 0;
    var maxScore = 0;
    scores.forEach(function(s) {
        totalScore += s.score;
        maxScore += 2;
    });

    // 판정
    var verdict, verdictClass, verdictBg;
    if (totalScore >= 5) {
        verdict = '강력 매수'; verdictClass = 'text-white'; verdictBg = 'bg-danger';
    } else if (totalScore >= 3) {
        verdict = '매수'; verdictClass = 'text-white'; verdictBg = 'bg-danger bg-opacity-75';
    } else if (totalScore >= 1) {
        verdict = '매수 관망'; verdictClass = 'text-light'; verdictBg = 'bg-warning bg-opacity-50';
    } else if (totalScore <= -5) {
        verdict = '강력 매도'; verdictClass = 'text-white'; verdictBg = 'bg-primary';
    } else if (totalScore <= -3) {
        verdict = '매도'; verdictClass = 'text-white'; verdictBg = 'bg-primary bg-opacity-75';
    } else if (totalScore <= -1) {
        verdict = '매도 관망'; verdictClass = 'text-light'; verdictBg = 'bg-info bg-opacity-50';
    } else {
        verdict = '중립 (관망)'; verdictClass = 'text-dark'; verdictBg = 'bg-secondary';
    }

    verdictEl.className = 'badge fs-6 ' + verdictBg + ' ' + verdictClass;
    verdictEl.textContent = verdict + ' (' + (totalScore > 0 ? '+' : '') + totalScore + '/' + maxScore + ')';

    // HTML 렌더링
    var html = '<div class="mb-3 small text-secondary">MA, RSI, MACD, 볼린저밴드, 거래량, 모멘텀을 종합 분석한 결과입니다.</div>';

    html += '<table class="table table-dark table-sm mb-3" style="font-size:0.82rem">';
    html += '<thead><tr class="text-secondary"><th>지표</th><th>신호</th><th>근거</th></tr></thead><tbody>';
    scores.forEach(function(s) {
        var signalIcon, signalText, signalClass;
        if (s.score >= 2)       { signalIcon = '&#9650;&#9650;'; signalText = '강력 매수'; signalClass = 'text-danger fw-bold'; }
        else if (s.score === 1) { signalIcon = '&#9650;';        signalText = '매수';      signalClass = 'text-danger'; }
        else if (s.score === 0) { signalIcon = '&#9472;';        signalText = '중립';      signalClass = 'text-secondary'; }
        else if (s.score === -1){ signalIcon = '&#9660;';        signalText = '매도';      signalClass = 'text-primary'; }
        else                    { signalIcon = '&#9660;&#9660;'; signalText = '강력 매도'; signalClass = 'text-primary fw-bold'; }

        html += '<tr><td class="text-light">' + escHtml(s.name) + '</td>';
        html += '<td class="' + signalClass + '">' + signalIcon + ' ' + signalText + '</td>';
        html += '<td class="text-secondary">' + escHtml(s.reason) + '</td></tr>';
    });
    html += '</tbody></table>';

    // 종합 코멘트
    html += '<div class="p-3 rounded" style="background:#1a1e2e;border:1px solid #2a2e39">';
    html += '<div class="fw-bold mb-2" style="color:#ffc107"><i class="bi bi-chat-left-text me-1"></i>종합 코멘트</div>';
    html += '<div class="small text-light" style="line-height:1.7">';
    html += generateComment(scores, totalScore, price, ma50Val, ma200Val, rsiVal, verdict);
    html += '</div></div>';

    html += '<div class="mt-2 text-secondary" style="font-size:0.7rem">* 본 분석은 기술적 지표 기반의 참고 자료이며 투자 권유가 아닙니다. 투자 판단은 본인 책임입니다.</div>';

    content.innerHTML = html;
}

function generateComment(scores, totalScore, price, ma50Val, ma200Val, rsiVal, verdict) {
    var lines = [];

    // 추세 판단
    if (ma50Val && ma200Val) {
        if (ma50Val > ma200Val) {
            lines.push('현재 이동평균선이 <strong style="color:#ef5350">정배열</strong> 상태로 중장기 상승 추세가 유지되고 있습니다.');
        } else {
            lines.push('현재 이동평균선이 <strong style="color:#42a5f5">역배열</strong> 상태로 중장기 하락 추세에 놓여 있습니다.');
        }
    }

    // RSI 코멘트
    if (rsiVal) {
        if (rsiVal >= 70) {
            lines.push('RSI ' + rsiVal.toFixed(1) + '으로 <strong style="color:#ef5350">과매수</strong> 영역에 진입해 있어, 단기 차익 실현 매물이 나올 수 있는 구간입니다.');
        } else if (rsiVal <= 30) {
            lines.push('RSI ' + rsiVal.toFixed(1) + '으로 <strong style="color:#42a5f5">과매도</strong> 영역에 있어, 기술적 반등이 기대되는 구간입니다.');
        } else if (rsiVal >= 50) {
            lines.push('RSI ' + rsiVal.toFixed(1) + '으로 매수 우위 구간에서 움직이고 있습니다.');
        } else {
            lines.push('RSI ' + rsiVal.toFixed(1) + '으로 매도 우위 구간에서 움직이고 있습니다.');
        }
    }

    // MACD 코멘트
    var macdScore = scores.find(function(s) { return s.name === 'MACD'; });
    if (macdScore) {
        if (macdScore.score >= 1) {
            lines.push('MACD에서 매수 시그널이 확인되며, 상승 모멘텀이 형성 중입니다.');
        } else if (macdScore.score <= -1) {
            lines.push('MACD에서 매도 시그널이 나타나고 있어, 하락 모멘텀에 주의가 필요합니다.');
        }
    }

    // 거래량 코멘트
    var volScore = scores.find(function(s) { return s.name === '거래량'; });
    if (volScore && Math.abs(volScore.score) >= 2) {
        lines.push('특히 <strong>거래량이 평소 대비 크게 증가</strong>하여 시장 참여자들의 관심이 집중되고 있습니다.');
    }

    // 크로스 코멘트
    var crossScore = scores.find(function(s) { return s.name === '크로스'; });
    if (crossScore && crossScore.score === 2) {
        lines.push('<strong style="color:#ffc107">골든크로스</strong>가 최근 발생하여 중기적 매수 신호가 강화되고 있습니다.');
    } else if (crossScore && crossScore.score === -2) {
        lines.push('<strong style="color:#ef5350">데드크로스</strong>가 최근 발생하여 중기적 매도 압력이 강화되고 있습니다.');
    }

    // 종합 판단
    lines.push('');
    if (totalScore >= 5) {
        lines.push('종합적으로 <strong style="color:#ef5350">다수의 지표가 매수를 가리키고 있으며</strong>, 추세와 모멘텀이 모두 우호적인 상황입니다. 다만, 급등 후에는 단기 조정 가능성도 열어두어야 합니다.');
    } else if (totalScore >= 3) {
        lines.push('종합적으로 <strong style="color:#ef5350">매수 우위</strong> 판단입니다. 추세 방향이 긍정적이나, 지지선 확인 후 진입하는 것이 안전합니다.');
    } else if (totalScore >= 1) {
        lines.push('전반적으로 매수 쪽에 약간 기울어 있으나, 확실한 시그널이 부족합니다. <strong>추가 확인 후 소량 분할 매수</strong>를 고려해 볼 수 있습니다.');
    } else if (totalScore <= -5) {
        lines.push('종합적으로 <strong style="color:#42a5f5">다수의 지표가 매도를 가리키고 있으며</strong>, 하락 추세가 강한 상황입니다. 보유 중이라면 손절 또는 비중 축소를 검토할 필요가 있습니다.');
    } else if (totalScore <= -3) {
        lines.push('종합적으로 <strong style="color:#42a5f5">매도 우위</strong> 판단입니다. 추세 반전 신호가 나올 때까지 신규 매수는 자제하는 것이 바람직합니다.');
    } else if (totalScore <= -1) {
        lines.push('전반적으로 매도 쪽에 약간 기울어 있습니다. <strong>추세 전환 시그널을 확인할 때까지 관망</strong>이 적절합니다.');
    } else {
        lines.push('현재 매수/매도 시그널이 혼재되어 <strong>뚜렷한 방향성이 없는 상태</strong>입니다. 확실한 추세가 형성될 때까지 관망하는 것을 권장합니다.');
    }

    return lines.join('<br>');
}

function safeVal(arr, idx) {
    if (!arr || idx < 0 || idx >= arr.length) return null;
    return arr[idx];
}

// ── 빠른 검색 ──

async function quickSearchDo(keyword) {
    var res = await fetch('/api/stock/search?keyword=' + encodeURIComponent(keyword));
    var results = await res.json();
    var container = document.getElementById('quickSearchResults');
    if (!results || results.length === 0) {
        container.innerHTML = '<div class="p-2 text-secondary small">결과 없음</div>';
        container.style.display = 'block';
        return;
    }
    container.innerHTML = results.map(function(s) {
        var badge = s.type === 'etf'
            ? '<span class="badge bg-info ms-1" style="font-size:0.65rem">ETF</span>'
            : '<span class="badge bg-secondary ms-1" style="font-size:0.65rem">' + escHtml(s.market || '') + '</span>';
        return '<a href="/chart/' + escHtml(s.symbol) + '" ' +
            'class="d-block px-3 py-2 text-light text-decoration-none border-bottom border-secondary search-item small">' +
            '<span class="fw-semibold">' + escHtml(s.name) + '</span> ' +
            '<span class="text-secondary font-monospace">' + escHtml(s.symbol) + '</span>' +
            badge + '</a>';
    }).join('');
    container.style.display = 'block';
}

// ── 유틸리티 ──

function toggleSeries(series, visible) {
    if (series) series.applyOptions({ visible: visible });
}

function showLoading(show) {
    document.getElementById('chartLoading').style.display = show ? 'flex' : 'none';
}

function showError(msg) {
    var el = document.getElementById('chartLoading');
    el.style.display = 'flex';
    el.innerHTML = '<div class="text-center text-danger">' +
        '<i class="bi bi-exclamation-triangle fs-3 mb-2"></i>' +
        '<div class="small">' + escHtml(msg) + '</div></div>';
}

function dateToTimestamp(dateStr) {
    if (!dateStr || dateStr.length < 8) return 0;
    var y = parseInt(dateStr.slice(0, 4), 10);
    var m = parseInt(dateStr.slice(4, 6), 10) - 1;
    var d = parseInt(dateStr.slice(6, 8), 10);
    return Date.UTC(y, m, d) / 1000;
}

function formatNumber(n) {
    if (n === undefined || n === null) return '-';
    return Number(n).toLocaleString('ko-KR');
}

function escHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

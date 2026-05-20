/**
 * TradingView Lightweight Charts v4 기반 차트 렌더링
 * MA5/20/50/100/200 이동평균선 + 볼린저 밴드
 * 타임프레임: 1분봉 / 3분봉 / 10분봉 / 일봉
 */

let mainChart = null, volumeChart = null;
let candleSeries = null, volumeSeries = null;
let ma5Series = null, ma20Series = null, ma50Series = null, ma100Series = null, ma200Series = null;
let bbUpperSeries = null, bbMiddleSeries = null, bbLowerSeries = null;

let currentSymbol = '';
let currentData = null;
let currentTimeframe = 'day';
let visibleDays = 66;

function isDarkTheme() {
    return document.body.getAttribute('data-bs-theme') !== 'light';
}

function getChartColors() {
    var dark = isDarkTheme();
    return {
        bg:     dark ? '#131722' : '#ffffff',
        grid:   dark ? '#1e222d' : '#f0f3fa',
        border: dark ? '#2a2e39' : '#d1d4dc',
        text:   dark ? '#d1d4dc' : '#191919',
    };
}

function initChart(symbol) {
    currentSymbol = symbol;

    createMainChart();
    createVolumeChart();

    // 타임프레임 탭 (1분/3분/10분/일)
    document.querySelectorAll('.timeframe-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.timeframe-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            currentTimeframe = btn.dataset.tf;

            var periodGroup = document.getElementById('periodBtnGroup');
            if (periodGroup) periodGroup.style.display = currentTimeframe === 'day' ? 'inline-flex' : 'none';

            loadChartData(currentSymbol);
        });
    });

    // 기간 탭 (일봉 전용)
    document.querySelectorAll('.period-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            document.querySelectorAll('.period-btn').forEach(function(b) { b.classList.remove('active'); });
            btn.classList.add('active');
            visibleDays = parseInt(btn.dataset.days, 10);
            if (currentData) applyVisibleRange();
        });
    });

    // USD/KRW 통화 토글
    document.querySelectorAll('.currency-toggle-btn').forEach(function(btn) {
        btn.addEventListener('click', function() {
            if (currentCurrency !== 'USD' || usdKrwRate <= 0) return;
            displayCurrency = btn.dataset.cur;
            showCurrencyToggle(displayCurrency);
            refreshPriceDisplay();
            if (currentData) {
                renderCandles(currentData);
                renderOverlays(currentData);
                updateMAValues();
                analyzeMA();
            }
        });
    });

    // 이동평균선 토글
    setupCheckbox('chkMA5', function() { return ma5Series; });
    setupCheckbox('chkMA20', function() { return ma20Series; });
    setupCheckbox('chkMA50', function() { return ma50Series; });
    setupCheckbox('chkMA100', function() { return ma100Series; });
    setupCheckbox('chkMA200', function() { return ma200Series; });

    // 볼린저 밴드 토글
    var chkBB = document.getElementById('chkBollinger');
    if (chkBB) {
        chkBB.addEventListener('change', function(e) {
            toggleSeries(bbUpperSeries, e.target.checked);
            toggleSeries(bbMiddleSeries, e.target.checked);
            toggleSeries(bbLowerSeries, e.target.checked);
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

function setupCheckbox(id, seriesGetter) {
    var el = document.getElementById(id);
    if (el) {
        el.addEventListener('change', function(e) { toggleSeries(seriesGetter(), e.target.checked); });
    }
}

// ── 차트 생성 ──

function createMainChart() {
    var container = document.getElementById('mainChart');
    var c = getChartColors();
    mainChart = LightweightCharts.createChart(container, {
        width: container.clientWidth, height: 480,
        layout: { background: { color: c.bg }, textColor: c.text },
        grid: { vertLines: { color: c.grid }, horzLines: { color: c.grid } },
        crosshair: { mode: LightweightCharts.CrosshairMode.Normal },
        rightPriceScale: { borderColor: c.border },
        timeScale: { borderColor: c.border, timeVisible: false, secondsVisible: false },
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
    });
}

function createVolumeChart() {
    var container = document.getElementById('volumeChart');
    var c = getChartColors();
    volumeChart = LightweightCharts.createChart(container, {
        width: container.clientWidth, height: 100,
        layout: { background: { color: c.bg }, textColor: c.text },
        grid: { vertLines: { color: c.grid }, horzLines: { visible: false } },
        rightPriceScale: { borderColor: c.border, scaleMargins: { top: 0.1, bottom: 0 } },
        timeScale: { borderColor: c.border, visible: false },
    });
    volumeSeries = volumeChart.addHistogramSeries({ priceFormat: { type: 'volume' }, priceScaleId: '' });
    volumeChart.priceScale('').applyOptions({ scaleMargins: { top: 0.1, bottom: 0 } });
}

// ── 데이터 로드 ──

async function loadChartData(symbol) {
    showLoading(true);
    try {
        var tfParam = currentTimeframe !== 'day' ? '?timeframe=' + currentTimeframe : '';
        var res = await fetch('/api/stock/' + symbol + '/candle' + tfParam);
        if (!res.ok) throw new Error('HTTP ' + res.status);
        currentData = await res.json();

        renderCandles(currentData);
        renderVolume(currentData);
        renderOverlays(currentData);

        var count = (currentData.candles || []).length;
        var isIntraday = currentTimeframe !== 'day';
        document.getElementById('infoCandleCount').textContent =
            '캔들 ' + count + '개' + (isIntraday ? '' : ' (최대 3년)');

        mainChart.applyOptions({
            timeScale: { timeVisible: isIntraday, secondsVisible: false }
        });

        if (isIntraday) {
            mainChart.timeScale().fitContent();
        } else {
            applyVisibleRange();
        }
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
    var rate = getDisplayRate();
    var candles = (data.candles || []).map(function(c) {
        return {
            time: dateToTimestamp(c.date),
            open:  c.open  * rate,
            high:  c.high  * rate,
            low:   c.low   * rate,
            close: c.close * rate
        };
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
    var rate = getDisplayRate();
    setLineData(ma5Series,      data.ma5,            data.candles, rate);
    setLineData(ma20Series,     data.ma20,           data.candles, rate);
    setLineData(ma50Series,     data.ma50,           data.candles, rate);
    setLineData(ma100Series,    data.ma100,          data.candles, rate);
    setLineData(ma200Series,    data.ma200,          data.candles, rate);
    setLineData(bbUpperSeries,  data.bollingerUpper, data.candles, rate);
    setLineData(bbMiddleSeries, data.bollingerMiddle,data.candles, rate);
    setLineData(bbLowerSeries,  data.bollingerLower, data.candles, rate);
}

function setLineData(series, values, candles, rate) {
    if (!series || !values || !candles) return;
    rate = rate || 1;
    var data = [];
    for (var i = 0; i < values.length && i < candles.length; i++) {
        if (values[i] != null) {
            data.push({ time: dateToTimestamp(candles[i].date), value: values[i] * rate });
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
    });
}

// ── 현재가 정보 ──

// 종목의 원래 통화 (USD / KRW)
var currentCurrency = 'KRW';
// 사용자가 선택한 표시 통화 (USD 종목만 토글 가능)
var displayCurrency = 'KRW';
// USD/KRW 환율 (시장 지표 API에서 가져옴)
var usdKrwRate = 0;
// /price API 응답 캐시 (토글 시 재계산에 사용)
var rawPriceData = null;

/** USD 종목을 KRW로 보여줄 때 적용할 배율 */
function getDisplayRate() {
    return (currentCurrency === 'USD' && displayCurrency === 'KRW' && usdKrwRate > 0)
        ? usdKrwRate : 1;
}

// price DTO 값 포맷 (USD: cents 단위 × 100으로 저장됨)
function fmtPrice(rawValue) {
    if (currentCurrency === 'USD') {
        var usd = rawValue / 100;
        if (displayCurrency === 'KRW' && usdKrwRate > 0)
            return formatNumber(Math.round(usd * usdKrwRate)) + '원';
        return '$' + usd.toFixed(2);
    }
    return formatNumber(rawValue) + '원';
}

function fmtChange(rawChange) {
    if (currentCurrency === 'USD') {
        var usd = Math.abs(rawChange / 100);
        if (displayCurrency === 'KRW' && usdKrwRate > 0)
            return formatNumber(Math.round(usd * usdKrwRate));
        return '$' + usd.toFixed(2);
    }
    return formatNumber(Math.abs(rawChange));
}

// 캔들 OHLC 포맷 (USD 종목: 원본 달러값 double)
function fmtCandle(value) {
    if (currentCurrency === 'USD') {
        if (displayCurrency === 'KRW' && usdKrwRate > 0)
            return formatNumber(Math.round(value * usdKrwRate)) + '원';
        return '$' + Number(value).toFixed(2);
    }
    return formatNumber(value) + '원';
}

/** 환율 로딩 — 전용 경량 API → 실패 시 시장 지표 API에서 추출 */
async function fetchUsdKrwRate() {
    try {
        var res = await fetch('/api/stock/usdkrw-rate');
        if (res.ok) {
            var rate = await res.json();
            if (rate > 0) { usdKrwRate = rate; return; }
        }
    } catch (e) { /* fallback 시도 */ }

    try {
        var res2 = await fetch('/api/stock/market-indicators');
        if (res2.ok) {
            var list = await res2.json();
            var item = list.find(function(i) { return i.id === 'USDKRW'; });
            if (item && item.currentValue > 0) usdKrwRate = item.currentValue;
        }
    } catch (e) { /* 환율 조회 완전 실패 — 토글 비활성화 */ }
}

async function loadPriceInfo(symbol) {
    try {
        var res = await fetch('/api/stock/' + symbol + '/price');
        if (!res.ok) return;
        var data = await res.json();

        currentCurrency = data.currency || 'KRW';
        displayCurrency = currentCurrency; // 초기값은 원래 통화
        rawPriceData = data;
        document.getElementById('stockName').textContent = data.name || symbol;

        var descEl = document.getElementById('stockDescription');
        if (descEl) {
            if (data.description) {
                descEl.textContent = data.description;
                descEl.style.display = '';
            } else {
                descEl.style.display = 'none';
            }
        }

        if (currentCurrency === 'USD') {
            await fetchUsdKrwRate();
            showCurrencyToggle(displayCurrency);
        } else {
            var tg = document.getElementById('currencyToggleGroup');
            if (tg) tg.style.display = 'none';
        }

        refreshPriceDisplay();
        loadStockNews(symbol, data.name || symbol);

    } catch (err) {
        document.getElementById('stockName').textContent =
            document.getElementById('stockSymbol').textContent || symbol;
        loadStockNews(symbol, symbol);
    }
}

async function loadStockNews(symbol, stockName) {
    var newsPanel = document.getElementById('stockNewsPanel');
    var newsEl = document.getElementById('stockNewsContent');
    if (!newsEl) return;
    if (isMarketIndex(symbol)) {
        if (newsPanel) newsPanel.style.display = 'none';
        return;
    }
    var keyword = stockName || symbol;
    try {
        var res = await fetch('/api/stock/news?limit=3&keywords=' + encodeURIComponent(keyword));
        if (!res.ok) throw new Error('뉴스 로드 실패');
        var news = await res.json();
        if (!news || news.length === 0) {
            newsEl.innerHTML = '<div class="text-secondary small text-center py-2">관련 뉴스 없음</div>';
            return;
        }
        newsEl.innerHTML = news.map(function(item, idx) {
            var dateStr = formatNewsDate(item.pubDate);
            var borderClass = idx < news.length - 1 ? ' border-bottom border-secondary' : '';
            return '<div class="py-2 px-1' + borderClass + '">' +
                '<a href="' + escHtml(item.link) + '" target="_blank" rel="noopener noreferrer" ' +
                'class="text-decoration-none small fw-semibold d-block mb-1 text-body" style="line-height:1.35;">' +
                escHtml(item.title) + '</a>' +
                '<span class="text-secondary" style="font-size:0.7rem;">' +
                escHtml(item.source || '') + (dateStr ? ' · ' + dateStr : '') +
                '</span></div>';
        }).join('');
    } catch (e) {
        newsEl.innerHTML = '<div class="text-secondary small text-center py-2">뉴스 로드 실패</div>';
    }
}

function formatNewsDate(pubDate) {
    if (!pubDate) return '';
    try {
        var d = new Date(pubDate);
        if (isNaN(d.getTime())) return '';
        var month = String(d.getMonth() + 1).padStart(2, '0');
        var day = String(d.getDate()).padStart(2, '0');
        var hour = String(d.getHours()).padStart(2, '0');
        var min = String(d.getMinutes()).padStart(2, '0');
        return month + '/' + day + ' ' + hour + ':' + min;
    } catch (e) { return ''; }
}

/** 토글 버튼 UI 갱신 */
function showCurrencyToggle(activeCur) {
    var group = document.getElementById('currencyToggleGroup');
    if (!group) return;
    group.style.display = '';
    group.querySelectorAll('.currency-toggle-btn').forEach(function(btn) {
        var isActive = btn.dataset.cur === activeCur;
        btn.className = isActive
            ? 'btn btn-warning btn-sm currency-toggle-btn active'
            : 'btn btn-outline-warning btn-sm currency-toggle-btn';
    });
}

/** 현재가 정보 패널을 현재 displayCurrency 기준으로 다시 그림 */
function refreshPriceDisplay() {
    if (!rawPriceData) return;
    var data = rawPriceData;

    var currentPriceEl = document.getElementById('infoCurrentPrice');
    var changeEl = document.getElementById('infoPriceChange');

    currentPriceEl.textContent = fmtPrice(data.currentPrice);

    var isUp = data.priceChange > 0;
    var isDown = data.priceChange < 0;
    var sign = isUp ? '+' : isDown ? '-' : '';
    currentPriceEl.className = 'info-value fs-4 ' + (isUp ? 'price-up' : isDown ? 'price-down' : 'price-flat');
    changeEl.innerHTML = '<span class="' + (isUp ? 'price-up' : isDown ? 'price-down' : 'price-flat') + '">' +
        sign + fmtChange(data.priceChange) + ' (' + sign + Math.abs(data.changeRate || 0).toFixed(2) + '%)</span>';

    document.getElementById('infoOpen').textContent = fmtPrice(data.open);
    document.getElementById('infoHigh').textContent = fmtPrice(data.high);
    document.getElementById('infoLow').textContent = fmtPrice(data.low);
    document.getElementById('infoVolume').textContent = formatNumber(data.volume);
}

function updateInfoPanel(candle) {
    if (!candle) return;
    document.getElementById('infoOpen').textContent = fmtCandle(candle.open);
    document.getElementById('infoHigh').textContent = fmtCandle(candle.high);
    document.getElementById('infoLow').textContent = fmtCandle(candle.low);
    document.getElementById('infoClose').textContent = fmtCandle(candle.close);
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

    var dark = isDarkTheme();
    var maList = [
        { name: 'MA 5', values: currentData.ma5, color: dark ? '#ffeb3b' : '#b8860b' },
        { name: 'MA 20', values: currentData.ma20, color: dark ? '#4fc3f7' : '#0277bd' },
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
            var diffClass = diff > 0 ? 'price-up' : diff < 0 ? 'price-down' : (isDarkTheme() ? 'text-light' : 'text-secondary');
            var diffSign = diff > 0 ? '+' : '';
            var valClass = isDarkTheme() ? 'text-light' : 'text-dark';
            html += '<div class="d-flex justify-content-between align-items-center mb-1">' +
                '<span class="small" style="color:' + ma.color + '">' + ma.name + '</span>' +
                '<span class="small ' + valClass + '">' + fmtCandle(val) + '</span>' +
                '<span class="small ' + diffClass + '">' + diffSign + diff + '%</span></div>';
        }
    });

    panel.innerHTML = html;
}

// ── MA 분석 ──

function analyzeMA() {
    var panel = document.getElementById('analysisPanel');
    var content = document.getElementById('analysisContent');
    if (isMarketIndex(currentSymbol)) {
        panel.style.display = 'none';
        return;
    }
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

    if (ma50Val != null) {
        if (price > ma50Val) signals.push({ type: 'bullish', text: '현재가가 MA50(' + fmtCandle(ma50Val) + ') 위에 위치 — 단기 상승 추세' });
        else signals.push({ type: 'bearish', text: '현재가가 MA50(' + fmtCandle(ma50Val) + ') 아래 위치 — 단기 하락 추세' });
    }
    if (ma200Val != null) {
        if (price > ma200Val) signals.push({ type: 'bullish', text: '현재가가 MA200(' + fmtCandle(ma200Val) + ') 위에 위치 — 장기 상승 추세' });
        else signals.push({ type: 'bearish', text: '현재가가 MA200(' + fmtCandle(ma200Val) + ') 아래 위치 — 장기 하락 추세' });
    }

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

// ── 대표 시장지수 목록 및 헬퍼 ──

var MARKET_INDEX_SYMBOLS = ['KOSPI', 'KOSDAQ', 'SP500', 'NASDAQ', 'DJI', 'SOX', 'VIX', 'WTI', 'USDKRW', 'GOLD', 'SILVER'];

function isMarketIndex(symbol) {
    return MARKET_INDEX_SYMBOLS.indexOf((symbol || '').toUpperCase()) >= 0;
}

function getMarketIndexInfo(symbol) {
    var info = {
        'KOSPI':  { name: '코스피 (KOSPI)',         icon: 'bi-bar-chart-fill',       keywords: '코스피,한국 증시,국내 주식시장,코스피 지수',
                    desc: '한국 종합주가지수 (KOSPI Composite Index). 한국거래소(KRX)에 상장된 모든 보통주의 시가총액 가중 지수로, 한국 증시 전반의 흐름을 나타냅니다.' },
        'KOSDAQ': { name: '코스닥 (KOSDAQ)',         icon: 'bi-bar-chart-fill',       keywords: '코스닥,기술주,바이오,벤처,코스닥 지수',
                    desc: '코스닥 시장 지수. 기술·바이오·벤처 중심의 중소·성장 기업들로 구성되어 고성장 기업의 동향을 반영합니다.' },
        'SP500':  { name: 'S&P 500',                icon: 'bi-globe-americas',       keywords: 'S&P500,미국 증시,뉴욕 증시,S&P 500',
                    desc: '미국 대형주 500개 기업으로 구성된 미국 증시의 핵심 벤치마크 지수. 전 세계 투자자의 미국 주식시장 가늠자로 사용됩니다.' },
        'NASDAQ': { name: '나스닥 (NASDAQ)',         icon: 'bi-globe-americas',       keywords: '나스닥,기술주,빅테크,미국 증시,나스닥 지수',
                    desc: '나스닥 종합주가지수. 애플·마이크로소프트·엔비디아 등 기술·성장주 중심으로 구성되어 글로벌 테크 산업의 온도를 보여줍니다.' },
        'DJI':    { name: '다우존스 (DJIA)',          icon: 'bi-globe-americas',       keywords: '다우존스,미국 증시,다우 지수,산업평균',
                    desc: '다우존스 산업평균지수 (DJIA). 미국 우량 30개 기업으로 구성된 가장 오래된 주가지수. 전통 산업·금융·소비재 대기업의 경기 체력을 반영합니다.' },
        'SOX':    { name: '필라델피아 반도체 (SOX)', icon: 'bi-cpu-fill',             keywords: '반도체,SOX,엔비디아,HBM,TSMC,필라델피아 반도체',
                    desc: '필라델피아 반도체 지수 (PHLX Semiconductor). 엔비디아·TSMC·인텔 등 글로벌 반도체 기업 30여 개로 구성되어 AI·HBM·파운드리 업황의 선행지표로 활용됩니다.' },
        'VIX':    { name: 'VIX 공포지수',           icon: 'bi-activity',             keywords: 'VIX,공포지수,시장 변동성,리스크,시장 불확실성',
                    desc: 'CBOE 변동성 지수 (VIX). S&P500 옵션 가격에서 도출한 향후 30일 예상 변동성으로, 시장 공포·불확실성의 척도입니다. 20 이하 안정, 30 이상 공포, 40 이상 패닉으로 봅니다.' },
        'WTI':    { name: 'WTI 유가',               icon: 'bi-droplet-fill',         keywords: '유가,원유,WTI,에너지,국제유가',
                    desc: '서부 텍사스산 원유 (WTI) 선물 가격. 국제 원유 가격의 기준으로, 에너지·물가·글로벌 경기의 선행지표 역할을 합니다.' },
        'USDKRW': { name: '달러/원 환율',           icon: 'bi-currency-exchange',    keywords: '달러 환율,원달러,외환시장,달러인덱스,환율',
                    desc: '미국 달러 대 한국 원화 환율. 달러 강세 시 원화 약세로 수출기업 수익은 유리하나 수입물가 상승·외국인 자금 이탈 우려가 있습니다.' },
        'GOLD':   { name: '금 (Gold)',               icon: 'bi-gem',                  keywords: '금 시세,금 선물,안전자산,인플레이션,금값',
                    desc: '국제 금 선물 가격 ($/트로이온스). 인플레이션 헤지·안전자산 대표주자. 달러 약세, 지정학적 리스크 고조 시 상승하는 경향이 있습니다.' },
        'SILVER': { name: '은 (Silver)',             icon: 'bi-gem',                  keywords: '은 시세,은 선물,산업용 금속,태양광,은값',
                    desc: '국제 은 선물 가격 ($/트로이온스). 안전자산이자 산업용 금속으로 금보다 변동성이 크며, 태양광·전기차·전자제품 수요와 연동됩니다.' },
    };
    var sym = (symbol || '').toUpperCase();
    return info[sym] || { name: sym, icon: 'bi-graph-up', desc: '', keywords: sym };
}

function generateMarketContextText(symbol, price, ma50Val, ma200Val, momentum20, momentum60, maAlignment) {
    var sym = (symbol || '').toUpperCase();
    var lines = [];
    var dark = isDarkTheme();
    var upColor   = '#ef5350';
    var downColor = dark ? '#42a5f5' : '#1565c0';
    var neutColor = dark ? '#ffc107' : '#d48800';

    if (ma50Val && ma200Val) {
        if (maAlignment === '정배열') {
            lines.push('이동평균선이 <strong style="color:' + upColor + '">정배열</strong> 상태를 유지하고 있어 중장기 상승 추세가 이어지고 있습니다.');
        } else if (maAlignment === '역배열') {
            lines.push('이동평균선이 <strong style="color:' + downColor + '">역배열</strong> 상태로 중장기 하락 압력이 지속되고 있습니다.');
        } else {
            lines.push('이동평균선이 <strong style="color:' + neutColor + '">혼조</strong> 상태로 방향성을 모색하는 국면입니다.');
        }
    }

    if (sym === 'VIX') {
        if (price < 15) {
            lines.push('현재 VIX가 <strong style="color:' + upColor + '">15 이하</strong>로 시장 참여자들이 매우 낙관적인 심리를 보이고 있습니다. 과도한 낙관은 조정의 전조일 수 있습니다.');
        } else if (price < 20) {
            lines.push('VIX가 15~20 구간으로 <strong>안정적인</strong> 시장 환경이 유지되고 있습니다.');
        } else if (price < 30) {
            lines.push('VIX가 20~30 구간으로 <strong style="color:' + neutColor + '">불확실성이 높아진</strong> 상태입니다. 변동성 확대에 대비한 리스크 관리가 필요합니다.');
        } else if (price < 40) {
            lines.push('VIX가 <strong style="color:' + downColor + '">30 이상의 공포</strong> 구간입니다. 과거 사례상 이 수준에서 역발상 매수 기회가 나타나기도 합니다.');
        } else {
            lines.push('VIX가 <strong style="color:' + downColor + '">40 이상의 극도의 공포</strong> 수준으로, 금융 위기급 변동성이 나타나고 있어 각별한 주의가 필요합니다.');
        }
    } else if (sym === 'USDKRW') {
        if (price > 1450) {
            lines.push('달러/원 환율이 <strong style="color:' + neutColor + '">1,450원 이상</strong>의 초고환율 구간입니다. 수출 기업엔 유리하나 수입 물가 급등 및 외국인 자금 이탈 우려가 커집니다.');
        } else if (price > 1350) {
            lines.push('달러/원 환율이 1,350~1,450원 구간으로 <strong>원화 약세</strong> 기조가 이어지고 있습니다. 외환시장 동향을 주시할 필요가 있습니다.');
        } else if (price > 1250) {
            lines.push('달러/원 환율이 1,250~1,350원 구간의 <strong>중립적</strong>인 수준입니다.');
        } else {
            lines.push('달러/원 환율이 1,250원 이하로 <strong>원화 강세</strong> 구간에 위치합니다. 수출기업 수익성에는 부담이 될 수 있습니다.');
        }
    } else if (sym === 'WTI') {
        if (price > 90) {
            lines.push('WTI 유가가 <strong style="color:' + neutColor + '">90달러 이상</strong>의 고유가 구간입니다. 물가 상승 압력과 경기 둔화 우려가 높아질 수 있습니다.');
        } else if (price > 70) {
            lines.push('WTI 유가가 70~90달러 구간의 <strong>안정적인</strong> 수준을 유지하고 있습니다.');
        } else {
            lines.push('WTI 유가가 <strong style="color:' + downColor + '">70달러 이하</strong>의 저유가 구간입니다. 경기 침체 우려가 반영되었을 가능성이 있습니다.');
        }
    } else if (sym === 'GOLD') {
        lines.push('금은 인플레이션 헤지 및 안전자산으로 달러 약세·지정학적 리스크 고조 시 강세를 보입니다.');
        if (momentum20 !== null && parseFloat(momentum20) > 5) {
            lines.push('최근 1개월 강한 상승세로 <strong style="color:' + upColor + '">안전자산 수요가 높아지는</strong> 신호로 볼 수 있습니다.');
        } else if (momentum20 !== null && parseFloat(momentum20) < -5) {
            lines.push('최근 1개월 조정을 받고 있어 <strong style="color:' + downColor + '">리스크온 분위기</strong>가 형성될 수 있습니다.');
        }
    } else if (sym === 'SOX') {
        lines.push('필라델피아 반도체 지수는 AI·데이터센터·모바일 수요를 반영하는 <strong>반도체 업황 선행지표</strong>입니다.');
        if (ma200Val && price > ma200Val) {
            lines.push('200일 이평선 상단을 유지하며 <strong style="color:' + upColor + '">반도체 장기 상승 사이클</strong>이 지속되고 있습니다.');
        } else if (ma200Val && price < ma200Val) {
            lines.push('200일 이평선 하단에 위치해 있어 <strong style="color:' + downColor + '">반도체 업황 둔화 우려</strong>가 반영되고 있습니다.');
        }
    }

    if (momentum20 !== null) {
        var m20 = parseFloat(momentum20);
        if (Math.abs(m20) > 8) {
            var dir = m20 > 0 ? '상승' : '하락';
            var cls = m20 > 0 ? upColor : downColor;
            lines.push('최근 1개월간 <strong style="color:' + cls + '">' + (m20 > 0 ? '+' : '') + m20 + '%의 강한 ' + dir + '세</strong>를 보이고 있습니다.');
        }
    }

    return lines.join('<br>') || '현재 데이터를 분석 중입니다.';
}

async function analyzeMarketIndex() {
    var panel = document.getElementById('tradingAnalysisPanel');
    var content = document.getElementById('tradingAnalysisContent');
    var verdictEl = document.getElementById('tradingVerdict');
    var titleEl = document.getElementById('tradingPanelTitle');

    var maValuesPanel = document.getElementById('maValuesPanel');
    var indicatorPanel = document.querySelector('.indicator-panel');
    if (maValuesPanel) maValuesPanel.style.display = 'none';
    if (indicatorPanel) indicatorPanel.style.display = 'none';

    if (titleEl) {
        titleEl.innerHTML = '<i class="bi bi-graph-up-arrow me-1"></i>시장 현황 분석';
    }
    if (verdictEl) {
        verdictEl.style.display = 'none';
        verdictEl.textContent = '';
    }

    if (!currentData || !currentData.candles || currentData.candles.length < 5) {
        content.innerHTML = '<div class="text-secondary small">분석 데이터가 부족합니다.</div>';
        return;
    }

    var dark = isDarkTheme();
    var indexInfo = getMarketIndexInfo(currentSymbol);
    var last = currentData.candles.length - 1;
    var price = currentData.candles[last].close;

    var ma20Val  = safeVal(currentData.ma20,  last);
    var ma50Val  = safeVal(currentData.ma50,  last);
    var ma100Val = safeVal(currentData.ma100, last);
    var ma200Val = safeVal(currentData.ma200, last);

    var momentum20 = last >= 20 ? ((price - currentData.candles[last - 20].close) / currentData.candles[last - 20].close * 100).toFixed(2) : null;
    var momentum60 = last >= 60 ? ((price - currentData.candles[last - 60].close) / currentData.candles[last - 60].close * 100).toFixed(2) : null;

    var maAlignment = '';
    if (ma50Val && ma100Val && ma200Val) {
        if (ma50Val > ma100Val && ma100Val > ma200Val)      maAlignment = '정배열';
        else if (ma50Val < ma100Val && ma100Val < ma200Val) maAlignment = '역배열';
        else                                                 maAlignment = '혼조';
    }

    var trendRows = [];
    if (ma20Val) {
        var d20 = ((price - ma20Val) / ma20Val * 100).toFixed(2);
        trendRows.push({ label: 'MA 20 대비', value: (d20 > 0 ? '+' : '') + d20 + '%', desc: '20일선 ' + (price > ma20Val ? '위' : '아래') + ' (단기)', cls: price > ma20Val ? 'text-danger' : 'text-primary' });
    }
    if (ma50Val) {
        var d50 = ((price - ma50Val) / ma50Val * 100).toFixed(2);
        trendRows.push({ label: 'MA 50 대비', value: (d50 > 0 ? '+' : '') + d50 + '%', desc: '50일선 ' + (price > ma50Val ? '위' : '아래') + ' (중기)', cls: price > ma50Val ? 'text-danger' : 'text-primary' });
    }
    if (ma200Val) {
        var d200 = ((price - ma200Val) / ma200Val * 100).toFixed(2);
        trendRows.push({ label: 'MA 200 대비', value: (d200 > 0 ? '+' : '') + d200 + '%', desc: '200일선 ' + (price > ma200Val ? '위' : '아래') + ' (장기)', cls: price > ma200Val ? 'text-danger' : 'text-primary' });
    }
    if (momentum20 !== null) {
        trendRows.push({ label: '1개월 변화', value: (parseFloat(momentum20) >= 0 ? '+' : '') + momentum20 + '%', desc: '최근 20 거래일 등락률', cls: parseFloat(momentum20) >= 0 ? 'text-danger' : 'text-primary' });
    }
    if (momentum60 !== null) {
        trendRows.push({ label: '3개월 변화', value: (parseFloat(momentum60) >= 0 ? '+' : '') + momentum60 + '%', desc: '최근 60 거래일 등락률', cls: parseFloat(momentum60) >= 0 ? 'text-danger' : 'text-primary' });
    }
    if (maAlignment) {
        var alignCls = maAlignment === '정배열' ? 'text-danger' : maAlignment === '역배열' ? 'text-primary' : 'text-secondary';
        trendRows.push({ label: 'MA 배열', value: maAlignment, desc: 'MA50/100/200 배열 상태', cls: alignCls });
    }

    var contextText = generateMarketContextText(currentSymbol, price, ma50Val, ma200Val, momentum20, momentum60, maAlignment);

    var tdText  = dark ? 'text-light' : 'text-dark';
    var bgPanel = dark ? '#1a1e2e' : '#f8f9fa';
    var bdPanel = dark ? '#2a2e39' : '#dee2e6';
    var bgDesc  = dark ? '#0d1117'  : '#e9ecef';

    var html = '';

    html += '<div class="mb-3 p-3 rounded" style="background:' + bgDesc + ';border-left:3px solid #ffc107">';
    html += '<div class="small fw-bold mb-1" style="color:#ffc107"><i class="bi ' + indexInfo.icon + ' me-1"></i>' + escHtml(indexInfo.name) + '</div>';
    html += '<div class="small ' + tdText + '" style="line-height:1.6">' + escHtml(indexInfo.desc) + '</div>';
    html += '</div>';

    if (trendRows.length > 0) {
        html += '<table class="table ' + (dark ? 'table-dark' : '') + ' table-sm mb-3" style="font-size:0.82rem">';
        html += '<thead><tr class="text-secondary"><th>지표</th><th>현재 수준</th><th>의미</th></tr></thead><tbody>';
        trendRows.forEach(function(row) {
            html += '<tr><td class="' + tdText + '">' + escHtml(row.label) + '</td>';
            html += '<td class="' + row.cls + ' fw-semibold">' + escHtml(row.value) + '</td>';
            html += '<td class="text-secondary">' + escHtml(row.desc) + '</td></tr>';
        });
        html += '</tbody></table>';
    }

    html += '<div class="p-3 rounded mb-3" style="background:' + bgPanel + ';border:1px solid ' + bdPanel + '">';
    html += '<div class="fw-bold mb-2" style="color:' + (dark ? '#ffc107' : '#d48800') + '"><i class="bi bi-lightbulb me-1"></i>현황 분석</div>';
    html += '<div class="small ' + tdText + '" style="line-height:1.8">' + contextText + '</div>';
    html += '</div>';

    html += '<div class="mt-2 mb-1 small fw-semibold text-secondary"><i class="bi bi-newspaper me-1"></i>관련 뉴스</div>';
    html += '<div id="marketIndexNewsInline"><div class="text-secondary small text-center py-2">뉴스 로딩 중...</div></div>';

    html += '<div class="mt-2 text-secondary" style="font-size:0.7rem">* 기술적 지표 기반 참고 자료입니다. 실제 투자 결정 시 다양한 요인을 종합 검토하세요.</div>';

    content.innerHTML = html;

    // 뉴스 비동기 로드
    try {
        var res = await fetch('/api/stock/news?limit=5&keywords=' + encodeURIComponent(indexInfo.keywords || currentSymbol));
        var newsEl = document.getElementById('marketIndexNewsInline');
        if (!newsEl) return;
        if (!res.ok) throw new Error('뉴스 로드 실패');
        var news = await res.json();
        if (!news || news.length === 0) {
            newsEl.innerHTML = '<div class="text-secondary small text-center py-2">관련 뉴스 없음</div>';
            return;
        }
        var dark2 = isDarkTheme();
        newsEl.innerHTML = news.map(function(item, idx) {
            var dateStr = formatNewsDate(item.pubDate);
            var borderCls = idx < news.length - 1 ? ' border-bottom ' + (dark2 ? 'border-secondary' : 'border-light-subtle') : '';
            return '<div class="py-2 px-1' + borderCls + '">' +
                '<a href="' + escHtml(item.link) + '" target="_blank" rel="noopener noreferrer" ' +
                'class="text-decoration-none small fw-semibold d-block mb-1 text-body" style="line-height:1.35;">' +
                escHtml(item.title) + '</a>' +
                '<span class="text-secondary" style="font-size:0.7rem;">' +
                escHtml(item.source || '') + (dateStr ? ' · ' + dateStr : '') + '</span></div>';
        }).join('');
    } catch (e) {
        var newsElErr = document.getElementById('marketIndexNewsInline');
        if (newsElErr) newsElErr.innerHTML = '<div class="text-secondary small text-center py-2">뉴스 로드 실패</div>';
    }
}

// ── 종합 매매 분석 ──

function analyzeTradingSignal() {
    if (isMarketIndex(currentSymbol)) {
        analyzeMarketIndex();
        return;
    }

    var panel = document.getElementById('tradingAnalysisPanel');
    var content = document.getElementById('tradingAnalysisContent');
    var verdictEl = document.getElementById('tradingVerdict');

    // 일반 종목: 패널 제목 원복 + verdict 표시 + 숨겼던 패널 복원
    var titleEl = document.getElementById('tradingPanelTitle');
    if (titleEl) titleEl.innerHTML = '<i class="bi bi-lightning-charge-fill me-1"></i>종합 매매 분석';
    if (verdictEl) verdictEl.style.display = '';
    var maValuesPanel = document.getElementById('maValuesPanel');
    var indicatorPanel = document.querySelector('.indicator-panel');
    if (maValuesPanel) maValuesPanel.style.display = '';
    if (indicatorPanel) indicatorPanel.style.display = '';

    if (!currentData || !currentData.candles || currentData.candles.length < 30) {
        content.innerHTML = '<div class="text-secondary small">분석에 필요한 데이터가 부족합니다 (최소 30개 캔들 필요).</div>';
        verdictEl.textContent = '';
        return;
    }

    var last = currentData.candles.length - 1;
    var price = currentData.candles[last].close;
    var prevPrice = currentData.candles[last - 1].close;
    var volume = currentData.candles[last].volume;

    var volSum = 0;
    var volCount = Math.min(20, last);
    for (var vi = last - volCount; vi < last; vi++) {
        volSum += currentData.candles[vi].volume;
    }
    var avgVolume = volSum / volCount;

    var scores = [];

    // 1) MA 배열 분석
    var ma50Val = safeVal(currentData.ma50, last);
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

    // 2) 골든크로스 / 데드크로스 (최근 10캔들)
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
            scores.push({ name: '크로스', score: 0, reason: '최근 크로스 시그널 없음' });
        }
    }

    // 3) 볼린저 밴드 분석
    var bbUpper = safeVal(currentData.bollingerUpper, last);
    var bbLower = safeVal(currentData.bollingerLower, last);
    if (bbUpper && bbLower) {
        if (price >= bbUpper) {
            scores.push({ name: '볼린저', score: -1, reason: '상단 밴드 도달/돌파 — 과열 주의' });
        } else if (price <= bbLower) {
            scores.push({ name: '볼린저', score: 1, reason: '하단 밴드 도달/돌파 — 반등 가능' });
        } else {
            var bbPos = (price - bbLower) / (bbUpper - bbLower);
            scores.push({ name: '볼린저', score: 0, reason: '밴드 ' + (bbPos > 0.5 ? '상단부' : '하단부') + ' 위치' });
        }
    }

    // 4) 거래량 분석
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

    // 5) 단기 모멘텀 (5캔들 수익률)
    if (last >= 5) {
        var price5ago = currentData.candles[last - 5].close;
        var momentum5 = ((price - price5ago) / price5ago * 100).toFixed(2);
        if (momentum5 > 5) {
            scores.push({ name: '단기 모멘텀', score: 1, reason: '최근 5캔들 +' + momentum5 + '% 상승 — 단기 강세' });
        } else if (momentum5 < -5) {
            scores.push({ name: '단기 모멘텀', score: -1, reason: '최근 5캔들 ' + momentum5 + '% 하락 — 단기 약세' });
        } else {
            scores.push({ name: '단기 모멘텀', score: 0, reason: '최근 5캔들 ' + (momentum5 > 0 ? '+' : '') + momentum5 + '% — 횡보' });
        }
    }

    // 종합 점수
    var totalScore = 0;
    var maxScore = 0;
    scores.forEach(function(s) {
        totalScore += s.score;
        maxScore += 2;
    });

    var verdict, verdictClass, verdictBg;
    if (totalScore >= 5) {
        verdict = '강력 매수'; verdictClass = 'text-white'; verdictBg = 'bg-danger';
    } else if (totalScore >= 3) {
        verdict = '매수'; verdictClass = 'text-white'; verdictBg = 'bg-danger bg-opacity-75';
    } else if (totalScore >= 1) {
        verdict = '매수 관망'; verdictClass = isDarkTheme() ? 'text-light' : 'text-dark'; verdictBg = 'bg-warning bg-opacity-50';
    } else if (totalScore <= -5) {
        verdict = '강력 매도'; verdictClass = 'text-white'; verdictBg = 'bg-primary';
    } else if (totalScore <= -3) {
        verdict = '매도'; verdictClass = 'text-white'; verdictBg = 'bg-primary bg-opacity-75';
    } else if (totalScore <= -1) {
        verdict = '매도 관망'; verdictClass = isDarkTheme() ? 'text-light' : 'text-dark'; verdictBg = 'bg-info bg-opacity-50';
    } else {
        verdict = '중립 (관망)'; verdictClass = 'text-dark'; verdictBg = 'bg-secondary';
    }

    verdictEl.className = 'badge fs-6 ' + verdictBg + ' ' + verdictClass;
    verdictEl.textContent = verdict + ' (' + (totalScore > 0 ? '+' : '') + totalScore + '/' + maxScore + ')';

    var dark = isDarkTheme();
    var tdText = dark ? 'text-light' : 'text-dark';
    var commentBg = dark ? 'background:#1a1e2e;border:1px solid #2a2e39' : 'background:#f8f9fa;border:1px solid #dee2e6';
    var commentText = dark ? 'text-light' : 'text-dark';

    var html = '<div class="mb-3 small text-secondary">MA, 볼린저밴드, 거래량, 모멘텀을 종합 분석한 결과입니다.</div>';

    html += '<table class="table ' + (dark ? 'table-dark' : '') + ' table-sm mb-3" style="font-size:0.82rem">';
    html += '<thead><tr class="text-secondary"><th>지표</th><th>신호</th><th>근거</th></tr></thead><tbody>';
    scores.forEach(function(s) {
        var signalIcon, signalText, signalClass;
        if (s.score >= 2)       { signalIcon = '&#9650;&#9650;'; signalText = '강력 매수'; signalClass = 'text-danger fw-bold'; }
        else if (s.score === 1) { signalIcon = '&#9650;';        signalText = '매수';      signalClass = 'text-danger'; }
        else if (s.score === 0) { signalIcon = '&#9472;';        signalText = '중립';      signalClass = 'text-secondary'; }
        else if (s.score === -1){ signalIcon = '&#9660;';        signalText = '매도';      signalClass = 'text-primary'; }
        else                    { signalIcon = '&#9660;&#9660;'; signalText = '강력 매도'; signalClass = 'text-primary fw-bold'; }

        html += '<tr><td class="' + tdText + '">' + escHtml(s.name) + '</td>';
        html += '<td class="' + signalClass + '">' + signalIcon + ' ' + signalText + '</td>';
        html += '<td class="text-secondary">' + escHtml(s.reason) + '</td></tr>';
    });
    html += '</tbody></table>';

    html += '<div class="p-3 rounded" style="' + commentBg + '">';
    html += '<div class="fw-bold mb-2" style="color:' + (dark ? '#ffc107' : '#d48800') + '"><i class="bi bi-chat-left-text me-1"></i>종합 코멘트</div>';
    html += '<div class="small ' + commentText + '" style="line-height:1.7">';
    html += generateComment(scores, totalScore, price, ma50Val, ma200Val, verdict);
    html += '</div></div>';

    html += '<div class="mt-2 text-secondary" style="font-size:0.7rem">* 본 분석은 기술적 지표 기반의 참고 자료이며 투자 권유가 아닙니다.</div>';

    content.innerHTML = html;
}

function generateComment(scores, totalScore, price, ma50Val, ma200Val, verdict) {
    var lines = [];
    var dark = isDarkTheme();
    var bearColor = dark ? '#42a5f5' : '#1565c0';
    var goldColor = dark ? '#ffc107' : '#d48800';

    if (ma50Val && ma200Val) {
        if (ma50Val > ma200Val) {
            lines.push('현재 이동평균선이 <strong style="color:#ef5350">정배열</strong> 상태로 중장기 상승 추세가 유지되고 있습니다.');
        } else {
            lines.push('현재 이동평균선이 <strong style="color:' + bearColor + '">역배열</strong> 상태로 중장기 하락 추세에 놓여 있습니다.');
        }
    }

    var volScore = scores.find(function(s) { return s.name === '거래량'; });
    if (volScore && Math.abs(volScore.score) >= 2) {
        lines.push('특히 <strong>거래량이 평소 대비 크게 증가</strong>하여 시장 참여자들의 관심이 집중되고 있습니다.');
    }

    var crossScore = scores.find(function(s) { return s.name === '크로스'; });
    if (crossScore && crossScore.score === 2) {
        lines.push('<strong style="color:' + goldColor + '">골든크로스</strong>가 최근 발생하여 중기적 매수 신호가 강화되고 있습니다.');
    } else if (crossScore && crossScore.score === -2) {
        lines.push('<strong style="color:#ef5350">데드크로스</strong>가 최근 발생하여 중기적 매도 압력이 강화되고 있습니다.');
    }

    lines.push('');
    if (totalScore >= 5) {
        lines.push('종합적으로 <strong style="color:#ef5350">다수의 지표가 매수를 가리키고 있습니다</strong>. 다만, 급등 후에는 단기 조정 가능성도 열어두어야 합니다.');
    } else if (totalScore >= 3) {
        lines.push('종합적으로 <strong style="color:#ef5350">매수 우위</strong> 판단입니다. 지지선 확인 후 진입하는 것이 안전합니다.');
    } else if (totalScore >= 1) {
        lines.push('매수 쪽에 약간 기울어 있으나, 확실한 시그널이 부족합니다. <strong>소량 분할 매수</strong>를 고려해 볼 수 있습니다.');
    } else if (totalScore <= -5) {
        lines.push('종합적으로 <strong style="color:#42a5f5">다수의 지표가 매도를 가리키고 있습니다</strong>. 보유 중이라면 손절 또는 비중 축소를 검토하세요.');
    } else if (totalScore <= -3) {
        lines.push('종합적으로 <strong style="color:#42a5f5">매도 우위</strong> 판단입니다. 추세 반전 신호가 나올 때까지 신규 매수는 자제하세요.');
    } else if (totalScore <= -1) {
        lines.push('매도 쪽에 약간 기울어 있습니다. <strong>추세 전환 시그널을 확인할 때까지 관망</strong>이 적절합니다.');
    } else {
        lines.push('매수/매도 시그널이 혼재되어 <strong>뚜렷한 방향성이 없는 상태</strong>입니다. 확실한 추세가 형성될 때까지 관망을 권장합니다.');
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
        var linkText = isDarkTheme() ? 'text-light' : 'text-dark';
        return '<a href="/chart/' + escHtml(s.symbol) + '" ' +
            'class="d-block px-3 py-2 ' + linkText + ' text-decoration-none border-bottom border-secondary search-item small">' +
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
    if (dateStr.length >= 12) {
        var hh = parseInt(dateStr.slice(8, 10), 10);
        var mm = parseInt(dateStr.slice(10, 12), 10);
        var ss = dateStr.length >= 14 ? parseInt(dateStr.slice(12, 14), 10) : 0;
        return Date.UTC(y, m, d, hh - 9, mm, ss) / 1000;
    }
    return Date.UTC(y, m, d) / 1000;
}

function formatNumber(n) {
    if (n === undefined || n === null) return '-';
    return Number(n).toLocaleString('ko-KR');
}

function escHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

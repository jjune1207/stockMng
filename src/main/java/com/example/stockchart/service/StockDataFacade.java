package com.example.stockchart.service;

import com.example.stockchart.dto.CandleDto;
import com.example.stockchart.dto.MarketIndicatorDto;
import com.example.stockchart.dto.StockPriceDto;
import com.example.stockchart.dto.StockSearchDto;
import com.example.stockchart.dto.UsNewsDto;
import com.example.stockchart.dto.WatchlistItemDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 네이버 증권 API 기반 주식/ETF 데이터 퍼사드
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataFacade {

    private final NaverStockService naverStockService;
    private final WatchlistService watchlistService;

    public StockPriceDto getCurrentPrice(String symbol) {
        return naverStockService.getCurrentPrice(symbol);
    }

    public List<CandleDto> getDailyCandles(String symbol) {
        return naverStockService.getDailyCandles(symbol);
    }

    public List<CandleDto> getMinuteCandles(String symbol, int intervalMinutes) {
        return naverStockService.getMinuteCandles(symbol, intervalMinutes);
    }

    public List<StockSearchDto> searchStock(String keyword) {
        return naverStockService.searchStock(keyword);
    }

    public List<StockSearchDto> getTopByVolume(String type, int limit) {
        if ("us_stock".equalsIgnoreCase(type) || "us_etf".equalsIgnoreCase(type)) {
            return naverStockService.getUsPopular(type, limit);
        }
        return naverStockService.getTopByVolume(type, limit);
    }

    public List<MarketIndicatorDto> getMarketIndicators() {
        return naverStockService.getMarketIndicators();
    }

    public List<UsNewsDto> getUsNews(int limit) {
        return naverStockService.getUsNews(limit);
    }

    public List<WatchlistItemDto> getWatchlist() {
        return watchlistService.getWatchlist();
    }

    public List<WatchlistItemDto> addWatchlistItem(WatchlistItemDto item) {
        return watchlistService.addWatchlistItem(item);
    }

    public List<WatchlistItemDto> removeWatchlistItem(String symbol) {
        return watchlistService.removeWatchlistItem(symbol);
    }

    public List<String> getWatchlistGroups() {
        return watchlistService.getGroups();
    }

    public List<WatchlistItemDto> moveToGroup(String symbol, String group) {
        return watchlistService.moveToGroup(symbol, group);
    }

    public List<WatchlistItemDto> deleteWatchlistGroup(String groupName) {
        return watchlistService.deleteGroup(groupName);
    }

    public List<WatchlistItemDto> renameWatchlistGroup(String oldName, String newName) {
        return watchlistService.renameGroup(oldName, newName);
    }
}

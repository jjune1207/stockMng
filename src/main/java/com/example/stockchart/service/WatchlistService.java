package com.example.stockchart.service;

import com.example.stockchart.dto.WatchlistItemDto;

import java.util.List;

public interface WatchlistService {

    List<WatchlistItemDto> getWatchlist();

    List<WatchlistItemDto> addWatchlistItem(WatchlistItemDto item);

    List<WatchlistItemDto> removeWatchlistItem(String symbol);

    List<String> getGroups();

    List<WatchlistItemDto> moveToGroup(String symbol, String group);

    List<WatchlistItemDto> deleteGroup(String groupName);

    List<WatchlistItemDto> renameGroup(String oldName, String newName);
}

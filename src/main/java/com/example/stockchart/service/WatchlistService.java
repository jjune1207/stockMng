package com.example.stockchart.service;

import com.example.stockchart.dto.WatchlistItemDto;

import java.util.List;

public interface WatchlistService {

    List<WatchlistItemDto> getWatchlist();

    List<WatchlistItemDto> addWatchlistItem(WatchlistItemDto item);

    List<WatchlistItemDto> removeWatchlistItem(String symbol);

    List<String> getGroups();

    List<WatchlistItemDto> moveToGroup(String symbol, String group);

    List<WatchlistItemDto> deleteGroup(String ownerName, String groupName);

    List<WatchlistItemDto> renameGroup(String ownerName, String oldName, String newName);

    List<WatchlistItemDto> updatePortfolio(String symbolOrKey, Double quantity, Double purchasePrice);

    List<String> getOwners();

    List<WatchlistItemDto> renameOwner(String oldName, String newName);

    List<WatchlistItemDto> deleteOwner(String ownerName);
}

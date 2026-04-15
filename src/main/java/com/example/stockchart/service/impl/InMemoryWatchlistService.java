package com.example.stockchart.service.impl;

import com.example.stockchart.dto.WatchlistItemDto;
import com.example.stockchart.service.WatchlistService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InMemoryWatchlistService implements WatchlistService {

    private static final int MAX_WATCHLIST_SIZE = 100;
    private static final String DATA_DIR = "data";
    private static final String WATCHLIST_FILE = "watchlist.json";

    /** ETF 종목명 패턴 — type 필드 없는 기존 데이터 마이그레이션용 */
    private static final Pattern ETF_NAME_PATTERN = Pattern.compile(
        "(KODEX|TIGER|KOSEF|ARIRANG|ACE|RISE|SOL|HANARO|KBSTAR|ETF|ETN|INVERS|LEVERAGE)",
        Pattern.CASE_INSENSITIVE
    );

    /** 복합키: symbol|group → 같은 종목을 여러 그룹에 등록 가능 */
    private final Map<String, WatchlistItemDto> watchlist = new ConcurrentHashMap<>();
    private final Object watchlistLock = new Object();
    private final ObjectMapper objectMapper;
    private final Path filePath;

    @Autowired
    public InMemoryWatchlistService(ObjectMapper objectMapper) {
        this(objectMapper, Paths.get(DATA_DIR));
    }

    InMemoryWatchlistService(ObjectMapper objectMapper, Path dataDirectory) {
        this.objectMapper = objectMapper;
        this.filePath = dataDirectory.resolve(WATCHLIST_FILE);
    }

    private String compositeKey(String symbol, String group) {
        return symbol + "|" + group;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }

    private String normalizeGroup(String group) {
        return (group == null || group.isBlank()) ? "" : group.trim();
    }

    private String resolveKeyForSymbol(String symbolOrKey) {
        String trimmed = normalizeSymbol(symbolOrKey);
        if (trimmed.isBlank()) {
            return null;
        }

        if (watchlist.containsKey(trimmed)) {
            return trimmed;
        }

        List<String> matchedKeys = watchlist.entrySet().stream()
            .filter(entry -> trimmed.equals(entry.getValue().getSymbol()))
            .map(Map.Entry::getKey)
            .toList();

        if (matchedKeys.isEmpty()) {
            return null;
        }
        if (matchedKeys.size() > 1) {
            throw new IllegalArgumentException("같은 종목이 여러 그룹에 등록되어 있습니다. symbol|group 형식으로 지정해 주세요.");
        }
        return matchedKeys.get(0);
    }

    private void persistSnapshotLocked() {
        try {
            Path parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path tempFile = Files.createTempFile(parent, "watchlist", ".tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), getWatchlistLocked());
                try {
                    Files.move(tempFile, filePath,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException atomicMoveException) {
                    Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempFile);
            }
        } catch (IOException e) {
            throw new IllegalStateException("관심 종목 파일 저장 실패: " + e.getMessage(), e);
        }
    }

    private List<WatchlistItemDto> getWatchlistLocked() {
        return watchlist.values().stream()
            .sorted(Comparator
                .comparing(WatchlistItemDto::getGroup, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WatchlistItemDto::getName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @PostConstruct
    void loadFromFile() {
        if (!Files.exists(filePath)) {
            log.info("관심 종목 파일 없음, 빈 목록으로 시작: {}", filePath.toAbsolutePath());
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(filePath);
            List<WatchlistItemDto> items = objectMapper.readValue(bytes, new TypeReference<>() {});
            boolean needsMigration = false;
            synchronized (watchlistLock) {
                watchlist.clear();
                for (WatchlistItemDto item : items) {
                    String normalizedSymbol = normalizeSymbol(item.getSymbol());
                    String group = normalizeGroup(item.getGroup());
                    item.setSymbol(normalizedSymbol);
                    item.setGroup(group);
                    // type 필드 없는 기존 데이터 마이그레이션: 종목명으로 ETF 여부 추론
                    if (item.getType() == null || item.getType().isBlank()) {
                        String name = item.getName() != null ? item.getName() : "";
                        boolean isEtf = ETF_NAME_PATTERN.matcher(name).find();
                        item.setType(isEtf ? "etf" : "stock");
                        needsMigration = true;
                    }
                    watchlist.put(compositeKey(normalizedSymbol, group), item);
                }
            }
            log.info("관심 종목 {}개 로드 완료: {}", watchlist.size(), filePath.toAbsolutePath());
            if (needsMigration) {
                try {
                    persistSnapshotLocked();
                } catch (IllegalStateException e) {
                    log.error("관심 종목 마이그레이션 저장 실패: {}", e.getMessage());
                }
                log.info("관심 종목 type 필드 마이그레이션 완료");
            }
        } catch (IOException e) {
            log.error("관심 종목 파일 로드 실패: {}", e.getMessage());
        }
    }

    @Override
    public List<WatchlistItemDto> getWatchlist() {
        synchronized (watchlistLock) {
            return getWatchlistLocked();
        }
    }

    @Override
    public List<String> getGroups() {
        synchronized (watchlistLock) {
            return watchlist.values().stream()
                .map(WatchlistItemDto::getGroup)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
        }
    }

    @Override
    public List<WatchlistItemDto> addWatchlistItem(WatchlistItemDto item) {
        synchronized (watchlistLock) {
            if (item == null || item.getSymbol() == null || item.getSymbol().isBlank()) {
                throw new IllegalArgumentException("관심 종목 코드는 필수입니다.");
            }
            String normalizedSymbol = normalizeSymbol(item.getSymbol());
            if (!normalizedSymbol.matches("^[A-Za-z0-9]{1,12}$")) {
                throw new IllegalArgumentException("올바른 종목 코드 형식이 아닙니다.");
            }
            String group = normalizeGroup(item.getGroup());
            if (group.isEmpty()) {
                throw new IllegalArgumentException("그룹을 선택해 주세요.");
            }

            String key = compositeKey(normalizedSymbol, group);
            if (!watchlist.containsKey(key) && watchlist.size() >= MAX_WATCHLIST_SIZE) {
                throw new IllegalArgumentException("관심 종목은 최대 " + MAX_WATCHLIST_SIZE + "개까지 등록할 수 있습니다.");
            }

            WatchlistItemDto saved = WatchlistItemDto.builder()
                .symbol(normalizedSymbol)
                .name(item.getName() == null || item.getName().isBlank() ? normalizedSymbol : item.getName().trim())
                .market(item.getMarket() == null ? "" : item.getMarket().trim())
                .type("etf".equalsIgnoreCase(item.getType()) ? "etf" : "stock")
                .group(group)
                .build();

            watchlist.put(key, saved);
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> removeWatchlistItem(String symbol) {
        synchronized (watchlistLock) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("삭제할 종목 코드를 입력해 주세요.");
            }
            // symbol|group 형식이면 정확히 삭제, 아니면 해당 symbol의 모든 항목 삭제
            String trimmed = normalizeSymbol(symbol);
            if (trimmed.contains("|")) {
                watchlist.remove(trimmed);
            } else {
                watchlist.entrySet().removeIf(e -> e.getValue().getSymbol().equals(trimmed));
            }
            persistSnapshotLocked();
            return new ArrayList<>(getWatchlistLocked());
        }
    }

    @Override
    public List<WatchlistItemDto> moveToGroup(String symbol, String group) {
        synchronized (watchlistLock) {
            if (symbol == null || symbol.isBlank()) {
                throw new IllegalArgumentException("종목 코드를 입력해 주세요.");
            }
            if (group == null || group.isBlank()) {
                throw new IllegalArgumentException("이동할 그룹을 지정해 주세요.");
            }

            String trimmed = normalizeSymbol(symbol);
            String targetGroup = normalizeGroup(group);
            String existingKey = resolveKeyForSymbol(trimmed);
            if (existingKey == null) {
                throw new IllegalArgumentException("관심 목록에 없는 항목입니다: " + trimmed);
            }

            WatchlistItemDto item = watchlist.remove(existingKey);
            item.setGroup(targetGroup);
            watchlist.put(compositeKey(item.getSymbol(), targetGroup), item);
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> deleteGroup(String groupName) {
        synchronized (watchlistLock) {
            if (groupName == null || groupName.isBlank()) {
                throw new IllegalArgumentException("그룹 이름을 입력해 주세요.");
            }
            String target = normalizeGroup(groupName);
            watchlist.entrySet().removeIf(e -> target.equals(e.getValue().getGroup()));
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> renameGroup(String oldName, String newName) {
        synchronized (watchlistLock) {
            if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
                throw new IllegalArgumentException("그룹 이름을 입력해 주세요.");
            }
            String from = normalizeGroup(oldName);
            String to = normalizeGroup(newName);
            List<WatchlistItemDto> toMove = watchlist.values().stream()
                .filter(item -> from.equals(item.getGroup()))
                .toList();
            toMove.forEach(item -> {
                watchlist.remove(compositeKey(item.getSymbol(), from));
                item.setGroup(to);
                watchlist.put(compositeKey(item.getSymbol(), to), item);
            });
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }
}

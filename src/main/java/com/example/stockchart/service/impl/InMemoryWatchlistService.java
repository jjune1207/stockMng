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

    /** 복합키: symbol|owner|group — 같은 종목을 여러 소유자/그룹에 등록 가능 */
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

    private String compositeKey(String symbol, String owner, String group) {
        return symbol + "|" + owner + "|" + group;
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim();
    }

    private String normalizeGroup(String group) {
        return (group == null || group.isBlank()) ? "" : group.trim();
    }

    private String normalizeOwner(String owner) {
        return (owner == null || owner.isBlank()) ? "나" : owner.trim();
    }

    /**
     * 단순 심볼 또는 복합키로 맵 키를 찾아 반환한다.
     * - 3파트(symbol|owner|group): 직접 조회
     * - 2파트(symbol|group): 레거시 형식 → symbol|나|group 으로 시도
     * - 1파트(symbol): 해당 심볼 단일 항목이면 반환, 복수면 예외
     */
    private String resolveKey(String symbolOrKey) {
        String trimmed = normalizeSymbol(symbolOrKey);
        if (trimmed.isBlank()) return null;

        if (watchlist.containsKey(trimmed)) return trimmed;

        String[] parts = trimmed.split("\\|", -1);

        if (parts.length == 3) {
            return null;
        }

        if (parts.length == 2) {
            String legacyKey = compositeKey(parts[0], "나", parts[1]);
            return watchlist.containsKey(legacyKey) ? legacyKey : null;
        }

        List<String> matchedKeys = watchlist.entrySet().stream()
            .filter(e -> trimmed.equals(e.getValue().getSymbol()))
            .map(Map.Entry::getKey)
            .toList();

        if (matchedKeys.isEmpty()) return null;
        if (matchedKeys.size() > 1) {
            throw new IllegalArgumentException("같은 종목이 여러 소유자/그룹에 등록되어 있습니다. symbol|소유자|그룹 형식으로 지정해 주세요.");
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
                .comparing(WatchlistItemDto::getOwner, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WatchlistItemDto::getGroup, String.CASE_INSENSITIVE_ORDER)
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

                    // type 필드 없는 기존 데이터 마이그레이션
                    if (item.getType() == null || item.getType().isBlank()) {
                        String name = item.getName() != null ? item.getName() : "";
                        item.setType(ETF_NAME_PATTERN.matcher(name).find() ? "etf" : "stock");
                        needsMigration = true;
                    }

                    // owner 필드 없는 기존 데이터 마이그레이션
                    if (item.getOwner() == null || item.getOwner().isBlank()) {
                        item.setOwner("나");
                        needsMigration = true;
                    }

                    watchlist.put(compositeKey(normalizedSymbol, item.getOwner(), group), item);
                }
            }
            log.info("관심 종목 {}개 로드 완료: {}", watchlist.size(), filePath.toAbsolutePath());
            if (needsMigration) {
                try {
                    persistSnapshotLocked();
                } catch (IllegalStateException e) {
                    log.error("관심 종목 마이그레이션 저장 실패: {}", e.getMessage());
                }
                log.info("관심 종목 마이그레이션 완료 (type/owner 필드)");
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
    public List<String> getOwners() {
        synchronized (watchlistLock) {
            return watchlist.values().stream()
                .map(WatchlistItemDto::getOwner)
                .filter(o -> o != null && !o.isBlank())
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
            String owner = normalizeOwner(item.getOwner());

            String key = compositeKey(normalizedSymbol, owner, group);
            if (!watchlist.containsKey(key) && watchlist.size() >= MAX_WATCHLIST_SIZE) {
                throw new IllegalArgumentException("관심 종목은 최대 " + MAX_WATCHLIST_SIZE + "개까지 등록할 수 있습니다.");
            }

            WatchlistItemDto saved = WatchlistItemDto.builder()
                .symbol(normalizedSymbol)
                .name(item.getName() == null || item.getName().isBlank() ? normalizedSymbol : item.getName().trim())
                .market(item.getMarket() == null ? "" : item.getMarket().trim())
                .type("etf".equalsIgnoreCase(item.getType()) ? "etf" : "stock")
                .group(group)
                .owner(owner)
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
            String trimmed = normalizeSymbol(symbol);
            String[] parts = trimmed.split("\\|", -1);

            if (parts.length == 3) {
                // 정확한 복합키(symbol|owner|group) 삭제
                watchlist.remove(trimmed);
            } else if (parts.length == 2) {
                // 레거시 형식(symbol|group) → symbol|나|group 로 시도
                String legacyKey = compositeKey(parts[0], "나", parts[1]);
                watchlist.remove(legacyKey);
            } else {
                // 심볼만: 해당 심볼 모든 항목 삭제
                watchlist.entrySet().removeIf(e -> e.getValue().getSymbol().equals(trimmed));
            }
            persistSnapshotLocked();
            return new ArrayList<>(getWatchlistLocked());
        }
    }

    @Override
    public List<WatchlistItemDto> moveToGroup(String symbolOrKey, String group) {
        synchronized (watchlistLock) {
            if (symbolOrKey == null || symbolOrKey.isBlank()) {
                throw new IllegalArgumentException("종목 코드를 입력해 주세요.");
            }
            if (group == null || group.isBlank()) {
                throw new IllegalArgumentException("이동할 그룹을 지정해 주세요.");
            }

            String trimmed = normalizeSymbol(symbolOrKey);
            String targetGroup = normalizeGroup(group);
            String existingKey = resolveKey(trimmed);
            if (existingKey == null) {
                throw new IllegalArgumentException("관심 목록에 없는 항목입니다: " + trimmed);
            }

            WatchlistItemDto item = watchlist.remove(existingKey);
            item.setGroup(targetGroup);
            watchlist.put(compositeKey(item.getSymbol(), item.getOwner(), targetGroup), item);
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> deleteGroup(String ownerName, String groupName) {
        synchronized (watchlistLock) {
            if (groupName == null || groupName.isBlank()) {
                throw new IllegalArgumentException("그룹 이름을 입력해 주세요.");
            }
            String targetOwner = normalizeOwner(ownerName);
            String targetGroup = normalizeGroup(groupName);
            watchlist.entrySet().removeIf(e -> {
                WatchlistItemDto it = e.getValue();
                return targetOwner.equals(it.getOwner()) && targetGroup.equals(it.getGroup());
            });
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> renameGroup(String ownerName, String oldName, String newName) {
        synchronized (watchlistLock) {
            if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
                throw new IllegalArgumentException("그룹 이름을 입력해 주세요.");
            }
            String targetOwner = normalizeOwner(ownerName);
            String from = normalizeGroup(oldName);
            String to = normalizeGroup(newName);
            List<WatchlistItemDto> toMove = watchlist.values().stream()
                .filter(it -> targetOwner.equals(it.getOwner()) && from.equals(it.getGroup()))
                .toList();
            toMove.forEach(it -> {
                watchlist.remove(compositeKey(it.getSymbol(), targetOwner, from));
                it.setGroup(to);
                watchlist.put(compositeKey(it.getSymbol(), targetOwner, to), it);
            });
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> updatePortfolio(String symbolOrKey, Double quantity, Double purchasePrice) {
        synchronized (watchlistLock) {
            String trimmed = normalizeSymbol(symbolOrKey);
            String key = watchlist.containsKey(trimmed) ? trimmed : resolveKey(trimmed);
            if (key == null) {
                throw new IllegalArgumentException("관심 목록에 없는 항목입니다: " + trimmed);
            }
            WatchlistItemDto item = watchlist.get(key);
            item.setQuantity(quantity);
            item.setPurchasePrice(purchasePrice);
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> renameOwner(String oldName, String newName) {
        synchronized (watchlistLock) {
            if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()) {
                throw new IllegalArgumentException("소유자 이름을 입력해 주세요.");
            }
            String from = normalizeOwner(oldName);
            String to = normalizeOwner(newName);
            List<WatchlistItemDto> toRename = watchlist.values().stream()
                .filter(it -> from.equals(it.getOwner()))
                .toList();
            toRename.forEach(it -> {
                watchlist.remove(compositeKey(it.getSymbol(), from, it.getGroup()));
                it.setOwner(to);
                watchlist.put(compositeKey(it.getSymbol(), to, it.getGroup()), it);
            });
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }

    @Override
    public List<WatchlistItemDto> deleteOwner(String ownerName) {
        synchronized (watchlistLock) {
            if (ownerName == null || ownerName.isBlank()) {
                throw new IllegalArgumentException("소유자 이름을 입력해 주세요.");
            }
            String target = normalizeOwner(ownerName);
            watchlist.entrySet().removeIf(e -> target.equals(e.getValue().getOwner()));
            persistSnapshotLocked();
            return getWatchlistLocked();
        }
    }
}

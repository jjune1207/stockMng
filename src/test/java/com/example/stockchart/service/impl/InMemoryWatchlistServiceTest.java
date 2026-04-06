package com.example.stockchart.service.impl;

import com.example.stockchart.dto.WatchlistItemDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryWatchlistServiceTest {

    private InMemoryWatchlistService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryWatchlistService(new ObjectMapper());
    }

    @Test
    void 관심종목을_정상_추가한다() {
        List<WatchlistItemDto> result = service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930")
            .name("삼성전자")
            .type("stock")
            .build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("005930");
        assertThat(result.get(0).getGroup()).isEqualTo("기본");
    }

    @Test
    void 그룹을_지정하여_추가한다() {
        List<WatchlistItemDto> result = service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930")
            .name("삼성전자")
            .type("stock")
            .group("성장주")
            .build());

        assertThat(result.get(0).getGroup()).isEqualTo("성장주");
    }

    @Test
    void 그룹을_이동할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").build());

        List<WatchlistItemDto> result = service.moveToGroup("005930", "배당주");
        assertThat(result.get(0).getGroup()).isEqualTo("배당주");
    }

    @Test
    void 그룹_삭제시_기본으로_이동한다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("테스트").build());

        List<WatchlistItemDto> result = service.deleteGroup("테스트");
        assertThat(result.get(0).getGroup()).isEqualTo("기본");
    }

    @Test
    void 기본_그룹은_삭제할_수_없다() {
        assertThatThrownBy(() -> service.deleteGroup("기본"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 그룹_이름을_변경할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("구이름").build());

        List<WatchlistItemDto> result = service.renameGroup("구이름", "새이름");
        assertThat(result.get(0).getGroup()).isEqualTo("새이름");
    }

    @Test
    void 잘못된_코드는_추가할_수_없다() {
        assertThatThrownBy(() -> service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("ABC")
            .name("잘못된코드")
            .type("stock")
            .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("종목 코드");
    }

    @Test
    void 관심종목을_삭제할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930")
            .name("삼성전자")
            .type("stock")
            .build());

        List<WatchlistItemDto> result = service.removeWatchlistItem("005930");
        assertThat(result).isEmpty();
    }

    @Test
    void 그룹_목록을_조회할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("성장주").build());
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("000660").name("SK하이닉스").type("stock").group("반도체").build());

        List<String> groups = service.getGroups();
        assertThat(groups).contains("기본", "성장주", "반도체");
    }
}

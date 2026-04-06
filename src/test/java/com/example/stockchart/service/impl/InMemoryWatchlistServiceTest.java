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
    void 그룹을_지정하여_추가한다() {
        List<WatchlistItemDto> result = service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930")
            .name("삼성전자")
            .type("stock")
            .group("성장주")
            .build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("005930");
        assertThat(result.get(0).getGroup()).isEqualTo("성장주");
    }

    @Test
    void 그룹_없이_추가하면_예외가_발생한다() {
        assertThatThrownBy(() -> service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930")
            .name("삼성전자")
            .type("stock")
            .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("그룹");
    }

    @Test
    void 같은_종목을_다른_그룹에_중복_등록할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("성장주").build());
        List<WatchlistItemDto> result = service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("배당주").build());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(WatchlistItemDto::getGroup)
            .containsExactlyInAnyOrder("배당주", "성장주");
    }

    @Test
    void 그룹을_이동할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("성장주").build());

        List<WatchlistItemDto> result = service.moveToGroup("005930|성장주", "배당주");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroup()).isEqualTo("배당주");
    }

    @Test
    void 그룹_삭제시_소속_종목도_삭제된다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("테스트").build());
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("000660").name("SK하이닉스").type("stock").group("보관").build());

        List<WatchlistItemDto> result = service.deleteGroup("테스트");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("000660");
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
            .group("테스트")
            .build()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("종목 코드");
    }

    @Test
    void 관심종목을_composite_key로_삭제할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("성장주").build());
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("배당주").build());

        List<WatchlistItemDto> result = service.removeWatchlistItem("005930|성장주");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getGroup()).isEqualTo("배당주");
    }

    @Test
    void 관심종목을_symbol로_전체삭제할_수_있다() {
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("성장주").build());
        service.addWatchlistItem(WatchlistItemDto.builder()
            .symbol("005930").name("삼성전자").type("stock").group("배당주").build());

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
        assertThat(groups).containsExactlyInAnyOrder("성장주", "반도체");
    }
}

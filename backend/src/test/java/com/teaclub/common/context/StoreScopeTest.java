package com.teaclub.common.context;

import com.teaclub.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * 门店数据范围单元测试：多门店隔离的核心逻辑。
 */
class StoreScopeTest {

    private LoginUser user(boolean all, Set<Long> storeIds) {
        return LoginUser.builder()
                .userId(1L).brandId(1L)
                .allStoreAccess(all)
                .storeIds(storeIds)
                .build();
    }

    @Test
    @DisplayName("ALL 范围可访问任意门店")
    void allScopeCanAccessAnyStore() {
        StoreScope scope = StoreScope.from(user(true, Set.of()));
        assertThat(scope.canAccess(1L)).isTrue();
        assertThat(scope.canAccess(999L)).isTrue();
        assertThat(scope.filterIds()).isNull();
        assertThat(scope.isEmptyLimited()).isFalse();
    }

    @Test
    @DisplayName("LIMITED 范围仅可访问授权门店")
    void limitedScopeOnlyOwnStores() {
        StoreScope scope = StoreScope.from(user(false, Set.of(1L, 2L)));
        assertThat(scope.canAccess(1L)).isTrue();
        assertThat(scope.canAccess(2L)).isTrue();
        assertThat(scope.canAccess(3L)).isFalse();
        assertThat(scope.filterIds()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("LIMITED 越权访问抛出门店权限异常")
    void limitedScopeThrowsOnForeignStore() {
        StoreScope scope = StoreScope.from(user(false, Set.of(1L)));
        assertThatThrownBy(() -> scope.assertAccessible(3L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("数据权限");
    }

    @Test
    @DisplayName("无授权门店时标记为空范围")
    void emptyLimitedScope() {
        StoreScope scope = StoreScope.from(user(false, Set.of()));
        assertThat(scope.isEmptyLimited()).isTrue();
        assertThat(scope.canAccess(1L)).isFalse();
    }

    @Test
    @DisplayName("未登录时抛异常")
    void nullUserThrows() {
        assertThatThrownBy(() -> StoreScope.from(null))
                .isInstanceOf(BizException.class);
    }
}

package za.co.capitecbank.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

class CacheConfigUnitTest {

    private final CacheConfig cacheConfig = new CacheConfig();

    @Test
    void cacheManager_shouldContainClientProfilesCache() {
        final CacheManager manager = cacheConfig.cacheManager();
        assertThat(manager.getCache(CacheConfig.CLIENT_PROFILES)).isNotNull();
    }

    @Test
    void cacheManager_shouldContainRecentTransactionCountsCache() {
        final CacheManager manager = cacheConfig.cacheManager();
        assertThat(manager.getCache(CacheConfig.RECENT_TRANSACTION_COUNTS)).isNotNull();
    }
}

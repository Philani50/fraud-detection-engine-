package za.co.capitecbank.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CLIENT_PROFILES = "clientProfiles";
    public static final String RECENT_TRANSACTION_COUNTS = "recentTransactionCounts";

    @Bean
    CacheManager cacheManager() {
        final CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(List.of(CLIENT_PROFILES, RECENT_TRANSACTION_COUNTS));
        manager.registerCustomCache(
                CLIENT_PROFILES,
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .recordStats()
                        .build());
        manager.registerCustomCache(
                RECENT_TRANSACTION_COUNTS,
                Caffeine.newBuilder()
                        .maximumSize(2_000)
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .recordStats()
                        .build());
        return manager;
    }
}

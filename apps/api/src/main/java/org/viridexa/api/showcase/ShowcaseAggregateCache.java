package org.viridexa.api.showcase;

import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** One-entry, version-keyed TTL cache; synchronizing the load provides single-flight behavior. */
final class ShowcaseAggregateCache<K, V> {
    private final long ttlNanos;
    private final LongSupplier ticker;
    private Entry<K, V> entry;

    ShowcaseAggregateCache(long ttlNanos, LongSupplier ticker) {
        if (ttlNanos <= 0) throw new IllegalArgumentException("ttlNanos must be positive");
        this.ttlNanos = ttlNanos;
        this.ticker = Objects.requireNonNull(ticker);
    }

    synchronized V getOrLoad(K key, Supplier<V> loader) {
        long now = ticker.getAsLong();
        if (entry != null && Objects.equals(entry.key(), key)
                && now - entry.createdAtNanos() < ttlNanos) {
            return entry.value();
        }

        // Discard an expired or version-mismatched result before querying. A failed query
        // therefore cannot silently serve the old aggregate under a new version key.
        entry = null;
        V value = Objects.requireNonNull(loader.get(), "aggregate loader returned null");
        entry = new Entry<>(key, value, ticker.getAsLong());
        return value;
    }

    private record Entry<K, V>(K key, V value, long createdAtNanos) {}
}

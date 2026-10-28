package org.viridexa.api.showcase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ShowcaseControllerTest {
    private static final String PLANTS_SQL = "SELECT COUNT(*) FROM plant";
    private static final String RELEASE_SQL = "SELECT COUNT(*) FROM source_release_event";
    private static final String SOURCE_SQL = "FROM scientific_source_version s ORDER BY s.dataset,s.id LIMIT 100";

    private JdbcTemplate jdbc;
    private AtomicLong ticker;
    private AtomicLong releases;
    private AtomicLong reviews;
    private AtomicLong decisions;
    private AtomicLong licenseEvents;
    private AtomicLong plants;
    private Map<String, Object> source;
    private AtomicBoolean failPlants;
    private ShowcaseController controller;

    @BeforeEach
    void setup() {
        jdbc = mock(JdbcTemplate.class);
        ticker = new AtomicLong(1_000L);
        releases = new AtomicLong(1L);
        reviews = new AtomicLong(8L);
        decisions = new AtomicLong(3L);
        licenseEvents = new AtomicLong(2L);
        plants = new AtomicLong(12L);
        failPlants = new AtomicBoolean();
        source = new java.util.concurrent.ConcurrentHashMap<>(Map.of(
                "id", "src-1", "dataset", "dataset-a", "file", "data.csv", "version", "v1",
                "sha256", "a".repeat(64), "integrityState", "VERIFIED", "licenseState", "PERMITTED"));
        when(jdbc.queryForList(containsSql(SOURCE_SQL))).thenAnswer(invocation -> List.of(Map.copyOf(source)));
        when(jdbc.queryForList("SELECT source_id,pipeline,COUNT(*) AS records FROM public_source_record GROUP BY source_id,pipeline"))
                .thenReturn(List.of(Map.of("source_id", "src-1", "pipeline", "LIPID", "records", 2L)));
        when(jdbc.queryForList("SELECT source_id,pipeline,COUNT(*) AS total,SUM(normalization_state='CANDIDATE') AS normalized,SUM(normalization_state='HELD') AS held FROM scientific_candidate GROUP BY source_id,pipeline"))
                .thenReturn(List.of(Map.of("source_id", "src-1", "pipeline", "LIPID", "total", 5L, "normalized", 4L, "held", 1L)));
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.equals(RELEASE_SQL)) return releases.get();
            if (sql.contains("FROM review_event")) return reviews.get();
            if (sql.contains("FROM scientific_decision")) return decisions.get();
            if (sql.contains("FROM source_license_event")) return licenseEvents.get();
            if (sql.equals(PLANTS_SQL) && failPlants.get()) throw new IllegalStateException("database unavailable");
            if (sql.equals(PLANTS_SQL)) return plants.get();
            if (sql.contains("graph_catalog")) return 7L;
            if (sql.contains("scientific_candidate WHERE normalization_state='HELD'")) return 1L;
            if (sql.contains("scientific_candidate")) return 5L;
            if (sql.contains("published_scientific_fact")) return 4L;
            if (sql.contains("scientific_sample WHERE")) return 1L;
            if (sql.contains("scientific_sample")) return 6L;
            if (sql.contains("license_state='UNKNOWN'")) return 0L;
            if (sql.contains("scientific_source_version")) return 1L;
            throw new AssertionError("Unexpected count query: " + sql);
        });
        controller = new ShowcaseController(jdbc, ticker::get,
                Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sameVersionUsesAggregateCacheButReadsSourceMetadataAndReturnsNoStore() {
        var first = controller.overview();
        var second = controller.overview();

        assertEquals(12L, ((Map<?, ?>) first.getBody().get("counts")).get("plants"));
        assertEquals(first.getBody().get("aggregateAsOf"), second.getBody().get("aggregateAsOf"));
        assertEquals("PERMITTED", ((Map<?, ?>) ((List<?>) second.getBody().get("sources")).getFirst()).get("licenseState"));
        assertEquals("no-store", second.getHeaders().getCacheControl());
        verify(jdbc, times(2)).queryForList(containsSql(SOURCE_SQL));
        verify(jdbc, times(1)).queryForObject(PLANTS_SQL, Long.class);
    }

    @Test
    void ttlExpiryAndPublicationVersionChangeRegenerateAggregates() {
        controller.overview();
        ticker.addAndGet(TimeUnit.SECONDS.toNanos(5));
        controller.overview();
        releases.incrementAndGet();
        controller.overview();

        verify(jdbc, times(3)).queryForObject(PLANTS_SQL, Long.class);
    }

    @Test
    void sourceLicenseChangeInvalidatesAggregateAndIsReturnedFresh() {
        controller.overview();
        source.put("licenseState", "RESTRICTED");
        var changed = controller.overview();

        assertEquals("RESTRICTED", ((Map<?, ?>) ((List<?>) changed.getBody().get("sources")).getFirst()).get("licenseState"));
        verify(jdbc, times(2)).queryForObject(PLANTS_SQL, Long.class);
    }

    @Test
    void governanceEventVersionsInvalidateTheCachedAggregate() {
        controller.overview();
        reviews.incrementAndGet();
        controller.overview();
        decisions.incrementAndGet();
        controller.overview();
        licenseEvents.incrementAndGet();
        controller.overview();

        verify(jdbc, times(4)).queryForObject(PLANTS_SQL, Long.class);
    }

    @Test
    void concurrentSameVersionRequestsShareOneAggregateLoad() throws Exception {
        int workers = 8;
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(workers)) {
            var futures = java.util.stream.IntStream.range(0, workers).mapToObj(i -> pool.submit(() -> {
                start.await();
                return controller.overview();
            })).toList();
            start.countDown();
            for (var future : futures) assertEquals(200, future.get(5, TimeUnit.SECONDS).getStatusCode().value());
        }

        verify(jdbc, times(1)).queryForObject(PLANTS_SQL, Long.class);
    }

    @Test
    void failedLoadDoesNotReusePriorVersionSnapshot() {
        var initial = controller.overview();
        releases.incrementAndGet();
        failPlants.set(true);
        assertThrows(IllegalStateException.class, controller::overview);

        failPlants.set(false);
        plants.set(18L);
        var refreshed = controller.overview();
        assertEquals(12L, ((Map<?, ?>) initial.getBody().get("counts")).get("plants"));
        assertEquals(18L, ((Map<?, ?>) refreshed.getBody().get("counts")).get("plants"));
        verify(jdbc, times(3)).queryForObject(PLANTS_SQL, Long.class);
    }

    private static String containsSql(String part) {
        return org.mockito.ArgumentMatchers.argThat(sql -> sql.contains(part));
    }
}

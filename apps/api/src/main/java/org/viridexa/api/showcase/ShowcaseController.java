package org.viridexa.api.showcase;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Live, bounded aggregate projections. No raw content, credentials or private paths. */
@RestController
@RequestMapping("/api/v1/showcase")
public class ShowcaseController {
    private static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final JdbcTemplate jdbc;
    private final ShowcaseAggregateCache<VersionKey, AggregateSnapshot> cache;
    private final Clock clock;

    @Autowired
    public ShowcaseController(JdbcTemplate jdbc) {
        this(jdbc, new ShowcaseAggregateCache<>(CACHE_TTL_NANOS, System::nanoTime), Clock.systemUTC());
    }

    ShowcaseController(JdbcTemplate jdbc, LongSupplier ticker, Clock clock) {
        this(jdbc, new ShowcaseAggregateCache<>(CACHE_TTL_NANOS, ticker), clock);
    }

    private ShowcaseController(JdbcTemplate jdbc,
            ShowcaseAggregateCache<VersionKey, AggregateSnapshot> cache, Clock clock) {
        this.jdbc = jdbc;
        this.cache = cache;
        this.clock = clock;
    }

    @GetMapping
    @Transactional(readOnly = true, timeout = 30, isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<Map<String, Object>> overview() {
        // Source metadata is deliberately read on every request. Only its public version
        // identity participates in the aggregate cache key; no source rows are cached.
        var sources = jdbc.queryForList("SELECT s.id,SUBSTRING_INDEX(s.dataset,'/',-1) AS dataset,"
                + "SUBSTRING_INDEX(s.path,'/',-1) AS file,s.version_label AS version,s.sha256,"
                + "s.integrity_state AS integrityState,s.license_state AS licenseState "
                + "FROM scientific_source_version s ORDER BY s.dataset,s.id LIMIT 100");
        var sourceVersions = sources.stream().map(s -> new SourceVersion(
                s.get("id").toString(), stringValue(s.get("sha256")), stringValue(s.get("version")),
                stringValue(s.get("licenseState")), stringValue(s.get("integrityState")))).toList();
        var key = new VersionKey(
                n("SELECT COUNT(*) FROM source_release_event"),
                n("SELECT COALESCE(MAX(id),0) FROM review_event"),
                n("SELECT COALESCE(MAX(id),0) FROM scientific_decision"),
                n("SELECT COALESCE(MAX(id),0) FROM source_license_event"),
                sourceVersions);

        var aggregates = cache.getOrLoad(key, this::loadAggregates);
        var responseSources = new ArrayList<Map<String, Object>>(sources.size());
        for (var row : sources) {
            var source = new LinkedHashMap<String, Object>(row);
            source.put("records", aggregates.sourceRecords().getOrDefault(source.get("id").toString(), 0L));
            source.put("publicRecords", aggregates.sourcePublicRecords().getOrDefault(source.get("id").toString(), 0L));
            Object version = source.get("version");
            if (version != null && (version.toString().contains("/Users/") || version.toString().startsWith("/"))) {
                source.put("version", "VERSION_REDACTED");
            }
            responseSources.add(source);
        }

        var body = new LinkedHashMap<String, Object>();
        body.put("counts", aggregates.counts());
        body.put("pipelines", aggregates.pipelines());
        body.put("sources", responseSources);
        body.put("policy", "PUBLIC is visibility, not human verification. Original documents are not redistributed.");
        body.put("generatedAt", clock.instant().toString());
        body.put("aggregateAsOf", aggregates.asOf());
        body.put("aggregateNote", "Counts and pipeline aggregates are version-checked and cached for up to 5 seconds; source metadata and license state are read on each request.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private AggregateSnapshot loadAggregates() {
        var counts = new LinkedHashMap<String, Long>();
        counts.put("plants", n("SELECT COUNT(*) FROM plant"));
        counts.put("publicPlants", n("SELECT COUNT(*) FROM graph_catalog WHERE is_public=1"));
        counts.put("candidates", n("SELECT COUNT(*) FROM scientific_candidate"));

        // Aggregate each visibility projection once; never repeat a full source scan per card.
        var publicGroups = jdbc.queryForList("SELECT source_id,pipeline,COUNT(*) AS records FROM public_source_record GROUP BY source_id,pipeline");
        counts.put("publicSourceRecords", publicGroups.stream().mapToLong(r -> ((Number) r.get("records")).longValue()).sum());
        counts.put("humanVerified", n("SELECT COUNT(*) FROM published_scientific_fact"));
        counts.put("samples", n("SELECT COUNT(*) FROM scientific_sample"));
        counts.put("unlinkedSamples", n("SELECT COUNT(*) FROM scientific_sample WHERE linkage_state='UNLINKED'"));
        counts.put("held", n("SELECT COUNT(*) FROM scientific_candidate WHERE normalization_state='HELD'"));
        counts.put("sourceVersions", n("SELECT COUNT(*) FROM scientific_source_version"));
        counts.put("unknownLicenses", n("SELECT COUNT(*) FROM scientific_source_version WHERE license_state='UNKNOWN'"));

        var groups = jdbc.queryForList("SELECT source_id,pipeline,COUNT(*) AS total,SUM(normalization_state='CANDIDATE') AS normalized,SUM(normalization_state='HELD') AS held FROM scientific_candidate GROUP BY source_id,pipeline");
        var pipelineMap = new TreeMap<String, Map<String, Object>>();
        for (var group : groups) {
            var pipeline = pipelineMap.computeIfAbsent(group.get("pipeline").toString(), k ->
                    new LinkedHashMap<>(Map.of("pipeline", k, "total", 0L, "normalized", 0L, "held", 0L, "publicRecords", 0L)));
            for (String key : List.of("total", "normalized", "held")) {
                pipeline.put(key, ((Number) pipeline.get(key)).longValue() + ((Number) group.get(key)).longValue());
            }
        }
        for (var group : publicGroups) {
            var pipeline = pipelineMap.get(group.get("pipeline").toString());
            if (pipeline != null) pipeline.put("publicRecords", ((Number) pipeline.get("publicRecords")).longValue()
                    + ((Number) group.get("records")).longValue());
        }

        var sourceRecords = groups.stream().collect(Collectors.groupingBy(
                g -> g.get("source_id").toString(), TreeMap::new,
                Collectors.summingLong(g -> ((Number) g.get("total")).longValue())));
        var sourcePublicRecords = publicGroups.stream().collect(Collectors.groupingBy(
                g -> g.get("source_id").toString(), TreeMap::new,
                Collectors.summingLong(g -> ((Number) g.get("records")).longValue())));
        var pipelines = pipelineMap.values().stream()
                .map(p -> Collections.unmodifiableMap(new LinkedHashMap<>(p))).toList();
        return new AggregateSnapshot(Collections.unmodifiableMap(new LinkedHashMap<>(counts)), pipelines,
                Map.copyOf(sourceRecords), Map.copyOf(sourcePublicRecords), clock.instant().toString());
    }

    private long n(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private static String stringValue(Object value) { return value == null ? "" : value.toString(); }

    private record SourceVersion(String id, String sha256, String version, String licenseState, String integrityState) {}
    private record VersionKey(long releaseEventCount, long reviewEventId, long decisionId, long licenseEventId,
            List<SourceVersion> sources) {}
    private record AggregateSnapshot(Map<String, Long> counts, List<Map<String, Object>> pipelines,
            Map<String, Long> sourceRecords, Map<String, Long> sourcePublicRecords, String asOf) {}
}

package org.viridexa.api.plant;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/plants/{plantId}/media")
public class PlantMediaController {
    private static final Set<String> ALLOWED = Set.of("CC0", "CC-BY");
    private final PlantService plants;
    private final JdbcTemplate jdbc;

    public PlantMediaController(PlantService plants, JdbcTemplate jdbc) {
        this.plants = plants;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<PlantMedia> list(@PathVariable long plantId) {
        PlantRecord plant = plants.getById(plantId);
        return jdbc.query("SELECT scientific_name, author, license_code, source_url, source_observation_id, sha256, public_filename, width, height FROM botanical_media WHERE plant_id = ? AND state = 'PUBLIC' ORDER BY id LIMIT 12",
                (rs, row) -> {
                    String license = rs.getString("license_code");
                    String identity = rs.getString("scientific_name");
                    String filename = rs.getString("public_filename");
                    String sourceUrl = rs.getString("source_url");
                    String sha = rs.getString("sha256");
                    if (!ALLOWED.contains(license) || !identity.equals(plant.scientificName())
                            || !filename.matches("[a-f0-9]{64}-(640|960|full)\\.webp") || !filename.startsWith(sha + "-")
                            || !sourceUrl.matches("https://www\\.inaturalist\\.org/observations/[0-9]+")) return null;
                    return new PlantMedia(identity, rs.getString("author"), license, sourceUrl,
                            rs.getLong("source_observation_id"), sha, "/media/" + filename,
                            rs.getInt("width"), rs.getInt("height"));
                }, plantId).stream().filter(java.util.Objects::nonNull).toList();
    }

    public record PlantMedia(String scientificName, String author, String license, String sourceUrl,
            long observationId, String sha256, String imageUrl, int width, int height) {}
}

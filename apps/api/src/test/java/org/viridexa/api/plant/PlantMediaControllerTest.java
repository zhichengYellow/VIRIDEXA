package org.viridexa.api.plant;

import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class PlantMediaControllerTest {
    @Test
    void exposesOnlyMatchingLicensedMediaAndSafeUrls() throws Exception {
        PlantService plants = mock(PlantService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        given(plants.getById(2)).willReturn(new PlantRecord(2, null, "Ginkgo biloba", null, null, null, null, null, null, null, null, null, null, null));
        given(jdbc.query(anyString(), any(RowMapper.class), eq(2L))).willAnswer(inv -> {
            String sql = inv.getArgument(0);
            assertTrue(sql.contains("state = 'PUBLIC'") && sql.contains("LIMIT 12"));
            @SuppressWarnings("unchecked") RowMapper<PlantMediaController.PlantMedia> mapper = inv.getArgument(1);
            var mapped = new java.util.ArrayList<PlantMediaController.PlantMedia>();
            mapped.add(row(mapper, "Ginkgo biloba", "CC-BY", "https://www.inaturalist.org/observations/21", "a".repeat(64) + "-full.webp"));
            mapped.add(row(mapper, "Ginkgo biloba", "CC-BY-NC", "https://www.inaturalist.org/observations/22", "b".repeat(64) + "-full.webp"));
            mapped.add(row(mapper, "Other species", "CC0", "https://www.inaturalist.org/observations/23", "c".repeat(64) + "-full.webp"));
            mapped.add(row(mapper, "Ginkgo biloba", "CC0", "https://evil.example/23", "d".repeat(64) + "-full.webp"));
            return mapped;
        });
        var result = new PlantMediaController(plants, jdbc).list(2);
        assertEquals(1, result.size());
        assertEquals("/media/" + "a".repeat(64) + "-full.webp", result.getFirst().imageUrl());
    }

    private PlantMediaController.PlantMedia row(RowMapper<PlantMediaController.PlantMedia> mapper, String name, String license, String url, String filename) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("license_code")).thenReturn(license); when(rs.getString("scientific_name")).thenReturn(name);
        when(rs.getString("public_filename")).thenReturn(filename); when(rs.getString("source_url")).thenReturn(url);
        when(rs.getString("sha256")).thenReturn(filename.substring(0, 64)); when(rs.getString("author")).thenReturn("Photographer");
        when(rs.getLong("source_observation_id")).thenReturn(23L); when(rs.getInt("width")).thenReturn(640); when(rs.getInt("height")).thenReturn(800);
        return mapper.mapRow(rs, 0);
    }
}

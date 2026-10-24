package org.viridexa.api.plant;

import java.util.List;

public record PlantSearchResult(String query, long total, List<PlantRecord> items) {
}

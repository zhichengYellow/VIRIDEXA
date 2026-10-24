package org.viridexa.api.plant;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PlantService {
    private final PlantMapper mapper;

    public PlantService(PlantMapper mapper) {
        this.mapper = mapper;
    }

    public PlantSearchResult search(String query, int limit) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        long total = mapper.countVerified(normalizedQuery);
        List<PlantRecord> items = mapper.searchVerified(normalizedQuery, limit);
        return new PlantSearchResult(normalizedQuery, total, items);
    }

    public PlantRecord getVerified(long id) {
        return mapper.findVerifiedById(id)
                .orElseThrow(() -> new PlantNotFoundException(id));
    }
}

package org.viridexa.api.plant;

import java.util.List;

public record PlantSearchResult(String query, long total, List<PlantRecord> items, Long nextAfterId) {
    public PlantSearchResult(String query,long total,List<PlantRecord> items){this(query,total,items,null);}
}

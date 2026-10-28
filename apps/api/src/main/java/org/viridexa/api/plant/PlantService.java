package org.viridexa.api.plant;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class PlantService {
    private final PlantMapper mapper;

    public PlantService(PlantMapper mapper) {
        this.mapper = mapper;
    }

    public List<PlantMapper.Suggestion> suggestions(String query,int limit){
        if(query==null||query.isBlank())return List.of();String q=query.trim();
        if(q.length()>80||q.chars().anyMatch(Character::isISOControl)||limit<1||limit>8)throw new IllegalArgumentException("Invalid suggestion query");
        String prefix=q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return mapper.suggestions(q,prefix,limit);
    }

    public PlantSearchResult search(String query, int limit) {
        String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
        long total = mapper.countPlants(normalizedQuery);
        List<PlantRecord> items = mapper.searchPlants(normalizedQuery, limit);
        return new PlantSearchResult(normalizedQuery, total, items, total>items.size()&&!items.isEmpty()?items.getLast().id():null);
    }

    public PlantSearchResult searchAfter(String query,int limit,long afterId){
        String q=query==null||query.isBlank()?null:query.trim();
        var rows=mapper.searchPlantsAfter(q,limit+1,afterId);
        var items=rows.stream().limit(limit).toList();
        return new PlantSearchResult(q,mapper.countPlants(q),items,rows.size()>limit?items.getLast().id():null);
    }

    public PlantRecord getById(long id) {
        return mapper.findById(id)
                .orElseThrow(() -> new PlantNotFoundException(id));
    }
}

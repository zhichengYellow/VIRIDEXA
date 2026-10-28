package org.viridexa.api.graph;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.viridexa.api.graph.GraphContract.Page;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;

class GraphRulesTest {
 @Test void acceptsOnlyNamespacedOpaqueNodeIds() {
  for (String id : List.of("source:"+"a".repeat(64), "candidate:"+"0".repeat(64), "sample:"+"f".repeat(64), "plant:1", "taxon:999", "pname:4", "tname:7", "file:3", "trait:"+"a".repeat(64)+":leaf_shape:1"))
   assertDoesNotThrow(() -> GraphService.validateId(id), id);
 }
 @Test void rejectsMalformedAndOutOfRangeIds() {
  for (String id : List.of("", "plant:0", "plant:-1", "plant:01", "plant:99999999999999999999", "plant:9999999999999999999", "candidate:"+"A".repeat(64), "candidate:"+"a".repeat(63), "source:"+"a".repeat(64)+"/../../x", "trait:"+"a".repeat(64)+":unknown:1", "null"))
   assertThrows(ResponseStatusException.class, () -> GraphService.validateId(id), id);
 }
 @Test void cursorIsBoundToViewQueryAndPageSize() {
  String valid=GraphService.cursor("PUBLIC|search|genus|12", "source:"+"a".repeat(64));
  assertEquals("source:"+"a".repeat(64), GraphService.after(valid,"PUBLIC|search|genus|12"));
  assertThrows(ResponseStatusException.class, () -> GraphService.after(valid,"ADMIN|search|genus|12"));
  assertThrows(ResponseStatusException.class, () -> GraphService.after(valid,"PUBLIC|search|species|12"));
  assertThrows(ResponseStatusException.class, () -> GraphService.after(valid,"PUBLIC|search|genus|10"));
 }
 @Test void malformedAndOversizedCursorsAreRejected() {
  for (String cursor : List.of("not-base64!", "YQ", "a".repeat(901)))
   assertThrows(ResponseStatusException.class, () -> GraphService.after(cursor,"PUBLIC|search||12"), cursor);
 }
 @Test void publicSearchAppliesMetadataPublicationBoundaryAndCursorPaging() {
  JdbcTemplate jdbc=mock(JdbcTemplate.class);GraphService service=new GraphService(jdbc);
  Map<String,Object> row=Map.of("id","source:"+"b".repeat(64),"node_type","SOURCE","display_label","dataset.csv","verification_state","VERIFIED_INTEGRITY","publication_state","METADATA_ONLY","source_id","b".repeat(64),"locator","{}","attributes","{}");
  when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(row));
  Page page=service.search("",1,null);
  assertEquals("PUBLIC",page.view());assertEquals(1,page.nodes().size());assertFalse(page.hasMore());
  verify(jdbc).queryForList(contains("publication_state IN ('METADATA_ONLY','PUBLISHED','SOURCE_PUBLIC')"),any(Object[].class));
 }
 @Test void validatesPageSizeBeforeDatabaseAccess() {
  JdbcTemplate jdbc=mock(JdbcTemplate.class);GraphService service=new GraphService(jdbc);
  assertThrows(ResponseStatusException.class,()->service.search("",0,null));
  assertThrows(ResponseStatusException.class,()->service.adminSearch("",41,null));
  verifyNoInteractions(jdbc);
 }
 @Test void rejectsUnsupportedNeighborFiltersBeforeDatabaseAccess() {
  JdbcTemplate jdbc=mock(JdbcTemplate.class);GraphService service=new GraphService(jdbc);
  assertThrows(ResponseStatusException.class,()->service.neighbors("plant:1","INVENTED",null,null,10,null,1));
  assertThrows(ResponseStatusException.class,()->service.neighbors("plant:1",null,null,null,10,null,2));
  assertThrows(ResponseStatusException.class,()->service.neighbors("plant:1",null,null,null,"INVENTED",10,null,1));
  verifyNoInteractions(jdbc);
 }
}

package org.viridexa.api.governance;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.viridexa.api.science.PublicScientificData;
import static org.junit.jupiter.api.Assertions.*;
class PublicDataTest {
 @Test void internalPayloadAndServerPathsAreRemoved(){
  var row=new LinkedHashMap<String,Object>();row.put("reviewer_id",12);row.put("rationale","private note");row.put("raw_payload","private text");row.put("path","/Users/private/file");
  row.put("normalized_payload","{\"scientific_name\":\"Cycas revoluta\",\"paper\":{\"path\":\"/Users/private/file\"},\"candidate_target_keys\":[\"internal\"]}");
  var safe=PublicScientificData.rows(List.of(row)).getFirst();assertFalse(safe.containsKey("raw_payload"));assertFalse(safe.containsKey("rationale"));assertFalse(safe.containsKey("reviewer_id"));assertFalse(safe.toString().contains("/Users/"));assertFalse(safe.toString().contains("internal"));assertTrue(safe.toString().contains("Cycas revoluta"));
 }
 @Test void nullDoesNotBecomeZero(){var n=new LinkedHashMap<String,Object>();n.put("value",null);n.put("kind","OIL_CONTENT");assertTrue(PublicScientificData.payload(n).containsKey("value"));assertNull(PublicScientificData.payload(n).get("value"));}
 @Test void locatorWhitelistsScientificPositions(){var row=new LinkedHashMap<String,Object>();row.put("locator",Map.of("pdf_page",4,"image_path","/Users/private/image.png"));var clean=PublicScientificData.rows(List.of(row)).getFirst();assertEquals(Map.of("pdf_page",4),clean.get("locator"));}
}

package org.viridexa.api.workbench;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.viridexa.api.plant.PlantService;
import org.viridexa.api.science.ScientificService;
import org.viridexa.api.graph.GraphService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.viridexa.api.workbench.WorkbenchContract.*;
class WorkbenchBoundaryTest {
 final WorkbenchRepository repo=mock(WorkbenchRepository.class);
 final WorkbenchService service=new WorkbenchService(repo,mock(PlantService.class),mock(ScientificService.class),mock(GraphService.class),new ComparabilityEngine());
 @Test void cursorsCannotCrossQueryOrVisibilityScope(){String token=WorkbenchService.cursor("PUBLIC|oil","candidate:"+"a".repeat(64));assertEquals("candidate:"+"a".repeat(64),WorkbenchService.after(token,"PUBLIC|oil"));assertThrows(ResponseStatusException.class,()->WorkbenchService.after(token,"ADMIN|oil"));assertThrows(ResponseStatusException.class,()->WorkbenchService.after(token,"PUBLIC|fame"));assertThrows(ResponseStatusException.class,()->WorkbenchService.after("!invalid","PUBLIC|oil"));}
 @Test void identifiersAndBatchBoundsFailBeforeDatabaseAccess(){assertThrows(ResponseStatusException.class,()->WorkbenchService.sampleIds(List.of("course-sample:9999999999999999999")));assertThrows(ResponseStatusException.class,()->WorkbenchService.pointId("course-measurement:9999999999999999999"));assertThrows(ResponseStatusException.class,()->WorkbenchService.sampleIds(Collections.nCopies(5,"sample:"+"a".repeat(64))));verifyNoInteractions(repo);}
 @Test void publicAgentNeverAcceptsCourseOrHiddenSampleContexts(){assertThrows(ResponseStatusException.class,()->service.validateAgentContext(new AgentContext(null,List.of("course-sample:1"),null,null,null)));assertThrows(ResponseStatusException.class,()->service.validateAgentContext(new AgentContext(null,List.of("sample:"+"a".repeat(64)),null,null,null)));verify(repo).visibleSample("sample:"+"a".repeat(64));}
 @Test void transcriptionMetadataIsNotAnAssayMethodAndMissingIsNotZero(){var row=new HashMap<String,Object>();row.put("id","course-measurement:1");row.put("sample_id","course-sample:1");row.put("method","visual_cell_check_from_300dpi_page_render");row.put("raw_value","not reported");row.put("normalized_payload",Map.of("paper",Map.of("method","GC-MS; peak-area normalization")));when(repo.points(eq(true),anyList(),isNull(),eq(""),eq(61))).thenReturn(List.of(row));var p=service.adminPoints(List.of("course-sample:1"),null,60,null).items().getFirst();assertNull(p.method());assertNull(p.value());assertEquals("not reported",p.rawValue());assertTrue(p.locator().containsKey("transcription_method"));assertEquals("GC-MS; peak-area normalization",p.locator().get("source_method_note"));assertEquals("PAPER_LEVEL_NOT_A_MEASUREMENT_METHOD",p.locator().get("method_scope"));}
 @Test void unlinkedIdentityDoesNotInheritAPlantFromText(){var row=new HashMap<String,Object>();row.put("id","sample:"+"a".repeat(64));row.put("label","Plantus textualis");row.put("identity_state","UNLINKED");when(repo.samples(eq(true),any(),eq(""),eq(21))).thenReturn(List.of(row));var s=service.adminSamples(new WorkbenchRepository.Filters(null,null,null,null,null,null,null),20,null).items().getFirst();assertNull(s.plantId());assertNull(s.taxonId());assertEquals("UNLINKED",s.identityState());}
}

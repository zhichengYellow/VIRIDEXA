package org.viridexa.api.governance;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.viridexa.api.science.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class IdentificationTest {
 @Test void unknownInputIsInsufficientNotConflict(){var mapper=mock(ScientificMapper.class);var result=new IdentificationService(mapper).identify(Map.of("leaf_shape","unexplained"));assertEquals("EVIDENCE_INSUFFICIENT",result.status());assertEquals("unexplained",result.ignoredUnknownCriteria().get("leaf_shape"));verifyNoInteractions(mapper);}
 @Test void missingPublishedEvidenceIsInsufficient(){var mapper=mock(ScientificMapper.class);when(mapper.identificationRecords()).thenReturn(List.of());assertEquals("EVIDENCE_INSUFFICIENT",new IdentificationService(mapper).identify(Map.of("leaf_shape","ovate")).status());}
 @Test void knownDifferentConceptIsNoMatch(){var mapper=mock(ScientificMapper.class);when(mapper.identificationRecords()).thenReturn(List.of(Map.of("id","fixture","projection",Map.of("traits",Map.of("leaf_shape",List.of(Map.of("concept","LINEAR")))))));assertEquals("NO_MATCH",new IdentificationService(mapper).identify(Map.of("leaf_shape","ovate")).status());}
 @Test void missingTraitIsNotNegativeEvidence(){var mapper=mock(ScientificMapper.class);when(mapper.identificationRecords()).thenReturn(List.of(Map.of("id","fixture","projection",Map.of("traits",Map.of()))));var result=new IdentificationService(mapper).identify(Map.of("leaf_shape","ovate"));assertEquals("EVIDENCE_INSUFFICIENT",result.status());assertEquals(List.of("fixture"),result.insufficientEvidenceCandidateIds());}
 @Test void exactConceptMatchesPublishedDataOnly(){var mapper=mock(ScientificMapper.class);when(mapper.identificationRecords()).thenReturn(List.of(Map.of("id","fixture","normalized_payload",Map.of("scientific_name","Source species"),"projection",Map.of("traits",Map.of("leaf_shape",List.of(Map.of("concept","OVATE")))))));assertEquals("MATCHES",new IdentificationService(mapper).identify(Map.of("leaf_shape","ovate")).status());}
}

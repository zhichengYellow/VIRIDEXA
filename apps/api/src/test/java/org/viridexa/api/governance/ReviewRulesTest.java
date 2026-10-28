package org.viridexa.api.governance;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
class ReviewRulesTest {
 @Test void firstDecisionsAndEvidenceReturn(){for(String action:new String[]{"APPROVED","REJECTED","NEEDS_EVIDENCE"})assertDoesNotThrow(()->ReviewRules.transition("PENDING",action));assertDoesNotThrow(()->ReviewRules.transition("NEEDS_EVIDENCE","APPROVED"));}
 @Test void withdrawalRequiresApproval(){assertDoesNotThrow(()->ReviewRules.transition("APPROVED","REVOKED"));assertThrows(ResponseStatusException.class,()->ReviewRules.transition("PENDING","REVOKED"));}
 @Test void repeatedApprovalCannotRewriteDecision(){assertThrows(ResponseStatusException.class,()->ReviewRules.transition("APPROVED","APPROVED"));assertThrows(ResponseStatusException.class,()->ReviewRules.transition("REJECTED","APPROVED"));assertDoesNotThrow(()->ReviewRules.transition("REJECTED","NEEDS_EVIDENCE"));}
 @Test void unitAndBasisAreMandatory(){assertThrows(ResponseStatusException.class,()->ReviewRules.lipid("FAME_COMPOSITION","mg/100g","oil",BigDecimal.ONE));assertThrows(ResponseStatusException.class,()->ReviewRules.lipid("OIL_CONTENT","%","",BigDecimal.ONE));}
 @Test void zeroAndMissingAreDifferent(){assertDoesNotThrow(()->ReviewRules.lipid("OIL_CONTENT","%","seed mass",BigDecimal.ZERO));assertThrows(ResponseStatusException.class,()->ReviewRules.lipid("OIL_CONTENT","%","seed mass",null));}
 @Test void nutritionRemainsSeparateAndRangesChecked(){assertDoesNotThrow(()->ReviewRules.lipid("FOOD_NUTRIENT","MG","per 100 g food",new BigDecimal("500")));assertThrows(ResponseStatusException.class,()->ReviewRules.lipid("OIL_FA_COMPOSITION","%","oil",new BigDecimal("101")));}
}

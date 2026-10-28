package org.viridexa.api.workbench;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.viridexa.api.workbench.WorkbenchContract.*;

class ComparabilityEngineTest {
    private final ComparabilityEngine engine = new ComparabilityEngine();

    @Test void sameVerifiedOilMeasurementsAreDirectAndNeverScientificApproval() {
        Comparison c = engine.check(List.of(point("a", "s1", "OIL_CONTENT", null, "12.3", "%", "dry seed mass"),
                point("b", "s2", "OIL_CONTENT", null, "0", "%", "dry seed mass")));
        assertEquals("DIRECT", c.conclusion());
        assertEquals(2, c.values().size());
        assertEquals(new BigDecimal("0"), c.values().get(1).value());
        assertFalse(c.scientificConclusion());
        assertTrue(c.notice().contains("不构成人工科学审核"));
    }

    @Test void explicitMassFractionAllowsExactMixedUnitConversionAndKeepsProvenance() {
        Comparison c = engine.check(List.of(point("a", "s1", "OIL_CONTENT", null, "2.5", "g/kg", "dry seed mass"),
                point("b", "s2", "OIL_CONTENT", null, "0.25", "%", "dry seed mass")));
        assertEquals("CONVERTIBLE", c.conclusion());
        assertEquals(new BigDecimal("0.25"), c.values().get(0).value());
        assertEquals("g/kg", c.values().get(0).originalUnit());
        assertEquals(new BigDecimal("2.5"), c.values().get(0).originalValue());
        assertEquals("a", c.values().get(0).pointId());
    }

    @Test void differentAnalytesAndOilKindsStaySideBySide() {
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(point("a", "s1", "OIL_FA_COMPOSITION", "oleic acid", "40", "%", "FAME fraction"),
                point("b", "s2", "OIL_FA_COMPOSITION", "linoleic acid", "30", "%", "FAME fraction"))).conclusion());
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(point("c", "s1", "OIL_CONTENT", null, "40", "%", "oil mass fraction"),
                point("d", "s2", "FAME_COMPOSITION", "oleic acid", "30", "%", "FAME fraction"))).conclusion());
    }

    @Test void identityPartAndBasisMustBeKnownAndMatch() {
        var unknownIdentity = withIdentity(point("a", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"), "PENDING", "taxon:1");
        assertEquals("INSUFFICIENT", engine.check(List.of(unknownIdentity, point("b", "s2", "OIL_CONTENT", null, "11", "%", "dry seed mass"))).conclusion());
        var noPart = withPart(point("c", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"), null);
        assertEquals("INSUFFICIENT", engine.check(List.of(noPart, point("d", "s2", "OIL_CONTENT", null, "11", "%", "dry seed mass"))).conclusion());
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(point("e", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"),
                point("f", "s2", "OIL_CONTENT", null, "11", "%", "unknown basis"))).conclusion());
    }

    @Test void unsupportedUnitsAndFoodNutrientNeverGetConverted() {
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(point("a", "s1", "OIL_CONTENT", null, "10", "volume %", "oil mass fraction"),
                point("b", "s2", "OIL_CONTENT", null, "11", "%", "oil mass fraction"))).conclusion());
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(point("c", "s1", "FOOD_NUTRIENT", "protein", "10", "g/100g", "per 100 g food"),
                point("d", "s2", "FOOD_NUTRIENT", "protein", "11", "g/100g", "per 100 g food"))).conclusion());
    }

    @Test void missingIsInsufficientWhileZeroIsAValidMeasurement() {
        var missing = point("a", "s1", "OIL_CONTENT", null, null, "%", "dry seed mass");
        assertEquals("INSUFFICIENT", engine.check(List.of(missing, point("b", "s2", "OIL_CONTENT", null, "0", "%", "dry seed mass"))).conclusion());
        assertEquals("DIRECT", engine.check(List.of(point("c", "s1", "OIL_CONTENT", null, "0", "%", "dry seed mass"),
                point("d", "s2", "OIL_CONTENT", null, "0", "%", "dry seed mass"))).conclusion());
    }

    @Test void differentMethodsOrUnapprovedEvidenceOnlySupportSideBySide() {
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(point("a", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"),
                withMethod(point("b", "s2", "OIL_CONTENT", null, "11", "%", "dry seed mass"), "NMR"))).conclusion());
        assertEquals("SIDE_BY_SIDE", engine.check(List.of(withState(point("c", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"), "PENDING", "UNKNOWN"),
                point("d", "s2", "OIL_CONTENT", null, "11", "%", "dry seed mass"))).conclusion());
    }

    @Test void sameSampleAndDuplicatePointIdentifiersAreRejected() {
        assertEquals("INSUFFICIENT", engine.check(List.of(point("a", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"),
                point("b", "s1", "OIL_CONTENT", null, "11", "%", "dry seed mass"))).conclusion());
        assertEquals("INSUFFICIENT", engine.check(List.of(point("a", "s1", "OIL_CONTENT", null, "10", "%", "dry seed mass"),
                point("a", "s2", "OIL_CONTENT", null, "11", "%", "dry seed mass"))).conclusion());
    }

    @Test void percentagesAreRangeCheckedWithoutAssumingCompositionSumsToOneHundred() {
        assertEquals("DIRECT", engine.check(List.of(point("a", "s1", "OIL_FA_COMPOSITION", "oleic acid", "35", "%", "FAME fraction"),
                point("b", "s2", "OIL_FA_COMPOSITION", "oleic acid", "35", "%", "FAME fraction"))).conclusion());
        assertEquals("INSUFFICIENT", engine.check(List.of(point("c", "s1", "OIL_CONTENT", null, "101", "%", "dry seed mass"),
                point("d", "s2", "OIL_CONTENT", null, "95", "%", "dry seed mass"))).conclusion());
    }

    @Test void unnamedComponentsAndMissingSampleIdsCannotBeCompared(){
        assertEquals("INSUFFICIENT",engine.check(List.of(point("a","s1","FAME_COMPOSITION",null,"20","%","FAME fraction"),point("b","s2","FAME_COMPOSITION",null,"30","%","FAME fraction"))).conclusion());
        assertEquals("INSUFFICIENT",engine.check(List.of(point("a","s1","OIL_CONTENT",null,"20","%","dry seed mass"),point("b","s2","OIL_CONTENT",null,"30","%","dry seed mass"),point("c",null,"OIL_CONTENT",null,"40","%","dry seed mass"))).conclusion());
    }
    @Test void insufficientIdentityStillReportsBasisMethodAndUnitChecks(){
        var a=withIdentity(point("a","s1","OIL_CONTENT",null,"10","%",null),"UNLINKED",null);
        var b=point("b","s2","OIL_CONTENT",null,"11","%",null);
        var result=engine.check(List.of(a,b));assertEquals("INSUFFICIENT",result.conclusion());
        for(String dimension:List.of("identity","plantPart","basis","method","units","publicationAndPermission"))assertTrue(result.checks().stream().anyMatch(c->c.dimension().equals(dimension)),dimension);
    }
    @Test void unitSymbolsRemainCaseSensitiveToAvoidConfusingMegaAndMilli(){
        assertEquals("SIDE_BY_SIDE",engine.check(List.of(point("a","s1","OIL_CONTENT",null,"10","Mg/g","dry seed mass"),point("b","s2","OIL_CONTENT",null,"11","%","dry seed mass"))).conclusion());
    }
    @org.junit.jupiter.api.Test void hyphenatedReportedBasisCannotBecomeDirectComparison() {
        assertEquals("SIDE_BY_SIDE",engine.check(List.of(point("a","s1","OIL_CONTENT",null,"10","%","as-reported oil content"),point("b","s2","OIL_CONTENT",null,"11","%","as-reported oil content"))).conclusion());
    }

    private static Point point(String id, String sample, String kind, String analyte, String value, String unit, String basis) {
        return new Point(id, sample, 7L, "taxon:species-7", "VERIFIED", "seed", kind, analyte,
                value, value == null ? null : new BigDecimal(value), unit, basis, "OIL_CONTENT".equals(kind)?"Soxhlet gravimetric":"GC-FID", "APPROVED", "PUBLISHED",
                "source:1@v1", "PERMITTED", "VERIFIED", Map.of(), null);
    }
    private static Point withIdentity(Point p, String state, String taxon) {
        return new Point(p.id(), p.sampleId(), p.plantId(), taxon, state, p.plantPart(), p.kind(), p.analyte(), p.rawValue(), p.value(), p.unit(), p.basis(), p.method(), p.reviewState(), p.publicationState(), p.sourceId(), p.sourceLicense(), p.sourceIntegrity(), p.locator(), p.graphNodeId());
    }
    private static Point withPart(Point p, String part) {
        return new Point(p.id(), p.sampleId(), p.plantId(), p.taxonId(), p.identityState(), part, p.kind(), p.analyte(), p.rawValue(), p.value(), p.unit(), p.basis(), p.method(), p.reviewState(), p.publicationState(), p.sourceId(), p.sourceLicense(), p.sourceIntegrity(), p.locator(), p.graphNodeId());
    }
    private static Point withMethod(Point p, String method) {
        return new Point(p.id(), p.sampleId(), p.plantId(), p.taxonId(), p.identityState(), p.plantPart(), p.kind(), p.analyte(), p.rawValue(), p.value(), p.unit(), p.basis(), method, p.reviewState(), p.publicationState(), p.sourceId(), p.sourceLicense(), p.sourceIntegrity(), p.locator(), p.graphNodeId());
    }
    private static Point withState(Point p, String review, String license) {
        return new Point(p.id(), p.sampleId(), p.plantId(), p.taxonId(), p.identityState(), p.plantPart(), p.kind(), p.analyte(), p.rawValue(), p.value(), p.unit(), p.basis(), p.method(), review, p.publicationState(), p.sourceId(), license, p.sourceIntegrity(), p.locator(), p.graphNodeId());
    }
}

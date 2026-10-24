package org.viridexa.api.plant;

public record PlantRecord(
        long id,
        String nameZh,
        String scientificName,
        String nameEn,
        String familyNameZh,
        String genusNameLatin,
        String morphology,
        String distribution,
        String recordStatus,
        String scientificNameStatus,
        String sourceDocument,
        String sourcePrintedPages,
        String sourcePages,
        String sourcePdfSha256) {
}

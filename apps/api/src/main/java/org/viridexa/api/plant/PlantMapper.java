package org.viridexa.api.plant;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlantMapper {
    String FROM_PLANTS = """
            FROM plant p
            LEFT JOIN taxon species ON species.id = p.taxon_id
            LEFT JOIN taxon genus ON genus.id = species.parent_id AND genus.taxon_rank = 'GENUS'
            LEFT JOIN taxon family ON family.id = genus.parent_id AND family.taxon_rank = 'FAMILY'
            LEFT JOIN evidence ev ON ev.id = species.evidence_id
            LEFT JOIN source_asset sa ON sa.id = ev.source_asset_id
            WHERE p.visibility_state = 'PUBLIC' AND p.review_state = 'APPROVED' AND species.identity_state = 'VERIFIED'
              AND EXISTS (SELECT 1 FROM review_event approved_review
                  WHERE approved_review.entity_type='PLANT' AND approved_review.entity_key=p.record_key
                    AND approved_review.actor_user_id IS NOT NULL AND approved_review.resulting_state='APPROVED'
                    AND NOT EXISTS (SELECT 1 FROM review_event later_review
                        WHERE later_review.entity_type=approved_review.entity_type AND later_review.entity_key=approved_review.entity_key
                          AND later_review.id>approved_review.id))
              AND EXISTS (SELECT 1 FROM scientific_source_version licensed_source
                  WHERE licensed_source.sha256=sa.sha256 AND licensed_source.integrity_state='VERIFIED'
                    AND licensed_source.license_state='PERMITTED')
              AND (#{query} IS NULL
                   OR EXISTS (SELECT 1 FROM plant_name search_name
                              WHERE search_name.plant_id = p.id AND search_name.review_state = 'APPROVED'
                                AND search_name.name_text LIKE CONCAT('%', #{query}, '%'))
                   OR species.canonical_name LIKE CONCAT('%', #{query}, '%'))
            """;

    String FROM_PLANT_DETAIL = """
            FROM plant p
            LEFT JOIN taxon species ON species.id = p.taxon_id
            LEFT JOIN taxon genus ON genus.id = species.parent_id AND genus.taxon_rank = 'GENUS'
            LEFT JOIN taxon family ON family.id = genus.parent_id AND family.taxon_rank = 'FAMILY'
            LEFT JOIN evidence ev ON ev.id = species.evidence_id
            LEFT JOIN source_asset sa ON sa.id = ev.source_asset_id
            WHERE p.visibility_state = 'PUBLIC' AND p.review_state = 'APPROVED' AND species.identity_state = 'VERIFIED'
              AND EXISTS (SELECT 1 FROM review_event approved_review
                  WHERE approved_review.entity_type='PLANT' AND approved_review.entity_key=p.record_key
                    AND approved_review.actor_user_id IS NOT NULL AND approved_review.resulting_state='APPROVED'
                    AND NOT EXISTS (SELECT 1 FROM review_event later_review
                        WHERE later_review.entity_type=approved_review.entity_type AND later_review.entity_key=approved_review.entity_key
                          AND later_review.id>approved_review.id))
              AND EXISTS (SELECT 1 FROM scientific_source_version licensed_source
                  WHERE licensed_source.sha256=sa.sha256 AND licensed_source.integrity_state='VERIFIED'
                    AND licensed_source.license_state='PERMITTED')
            """;

    String SELECT_COLUMNS = """
            SELECT p.id AS id,
                   (SELECT pn.name_text FROM plant_name pn
                    WHERE pn.plant_id = p.id AND pn.review_state = 'APPROVED' AND pn.name_role = 'COMMON' AND pn.language_tag LIKE 'zh%'
                    ORDER BY pn.id LIMIT 1) AS name_zh,
                   COALESCE(species.canonical_name,
                       (SELECT pn.name_text FROM plant_name pn
                        WHERE pn.plant_id = p.id AND pn.review_state = 'APPROVED' AND pn.name_role = 'SCIENTIFIC'
                        ORDER BY pn.id LIMIT 1)) AS scientific_name,
                   (SELECT pn.name_text FROM plant_name pn
                    WHERE pn.plant_id = p.id AND pn.review_state = 'APPROVED' AND pn.name_role = 'ENGLISH'
                    ORDER BY pn.id LIMIT 1) AS name_en,
                   (SELECT tn.name_text FROM taxon_name tn
                    WHERE tn.taxon_id = family.id AND tn.review_state = 'APPROVED' AND tn.language_tag LIKE 'zh%'
                    ORDER BY tn.id LIMIT 1) AS family_name_zh,
                   genus.canonical_name AS genus_name_latin,
                   (SELECT obs.raw_value FROM trait_observation obs
                    JOIN trait_definition td ON td.id = obs.trait_definition_id
                    WHERE obs.plant_id = p.id AND obs.review_state = 'APPROVED' AND td.trait_key = 'morphology'
                    ORDER BY obs.id LIMIT 1) AS morphology,
                   (SELECT obs.raw_value FROM trait_observation obs
                    JOIN trait_definition td ON td.id = obs.trait_definition_id
                    WHERE obs.plant_id = p.id AND obs.review_state = 'APPROVED' AND td.trait_key = 'distribution'
                    ORDER BY obs.id LIMIT 1) AS distribution,
                   p.review_state AS record_status,
                   (SELECT pn.review_state FROM plant_name pn
                    WHERE pn.plant_id = p.id AND pn.review_state = 'APPROVED' AND pn.name_role = 'SCIENTIFIC'
                    ORDER BY pn.id LIMIT 1) AS scientific_name_status,
                   sa.relative_path AS source_document,
                   JSON_UNQUOTE(JSON_EXTRACT(ev.locator, '$.printed_page')) AS source_printed_pages,
                   JSON_UNQUOTE(JSON_EXTRACT(ev.locator, '$.pdf_page')) AS source_pages,
                   sa.sha256 AS source_pdf_sha256
            """;

    @Select("SELECT COUNT(DISTINCT p.id) " + FROM_PLANTS)
    long countPlants(@Param("query") String query);

    @Select(SELECT_COLUMNS + FROM_PLANTS + " ORDER BY p.id ASC LIMIT #{limit}")
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class, id = true),
            @Arg(column = "name_zh", javaType = String.class),
            @Arg(column = "scientific_name", javaType = String.class),
            @Arg(column = "name_en", javaType = String.class),
            @Arg(column = "family_name_zh", javaType = String.class),
            @Arg(column = "genus_name_latin", javaType = String.class),
            @Arg(column = "morphology", javaType = String.class),
            @Arg(column = "distribution", javaType = String.class),
            @Arg(column = "record_status", javaType = String.class),
            @Arg(column = "scientific_name_status", javaType = String.class),
            @Arg(column = "source_document", javaType = String.class),
            @Arg(column = "source_printed_pages", javaType = String.class),
            @Arg(column = "source_pages", javaType = String.class),
            @Arg(column = "source_pdf_sha256", javaType = String.class)
    })
    List<PlantRecord> searchPlants(@Param("query") String query, @Param("limit") int limit);

    @Select(SELECT_COLUMNS + FROM_PLANT_DETAIL + " AND p.id = #{id}")
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class, id = true),
            @Arg(column = "name_zh", javaType = String.class),
            @Arg(column = "scientific_name", javaType = String.class),
            @Arg(column = "name_en", javaType = String.class),
            @Arg(column = "family_name_zh", javaType = String.class),
            @Arg(column = "genus_name_latin", javaType = String.class),
            @Arg(column = "morphology", javaType = String.class),
            @Arg(column = "distribution", javaType = String.class),
            @Arg(column = "record_status", javaType = String.class),
            @Arg(column = "scientific_name_status", javaType = String.class),
            @Arg(column = "source_document", javaType = String.class),
            @Arg(column = "source_printed_pages", javaType = String.class),
            @Arg(column = "source_pages", javaType = String.class),
            @Arg(column = "source_pdf_sha256", javaType = String.class)
    })
    Optional<PlantRecord> findById(@Param("id") long id);
}

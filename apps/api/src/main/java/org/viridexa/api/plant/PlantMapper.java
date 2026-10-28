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
    record Suggestion(long id,String label,String scientificName,String matchedName,String matchType) {}
    @Select("""
      SELECT p.id,COALESCE((SELECT cn.name_text FROM plant_name cn WHERE cn.plant_id=p.id AND cn.language_tag LIKE 'zh%' AND cn.name_role='COMMON' AND cn.review_state IN ('APPROVED','SOURCE_REPORTED') ORDER BY cn.id LIMIT 1),t.canonical_name,n.name_text) AS label,
       t.canonical_name AS scientificName,n.name_text AS matchedName,
       CASE WHEN n.name_role='SCIENTIFIC' THEN 'SCIENTIFIC_NAME' WHEN n.language_tag LIKE 'zh%' THEN 'CHINESE_NAME_OR_ALIAS' ELSE 'SOURCE_NAME' END AS matchType
      FROM graph_catalog p JOIN plant_name n ON n.plant_id=p.id LEFT JOIN taxon t ON t.id=p.taxon_id
      WHERE p.is_public=1 AND (n.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND n.review_state='VERIFIED_FROM_PRINTED_HEADING'))
       AND n.name_text LIKE #{prefix} ESCAPE '!'
      GROUP BY p.id,label,t.canonical_name,n.name_text,n.name_role,n.language_tag
      ORDER BY (n.name_text=#{term}) DESC,n.name_text,p.id LIMIT #{limit}
      """)
    List<Suggestion> suggestions(@Param("term") String term,@Param("prefix") String prefix,@Param("limit") int limit);

    String FROM_PLANTS = """
            FROM graph_catalog p
            LEFT JOIN taxon species ON species.id = p.taxon_id
            LEFT JOIN taxon genus ON genus.id = species.parent_id AND genus.taxon_rank = 'GENUS'
            LEFT JOIN taxon family ON family.id = genus.parent_id AND family.taxon_rank = 'FAMILY'
            LEFT JOIN evidence ev ON ev.id = species.evidence_id
            LEFT JOIN source_asset sa ON sa.id = ev.source_asset_id
            WHERE p.is_public=1
              AND (#{query} IS NULL
                   OR EXISTS (SELECT 1 FROM plant_name search_name
                              WHERE search_name.plant_id = p.id AND (search_name.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND search_name.review_state='VERIFIED_FROM_PRINTED_HEADING'))
                                AND search_name.name_text LIKE CONCAT('%', #{query}, '%'))
                   OR species.canonical_name LIKE CONCAT('%', #{query}, '%') OR genus.canonical_name LIKE CONCAT('%', #{query}, '%') OR family.canonical_name LIKE CONCAT('%', #{query}, '%'))
            """;

    String FROM_PLANT_DETAIL = """
            FROM graph_catalog p
            LEFT JOIN taxon species ON species.id = p.taxon_id
            LEFT JOIN taxon genus ON genus.id = species.parent_id AND genus.taxon_rank = 'GENUS'
            LEFT JOIN taxon family ON family.id = genus.parent_id AND family.taxon_rank = 'FAMILY'
            LEFT JOIN evidence ev ON ev.id = species.evidence_id
            LEFT JOIN source_asset sa ON sa.id = ev.source_asset_id
            WHERE p.is_public=1
            """;

    String SELECT_COLUMNS = """
            SELECT p.id AS id,
                   (SELECT pn.name_text FROM plant_name pn
                    WHERE pn.plant_id = p.id AND (pn.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND pn.review_state='VERIFIED_FROM_PRINTED_HEADING')) AND pn.name_role = 'COMMON' AND pn.language_tag LIKE 'zh%'
                    ORDER BY pn.id LIMIT 1) AS name_zh,
                   COALESCE(species.canonical_name,
                       (SELECT pn.name_text FROM plant_name pn
                        WHERE pn.plant_id = p.id AND (pn.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND pn.review_state='VERIFIED_FROM_PRINTED_HEADING')) AND pn.name_role = 'SCIENTIFIC'
                        ORDER BY pn.id LIMIT 1)) AS scientific_name,
                   (SELECT pn.name_text FROM plant_name pn
                    WHERE pn.plant_id = p.id AND (pn.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND pn.review_state='VERIFIED_FROM_PRINTED_HEADING')) AND pn.name_role = 'ENGLISH'
                    ORDER BY pn.id LIMIT 1) AS name_en,
                   (SELECT tn.name_text FROM taxon_name tn
                    WHERE tn.taxon_id = family.id AND tn.review_state = 'APPROVED' AND tn.language_tag LIKE 'zh%'
                    ORDER BY tn.id LIMIT 1) AS family_name_zh,
                   genus.canonical_name AS genus_name_latin,
                   (SELECT obs.raw_value FROM trait_observation obs
                    JOIN trait_definition td ON td.id = obs.trait_definition_id
                    WHERE p.review_state='APPROVED' AND obs.plant_id = p.id AND obs.review_state = 'APPROVED' AND td.trait_key = 'morphology'
                    ORDER BY obs.id LIMIT 1) AS morphology,
                   (SELECT obs.raw_value FROM trait_observation obs
                    JOIN trait_definition td ON td.id = obs.trait_definition_id
                    WHERE p.review_state='APPROVED' AND obs.plant_id = p.id AND obs.review_state = 'APPROVED' AND td.trait_key = 'distribution'
                    ORDER BY obs.id LIMIT 1) AS distribution,
                   IF(p.review_state='APPROVED','APPROVED','METADATA_ONLY') AS record_status,
                   (SELECT pn.review_state FROM plant_name pn
                    WHERE pn.plant_id = p.id AND (pn.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND pn.review_state='VERIFIED_FROM_PRINTED_HEADING')) AND pn.name_role = 'SCIENTIFIC'
                    ORDER BY pn.id LIMIT 1) AS scientific_name_status,
                   SUBSTRING_INDEX(sa.relative_path,'/',-1) AS source_document,
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
    @Select(SELECT_COLUMNS + FROM_PLANTS + " AND p.id > #{afterId} ORDER BY p.id ASC LIMIT #{limit}")
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
    List<PlantRecord> searchPlantsAfter(@Param("query") String query, @Param("limit") int limit, @Param("afterId") long afterId);

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

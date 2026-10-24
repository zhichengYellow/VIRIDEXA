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
    String SELECT_COLUMNS = """
            p.id AS id,
            p.name_zh AS name_zh,
            p.scientific_name AS scientific_name,
            p.name_en AS name_en,
            f.family_name_zh AS family_name_zh,
            g.genus_name_latin AS genus_name_latin,
            p.morphology AS morphology,
            p.distribution AS distribution,
            p.verification_status AS verification_status,
            p.scientific_name_status AS scientific_name_status,
            p.source_document AS source_document,
            p.source_printed_pages AS source_printed_pages,
            p.source_pages AS source_pages,
            p.source_pdf_sha256 AS source_pdf_sha256
            """;

    String FROM_VERIFIED_PLANTS = """
            FROM plants p
            LEFT JOIN plant_genera g ON g.id = p.genus_id
            LEFT JOIN plant_families f ON f.id = g.family_id
            WHERE p.is_public = TRUE
              AND p.verification_status = 'VERIFIED'
            """;

    @Select("SELECT COUNT(*) " + FROM_VERIFIED_PLANTS + " " +
            "AND (#{query} IS NULL " +
            "OR p.name_zh LIKE CONCAT('%', #{query}, '%') " +
            "OR p.scientific_name LIKE CONCAT('%', #{query}, '%') " +
            "OR EXISTS (SELECT 1 FROM plant_aliases a WHERE a.plant_id = p.id " +
            "AND a.source_status = 'VERIFIED' AND a.alias LIKE CONCAT('%', #{query}, '%'))) ")
    long countVerified(@Param("query") String query);

    @Select("SELECT " + SELECT_COLUMNS + " " + FROM_VERIFIED_PLANTS + " " +
            "AND (#{query} IS NULL " +
            "OR p.name_zh LIKE CONCAT('%', #{query}, '%') " +
            "OR p.scientific_name LIKE CONCAT('%', #{query}, '%') " +
            "OR EXISTS (SELECT 1 FROM plant_aliases a WHERE a.plant_id = p.id " +
            "AND a.source_status = 'VERIFIED' AND a.alias LIKE CONCAT('%', #{query}, '%'))) " +
            "ORDER BY p.id ASC LIMIT #{limit}")
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class, id = true),
            @Arg(column = "name_zh", javaType = String.class),
            @Arg(column = "scientific_name", javaType = String.class),
            @Arg(column = "name_en", javaType = String.class),
            @Arg(column = "family_name_zh", javaType = String.class),
            @Arg(column = "genus_name_latin", javaType = String.class),
            @Arg(column = "morphology", javaType = String.class),
            @Arg(column = "distribution", javaType = String.class),
            @Arg(column = "verification_status", javaType = String.class),
            @Arg(column = "scientific_name_status", javaType = String.class),
            @Arg(column = "source_document", javaType = String.class),
            @Arg(column = "source_printed_pages", javaType = String.class),
            @Arg(column = "source_pages", javaType = String.class),
            @Arg(column = "source_pdf_sha256", javaType = String.class)
    })
    List<PlantRecord> searchVerified(@Param("query") String query, @Param("limit") int limit);

    @Select("SELECT " + SELECT_COLUMNS + " " + FROM_VERIFIED_PLANTS + " AND p.id = #{id}")
    @ConstructorArgs({
            @Arg(column = "id", javaType = long.class, id = true),
            @Arg(column = "name_zh", javaType = String.class),
            @Arg(column = "scientific_name", javaType = String.class),
            @Arg(column = "name_en", javaType = String.class),
            @Arg(column = "family_name_zh", javaType = String.class),
            @Arg(column = "genus_name_latin", javaType = String.class),
            @Arg(column = "morphology", javaType = String.class),
            @Arg(column = "distribution", javaType = String.class),
            @Arg(column = "verification_status", javaType = String.class),
            @Arg(column = "scientific_name_status", javaType = String.class),
            @Arg(column = "source_document", javaType = String.class),
            @Arg(column = "source_printed_pages", javaType = String.class),
            @Arg(column = "source_pages", javaType = String.class),
            @Arg(column = "source_pdf_sha256", javaType = String.class)
    })
    Optional<PlantRecord> findVerifiedById(@Param("id") long id);
}

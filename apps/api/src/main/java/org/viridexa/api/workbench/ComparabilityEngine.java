package org.viridexa.api.workbench;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.viridexa.api.workbench.WorkbenchContract.*;

/** Conservative technical comparability checks; this does not make a scientific determination. */
@Component
public class ComparabilityEngine {
    private static final String NOTICE = "技术可比性检查不构成人工科学审核";
    private static final Set<String> OIL_KINDS = Set.of("OIL_CONTENT", "OIL_FA_COMPOSITION", "FAME_COMPOSITION");

    public Comparison check(List<Point> points) {
        List<CheckItem> checks = new ArrayList<>();
        List<Converted> converted = new ArrayList<>();
        if (points == null || points.size() < 2 || points.size() > 12 || points.stream().anyMatch(Objects::isNull)) {
            add(checks, "selection", "INSUFFICIENT", "需要选择2至12条有效测量记录");
            return result("INSUFFICIENT", checks, converted);
        }

        Set<String> ids = new HashSet<>();
        Set<String> samples = new HashSet<>();
        boolean badId = false;
        for (Point p : points) {
            if (blank(p.id()) || !ids.add(p.id())) badId = true;
            if (!blank(p.sampleId())) samples.add(p.sampleId());
        }
        if (badId) {
            add(checks, "selection", "INSUFFICIENT", "测量记录标识缺失或重复");
            return result("INSUFFICIENT", checks, converted);
        }
        if (samples.size() < 2 || points.stream().anyMatch(p -> blank(p.sampleId()))) {
            add(checks, "samples", "INSUFFICIENT", "至少需要来自两个不同样本的测量记录");
            return result("INSUFFICIENT", checks, converted);
        }

        boolean insufficient = false;
        boolean sideBySide = false;
        for (Point p : points) {
            if (p.value() == null || !finite(p.value()) || blank(p.unit())) {
                insufficient = true;
            }
            if (!"VERIFIED".equals(p.identityState()) || blank(p.taxonId())) insufficient = true;
            if (blank(p.plantPart())) insufficient = true;
            if (!"VERIFIED".equals(p.sourceIntegrity()) || blank(p.sourceId())) insufficient = true;
            if (!"OIL_CONTENT".equals(p.kind()) && blank(p.analyte())) insufficient = true;
        }
        add(checks, "analyteIdentity", points.stream().allMatch(p -> "OIL_CONTENT".equals(p.kind()) || !blank(p.analyte())) ? "PASS" : "INSUFFICIENT", "组分比较必须具有明确分析物，缺失分析物不能视为相同组分");
        add(checks, "values", insufficientValue(points) ? "INSUFFICIENT" : "PASS",
                insufficientValue(points) ? "每条记录都需要有限的数值和明确单位；缺失值不等同于零" : "数值和单位齐全");
        add(checks, "identity", points.stream().allMatch(p -> "VERIFIED".equals(p.identityState()) && !blank(p.taxonId())) ? "PASS" : "INSUFFICIENT",
                "需要已核验的物种 taxon 标识；植物条目 ID 本身不能证明物种相同");
        add(checks, "plantPart", points.stream().allMatch(p -> !blank(p.plantPart())) ? "PASS" : "INSUFFICIENT",
                "需要明确植物部位，并且文字规范化后完全一致");
        add(checks, "sourceIntegrity", points.stream().allMatch(p -> "VERIFIED".equals(p.sourceIntegrity()) && !blank(p.sourceId())) ? "PASS" : "INSUFFICIENT",
                "所有来源数据完整性都必须已核验");


        Point first = points.getFirst();
        boolean identitySame = points.stream().allMatch(p -> Objects.equals(first.taxonId(), p.taxonId())
                && normalize(first.plantPart()).equals(normalize(p.plantPart())));
        if (!identitySame) sideBySide = true;
        add(checks, "scientificIdentity", identitySame ? "PASS" : "SIDE_BY_SIDE",
                "物种 taxon 与植物部位必须分别完全一致");

        boolean kindSame = points.stream().allMatch(p -> Objects.equals(first.kind(), p.kind()));
        boolean anyFood = points.stream().anyMatch(p -> "FOOD_NUTRIENT".equals(p.kind()));
        boolean allOil = points.stream().allMatch(p -> OIL_KINDS.contains(p.kind()));
        if (!kindSame || (anyFood && !points.stream().allMatch(p -> "FOOD_NUTRIENT".equals(p.kind())))) sideBySide = true;
        add(checks, "measurementKind", kindSame ? "PASS" : "SIDE_BY_SIDE",
                anyFood ? "食品营养测量与油脂测量不能混合，也不在此引擎中做营养单位换算" : "测量类别必须一致，不能把不同油脂测量类别合并");

        boolean analyteSame = points.stream().allMatch(p -> Objects.equals(first.analyte(), p.analyte()));
        if (!(allOil && "OIL_CONTENT".equals(first.kind()) && points.stream().allMatch(p -> p.analyte() == null))) {
            if (!analyteSame) sideBySide = true;
        }
        add(checks, "analyte", analyteSame || (allOil && "OIL_CONTENT".equals(first.kind()) && points.stream().allMatch(p -> p.analyte() == null)) ? "PASS" : "SIDE_BY_SIDE",
                "分析物必须一致；只有未指定分析物的 OIL_CONTENT 可整体为空");

        boolean basisValid = points.stream().allMatch(p -> knownBasis(p.basis()));
        boolean basisSame = basisValid && points.stream().allMatch(p -> normalize(p.basis()).equals(normalize(first.basis())));
        if (!basisSame) sideBySide = true;
        add(checks, "basis", basisSame ? "PASS" : "SIDE_BY_SIDE", "计量基础必须明确且完全一致；不会猜测或补全来源口径");

        boolean methodsKnownSame = points.stream().allMatch(p -> !blank(p.method()) && !unknown(p.method()))
                && points.stream().allMatch(p -> normalize(p.method()).equals(normalize(first.method())));
        if (!methodsKnownSame) sideBySide = true;
        add(checks, "method", methodsKnownSame ? "PASS" : "SIDE_BY_SIDE", "分析方法缺失或不同，无法视为同一技术口径");

        boolean sourceReady = points.stream().allMatch(p -> "PERMITTED".equals(p.sourceLicense())
                && "PUBLISHED".equals(p.publicationState()) && "APPROVED".equals(p.reviewState()));
        if (!sourceReady) sideBySide = true;
        add(checks, "publicationAndPermission", sourceReady ? "PASS" : "SIDE_BY_SIDE",
                "直接比较要求来源许可允许、数据已发布且审核已批准");

        boolean fameBasisValid = !"FAME_COMPOSITION".equals(first.kind())
                || (normalize(first.basis()).contains("fame") && methodsKnownSame);
        if (!fameBasisValid) sideBySide = true;
        add(checks, "fameBasis", fameBasisValid ? "PASS" : "SIDE_BY_SIDE",
                "FAME 百分比仅在明确 FAME 基础且分析方法已知一致时比较");

        if (anyFood) sideBySide = true;
        if (!kindSame || !identitySame || !basisSame || !methodsKnownSame || !sourceReady || !analyteSame || !allOil || !fameBasisValid) {
            sideBySide = true;
        }
        Conversion conversion = conversion(points);
        if (conversion == null) {
            add(checks, "units", "SIDE_BY_SIDE", "单位不相同或不属于支持的精确油脂单位换算范围");
            if(insufficient)return result("INSUFFICIENT", checks, converted);
            return result("SIDE_BY_SIDE", checks, converted);
        }
        boolean convertedUnits = points.stream().anyMatch(p -> !unit(p.unit()).equals("%"));
        add(checks, "units", convertedUnits ? "CONVERTIBLE" : "PASS", conversion.rule());

        if(insufficient)return result("INSUFFICIENT", checks, converted);
        if(sideBySide)return result("SIDE_BY_SIDE", checks, converted);
        for (Point p : points) {
            BigDecimal factor = switch (unit(p.unit())) {
                case "g/kg", "mg/g" -> new BigDecimal("0.1");
                default -> BigDecimal.ONE;
            };
            BigDecimal value = p.value().multiply(factor);
            if (OIL_KINDS.contains(p.kind()) && (value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0)) {
                add(checks, "range", "INSUFFICIENT", "油脂百分数换算后必须处于0至100之间");
                return result("INSUFFICIENT", checks, List.of());
            }
            converted.add(new Converted(p.id(), p.value(), p.unit(), value, "%", conversion.rule()));
        }
        return result(convertedUnits ? "CONVERTIBLE" : "DIRECT", checks, converted);
    }

    private static Conversion conversion(List<Point> points) {
        if (points.stream().allMatch(p -> unit(p.unit()).equals("%"))) return new Conversion(BigDecimal.ONE, "同单位 %，数值原样保留");
        if (!points.stream().allMatch(p -> fractionBasis(p.basis()))) return null;
        for (Point p : points) {
            String unit = unit(p.unit());
            if (!(unit.equals("%") || unit.equals("g/100g") || unit.equals("g/kg") || unit.equals("mg/g"))) return null;
        }
        return new Conversion(BigDecimal.ONE, "按明确质量分数基础换算为 %：g/100g × 1；g/kg 或 mg/g × 0.1");
    }

    private static boolean fractionBasis(String basis) {
        String b = normalize(basis);
        return b.contains("mass/mass") || b.contains("mass fraction") || b.contains("dry seed mass") || b.contains("质量分数") || b.contains("干种子质量");
    }
    private static boolean knownBasis(String s) { return !blank(s) && !unknown(s); }
    private static boolean unknown(String s) {
        String n = normalize(s).replace('-', ' ').replace('_', ' ');
        return n.contains("unknown") || n.contains("unspecified") || n.contains("as reported") || n.contains("未知") || n.contains("未说明") || n.contains("不明");
    }
    private static boolean insufficientValue(List<Point> points) { return points.stream().anyMatch(p -> p.value() == null || !finite(p.value()) || blank(p.unit())); }
    private static boolean finite(BigDecimal v) { return v != null; } // BigDecimal cannot represent NaN or infinity.
    private static String unit(String s) { return s == null ? "" : s.trim(); }
    private static String normalize(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static void add(List<CheckItem> checks, String dimension, String status, String reason) { checks.add(new CheckItem(dimension, status, reason)); }
    private static Comparison result(String conclusion, List<CheckItem> checks, List<Converted> values) { return new Comparison(conclusion, List.copyOf(checks), List.copyOf(values), false, NOTICE); }
    private record Conversion(BigDecimal factor, String rule) {}
}

package com.forestry.aireviewer.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Filename-keyword classifier that maps a Chinese forestry filename to a
 * document type code. The rule list is order-sensitive — more specific
 * patterns (e.g. 国家储备林 + 建设方案) must precede broader fallbacks
 * (e.g. plain 可行性研究) to avoid mis-classification.
 */
@Component
public class DocumentTypeClassifier {

    private record Rule(Pattern pattern, String code) {}

    private static final List<Rule> RULES = List.of(
            // -- Suffix-driven types take precedence over body keywords --
            // "森林经营方案_审核意见" must classify as REVIEW_OPINION, not 经营方案
            new Rule(Pattern.compile("(审核意见|审查意见|修改意见|审查修改意见)"),
                    "REVIEW_OPINION"),
            new Rule(Pattern.compile("(院审|评审材料|评审会|评审稿|评审-)"),
                    "INSTITUTIONAL_REVIEW_MATERIAL"),
            // "森林经营规划编制指南" must classify as COMPILATION_GUIDELINE, not 经营规划
            new Rule(Pattern.compile("编制指南"),
                    "COMPILATION_GUIDELINE"),
            new Rule(Pattern.compile("竣工指南"),
                    "PROJECT_COMPLETION_GUIDELINE"),

            // Species-specific topical assessments (must come before generic biodiversity)
            new Rule(Pattern.compile("(鸟类|兽类|鱼类|爬行类|两栖类|昆虫|植物).{0,8}(专题|影响|评价)"),
                    "SPECIES_SPECIAL_TOPIC_ASSESSMENT"),

            // National reserve forest — program (most specific first)
            new Rule(Pattern.compile("(国家储备林|国储林).{0,6}(建设方案|建设规划|实施方案|建设)"),
                    "NATIONAL_RESERVE_FOREST_PROGRAM"),
            new Rule(Pattern.compile("(国家储备林|国储林).{0,8}(可行性|可研)"),
                    "NATIONAL_RESERVE_FOREST_FEASIBILITY"),

            // Forest land use — argumentation and feasibility
            new Rule(Pattern.compile("(不可避让|使用林地.{0,4}论证|占用.{0,4}林地.{0,4}论证)"),
                    "FOREST_LAND_USE_ARGUMENTATION"),
            new Rule(Pattern.compile("(使用林地.{0,8}可行性|占用.{0,4}林地.{0,8}可行性|林地.{0,4}可研)"),
                    "FOREST_LAND_USE_FEASIBILITY"),

            // Impact assessment — order: biodiversity > nature-reserve-project > ecological > environmental
            new Rule(Pattern.compile("生物多样性.{0,10}(影响)?(评价|评估)"),
                    "BIODIVERSITY_IMPACT_ASSESSMENT"),
            new Rule(Pattern.compile("自然保护区.{0,8}建设项目.{0,10}评价"),
                    "NATURE_RESERVE_PROJECT_ASSESSMENT"),
            new Rule(Pattern.compile("生态.{0,4}(影响)?评价"),
                    "ECOLOGICAL_IMPACT_ASSESSMENT"),
            new Rule(Pattern.compile("环境影响评价"),
                    "ENVIRONMENTAL_IMPACT_ASSESSMENT"),

            // Site selection argumentation
            new Rule(Pattern.compile("(规划选址|选址论证)"),
                    "SITE_SELECTION_ARGUMENTATION"),

            // Plans
            new Rule(Pattern.compile("植被恢复方案"),
                    "VEGETATION_RESTORATION_PLAN"),
            new Rule(Pattern.compile("(临时.{0,4}占用.{0,4}林地.{0,4}恢复方案|恢复方案)"),
                    "VEGETATION_RESTORATION_PLAN"),
            new Rule(Pattern.compile("占补平衡"),
                    "COMPENSATION_BALANCE_PLAN"),
            new Rule(Pattern.compile("森林经营规划"),
                    "FOREST_MANAGEMENT_REGIONAL_PLAN"),
            new Rule(Pattern.compile("森林经营方案"),
                    "FOREST_MANAGEMENT_PLAN"),
            new Rule(Pattern.compile("林地保护利用规划"),
                    "FOREST_LAND_PROTECTION_PLAN"),
            new Rule(Pattern.compile("自然保护区.{0,4}总体规划"),
                    "NATURE_RESERVE_MASTER_PLAN"),
            new Rule(Pattern.compile("(防火林带|防火道路|防火工程)"),
                    "FIRE_PREVENTION_CONSTRUCTION_PLAN"),

            // Design documents
            new Rule(Pattern.compile("初步设计"),
                    "PRELIMINARY_DESIGN"),
            new Rule(Pattern.compile("作业设计"),
                    "OPERATION_DESIGN"),

            // Survey / form
            new Rule(Pattern.compile("林地现状调查表"),
                    "FOREST_LAND_STATUS_FORM"),
            new Rule(Pattern.compile("(造林.{0,4}调查|调查评估|调查报告)"),
                    "FORESTRY_SURVEY_ASSESSMENT"),

            // Acceptance / completion
            new Rule(Pattern.compile("森林.{0,4}采伐.{0,4}验收"),
                    "FOREST_HARVEST_ACCEPTANCE"),
            new Rule(Pattern.compile("公益林.{0,4}(检查)?.{0,4}验收"),
                    "PUBLIC_WELFARE_FOREST_INSPECTION"),
            new Rule(Pattern.compile("(竣工验收|工作总结|总结报告)"),
                    "PROJECT_COMPLETION_SUMMARY"),

            // Industry standards (broad fallback among guidelines)
            new Rule(Pattern.compile("(技术规范|国标|行业标准|GB/T|LY/T)"),
                    "INDUSTRY_TECHNICAL_STANDARD"),

            // Broadest fallbacks last
            new Rule(Pattern.compile("(可行性研究|可行性报告|可研报告|可研)"),
                    "FORESTRY_PROJECT_FEASIBILITY")
    );

    public Optional<String> classifyByFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return Optional.empty();
        }
        for (Rule rule : RULES) {
            if (rule.pattern.matcher(filename).find()) {
                return Optional.of(rule.code);
            }
        }
        return Optional.empty();
    }
}

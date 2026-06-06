package com.forestry.aireviewer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentTypeClassifierTest {

    private final DocumentTypeClassifier classifier = new DocumentTypeClassifier();

    @ParameterizedTest
    @CsvSource({
            "'幸福220千伏输变电工程占用湿地生态影响评价及占补平衡方案.docx',           ECOLOGICAL_IMPACT_ASSESSMENT",
            "'G1535潮州至南昌国家高速公路对南昌白虎岭县级自然保护区生物多样性影响评价报告.docx', BIODIVERSITY_IMPACT_ASSESSMENT",
            "'柴桑区三能风电场项目鸟类影响专题评价报告.docx',                       SPECIES_SPECIAL_TOPIC_ASSESSMENT",
            "'赣州北500千伏输变电工程项目使用林地可行性报告.docx',                  FOREST_LAND_USE_FEASIBILITY",
            "'于都县国家储备林可研报告.docx',                                       NATIONAL_RESERVE_FOREST_FEASIBILITY",
            "'德安县国家储备林项目建设方案.pdf',                                    NATIONAL_RESERVE_FOREST_PROGRAM",
            "'国家储备林建设规划 (2021-2035年).doc',                                NATIONAL_RESERVE_FOREST_PROGRAM",
            "'鄱阳湖珠湖蓄滞洪区临时使用乔木林不可避让性论证审0413.docx',           FOREST_LAND_USE_ARGUMENTATION",
            "'广昌县青龙湖-龙凤岩风景名胜区水云水库工程规划选址论证报告.pdf',       SITE_SELECTION_ARGUMENTATION",
            "'江西蓄滞洪（方洲斜塘）植被恢复方案.pdf',                              VEGETATION_RESTORATION_PLAN",
            "'0414蓄滞洪临时用地恢复方案.docx',                                     VEGETATION_RESTORATION_PLAN",
            "'坳上林场森林经营方案2026-2035年（修正版）.docx',                      FOREST_MANAGEMENT_PLAN",
            "'森林经营规划编制指南.pdf',                                            COMPILATION_GUIDELINE",
            "'宜黄县园区扩区调区林地保护利用规划.docx',                             FOREST_LAND_PROTECTION_PLAN",
            "'江西婺源森林鸟类国家级自然保护区总体规划 (2022-2031年).pdf',          NATURE_RESERVE_MASTER_PLAN",
            "'章贡区防火林带（保护区）.docx',                                       FIRE_PREVENTION_CONSTRUCTION_PLAN",
            "'章贡区防火道路（森林公园）.docx',                                     FIRE_PREVENTION_CONSTRUCTION_PLAN",
            "'初步设计总说明书.docx',                                               PRELIMINARY_DESIGN",
            "'安义县长埠镇大路村森林火灾迹地复绿项目作业设计.pdf',                  OPERATION_DESIGN",
            "'361102信州区造林绿化空间调查评估成果报告 240201.doc',                 FORESTRY_SURVEY_ASSESSMENT",
            "'弋阳县 2023年度森林采伐区验收报告.doc',                               FOREST_HARVEST_ACCEPTANCE",
            "'弋阳县 2023年公益林检查验收报告.docx',                                PUBLIC_WELFARE_FOREST_INSPECTION",
            "'江西余江白塔河省级湿地公园试点建设工作总结报告.docx',                 PROJECT_COMPLETION_SUMMARY",
            "'坳上林场森林经营方案_审核意见.docx',                                  REVIEW_OPINION",
            "'会昌防火道路影响评价审查修改意见.docx',                               REVIEW_OPINION",
            "'国有林场森林经营方案编制指南.pdf',                                    COMPILATION_GUIDELINE",
            "'欧投行长江经济带珍稀树种保护与发展项目竣工指南 2024评审发出稿.docx', PROJECT_COMPLETION_GUIDELINE",
            "'高安市赣能50MW农光互补地面光伏发电项目.docx',                         ",
            "'1.于都县国家储备林可研报告.docx',                                     NATIONAL_RESERVE_FOREST_FEASIBILITY"
    })
    @DisplayName("filename keyword → document type code")
    void classifyByFilename(String filename, String expectedCode) {
        var actual = classifier.classifyByFilename(filename);
        if (expectedCode == null || expectedCode.isBlank()) {
            assertThat(actual).isEmpty();
        } else {
            assertThat(actual).contains(expectedCode);
        }
    }
}

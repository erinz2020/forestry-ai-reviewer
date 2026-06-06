-- Catalog of document types. Each type captures a class of forestry review
-- document (生物多样性影响评价报告, 国家储备林可行性研究报告, ...) so review
-- workflows can apply type-specific criteria and retrieval can be filtered.

CREATE TABLE document_types (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    code         VARCHAR(64)  NOT NULL UNIQUE,
    name         VARCHAR(128) NOT NULL,
    category     VARCHAR(64)  NOT NULL,
    description  TEXT,
    -- profile encodes what this type's documents commonly look like:
    -- required sections, frequent issues, mandatory evidence, etc.
    profile      JSONB,
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_types_category ON document_types (category);

INSERT INTO document_types (code, name, category) VALUES
    ('BIODIVERSITY_IMPACT_ASSESSMENT',          '生物多样性影响评价报告',                'IMPACT_ASSESSMENT'),
    ('ECOLOGICAL_IMPACT_ASSESSMENT',            '生态影响评价报告',                      'IMPACT_ASSESSMENT'),
    ('NATURE_RESERVE_PROJECT_ASSESSMENT',       '自然保护区建设项目影响评价报告',        'IMPACT_ASSESSMENT'),
    ('SPECIES_SPECIAL_TOPIC_ASSESSMENT',        '鸟类/其它专题影响评价报告',             'IMPACT_ASSESSMENT'),
    ('ENVIRONMENTAL_IMPACT_ASSESSMENT',         '环境影响评价报告',                      'IMPACT_ASSESSMENT'),

    ('FOREST_LAND_USE_FEASIBILITY',             '使用林地可行性报告',                    'FEASIBILITY_STUDY'),
    ('NATIONAL_RESERVE_FOREST_FEASIBILITY',     '国家储备林可行性研究报告',              'FEASIBILITY_STUDY'),
    ('FORESTRY_PROJECT_FEASIBILITY',            '林业建设项目可行性研究报告',            'FEASIBILITY_STUDY'),

    ('SITE_SELECTION_ARGUMENTATION',            '规划选址论证报告',                      'ARGUMENTATION_REPORT'),
    ('FOREST_LAND_USE_ARGUMENTATION',           '不可避让/使用林地论证报告',             'ARGUMENTATION_REPORT'),

    ('VEGETATION_RESTORATION_PLAN',             '植被恢复方案',                          'PLAN_AND_PROGRAM'),
    ('COMPENSATION_BALANCE_PLAN',               '占补平衡方案',                          'PLAN_AND_PROGRAM'),
    ('FOREST_MANAGEMENT_PLAN',                  '森林经营方案',                          'PLAN_AND_PROGRAM'),
    ('FOREST_MANAGEMENT_REGIONAL_PLAN',         '森林经营规划',                          'PLAN_AND_PROGRAM'),
    ('FOREST_LAND_PROTECTION_PLAN',             '林地保护利用规划',                      'PLAN_AND_PROGRAM'),
    ('NATURE_RESERVE_MASTER_PLAN',              '自然保护区总体规划',                    'PLAN_AND_PROGRAM'),
    ('NATIONAL_RESERVE_FOREST_PROGRAM',         '国家储备林建设规划/方案/实施方案',      'PLAN_AND_PROGRAM'),
    ('FIRE_PREVENTION_CONSTRUCTION_PLAN',       '防火工程建设方案',                      'PLAN_AND_PROGRAM'),

    ('PRELIMINARY_DESIGN',                      '初步设计',                              'DESIGN_DOCUMENT'),
    ('OPERATION_DESIGN',                        '作业设计',                              'DESIGN_DOCUMENT'),

    ('FORESTRY_SURVEY_ASSESSMENT',              '林业调查评估报告',                      'SURVEY_ASSESSMENT'),
    ('FOREST_LAND_STATUS_FORM',                 '林地现状调查表',                        'SURVEY_ASSESSMENT'),

    ('FOREST_HARVEST_ACCEPTANCE',               '森林采伐验收报告',                      'ACCEPTANCE_SUMMARY'),
    ('PUBLIC_WELFARE_FOREST_INSPECTION',        '公益林检查验收报告',                    'ACCEPTANCE_SUMMARY'),
    ('PROJECT_COMPLETION_SUMMARY',              '项目竣工/工作总结报告',                 'ACCEPTANCE_SUMMARY'),

    ('REVIEW_OPINION',                          '审核意见',                              'REVIEW_DOCUMENT'),
    ('INSTITUTIONAL_REVIEW_MATERIAL',           '院审/评审材料',                         'REVIEW_DOCUMENT'),

    ('COMPILATION_GUIDELINE',                   '编制指南',                              'STANDARD_GUIDELINE'),
    ('PROJECT_COMPLETION_GUIDELINE',            '项目竣工指南',                          'STANDARD_GUIDELINE'),
    ('INDUSTRY_TECHNICAL_STANDARD',             '行业技术规范',                          'STANDARD_GUIDELINE');

ALTER TABLE documents
    ADD COLUMN document_type_id UUID REFERENCES document_types(id);

ALTER TABLE review_cases
    ADD COLUMN document_type_id UUID REFERENCES document_types(id);

CREATE INDEX idx_documents_type        ON documents (document_type_id);
CREATE INDEX idx_review_cases_type     ON review_cases (document_type_id);

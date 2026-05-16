package com.forestry.aireviewer.service;

import com.forestry.aireviewer.dto.AIReviewCategory;
import com.forestry.aireviewer.dto.AIReviewFinding;
import com.forestry.aireviewer.dto.AIReviewResponse;
import com.forestry.aireviewer.dto.AIReviewSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic mock provider used for local development and tests. Returns a
 * small, chunk-deterministic slice of a 7-template pool so multi-chunk
 * documents produce a realistic spread of findings without flooding the
 * database. Active when {@code app.review.provider=mock} (the default).
 */
@Service
@ConditionalOnProperty(name = "app.review.provider", havingValue = "mock", matchIfMissing = true)
public class MockReviewService implements ReviewService {

    private static final Logger log = LoggerFactory.getLogger(MockReviewService.class);

    private static final List<AIReviewFinding> TEMPLATES = List.of(
            new AIReviewFinding(
                    AIReviewCategory.INTERNAL_CONTRADICTION,
                    AIReviewSeverity.HIGH,
                    0.92,
                    "Section 2.1",
                    "The stated project area differs between sections.",
                    "Section 1 states \"total project area of 500 hectares\" while Section 2.1 refers to \"approximately 350 hectares of forest land\".",
                    "Verify the correct total project area and update all references consistently.",
                    List.of("Document Section 1", "Document Section 2.1")
            ),
            new AIReviewFinding(
                    AIReviewCategory.MISSING_EVIDENCE,
                    AIReviewSeverity.HIGH,
                    0.88,
                    "Section 3.2",
                    "Environmental impact assessment regulations require baseline biodiversity data before project approval.",
                    "Per Regulation 12.3 of the Environmental Impact Assessment Guidelines, baseline biodiversity data must cover at least one full year.",
                    "Conduct and include a comprehensive biodiversity survey covering all four seasons.",
                    List.of("Environmental Impact Assessment Guidelines §12.3")
            ),
            new AIReviewFinding(
                    AIReviewCategory.BIODIVERSITY_RISK,
                    AIReviewSeverity.HIGH,
                    0.81,
                    "Section 4.1",
                    "Logging activity may affect water quality and habitat for protected species.",
                    "Satellite imagery shows a wetland ecosystem within 2km downstream of the proposed logging site.",
                    "Include a wetland impact assessment and propose buffer zones around water bodies.",
                    List.of("Satellite imagery cross-reference")
            ),
            new AIReviewFinding(
                    AIReviewCategory.UNSUPPORTED_CONCLUSION,
                    AIReviewSeverity.MEDIUM,
                    0.74,
                    "Section 5.3",
                    "Conclusion lacks supporting data, modeling, or referenced studies.",
                    "No environmental modeling, peer-reviewed references, or quantitative data are cited in Section 5.",
                    "Provide quantitative environmental impact analysis (e.g., carbon emissions, habitat loss calculations) to support this conclusion.",
                    List.of("Document Section 5.3")
            ),
            new AIReviewFinding(
                    AIReviewCategory.VAGUE_LANGUAGE,
                    AIReviewSeverity.MEDIUM,
                    0.69,
                    "Section 3.4",
                    "Vague commitments without actionable detail or timelines.",
                    "The phrase \"appropriate measures\" appears 3 times in the document without further specification.",
                    "Replace with specific mitigation measures, responsible parties, implementation timelines, and monitoring plans.",
                    List.of("Document Section 3.4")
            ),
            new AIReviewFinding(
                    AIReviewCategory.WEAK_MITIGATION,
                    AIReviewSeverity.MEDIUM,
                    0.72,
                    "Section 6.1",
                    "Proposed reforestation species list includes Eucalyptus, which is not native and may harm local ecosystems.",
                    "The regional forestry guidelines (Appendix C) list approved native species for reforestation.",
                    "Consult with a local botanist and replace non-native species with indigenous alternatives.",
                    List.of("Regional Forestry Guidelines, Appendix C")
            ),
            new AIReviewFinding(
                    AIReviewCategory.POSSIBLE_REGULATORY_CONCERN,
                    AIReviewSeverity.LOW,
                    0.58,
                    "Section 7",
                    "Monitoring frequency and methodology are undefined.",
                    "Standard practice requires at minimum semi-annual monitoring reports per forestry management guidelines.",
                    "Specify monitoring schedule (e.g., quarterly), parameters to measure, and reporting requirements.",
                    List.of("Forestry Management Guidelines")
            )
    );

    @Override
    public AIReviewResponse review(ReviewRequest request) {
        log.info("Mock AI reviewing '{}' chunk {}/{} ({} chars)",
                request.documentTitle(),
                request.chunkIndex() + 1,
                request.totalChunks(),
                request.chunkContent().length());

        int count = 2 + (request.chunkIndex() % 2);
        List<AIReviewFinding> findings = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int templateIdx = (request.chunkIndex() * 2 + i) % TEMPLATES.size();
            findings.add(TEMPLATES.get(templateIdx));
        }
        return new AIReviewResponse(findings);
    }
}

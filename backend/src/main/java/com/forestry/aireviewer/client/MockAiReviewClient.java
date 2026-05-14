package com.forestry.aireviewer.client;

import com.forestry.aireviewer.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class MockAiReviewClient implements AiReviewClient {

    private static final Logger log = LoggerFactory.getLogger(MockAiReviewClient.class);

    @Override
    public List<Finding> review(String extractedText, String documentTitle, UUID documentId) {
        log.info("Mock AI reviewing document '{}' ({} chars)", documentTitle, extractedText.length());

        List<Finding> findings = new ArrayList<>();

        findings.add(createFinding(documentId,
                FindingType.INTERNAL_CONTRADICTION, FindingSeverity.HIGH,
                "Section 2.1",
                "The project area is described as 500 hectares in Section 1 but 350 hectares in Section 2.",
                "The stated project area differs between sections.",
                "Verify the correct total project area and update all references consistently.",
                "Section 1 states \"total project area of 500 hectares\" while Section 2.1 refers to \"approximately 350 hectares of forest land\"."));

        findings.add(createFinding(documentId,
                FindingType.MISSING_EVIDENCE, FindingSeverity.HIGH,
                "Section 3.2",
                "No baseline biodiversity survey data is provided for the proposed logging area.",
                "Environmental impact assessment regulations require baseline biodiversity data before project approval.",
                "Conduct and include a comprehensive biodiversity survey covering all four seasons.",
                "Per Regulation 12.3 of the Environmental Impact Assessment Guidelines, baseline biodiversity data must cover at least one full year."));

        findings.add(createFinding(documentId,
                FindingType.BIODIVERSITY_RISK, FindingSeverity.HIGH,
                "Section 4.1",
                "The report does not address potential impact on the protected wetland habitat downstream.",
                "Logging activity may affect water quality and habitat for protected species.",
                "Include a wetland impact assessment and propose buffer zones around water bodies.",
                "Satellite imagery shows a wetland ecosystem within 2km downstream of the proposed logging site."));

        findings.add(createFinding(documentId,
                FindingType.UNSUPPORTED_CONCLUSION, FindingSeverity.MEDIUM,
                "Section 5.3",
                "\"The project will have minimal environmental impact\" is stated without supporting analysis.",
                "Conclusion lacks supporting data, modeling, or referenced studies.",
                "Provide quantitative environmental impact analysis (e.g., carbon emissions, habitat loss calculations) to support this conclusion.",
                "No environmental modeling, peer-reviewed references, or quantitative data are cited in Section 5."));

        findings.add(createFinding(documentId,
                FindingType.VAGUE_LANGUAGE, FindingSeverity.MEDIUM,
                "Section 3.4",
                "\"Appropriate measures will be taken to protect wildlife\" — no specific measures are described.",
                "Vague commitments without actionable detail or timelines.",
                "Replace with specific mitigation measures, responsible parties, implementation timelines, and monitoring plans.",
                "The phrase \"appropriate measures\" appears 3 times in the document without further specification."));

        findings.add(createFinding(documentId,
                FindingType.MISSING_EVIDENCE, FindingSeverity.MEDIUM,
                "Section 6.1",
                "The reforestation plan references tree species not native to the region.",
                "Proposed reforestation species list includes Eucalyptus, which is not native and may harm local ecosystems.",
                "Consult with a local botanist and replace non-native species with indigenous alternatives.",
                "The regional forestry guidelines (Appendix C) list approved native species for reforestation."));

        findings.add(createFinding(documentId,
                FindingType.VAGUE_LANGUAGE, FindingSeverity.LOW,
                "Section 7",
                "\"Monitoring will be conducted regularly\" — no frequency or methodology specified.",
                "Monitoring frequency and methodology are undefined.",
                "Specify monitoring schedule (e.g., quarterly), parameters to measure, and reporting requirements.",
                "Standard practice requires at minimum semi-annual monitoring reports per forestry management guidelines."));

        return findings;
    }

    private Finding createFinding(UUID documentId, FindingType type, FindingSeverity severity,
                                  String location, String quote, String description,
                                  String suggestion, String evidence) {
        Finding f = new Finding();
        f.setDocumentId(documentId);
        f.setType(type);
        f.setSeverity(severity);
        f.setLocation(location);
        f.setQuote(quote);
        f.setDescription(description);
        f.setSuggestion(suggestion);
        f.setEvidence(evidence);
        return f;
    }
}

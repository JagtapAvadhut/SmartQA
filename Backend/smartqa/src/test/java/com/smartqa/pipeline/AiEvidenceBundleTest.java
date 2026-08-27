package com.smartqa.pipeline;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiEvidenceBundleTest {

    @Test
    void fromFailureEvidenceIncludesCandidatesAndMasksSecrets() throws Exception {
        Path shot = Files.createTempFile("smartqa-evidence", ".png");
        Files.write(shot, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        FailureEvidence evidence = FailureEvidence.builder()
                .url("https://www.urbancompany.com/pune")
                .pageTitle("Urban Company")
                .instruction("Click profile icon")
                .action("click")
                .target("profile")
                .candidateLocators(List.of("cart-icon", "profile-icon"))
                .candidateScores(List.of(88.0, 86.0))
                .domExcerpt("button api_key=sk-secret123 aria-label=cart")
                .screenshotPath(shot.toString())
                .previousAttempts(List.of("CLOSE_OVERLAY"))
                .build();

        AiEvidenceBundle bundle = AiEvidenceBundle.from(evidence);

        assertTrue(bundle.screenshotIncluded());
        assertTrue(bundle.domIncluded());
        assertTrue(bundle.toCompactText().contains("candidate-A"));
        assertTrue(bundle.toCompactText().contains("candidate-B"));
        assertTrue(bundle.toCompactText().contains("api_key=***"));
        assertFalse(bundle.toCompactText().contains("sk-secret123"));
        assertEquals(1, bundle.mediaParts().size());
        Files.deleteIfExists(shot);
    }

    @Test
    void forBeforeActionAttachesScreenshotMedia() {
        AiEvidenceBundle bundle = AiEvidenceBundle.forBeforeAction(
                "https://example.com",
                "Example",
                "click",
                "profile",
                "Click profile",
                List.of("A", "B"),
                List.of(90.0, 88.0),
                new byte[] {1, 2, 3, 4},
                "div role=button",
                "name=Account");
        assertTrue(bundle.screenshotIncluded());
        assertTrue(bundle.evidenceSize() > 0);
        assertEquals("image/png", bundle.mediaParts().getFirst().mimeType());
    }

    @Test
    void emptyBundleDoesNotClaimMissingEvidence() {
        AiEvidenceBundle empty = AiEvidenceBundle.empty();
        assertFalse(empty.screenshotIncluded());
        assertFalse(empty.domIncluded());
        assertFalse(empty.accessibilityIncluded());
        assertFalse(empty.browserEvidencePresent());
        assertFalse(empty.assertionEvidencePresent());
        assertFalse(empty.candidatesPresent());
        assertTrue(empty.toCompactText().contains("screenshot=false"));
    }
}

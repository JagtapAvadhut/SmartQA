package com.smartqa.browser.intelligence;

import java.util.List;
import java.util.Set;

/**
 * Normalized browser-control projection of {@link ElementCandidate} plus optional CDP/AX.
 * Evidence only — not an executor and not a second candidate store.
 */
public record PhysicalControl(
        String controlId,
        String role,
        ControlType controlType,
        String accessibleName,
        String visibleText,
        String value,
        String placeholder,
        String ariaLabel,
        String ariaRole,
        String bbox,
        boolean visible,
        boolean enabled,
        boolean actionable,
        boolean checked,
        boolean selected,
        boolean disabled,
        String parentId,
        String containerId,
        List<String> ancestorIds,
        List<String> siblingIds,
        String frameId,
        String shadowRootId,
        String visualRegionId,
        String semanticContext,
        Set<ControlCapability> capabilities
) {
    public PhysicalControl {
        ancestorIds = ancestorIds == null ? List.of() : List.copyOf(ancestorIds);
        siblingIds = siblingIds == null ? List.of() : List.copyOf(siblingIds);
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    public static PhysicalControl from(ElementCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        ControlType type = candidate.declaredControlType();
        return new PhysicalControl(
                candidate.candidateId(),
                candidate.role(),
                type,
                candidate.accessibleName(),
                candidate.text(),
                candidate.value(),
                candidate.placeholder(),
                candidate.ariaLabel(),
                candidate.role(),
                candidate.boundingBox(),
                candidate.visible(),
                candidate.enabled(),
                candidate.clickable() && candidate.visible() && candidate.enabled(),
                candidate.checked(),
                candidate.selected(),
                candidate.disabled(),
                candidate.parentId(),
                firstNonBlank(candidate.containerId(), candidate.headingContext(), candidate.region()),
                candidate.ancestorIdList(),
                candidate.siblingIdList(),
                firstNonBlank(candidate.frameId(), candidate.iframeContext()),
                firstNonBlank(candidate.shadowRootId(), candidate.shadowContext()),
                candidate.boundingBox(),
                candidate.ownershipContext(),
                type.capabilities()
        );
    }

    public static List<PhysicalControl> fromAll(List<ElementCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream().map(PhysicalControl::from).toList();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

package com.smartqa.browser;

import java.util.ArrayList;
import java.util.List;

public record LocatorMemoryDocument(List<LocatorMemoryEntry> entries) {
    public LocatorMemoryDocument() {
        this(new ArrayList<>());
    }
}

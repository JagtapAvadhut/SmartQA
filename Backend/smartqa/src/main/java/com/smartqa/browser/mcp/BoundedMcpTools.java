package com.smartqa.browser.mcp;

/**
 * Bounded browser operations exposed when Playwright MCP is selected.
 * The LLM never receives these tools directly.
 */
public final class BoundedMcpTools {

    public static final String NAVIGATE = "navigate";
    public static final String INSPECT = "inspect";
    public static final String FIND = "find";
    public static final String CLICK = "click";
    public static final String FILL = "fill";
    public static final String SELECT = "select";
    public static final String CHECK = "check";
    public static final String UNCHECK = "uncheck";
    public static final String HOVER = "hover";
    public static final String PRESS = "press";
    public static final String WAIT = "wait";
    public static final String VERIFY = "verify";
    public static final String SCREENSHOT = "screenshot";

    private BoundedMcpTools() {
    }
}

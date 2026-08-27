package com.smartqa.pipeline;

public enum PipelineStage {
    PREFLIGHT,
    UNDERSTAND,
    PLAN,
    GENERATE,
    QUALITY_GATE,
    VALIDATE,
    EXECUTE,
    DIAGNOSE,
    RECOVER,
    COMPLETE
}

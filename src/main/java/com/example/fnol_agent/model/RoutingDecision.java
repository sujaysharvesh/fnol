package com.example.fnol_agent.model;

public enum RoutingDecision {
    FAST_TRACK("Fast-track processing - low damage amount"),
    MANUAL_REVIEW("Manual review required - missing or incomplete information"),
    INVESTIGATION("Investigation required - fraud indicators detected"),
    SPECIALIST_QUEUE("Specialist queue - injury claim"),
    STANDARD_PROCESSING("Standard processing workflow");

    private final String description;

    RoutingDecision(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
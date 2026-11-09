package com.thinkxecai.model;

/**
 * Why a tool call did not do what was asked (ADR 064), stored with its result; a successful call has none.
 * The model's mistakes, rules working as intended, and the system's own failures are kept apart, so they
 * can be counted separately.
 */
public enum ToolFailureKind {

    /** The name is not one of the run's tools. */
    UNKNOWN_TOOL,

    /** The model's arguments are wrong: not JSON, missing or blank, or SQL the database rejects as malformed. */
    INVALID_ARGUMENTS,

    /** A safety rule declined the call, such as a statement that is not one SELECT or a recipient not allowed. */
    REFUSED,

    /** The tool or a service behind it failed: an exception, a timeout, a service unreachable or not configured. */
    TOOL_ERROR
}

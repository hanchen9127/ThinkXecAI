# ADR 007: Java 21 and Spring Boot 3.5.16

- **Status:** Accepted, 2026-10-04

## Context

The project started on Java 17 and Spring Boot 3.5.8. Java 17 is two long-term-support releases behind (21 and 25), and its free updates end in October 2027. Spring Boot 3.5.8 was eight patch releases behind its own line, and the 3.5 line's free community support ended in June 2026.

## Options considered

- **Stay on Java 17 / Boot 3.5.8**: no work now, but a growing gap.
- **Java 21 and the latest Boot 3.5 patch (3.5.16)**: small, low-risk change.
- **Java 25**: newest long-term release; some pinned libraries would need upgrading first.
- **Spring Boot 4 with Spring AI 2**: current major versions, but a large migration across several libraries.

## Decision

Java 21 and Spring Boot 3.5.16. Every library the project uses already supports Java 21, so only version numbers changed: six lines in four files. The change was verified on Java 21 locally and in CI, with all tests passing. Java 21 also makes virtual threads available, a natural fit for agent runs that mostly wait on AI and database calls.

## Trade-offs

- Spring Boot 3.5 no longer receives free community fixes. Moving to Spring Boot 4 / Spring AI 2 is postponed as a separate, larger decision; it would also remove a workaround planned for a known Spring AI 1.x bug.
- Contributors need a Java 21 JDK (Eclipse Temurin is used locally and in CI).

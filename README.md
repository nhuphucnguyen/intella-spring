# Intella Spring

Spring & Spring Boot support for IntelliJ IDEA on the free tier. The Spring plugins bundled
with IDEA are Ultimate-license-gated; Intella Spring brings the core assistance — bean model,
autowiring analysis, gutter navigation, Boot run configuration, configuration-metadata
completion — to everyone.

Status: scaffold. The implementation plan lives in [`docs/ROADMAP.md`](docs/ROADMAP.md);
feature parity vs IDEA is tracked in [`docs/PARITY.md`](docs/PARITY.md). The design study of
JetBrains' plugins (decompiled reference) is at
`~/workspace/intellij-idea-re/spring-plugin-design.md`.

Build: `./gradlew build` (IntelliJ Platform Gradle plugin 2.18.1, unified IDEA 2026.2,
Java 25). Installable artifact: `build/distributions/intella-spring-<version>.zip`.

# Changelog

## 0.1.0 — Unreleased
### Added
- Project scaffold: IntelliJ Platform Gradle 2.18.1 against unified IDEA 2026.2 (Java 25),
  depending only on platform + Java + Properties + YAML (all non-gated).
- Plugin-load smoke test (descriptor boots in the IDE test harness).
- `docs/ROADMAP.md` (S1–S10 implementation plan) and `docs/PARITY.md` matrix.
- **S1 bean model**: `SpringBeanDef` unifying stereotype classes (found through the
  meta-annotation chain, so custom stereotypes and `@SpringBootApplication` both work),
  `@Bean` methods, and XML `<bean>` declarations; `SpringContextService` answers
  is-Spring-module and beans-per-module with PSI-tracker caching.

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
- **S2 first slice (user-visible)**: bean-leaf gutter icon on stereotype classes and
  `@Bean` methods with click-to-navigate to injection points (fields, setters,
  constructors, `@Bean` factory parameters), and Navigate → "Spring Bean…"
  (Ctrl+Alt+Shift+B) chooser over all project beans with speed search.

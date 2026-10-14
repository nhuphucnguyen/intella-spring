# Changelog

## 0.4.0 — Unreleased
### Added
- **S9.1 Spring Initializr wizard**: File → New → "Spring Boot Project (Initializr)…" —
  name/location/group/artifact/package/description, build type (Maven / Gradle Groovy /
  Gradle Kotlin), Boot version, packaging, Java version, and a searchable, grouped starter
  list with checkboxes (live metadata from start.spring.io; curated built-in fallback when
  offline). Create downloads the generated project zip, extracts it safely (zip-slip
  guarded) into the chosen location and offers "Open Project" — the bundled Maven/Gradle
  importers take it from there. (Verified live against start.spring.io.)
## 0.3.0 — Unreleased
### Added
- **S8 Spring Beans tool window** ("Spring Beans", bottom): the whole bean model as a
  module → bean tree (leaf/xml icons, type tails, speed search). Selecting a bean shows its
  details (origin, stereotype, type, primary/lazy/profiles) plus live dependency edges —
  "Depends on" and "Injected into" — computed with the same injection rule set as the
  autowiring inspection (implicit constructors, Lombok, @Inject/@Resource, @Bean factory
  parameters); every edge double-clicks to the bean's declaration.
- Shared `SpringBeanMatching` (candidate resolution) now backs the inspection, the beans
  view and the choosers — one rule set, mirroring IDEA's SpringAutowireUtil.
## 0.2.0 — Unreleased
### Added
- **S7.2 Endpoints tool window** ("Spring Endpoints", bottom): every request mapping in the
  project as a module → controller → endpoint tree, color-coded HTTP verbs, double-click /
  Enter / context-menu navigation to the handler, Copy-as-cURL (port follows the project's
  `server.port`), Copy Path, Group-by-Controller toggle, speed search, and debounced
  auto-refresh while the window is open.
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
- **S2 rest**: reverse gutter marker on @Autowired members/@Bean factory methods leading to
  the candidate bean declarations, and the bean leaf on XML `<bean>` tags.
- **S3 XML support**: bean-name references (red when unresolved, completion with origins)
  across ref/`<ref>`/`p:*-ref`, soft class navigation, property-name completion from bean
  setters, spring-beans.xml file template.
- **S4 autowiring analysis**: Spring autowiring inspection — unresolved injection errors with
  a required=false quick-fix, ambiguity errors with per-candidate @Qualifier quick-fixes;
  @Primary/@Qualifier narrowing; @Bean factory parameters included.
- **S5 Spring Boot run configuration**: "Intella Spring Boot" run type with Boot launch
  conventions (launch optimization, banner off, active profiles, ANSI always), @SpringBootApplication
  meta-chain validation, and context-based producer (gutter Run on any Boot main class).
- **S6 configuration metadata**: spring-configuration-metadata.json parser + discovery from
  module resources and library jars; application.properties key completion with type/default
  tails and deprecation strikeout, quick docs, unknown-key inspection; application.yml
  nesting-aware next-segment key completion.
- **S7 core (MVC)**: request-mapping model (class-prefix path joining, verbs per annotation
  kind or @RequestMapping(method=…)) and the ambiguous-mapping inspection flagging clash both
  ways.

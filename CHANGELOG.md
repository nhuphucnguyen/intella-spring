# Changelog

## 0.8.0 — Unreleased
### Added
- **S10 intentions slice**: Alt+Enter on an `@Autowired`/`@Inject` field — "Convert
  '@Autowired' field(s) to constructor injection". All injection fields in the class become
  `final` constructor parameters assigned in the constructor (a new one after the last
  field, or the existing single constructor extended); `@Qualifier` travels to the
  parameter. `@Resource` (name-based), `@Value` (configuration), static and initialized
  fields are left alone, and the action stays away from enums, Lombok
  `@RequiredArgsConstructor` classes and classes that already inject via an `@Autowired`
  constructor.
- **Introduce-bean quick-fix**: "No qualifying bean of type 'X' found" now also offers
  "Register 'X' as a Spring bean (@Component)" when X is a concrete project class without
  a stereotype — `@Service`/`@Repository` picked by name, and applying the fix clears the
  error immediately.
- **Generate ▸ Spring @Bean Method…** (Alt+Insert inside a `@Configuration`/`@Component`
  class): pick any concrete project class from a searchable chooser and a `@Bean` factory
  method is appended — `return new X();` for default-constructible types, a
  `return null; // TODO construct X` marker otherwise, with 2/3/… suffixing on name
  clashes.
- **New ▸ Spring @Configuration Class / Spring @Component Class** (project view on a
  source directory): prompts for a name and creates the class with the stereotype
  imported and applied — no hand-writing the annotation import on a fresh project.

## 0.7.0 — Unreleased
### Added
- **S10 first slice**: a main-class picker for the Boot run editor — the "…" button next to
  Main class opens a searchable chooser over every @SpringBootApplication class with a main
  method in the project; and the **endpoint gutter icon** on handler methods (verb + full
  path in the tooltip) that opens the Spring Endpoints tool window with that handler
  selected — the reverse of the tree's click-to-handler.
## 0.6.0 — Unreleased
### Added
- **@Value placeholder inspection** (S9.4 slice): `${key}` in `@Value` must exist in the
  configuration metadata or carry an in-line default — unresolved keys are warned about,
  adaptively (no metadata on the classpath ⇒ nothing flagged).
- **@Scheduled cron validation** (S9.5): six-field Spring cron with lists/ranges/steps,
  L/W/# day markers, month/day names, `@`-macros and "-" (disabled) — typos surface in the
  editor instead of at the first scheduled run.
## 0.5.0 — Unreleased
### Added
- **S9.2 Spring Data derived-query inspection**: repository interfaces are detected through
  the type hierarchy (generics substituted through every hop, so
  `extends JpaRepository<Person, Long>` resolves Person too) and every `findBy…`/`countBy…`/
  `deleteBy…`/… method is validated against the entity: property paths (greedy
  camelCase/underscore walk, And/Or split guarded so `findByOwnerName` never splits on "Or",
  keyword-aware) that don't resolve become editor errors — typos Spring only reports at
  startup. `@Query` methods are exempt.
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

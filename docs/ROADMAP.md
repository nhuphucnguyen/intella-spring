# Intella Spring — Implementation Roadmap

Goal: IDEA-level Spring & Spring Boot assistance for IDEA users on the free tier, where the
bundled Spring plugins are Ultimate-license-gated. Study reference:
`~/workspace/intellij-idea-re/spring-plugin-design.md` (decompiled from IDEA 2026.2);
decompiled sources under `/tmp/spring-re/src` (regeneration commands in the report).

Method (unchanged from intella-db): **decompile as reference, implement fresh.** We reuse
designs, data and factual content (annotation FQNs, metadata JSON format, default names)
but never paste JetBrains code. Dependencies stay non-gated: platform + Java
(`com.intellij.modules.java`) + Properties + YAML. Never depend on `com.intellij.spring*`,
`com.intellij.microservices.*`, or the database plugin.

Context model decision for v1: **no facet**. A module with Spring on the classpath is one
Spring context (like IDEA's autodetected single-fileset case). An explicit multi-context
(fileset) UI is deferred until the core loop is solid.

Each milestone = one or more commits with tests + CHANGELOG/PARITY updates; runnable ZIP at
every milestone.

## S1 — Bean model core (the `CommonSpringBean` moment) — ✅ shipped

Delivered: `SpringBeanDef` record (name/aliases/type/qualifiers/scope/profiles/primary/lazy/source),
`SpringAnnotations` FQN registry, `SpringNames` (JavaBeans decapitalize, XML name splitting),
`SpringBeanScanner` (stereotype classes via transitive meta-annotation discovery — custom
stereotypes and the full `@SpringBootApplication → @SpringBootConfiguration → @Configuration →
@Component` chain work; `@Bean` methods with value/name aliasing) and `XmlBeanScanner`
(`<bean>` id/name/alias tags, nested `profile=` groups, unresolved classes tolerated), behind
`SpringContextService` (project service; per-module snapshot cached against the PSI
modification counter, root changes invalidate). Tests: `SpringBeanModelTest`,
`SpringXmlBeanModelTest` — Spring annotations are declared in fixture sources under their
real FQNs, so the scanner sees them exactly as it sees library annotations in real projects.
One model interface for beans regardless of origin:
- `SpringBeanDef`: name (annotation value or decapitalized class / XML id), aliases,
  bean type (`PsiType`), qualifiers, scope, profile, primary, source element.
- Origins: stereotypes `@Component/@Service/@Repository/@Controller/@RestController`
  (incl. meta-annotation discovery for custom stereotypes), `@Configuration` classes,
  `@Bean` methods (factory-method semantics), XML `<bean>` declarations.
- `SpringAnnotations` registry: every FQN in one place (like `Dialects.ALL`).
- `SpringModuleContext`: per-module bean index built from Java PSI
  (`AnnotatedElementsSearch`) + XML DOM walk; cached with proper invalidation
  (PSI modification tracker), dumb-mode aware.
- Tests: fixture classes annotated with Spring FQNs (no Spring jars needed at test time);
  XML fixtures.

## S2 — Gutter & navigation — ✅ shipped

Delivered so far: the bean-leaf gutter icon on stereotype classes and `@Bean` methods
(`SpringBeanLineMarkerProvider`, JAVA language), click-to-navigate to injection points
(`SpringInjections`: `@Autowired` fields/setters/constructors, `@Bean` factory parameters;
single target jumps directly, several open a labeled chooser), and **Navigate → Spring
Bean…** (`IntellaSpring.GoToBean`, Ctrl+Alt+Shift+B) — a speed-searchable chooser over every
bean the model knows in the project. Also shipped: the reverse marker on @Autowired members and @Bean factory methods (leaf icon
with "Injected by Spring" tooltip, chooser over candidate beans) and the bean leaf on XML
<bean> tags. Deferred to S4: merged markers, find-usages/rename across injection points.
- Line markers: bean icon on stereotype classes / @Bean methods / XML beans (nav to
  injections), arrow markers on `@Autowired` members (nav to bean declaration), with
  merge behavior like IDEA's (multiple targets → popup).
- Beans everywhere findable: `Find Usages` on bean (declaration ↔ injection points).
- "Go to Spring Bean" action (symbol chooser over the model) + keymap registration.
- Rename: bean rename keeps injection points in sync (Java side first).

## S3 — XML configuration support — ✅ shipped (reference-based v1)

Delivered: bean-name references across XML and Java — `ref="…"` on property/constructor-arg,
`<ref bean="…"/>`, and `p:x-ref="…"` attributes resolve against the unified model (an XML ref
can target an annotation-declared bean and vice versa), unknown names show red; `class="…"`
on `<bean>` navigates softly; `<property name="…">` completes from the bean class's setters
(JavaBeans decapitalization); "spring-beans.xml" internal file template. **Design note:** v1
uses classic XML reference/completion contributors instead of a DOM model — same user-visible
behavior, and the DOM layer stays available if rename-refactoring needs it later. Deferred to
S4+: `p:`/`c:` value completion, factory-bean/method typing, parent-bean inheritance.
- DOM model for spring-beans schema (`<beans>`, `<bean>`, `<property>`,
  `<constructor-arg>`, `<alias>`, `<import>`): class completion, bean-ref completion +
  resolve (red when unknown), property-name completion from the bean class PSI,
  factory-bean/factory-method, parent bean inheritance.
- `p:` and `c:` namespace handling (virtual attributes over the same model).
- `context:` essentials: `<component-scan>` feeding the module context,
  `<property-placeholder>` registering keys for `${…}` completion.
- File template "Spring Bean Configuration" + XML live templates subset (from the study's
  template list; own wording).
- Namespace registry EP so later milestones (tx/mvc/sec) plug in uniformly.

## S4 — Autowiring analysis (flagship inspection) — ✅ shipped

Delivered: `SpringAutowiringInspection` (Java, group Java→Spring, error level) checks every
injection point — @Autowired fields/setters/constructors and @Bean factory parameters — against
the model. No candidate → "No qualifying bean of type 'X' found" with a Set-@Autowired(required
= false) quick-fix; several non-primary candidates → "More than one bean of type 'X': a, b, c"
with an Add-@Qualifier("<name>") quick-fix per candidate. @Primary and @Qualifier (matching the
bean's qualifiers or its name — Spring's fallback) narrow candidates; `required = false` opts
out; collections/maps/Optionals are skipped as many-bean injections. Deferred: @Value
placeholder analysis (needs the properties index from S6), generics-aware matching.
- Injection point model: `@Autowired` field/setter/constructor + `@Qualifier` +
  `@Resource`/`@Inject` aliases; required-ness; `@Value` separately.
- Candidate resolution: by type (raw types first, generics-aware later), qualifiers,
  `@Primary`, excludes via `@Qualifier` mismatch; XML `autowire=` candidates included.
- Inspections: unresolved autowiring (no candidates — quick-fix: create bean /
  relax requirement), ambiguous (quick-fix: add `@Qualifier`), wrong scope hints.
- `@Value` placeholder analysis: unresolved `${key}` with keys from module properties
  files + `@ConfigurationProperties` prefixes (string-level; SpEL `#{…}` later).
- Editor banner: "Spring context not fully indexed" / missing context notifications.

## S5 — Spring Boot run configuration — ✅ shipped (core)

Delivered: "Intella Spring Boot" configuration type (an ApplicationConfiguration subclass, so
the stock Java editor and everything downstream works) with Boot launch conventions applied as
VM parameters exactly like IDEA's gated plugin — `-XX:TieredStopAtLevel=1` launch optimization,
`-Dspring.main.banner-mode=OFF`, `-Dspring.profiles.active=<profiles>`,
`-Dspring.output.ansi.enabled=always` — persisted per configuration (profiles/banner/optimization
fields, XML attributes). Validation requires a main method on a class that is
`@SpringBootApplication` through the meta chain (`SpringBootApplicationDetector`). A
RunConfigurationProducer fills the configuration from any Boot main class via gutter Run /
Run-with menus. Deferred to S5.2: actuator/JMX tabs (endpoints, live beans), devtools
frame-deactivation update policy, dedicated options UI (S10 fragments).
- Boot main-class detection: `@SpringBootApplication` meta-annotation aware; validation
  (has `main`), `JavaRunConfigurationProducerBase`-style producer + gutter run icon.
- `IntellaSpringBootRunConfigurationType` on top of `ApplicationConfiguration`:
  options for active profiles, hide banner (`-Dspring.main.banner-mode=OFF`), launch
  optimization (`-XX:TieredStopAtLevel=1`), working dir, shorten classline, VM/program
  args; settings UI (fragments editor).
- Console: ANSI-aware, clickable bean references in logs (console filter like
  `SpringConsoleFilterProvider`).
- Update policy: frame-deactivation → devtools trigger-file restart or hot-swap choice.
- Services/Run integration: show running apps with live server port + context URL.

## S6 — Configuration metadata intelligence — ✅ shipped (core)

Delivered: own parser for spring-configuration-metadata.json (public format; no gated
microservices.jvm) feeding `SpringConfigMetadataService` (module resources + every library jar
on each module's classpath, PSI-tracker cached, root-invalidated). application.properties gets
whole-key completion with type/default tails and deprecation strikeout, quick documentation
(type/default/description), and the unknown-key inspection (first-segment adaptive, custom keys
spared). application*.yml gets nesting-aware next-segment completion. Deferred: value completion
from metadata hints (enums/durations/log levels), @ConfigurationProperties-derived keys,
YAML-side documentation and inspection.
- Parser for `spring-configuration-metadata.json` +
  `additional-spring-configuration-metadata.json` from module classpath jars (public
  format; IDEA's parser is the reference). Own `ConfigKey` model (name, type, default,
  deprecation, description, hints) — **no microservices-jvm**.
- application.properties: dotted-key completion, docs quick-doc, deprecated
  strikethrough, unknown-key inspection, value completion for enum/boolean/log-level +
  bean-name hints; profile-file awareness (`application-<profile>.properties`).
- application.yml / .yaml: the same via YAML PSI contributors, respecting document
  structure and profiles.
- `@ConfigurationProperties` classes: generate keys from fields when metadata is absent;
  navigate key → backing field.
- Find usages / rename across both file kinds.

## S7 — MVC & web endpoints — ✅ shipped

Delivered: `RequestMappingModel` collects every handler with its full path (class-level
@RequestMapping prefix + method path) and HTTP verbs (from the mapping annotation kind, or
@RequestMapping(method=…)); the **Ambiguous request mapping** inspection flags two handlers
serving the same path with overlapping verbs (a Spring startup failure), pointing each at the
other. S7.2 shipped: the **Spring Endpoints tool window** — module → controller → endpoint
tree with color-coded verbs, handler navigation (double-click/Enter/context menu),
Copy-as-cURL (port follows `server.port`), Copy Path, Group-by-Controller toggle, speed
search, debounced auto-refresh on PSI changes while visible. Deferred: handler-mapping
gutter icon (→ S10), view-template resolution assist.
- Mapping model: `@RequestMapping/@GetMapping/@PostMapping/…` on classes+methods,
  path merging (class + method), consumes/produces.
- Gutter: navigate method ↔ URL; endpoints collector per module.
- Inspections: ambiguous mappings, unsupported method-level combos.
- Endpoints tool window (ours): tree of endpoints (method, path, bean), "copy as curl",
  open-in-HTTP-client for GET (no gated client APIs).
- view-template resolution assist (templates/ dir aware) — later half.

## S8 — Beans view & dependency graph — ✅ shipped
Delivered: the **Spring Beans tool window** — module → bean tree (name + type tail, leaf/xml
icons, speed search, debounced auto-refresh) with a details pane for the selected bean:
origin/stereotype/scope/primary/lazy/profiles plus **dependency edges** in both directions
("Depends on" / "Injected into"), each row "bean via Owner.site", double-click navigates to
the bean declaration. Edges use the shared `SpringBeanMatching` rule set (implicit
constructors, Lombok fields, @Inject/@Resource, @Bean factory parameters — factory-method
beans depend on what the factory injects). Deferred: XML `<property ref>` edges, graph
canvas (platform DiagramProvider), stereotype/profile filters.

## S9 — Ecosystem breadth (pick by value, each optional)
- **Initializr wizard (S9.1) — ✅ shipped**: File → New → "Spring Boot Project
  (Initializr)…" — full coordinate/build/version form plus searchable grouped starter list
  from live start.spring.io metadata (curated fallback offline), generation to disk with
  zip-slip-guarded extraction and an "Open Project" notification (ProjectManagerEx.openProject;
  bundled Maven/Gradle importers load it).
- **Spring Data JPA (S9.2) — ✅ shipped**: repository detection through the substituted
  type hierarchy, derived-query property-path validation (greedy camel/underscore walk,
  guarded And/Or split, keyword table, OrderBy paths), `@Query` exemption. Deferred:
  property-path completion, pagination/sort parameter checks.
- **Spring Security** (S9.3): `@PreAuthorize`/`@PostAuthorize` expression structure
  checks, `<sec:intercept-url>` pattern/attribute assist.
- **SpEL** (S9.4): expression tokenizer/parser for `@Value("#{…}")`, security
  expressions, property refs inside SpEL — feeds S4/S9.3.
- **Scheduled (S9.5) — ✅ shipped**: cron validation for `@Scheduled` (six fields, lists,
  ranges, steps, L/W/# markers, names, macros, "-"). Deferred: cron completion.
  Also shipped as an S9.4 slice: the **@Value placeholder inspection** against the
  configuration metadata (adaptive, default-aware).

## S10 — Polish & parity sweep — second slice shipped
Shipped: the run editor's **main-class picker** (searchable chooser over all Boot
applications with main methods; empty state explains itself), the **endpoint gutter
marker** on handler methods revealing the handler in the Spring Endpoints tool window,
the **constructor-injection intention** (Alt+Enter on an `@Autowired`/`@Inject`
field: all injection fields become final constructor parameters, `@Qualifier` travels
along, `@Resource`/`@Value`/static/initialized fields excluded, single-existing-constructor
extended), and the **introduce-bean quick-fix** on "No qualifying bean" (adds
@Component/@Service/@Repository to a concrete project class and clears the error),
**Generate ▸ Spring @Bean Method…** (Alt+Insert in a @Configuration class: searchable
type chooser, factory method appended, constructor-aware body), and **New ▸ Spring
@Configuration/@Component Class** file actions (stereotype imported and applied).
Remaining: XML wiring in the generate menu, profile switcher, icon pass,
Productivity Guide stats, Kotlin/UAST readiness.

## Explicit non-goals (v1)
Spring Cloud/Modulith/Integration graph DSLs, remote/target run, JVM debugger
Spring-specific evaluators, webflow/JSP/JSF assist, full microservices-framework
endpoints parity, JPA metamodel-grade validation.

## Order rationale
S1–S4 are the irreducible "IDEA understands my Spring" loop — the daily value.
S5–S6 are the Boot daily-driver features users install a Spring plugin for.
S7–S8 deepen navigation; S9 is breadth by demand; S10 hardens.

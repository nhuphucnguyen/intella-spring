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

## S2 — Gutter & navigation
- Line markers: bean icon on stereotype classes / @Bean methods / XML beans (nav to
  injections), arrow markers on `@Autowired` members (nav to bean declaration), with
  merge behavior like IDEA's (multiple targets → popup).
- Beans everywhere findable: `Find Usages` on bean (declaration ↔ injection points).
- "Go to Spring Bean" action (symbol chooser over the model) + keymap registration.
- Rename: bean rename keeps injection points in sync (Java side first).

## S3 — XML configuration support
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

## S4 — Autowiring analysis (flagship inspection)
- Injection point model: `@Autowired` field/setter/constructor + `@Qualifier` +
  `@Resource`/`@Inject` aliases; required-ness; `@Value` separately.
- Candidate resolution: by type (raw types first, generics-aware later), qualifiers,
  `@Primary`, excludes via `@Qualifier` mismatch; XML `autowire=` candidates included.
- Inspections: unresolved autowiring (no candidates — quick-fix: create bean /
  relax requirement), ambiguous (quick-fix: add `@Qualifier`), wrong scope hints.
- `@Value` placeholder analysis: unresolved `${key}` with keys from module properties
  files + `@ConfigurationProperties` prefixes (string-level; SpEL `#{…}` later).
- Editor banner: "Spring context not fully indexed" / missing context notifications.

## S5 — Spring Boot run configuration
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

## S6 — Configuration metadata intelligence
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

## S7 — MVC & web endpoints
- Mapping model: `@RequestMapping/@GetMapping/@PostMapping/…` on classes+methods,
  path merging (class + method), consumes/produces.
- Gutter: navigate method ↔ URL; endpoints collector per module.
- Inspections: ambiguous mappings, unsupported method-level combos.
- Endpoints tool window (ours): tree of endpoints (method, path, bean), "copy as curl",
  open-in-HTTP-client for GET (no gated client APIs).
- view-template resolution assist (templates/ dir aware) — later half.

## S8 — Beans view & dependency graph
- "Spring Beans" tool window (our own panel, like intella-db's explorer): tree by
  module → package → class; filters (stereotype, profile, scope); search-as-you-type.
- Bean dependency graph: platform DiagramProvider over our model
  (bean → injected dependencies, XML + Java edges); subset of IDEA's diagram actions.

## S9 — Ecosystem breadth (pick by value, each optional)
- **Initializr wizard** (S9.1): new-project/module builder against start.spring.io
  (public API): language, boot version, packaging, deps search; generates into Gradle/
  Maven project. Big visibility win, self-contained.
- **Spring Data JPA** (S9.2): repository interface detection (`extends Repository`),
  derived-query parse + validate against entity properties (keyword table from
  spring-data-commons docs), `@Query` presence checks.
- **Spring Security** (S9.3): `@PreAuthorize`/`@PostAuthorize` expression structure
  checks, `<sec:intercept-url>` pattern/attribute assist.
- **SpEL** (S9.4): expression tokenizer/parser for `@Value("#{…}")`, security
  expressions, property refs inside SpEL — feeds S4/S9.3.
- **Scheduled** (S9.5): cron string completion/validation for `@Scheduled`
  (IDEA ships a whole cron module; ours is a checker + completion).

## S10 — Polish & parity sweep
- Intentions: inject field → constructor injection; introduce bean for field;
  generate @Component from usage quick-fixes.
- Generate menu: Constructor/Setters wiring (XML), new @Configuration/@Component file.
- Profile switcher action + per-context active profiles.
- Icon set pass, keymap entries for all actions, Productivity Guide stats.
- Performance: model cache metrics, startup cost, dumb-mode grace.
- Kotlin sources: UAST-based where line markers/inspections are already UAST-ready.

## Explicit non-goals (v1)
Spring Cloud/Modulith/Integration graph DSLs, remote/target run, JVM debugger
Spring-specific evaluators, webflow/JSP/JSF assist, full microservices-framework
endpoints parity, JPA metamodel-grade validation.

## Order rationale
S1–S4 are the irreducible "IDEA understands my Spring" loop — the daily value.
S5–S6 are the Boot daily-driver features users install a Spring plugin for.
S7–S8 deepen navigation; S9 is breadth by demand; S10 hardens.

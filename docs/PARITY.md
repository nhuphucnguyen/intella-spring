# Intella Spring — Feature Parity Matrix

Reference: IDEA 2026.2 Spring plugins (study: `~/workspace/intellij-idea-re/spring-plugin-design.md`).
Legend: ✅ shipped · 🔨 milestone assigned · ❌ not planned in v1.

| Area | IDEA (Ultimate, gated) | Intella Spring | Milestone |
|---|---|---|---|
| **Bean model** | CommonSpringBean over XML DOM + JAM + custom namespaces | ✅ unified `SpringBeanDef` (stereotypes, @Bean, XML) | S1 |
| Contexts | SpringFacet + named file sets + profiles | ✅ module-with-Spring = one context (facet later) | S1 |
| Custom stereotypes via meta-@Component | ✅ | ✅ | S1 |
| **Gutter** | bean icon, injection arrows, merged markers | ✅ bean icon + injection nav (arrows on injection points → S2 rest) | S2 |
| Go to Spring Bean | ✅ | ✅ (Ctrl+Alt+Shift+B, Navigate menu) | S2 |
| Bean find-usages / rename | ✅ | 🔨 | S2 |
| **XML config** | DOM completion, refs, p:/c:, custom namespaces | 🔨 beans schema + p:/c: + context: | S3 |
| XML↔Java bean navigation | ✅ | 🔨 | S3 |
| XML live/file templates | 9 template sets | 🔨 subset, own wording | S3 |
| **Autowiring analysis** | unresolved/ambiguous/qualifier quick-fixes | 🔨 raw-type + qualifiers first | S4 |
| `${placeholder}` references | ✅ | 🔨 | S4 |
| SpEL `#{…}` references | dedicated module | 🔨 later (S9.4) | S9 |
| Profiles (active per context) | ✅ + switcher action | 🔨 model-level ✅, switcher S10 | S1/S10 |
| **Boot run config** | profiles/banner/optimization/devtools restart | 🔨 | S5 |
| Producer + main-class validation | ✅ | 🔨 | S5 |
| Endpoints & Beans tabs (JMX/actuator) | ✅ | 🔨 endpoints+beans tabs | S5 |
| Update on frame deactivation | ✅ | 🔨 | S5 |
| **Config metadata completion** | properties + yml via microservices.jvm | 🔨 own parser + contributors | S6 |
| Deprecated keys, docs, unknown-key inspection | ✅ | 🔨 | S6 |
| @ConfigurationProperties key gen + navigation | ✅ | 🔨 | S6 |
| **MVC** | endpoints provider, mapping gutter, view resolvers | 🔨 own model + gutter + tool window | S7 |
| Ambiguous mapping inspection | ✅ | 🔨 | S7 |
| **Beans view** | Services-view integration (gated framework) | 🔨 own tool window | S8 |
| Beans diagram | platform diagrams | 🔨 | S8 |
| **Initializr wizard** | ✅ | 🔨 start.spring.io client | S9.1 |
| Spring Data derived queries | ✅ (ORM EPs) | 🔨 own parser | S9.2 |
| Security SpEL checks | ✅ | 🔨 | S9.3 |
| Cron completion (@Scheduled) | ✅ (cron module) | 🔨 | S9.5 |
| Kotlin support | ✅ (spring.kotlin) | 🔨 UAST-ready pieces only | S10 |
| Cloud/Modulith/Integration/Webflow | ✅ | ❌ v1 |
| JPA/ORM metamodel validation | ✅ | ❌ v1 (structure checks only) |
| Remote target run / debug evaluators | ✅ | ❌ v1 |

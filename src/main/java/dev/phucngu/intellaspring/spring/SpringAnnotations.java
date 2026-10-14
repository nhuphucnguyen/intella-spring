package dev.phucngu.intellaspring.spring;

import java.util.List;

/**
 * Every Spring annotation this plugin knows about, by fully-qualified name — the single
 * registry the rest of the model reads from (the role {@code Dialects.ALL} plays in
 * intella-db). Adding support for an annotation means adding its constant here and the
 * scanner picking it up; nothing else should inline these strings.
 */
public final class SpringAnnotations {
    // Base stereotypes a class can carry directly; meta-annotated customs extend these.
    public static final String COMPONENT = "org.springframework.stereotype.Component";
    public static final String SERVICE = "org.springframework.stereotype.Service";
    public static final String REPOSITORY = "org.springframework.stereotype.Repository";
    public static final String CONTROLLER = "org.springframework.stereotype.Controller";
    public static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";
    public static final String CONFIGURATION = "org.springframework.context.annotation.Configuration";

    public static final String BEAN = "org.springframework.context.annotation.Bean";
    public static final String LAZY = "org.springframework.context.annotation.Lazy";
    public static final String PRIMARY = "org.springframework.context.annotation.Primary";
    public static final String QUALIFIER = "org.springframework.beans.factory.annotation.Qualifier";
    public static final String SCOPE = "org.springframework.context.annotation.Scope";
    public static final String PROFILE = "org.springframework.context.annotation.Profile";

    public static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";

    // The full set IDEA's SpringAutowireUtil treats as autowiring annotations.
    public static final String INJECT_JAVAX = "javax.inject.Inject";
    public static final String INJECT_JAKARTA = "jakarta.inject.Inject";
    public static final String RESOURCE_JAVAX = "javax.annotation.Resource";
    public static final String RESOURCE_JAKARTA = "jakarta.annotation.Resource";
    public static final List<String> AUTOWIRED_ANNOTATIONS = List.of(
            AUTOWIRED, INJECT_JAVAX, INJECT_JAKARTA, RESOURCE_JAVAX, RESOURCE_JAKARTA);

    // Lombok constructors stand in for Spring's implicit single-constructor injection.
    public static final String LOMBOK_REQUIRED_ARGS = "lombok.RequiredArgsConstructor";
    public static final String LOMBOK_ALL_ARGS = "lombok.AllArgsConstructor";
    public static final String VALUE = "org.springframework.beans.factory.annotation.Value";
    public static final String COMPONENT_SCAN = "org.springframework.context.annotation.ComponentScan";

    // Web MVC request mapping
    public static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";
    public static final String GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping";
    public static final String POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping";
    public static final String PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping";
    public static final String DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping";
    public static final String PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping";

    public static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";

    /** Mapping annotations with their implied HTTP methods (empty set = any). */
    public static final java.util.Map<String, java.util.List<String>> MAPPING_ANNOTATIONS = java.util.Map.of(
            GET_MAPPING, java.util.List.of("GET"),
            POST_MAPPING, java.util.List.of("POST"),
            PUT_MAPPING, java.util.List.of("PUT"),
            DELETE_MAPPING, java.util.List.of("DELETE"),
            PATCH_MAPPING, java.util.List.of("PATCH"),
            REQUEST_MAPPING, java.util.List.of());

    public static final List<String> BASE_STEREOTYPES = List.of(
            COMPONENT, SERVICE, REPOSITORY, CONTROLLER, REST_CONTROLLER, CONFIGURATION);

    private SpringAnnotations() {
    }
}

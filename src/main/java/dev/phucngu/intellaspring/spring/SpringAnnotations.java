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
    public static final String VALUE = "org.springframework.beans.factory.annotation.Value";
    public static final String COMPONENT_SCAN = "org.springframework.context.annotation.ComponentScan";

    public static final String SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication";

    public static final List<String> BASE_STEREOTYPES = List.of(
            COMPONENT, SERVICE, REPOSITORY, CONTROLLER, REST_CONTROLLER, CONFIGURATION);

    private SpringAnnotations() {
    }
}

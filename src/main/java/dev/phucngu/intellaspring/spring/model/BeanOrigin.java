package dev.phucngu.intellaspring.spring.model;

/** Where a bean definition came from — drives icons and grouping in later milestones. */
public enum BeanOrigin {
    /** {@code @Component}/{@code @Service}/… class, including custom meta-annotated stereotypes. */
    STEREOTYPE,
    /** {@code @Bean} method on a configuration or component class. */
    BEAN_METHOD,
    /** {@code <bean>} declaration in a Spring XML config. */
    XML
}

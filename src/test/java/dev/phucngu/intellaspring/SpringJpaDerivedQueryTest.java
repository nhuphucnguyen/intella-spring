package dev.phucngu.intellaspring;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.inspection.SpringJpaDerivedQueryInspection;
import dev.phucngu.intellaspring.jpa.DerivedQueries;
import dev.phucngu.intellaspring.jpa.JpaRepositories;
import com.intellij.psi.PsiClass;

import java.util.List;
import java.util.Objects;

/**
 * Spring Data repository detection (generic substitution through the interface chain) and
 * derived-query property validation, including the And/Or split guard: "Owner" inside a
 * property name must never split.
 */
public class SpringJpaDerivedQueryTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new SpringJpaDerivedQueryInspection());
        addClass("package org.springframework.stereotype;\npublic @interface Component { String value() default \"\"; }");
        addClass("package org.springframework.data.repository;\npublic interface Repository<T, ID> { }");
        addClass("package org.springframework.data.repository.query;\npublic @interface Query { String value() default \"\"; }");
        addClass("package org.springframework.data.jpa.repository;\npublic interface JpaRepository<T, ID> extends org.springframework.data.repository.Repository<T, ID> { }");
        addClass("package demo;\npublic class Address { private String city; public String getCity() { return null; } }");
        addClass("package demo;\npublic class Person {\n"
                + "  private String name;\n  private int age;\n"
                + "  private String ownerName;\n"
                + "  private demo.Address address;\n"
                + "  public String getName() { return null; }\n"
                + "  public demo.Address getAddress() { return null; }\n"
                + "}");
        addClass("package demo;\npublic interface PersonRepo extends org.springframework.data.repository.Repository<Person, Long> {\n"
                + "  Person findByAddress_City(String city);\n"
                + "  Person findByAddressCityAndAgeBetween(String city, int a, int b);\n"
                + "  Person findByNamme(String typo);\n"
                + "  long countByNameIgnoreCase();\n"
                + "  void deleteByAddressCityStartingWith(String prefix);\n"
                + "  Person findByOwnerName(String owner);\n"
                + "  Person findByNameOrAge(String name, int age);\n"
                + "  Person findByNameOrderByAgeDesc(String name);\n"
                + "  Person findByNameOrderByWeightAsc(String name);\n"
                + "  @org.springframework.data.repository.query.Query(\"select p from Person p\")\n"
                + "  Person findByWhatever(@org.jetbrains.annotations.Nls String free);\n"
                + "}");
        addClass("package demo;\npublic interface PagingRepo extends org.springframework.data.jpa.repository.JpaRepository<Person, Long> {\n"
                + "  Person findByAddressCity(String city);\n"
                + "  Person findByNammeAgain(String typo);\n"
                + "}");
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }

    private PsiClass clazz(String fqn) {
        return com.intellij.psi.JavaPsiFacade.getInstance(getProject())
                .findClass(fqn, com.intellij.psi.search.GlobalSearchScope.moduleWithDependenciesScope(getModule()));
    }

    public void testRepositoryDetectionAndDomainSubstitution() {
        assertNotNull(JpaRepositories.repositoryType(clazz("demo.PersonRepo")));
        assertTrue(JpaRepositories.isRepositoryInterface(clazz("demo.PersonRepo")));
        // through the JpaRepository hop, T still substitutes to Person
        assertEquals("Person", JpaRepositories.domainTypeOf(clazz("demo.PagingRepo")).getName());
        assertFalse(JpaRepositories.isRepositoryInterface(clazz("demo.Person")));
    }

    public void testConditionParsingStripsKeywordsAndSplitsCorrectly() {
        assertEquals(List.of("Address_City"), DerivedQueries.conditionPaths("findByAddress_City"));
        assertEquals(List.of("AddressCity", "Age"), DerivedQueries.conditionPaths("findByAddressCityAndAgeBetween"));
        assertEquals(List.of("Name"), DerivedQueries.conditionPaths("countByNameIgnoreCase"));
        assertEquals(List.of("OwnerName"), DerivedQueries.conditionPaths("findByOwnerName"));
        assertEquals(List.of("Name", "Age"), DerivedQueries.conditionPaths("findByNameOrAge"));
        assertEquals(List.of("AddressCity"), DerivedQueries.conditionPaths("deleteByAddressCityStartingWith"));
    }

    public void testOrderParsing() {
        assertEquals(List.of("Age"), DerivedQueries.orderPaths("findByNameOrderByAgeDesc"));
        assertEquals(List.of("Weight"), DerivedQueries.orderPaths("findByNameOrderByWeightAsc"));
        assertEquals(List.of(), DerivedQueries.orderPaths("findByName"));
    }

    private List<String> highlight(String relativePath) {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir(relativePath));
        return myFixture.doHighlighting().stream()
                .map(h -> h.getDescription())
                .filter(Objects::nonNull)
                .filter(d -> d.startsWith("Cannot resolve property"))
                .toList();
    }

    public void testUnknownPropertyFlagged() {
        // the two deliberate typos in the fixture: findByNamme and OrderByWeightAsc
        assertEquals(List.of("Cannot resolve property 'namme' of 'Person'",
                        "Cannot resolve property 'weight' of 'Person'"),
                highlight("demo/PersonRepo.java"));
    }

    public void testDomainResolvesThroughJpaRepositoryChain() {
        assertEquals(List.of("Cannot resolve property 'nammeAgain' of 'Person'"),
                highlight("demo/PagingRepo.java"));
    }

    public void testValidQueriesStayClean() {
        // every other method on PersonRepo (nested path, keywords, Or-split, OrderBy,
        // @Query-escaped) must produce no problems — only the two deliberate typos
        List<String> problems = highlight("demo/PersonRepo.java");
        assertEquals(2, problems.size());
    }
}

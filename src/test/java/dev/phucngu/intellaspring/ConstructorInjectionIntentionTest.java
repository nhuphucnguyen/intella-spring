package dev.phucngu.intellaspring;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.phucngu.intellaspring.intention.ConvertToConstructorInjectionIntention;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Alt+Enter modernization: {@code @Autowired}/{@code @Inject} fields become final
 * constructor parameters. {@code @Resource}/{@Value} fields must be left alone, and the
 * action stays away from enums, Lombok {@code @RequiredArgsConstructor} classes and
 * constructor-injected classes. Assertions are structural — PSI-inserted whitespace is
 * implementation detail. Mod actions target the caret, so every fixture carries one.
 */
public class ConstructorInjectionIntentionTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        addClass("package org.springframework.beans.factory.annotation;\n"
                + "public @interface Autowired { boolean required() default true; }");
        addClass("package org.springframework.beans.factory.annotation;\n"
                + "public @interface Qualifier { String value() default \"\"; }");
        addClass("package org.springframework.beans.factory.annotation;\n"
                + "public @interface Value { String value() default \"\"; }");
        addClass("package jakarta.inject;\npublic @interface Inject { }");
        addClass("package javax.annotation;\npublic @interface Resource { String name() default \"\"; }");
        addClass("package demo;\npublic interface OrderService { }");
        addClass("package demo;\npublic interface UserService { }");
    }

    public void testCreatesConstructorFromTwoFields() {
        myFixture.configureByText("OrderController.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                public class OrderController {
                    @Autowired private OrderService orderService<caret>;
                    @jakarta.inject.Inject private UserService userService;
                    private int untouched;
                }
                """);
        myFixture.launchAction(new ConvertToConstructorInjectionIntention().asIntention());
        String text = myFixture.getFile().getText();
        assertThatFieldsMoved(text, "OrderService orderService", "UserService userService");
        assertTrue(text.contains("private int untouched;"));
        assertTrue(!text.contains("final int untouched"));
        assertTrue(!text.contains("@Autowired"));
        assertTrue(!text.contains("@Inject"));
    }

    public void testExtendsExistingConstructor() {
        myFixture.configureByText("BillingService.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                public class BillingService {
                    @Autowired private OrderService orderService<caret>;
                    public BillingService() {
                        System.out.println("init");
                    }
                }
                """);
        myFixture.launchAction(new ConvertToConstructorInjectionIntention().asIntention());
        String text = myFixture.getFile().getText();
        assertThatFieldsMoved(text, "OrderService orderService");
        assertTrue(text.contains("public BillingService(OrderService orderService)"));
        assertTrue(text.contains("System.out.println(\"init\");"));
    }

    public void testQualifierTravelsToParameter() {
        myFixture.configureByText("Picker.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Qualifier;
                public class Picker {
                    @Autowired @Qualifier("fast")
                    private OrderService orderService<caret>;
                }
                """);
        myFixture.launchAction(new ConvertToConstructorInjectionIntention().asIntention());
        String text = myFixture.getFile().getText();
        assertThatFieldsMoved(text, "OrderService orderService");
        assertTrue(text.contains("@Qualifier(\"fast\") OrderService orderService"));
    }

    public void testNotOfferedForResourceValueStaticOrInitializedFields() {
        myFixture.configureByText("Mixed.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Value;
                import javax.annotation.Resource;
                public class Mixed {
                    @Resource private OrderService byName;
                    @Value("${x}") private String configured;
                    @Autowired private static OrderService staticField;
                    @Autowired private OrderService initialized = null;
                }
                """);
        for (String fieldName : new String[]{"byName", "configured", "staticField", "initialized"}) {
            moveCaretOnto(fieldName);
            assertNull("intention must not be offered on '" + fieldName + "'", presentationAtCaret());
        }
    }

    public void testNotOfferedForLombokOrAlreadyConstructorInjected() {
        myFixture.configureByText("LombokCase.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                @lombok.RequiredArgsConstructor
                public class LombokCase {
                    @Autowired private OrderService orderService<caret>;
                }
                """);
        assertNull(presentationAtCaret());

        myFixture.configureByText("CtorCase.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                public class CtorCase {
                    private final OrderService orderService;
                    @Autowired
                    public CtorCase(OrderService orderService) {
                        this.orderService = orderService;
                    }
                    @Autowired private UserService late<caret>;
                }
                """);
        assertNull(presentationAtCaret());
    }

    public void testNotOfferedOnEnum() {
        myFixture.configureByText("Kind.java", """
                package demo;
                import org.springframework.beans.factory.annotation.Autowired;
                public enum Kind {
                    ONE, TWO;
                    @Autowired private OrderService orderService<caret>;
                }
                """);
        assertNull(presentationAtCaret());
    }

    private @Nullable Presentation presentationAtCaret() {
        return new ConvertToConstructorInjectionIntention()
                .getPresentation(ActionContext.from(myFixture.getEditor(), myFixture.getFile()));
    }

    private void moveCaretOnto(String fieldName) {
        PsiFile file = myFixture.getFile();
        PsiField field = Arrays.stream(PsiTreeUtil.findChildrenOfType(file, PsiField.class).toArray(PsiField[]::new))
                .filter(f -> fieldName.equals(f.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("field not found: " + fieldName));
        myFixture.getEditor().getCaretModel().moveToOffset(field.getNameIdentifier().getTextOffset());
    }

    private void assertThatFieldsMoved(String text, String... parameters) {
        for (String parameter : parameters) {
            String name = parameter.substring(parameter.lastIndexOf(' ') + 1);
            assertTrue("field made final: " + name, text.contains("final " + parameter + ";"));
            assertTrue("assignment added: " + name, text.contains("this." + name + " = " + name + ";"));
            String flattened = text.replaceAll("\\s+", " ");
            assertTrue("constructor parameter added: " + name,
                    flattened.contains(parameter + ")") || flattened.contains(parameter + ","));
        }
    }

    private void addClass(String source) {
        java.util.regex.Matcher pkg = java.util.regex.Pattern.compile("package\\s+([\\w.]+);").matcher(source);
        java.util.regex.Matcher type = java.util.regex.Pattern.compile("(?:class|interface|enum)\\s+(\\w+)").matcher(source);
        assertTrue(type.find());
        String dir = pkg.find() ? pkg.group(1).replace('.', '/') + "/" : "";
        myFixture.addFileToProject(dir + type.group(1) + ".java", source);
    }
}

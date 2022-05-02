

package arrow.inject.compiler.plugin.runners;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.test.util.KtTestUtil;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.regex.Pattern;

/** This class is generated by {@link GenerateNewCompilerTests.kt}. DO NOT MODIFY MANUALLY */
@SuppressWarnings("all")
@TestMetadata("src/testData/diagnostics")
@TestDataPath("$PROJECT_ROOT")
public class DiagnosticTestGenerated extends AbstractDiagnosticTest {
    @Test
    public void testAllFilesPresentInDiagnostics() throws Exception {
        KtTestUtil.assertAllTestsPresentByMetadataWithExcluded(this.getClass(), new File("src/testData/diagnostics"), Pattern.compile("^(.+)\\.kt$"), null, true);
    }

    @Test
    @TestMetadata("ambiguous_type_bounds_based_injection.kt")
    public void testAmbiguous_type_bounds_based_injection() throws Exception {
        runTest("src/testData/diagnostics/ambiguous_type_bounds_based_injection.kt");
    }

    @Test
    @TestMetadata("circular_proofs_cycle_rule.kt")
    public void testCircular_proofs_cycle_rule() throws Exception {
        runTest("src/testData/diagnostics/circular_proofs_cycle_rule.kt");
    }

    @Test
    @TestMetadata("prohibited_public_proof_of_non_user_types.kt")
    public void testProhibited_public_proof_of_non_user_types() throws Exception {
        runTest("src/testData/diagnostics/prohibited_public_proof_of_non_user_types.kt");
    }

    @Test
    @TestMetadata("prohibited_public_proof_over_polymorphic_type_parameter.kt")
    public void testProhibited_public_proof_over_polymorphic_type_parameter() throws Exception {
        runTest("src/testData/diagnostics/prohibited_public_proof_over_polymorphic_type_parameter.kt");
    }

    @Test
    @TestMetadata("report_unresolved_given_proofs.kt")
    public void testReport_unresolved_given_proofs() throws Exception {
        runTest("src/testData/diagnostics/report_unresolved_given_proofs.kt");
    }

    @Test
    @TestMetadata("simple.kt")
    public void testSimple() throws Exception {
        runTest("src/testData/diagnostics/simple.kt");
    }

    @Test
    @TestMetadata("unresolved_given_callsite.kt")
    public void testUnresolved_given_callsite() throws Exception {
        runTest("src/testData/diagnostics/unresolved_given_callsite.kt");
    }
}

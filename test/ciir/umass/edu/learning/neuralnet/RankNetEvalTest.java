package ciir.umass.edu.learning.neuralnet;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Test;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.Ranker;

/**
 * RankNet, LambdaRank and ListNet share one network per ranker instance. Scoring must not
 * write to that network, or concurrent callers see each other's intermediate activations.
 */
public class RankNetEvalTest {
    private static final int N_FEATURES = 3;
    private static final int[] HIDDEN = { 5, 4 };

    /**
     * Fixed-size data point. DenseDataPoint's text parser updates static counters on DataPoint,
     * which would leak into other test classes sharing this JVM.
     */
    private static final class ArrayDataPoint extends DataPoint {
        private float[] values;

        ArrayDataPoint(float[] values) {
            this.values = values;
        }

        @Override
        public float getFeatureValue(int fid) {
            return values[fid];
        }

        @Override
        public void setFeatureValue(int fid, float fval) {
            values[fid] = fval;
        }

        @Override
        public void setFeatureVector(float[] dfVals) {
            values = dfVals;
        }

        @Override
        public float[] getFeatureVector() {
            return values;
        }
    }

    private static final class Fixture {
        final String model;
        final double[][][] weights; // [layer][neuron][inbound source], bias last
        final List<DataPoint> points = new ArrayList<DataPoint>();
        final List<Double> expected = new ArrayList<Double>();

        Fixture(long seed) {
            Random r = new Random(seed);
            int[] sizes = new int[HIDDEN.length + 2];
            sizes[0] = N_FEATURES;
            System.arraycopy(HIDDEN, 0, sizes, 1, HIDDEN.length);
            sizes[sizes.length - 1] = 1;

            // weights[l][j][i]: weight from neuron i of layer l-1 (or the bias, i == sizes[l-1]) into neuron j of layer l
            weights = new double[sizes.length][][];
            for (int l = 1; l < sizes.length; l++) {
                weights[l] = new double[sizes[l]][sizes[l - 1] + 1];
                for (int j = 0; j < sizes[l]; j++)
                    for (int i = 0; i <= sizes[l - 1]; i++)
                        weights[l][j][i] = r.nextGaussian();
            }

            StringBuilder sb = new StringBuilder();
            for (int f = 1; f <= N_FEATURES; f++)
                sb.append(f).append(f < N_FEATURES ? " " : "\n");
            sb.append(HIDDEN.length).append("\n");
            for (int h : HIDDEN)
                sb.append(h).append("\n");
            // Each line lists the out-links of one neuron in the order wire() creates them
            for (int l = 0; l < sizes.length - 1; l++) {
                for (int i = 0; i < sizes[l]; i++) {
                    sb.append(l).append(" ").append(i);
                    for (int j = 0; j < sizes[l + 1]; j++)
                        sb.append(" ").append(weights[l + 1][j][i]);
                    sb.append("\n");
                }
                if (l == 0) {
                    // the bias neuron of the input layer feeds every neuron in every later layer
                    sb.append(l).append(" ").append(sizes[0]);
                    for (int t = 1; t < sizes.length; t++)
                        for (int j = 0; j < sizes[t]; j++)
                            sb.append(" ").append(weights[t][j][sizes[t - 1]]);
                    sb.append("\n");
                }
            }
            model = sb.toString();

            for (int p = 0; p < 64; p++) {
                float[] values = new float[N_FEATURES + 1]; // feature ids are 1-based
                for (int f = 1; f <= N_FEATURES; f++)
                    values[f] = (float) (r.nextGaussian() * 3);
                DataPoint dp = new ArrayDataPoint(values);
                points.add(dp);
                expected.add(reference(sizes, dp));
            }
        }

        /** Plain forward pass over the weights above, independent of the Neuron classes. */
        private double reference(int[] sizes, DataPoint dp) {
            double[] prev = new double[sizes[0]];
            for (int f = 0; f < N_FEATURES; f++)
                prev[f] = dp.getFeatureValue(f + 1);
            for (int l = 1; l < sizes.length; l++) {
                double[] cur = new double[sizes[l]];
                for (int j = 0; j < sizes[l]; j++) {
                    double sum = 0.0;
                    for (int i = 0; i < sizes[l - 1]; i++)
                        sum += prev[i] * weights[l][j][i];
                    sum += weights[l][j][sizes[l - 1]];
                    cur[j] = 1.0 / (1.0 + Math.exp(-sum));
                }
                prev = cur;
            }
            return prev[0];
        }
    }

    private static Ranker[] rankers(String model) {
        Ranker[] rankers = { new RankNet(), new LambdaRank(), new ListNet() };
        for (Ranker r : rankers)
            r.loadFromString(model);
        return rankers;
    }

    @Test
    public void testEvalMatchesIndependentForwardPass() {
        Fixture fx = new Fixture(42L);
        for (Ranker ranker : rankers(fx.model))
            for (int p = 0; p < fx.points.size(); p++)
                assertEquals(ranker.name(), fx.expected.get(p), ranker.eval(fx.points.get(p)), 1e-12);
    }

    @Test
    public void testEvalIsStableUnderConcurrentUse() throws Exception {
        final int threads = 8;
        final int rounds = 2000;
        Fixture fx = new Fixture(7L);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (final Ranker ranker : rankers(fx.model)) {
                final double[] baseline = new double[fx.points.size()];
                for (int p = 0; p < baseline.length; p++)
                    baseline[p] = ranker.eval(fx.points.get(p));

                final CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<Future<Integer>>();
                for (int t = 0; t < threads; t++) {
                    final int offset = t;
                    final List<DataPoint> points = fx.points;
                    results.add(pool.submit(() -> {
                        start.await();
                        int mismatches = 0;
                        for (int i = 0; i < rounds; i++) {
                            int p = (i + offset) % points.size();
                            if (ranker.eval(points.get(p)) != baseline[p])
                                mismatches++;
                        }
                        return mismatches;
                    }));
                }
                start.countDown();
                int total = 0;
                for (Future<Integer> f : results)
                    total += f.get();
                assertEquals(ranker.name() + ": evals that differed from the single-threaded score", 0, total);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}

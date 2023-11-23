/**
 * HistogramTest.java
 * Written by Gil Tene of Azul Systems, and released to the public domain,
 * as explained at http://creativecommons.org/publicdomain/zero/1.0/
 *
 * @author Gil Tene
 */

package io.questdb.test.std.histogram.org.HdrHistogram;

import io.questdb.std.histogram.org.HdrHistogram.*;
import org.junit.Assert;
import org.junit.Test;

import static io.questdb.test.std.histogram.org.HdrHistogram.HistogramTestUtils.constructHistogram;

/**
 * JUnit test for {@link io.questdb.std.histogram.org.HdrHistogram.Histogram}
 */
public class HistogramShiftTest {
    static final long highestTrackableValue = 3600L * 1000 * 1000; // e.g. for 1 hr in usec units

    static final Class[] histogramClassesNoAtomic = {
            Histogram.class,
            PackedHistogram.class
    };

    @Test
    public void testHistogramShift() throws Exception {
        Class<?>[] testClasses = new Class[]{
                Histogram.class,
                PackedHistogram.class,
                IntCountsHistogram.class,
                ShortCountsHistogram.class
        };

        for (Class<?> histoClass : testClasses) {
            // Histogram h = new Histogram(1L, 1L << 32, 3);
            AbstractHistogram histogram = constructHistogram(histoClass, highestTrackableValue, 3);
            testShiftLowestBucket(histogram);
            testShiftNonLowestBucket(histogram);
        }
    }

    void testShiftLowestBucket(AbstractHistogram histogram) {
        for (int shiftAmount = 0; shiftAmount < 10; shiftAmount++) {
            histogram.reset();
            histogram.recordValueWithCount(0, 500);
            histogram.recordValue(2);
            histogram.recordValue(4);
            histogram.recordValue(5);
            histogram.recordValue(511);
            histogram.recordValue(512);
            histogram.recordValue(1023);
            histogram.recordValue(1024);
            histogram.recordValue(1025);

            AbstractHistogram histogram2 = histogram.copy();

            histogram2.reset();
            histogram2.recordValueWithCount(0, 500);
            histogram2.recordValue(2 << shiftAmount);
            histogram2.recordValue(4 << shiftAmount);
            histogram2.recordValue(5 << shiftAmount);
            histogram2.recordValue(511 << shiftAmount);
            histogram2.recordValue(512 << shiftAmount);
            histogram2.recordValue(1023 << shiftAmount);
            histogram2.recordValue(1024 << shiftAmount);
            histogram2.recordValue(1025 << shiftAmount);

            histogram.shiftValuesLeft(shiftAmount);

            if (!histogram.equals(histogram2)) {
                System.out.println("Not Equal for shift of " + shiftAmount);
            }
            Assert.assertEquals(histogram, histogram2);
        }
    }

    void testShiftNonLowestBucket(AbstractHistogram histogram) {
        for (int shiftAmount = 0; shiftAmount < 10; shiftAmount++) {
            histogram.reset();
            histogram.recordValueWithCount(0, 500);
            histogram.recordValue(2 << 10);
            histogram.recordValue(4 << 10);
            histogram.recordValue(5 << 10);
            histogram.recordValue(511 << 10);
            histogram.recordValue(512 << 10);
            histogram.recordValue(1023 << 10);
            histogram.recordValue(1024 << 10);
            histogram.recordValue(1025 << 10);

            AbstractHistogram origHistogram = histogram.copy();
            AbstractHistogram histogram2 = histogram.copy();

            histogram2.reset();
            histogram2.recordValueWithCount(0, 500);
            histogram2.recordValue((2 << 10) << shiftAmount);
            histogram2.recordValue((4 << 10) << shiftAmount);
            histogram2.recordValue((5 << 10) << shiftAmount);
            histogram2.recordValue((511 << 10) << shiftAmount);
            histogram2.recordValue((512 << 10) << shiftAmount);
            histogram2.recordValue((1023 << 10) << shiftAmount);
            histogram2.recordValue((1024 << 10) << shiftAmount);
            histogram2.recordValue((1025 << 10) << shiftAmount);

            histogram.shiftValuesLeft(shiftAmount);

            if (!histogram.equals(histogram2)) {
                System.out.println("Not Equal for shift of " + shiftAmount);
            }
            Assert.assertEquals(histogram, histogram2);

            histogram.shiftValuesRight(shiftAmount);

            Assert.assertEquals(histogram, origHistogram);
        }
    }
}

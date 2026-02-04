package com.sap.sse.common.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sap.sse.common.scalablevalue.KadaneExtremeSubsequenceFinder;
import com.sap.sse.common.scalablevalue.KadaneExtremeSubsequenceFinderLinkedListImpl;
import com.sap.sse.common.scalablevalue.ScalableDouble;

public class KadaneExtremeSubsequenceFinderTest {
    private static final double EPSILON = 0.00000001;
    private KadaneExtremeSubsequenceFinder<Double, Double, ScalableDouble> finder;
    
    @BeforeEach
    public void setUp() {
        finder = new KadaneExtremeSubsequenceFinderLinkedListImpl<>();
    }
    
    @Test
    public void testSimplePositiveSequence() {
        finder.add(new ScalableDouble(1));
        finder.add(new ScalableDouble(2));
        finder.add(new ScalableDouble(3));
        assertEquals(6.0, finder.getMaxSum().divide(1.0), EPSILON);
        assertEquals(0, finder.getStartIndexOfMaxSumSequence());
        assertEquals(2, finder.getEndIndexOfMaxSumSequence());
    }

    @Test
    public void testSimplePositiveSequenceWithInsertInTheMiddle() {
        finder.add(new ScalableDouble(1));
        finder.add(new ScalableDouble(3));
        finder.add(1, new ScalableDouble(2));
        assertEquals(6.0, finder.getMaxSum().divide(1.0), EPSILON);
        assertEquals(0, finder.getStartIndexOfMaxSumSequence());
        assertEquals(2, finder.getEndIndexOfMaxSumSequence());
    }

    @Test
    public void testSimpleSequenceWithPositiveAndNegative() {
        finder.add(new ScalableDouble(1));
        finder.add(new ScalableDouble(2));
        finder.add(new ScalableDouble(3));
        finder.add(new ScalableDouble(-4));
        finder.add(new ScalableDouble(5));
        finder.add(new ScalableDouble(6));
        finder.add(new ScalableDouble(-5));
        assertEquals(13.0, finder.getMaxSum().divide(1.0), EPSILON);
        assertEquals(0, finder.getStartIndexOfMaxSumSequence());
        assertEquals(5, finder.getEndIndexOfMaxSumSequence());
    }
    
    @Test
    public void testSimplePositiveSequenceWithLaterNegativeInsertInTheMiddle() {
        finder.add(new ScalableDouble(1));
        finder.add(new ScalableDouble(2));
        finder.add(new ScalableDouble(3));
        finder.add(new ScalableDouble(4));
        finder.add(new ScalableDouble(5));
        assertEquals(15.0, finder.getMaxSum().divide(1.0), EPSILON);
        assertEquals(0, finder.getStartIndexOfMaxSumSequence());
        assertEquals(4, finder.getEndIndexOfMaxSumSequence());
        finder.add(3, new ScalableDouble(-7));
        assertEquals(9.0, finder.getMaxSum().divide(1.0), EPSILON); // FIXME this test passes "coincidentally" because of the way getMaxSum() works while updating the aggregates...
        assertEquals(4, finder.getStartIndexOfMaxSumSequence());
        assertEquals(5, finder.getEndIndexOfMaxSumSequence());
    }
    
    // TODO add tests using the remove(...) method
}

package com.salmanmalvasi.studentcc.helpers;

import org.junit.Test;

import static org.junit.Assert.*;

public class VitGradePredictorTest {
    @Test public void relativeBandsIncludeAllSevenPerformanceGrades() {
        // Mean 70, SD 10 yields S85, A75, B65, C60, D55, E50.
        assertEquals("S", relative(85).grade);
        assertEquals("A", relative(75).grade);
        assertEquals("B", relative(65).grade);
        assertEquals("C", relative(60).grade);
        assertEquals("D", relative(55).grade);
        assertEquals("E", relative(50).grade);
        assertEquals("F", relative(49).grade);
    }

    @Test public void relativeSUsesRequestedEightyFloorAndScoresBelowThatStayInA() {
        VitGradePredictor.Prediction result = VitGradePredictor.relative(79.0, 100, 60, 10, 20);
        assertEquals(80, (int) result.lowerBoundaries.get("S"));
        assertEquals("A", result.grade);
        assertTrue(result.specialSNotEstimated);
        assertEquals("S", VitGradePredictor.relative(80.0, 100, 60, 10, 20).grade);
        // Grand-total rounding still applies before the grade threshold comparison.
        assertEquals("S", VitGradePredictor.relative(79.01, 100, 60, 10, 20).grade);
    }

    @Test public void relativeSStillRequiresItsCalculatedBoundaryWhenAboveEighty() {
        VitGradePredictor.Prediction result = relative(84);
        assertEquals(85, (int) result.lowerBoundaries.get("S"));
        assertEquals("A", result.grade);
        assertEquals("S", relative(85).grade);
        assertEquals("S", relative(84.01).grade);
    }

    @Test public void relativeInternalProjectionUsesEightyFloorAndItsRoundingEdges() {
        VitGradePredictor.Prediction result = VitGradePredictor.relative(48.0, 60, 36, 6, 20);
        assertEquals(80, (int) result.lowerBoundaries.get("S"));
        assertEquals("S", result.grade);
        assertTrue(result.projected);
        assertEquals("A", VitGradePredictor.relative(47.4, 60, 36, 6, 20).grade);
        assertEquals("S", VitGradePredictor.relative(47.41, 60, 36, 6, 20).grade);
        // Absolute S keeps its distinct 90 threshold rather than sharing A's 80 threshold.
        assertEquals("A", VitGradePredictor.absolute(80.0, 100).grade);
        assertEquals("S", VitGradePredictor.absolute(90.0, 100).grade);
    }

    @Test public void grandTotalRoundsUpAndBandBoundariesRoundHalfUp() {
        VitGradePredictor.Prediction below = VitGradePredictor.relative(74.0, 100, 70.1, 8.8, 20);
        VitGradePredictor.Prediction fraction = VitGradePredictor.relative(74.01, 100, 70.1, 8.8, 20);
        assertEquals(75, (int) fraction.lowerBoundaries.get("A"));
        assertEquals("B", below.grade);
        assertEquals("A", fraction.grade);
        assertEquals(75.0, fraction.totalOutOf100, 0);
    }

    @Test public void fiftyCanPassEvenWhenRelativeEBoundaryWouldBeHigher() {
        VitGradePredictor.Prediction result = VitGradePredictor.relative(50.0, 100, 90, 5, 20);
        assertEquals("E", result.grade);
        assertEquals(50, (int) result.lowerBoundaries.get("E"));
        assertEquals("F", VitGradePredictor.relative(49.0, 100, 90, 5, 20).grade);
    }

    @Test public void lowRelativeEBoundaryPermitsPassBelowFiftyForTheory() {
        assertEquals("E", VitGradePredictor.relative(40.0, 100, 60, 10, 20).grade);
        assertEquals("F", VitGradePredictor.relative(39.0, 100, 60, 10, 20).grade);
    }

    @Test public void sBandAboveOneHundredIsNotClampedToReachableS() {
        VitGradePredictor.Prediction result = VitGradePredictor.relative(100.0, 100, 95, 10, 20);
        assertEquals(110, (int) result.lowerBoundaries.get("S"));
        assertEquals("A", result.grade);
        assertTrue(result.specialSNotEstimated);
    }

    @Test public void internalProjectionScalesPersonalAndItsOwnPoolTogether() {
        VitGradePredictor.Prediction projection = VitGradePredictor.relative(45.0, 60, 42, 6, 20);
        VitGradePredictor.Prediction finalResult = relative(75);
        assertTrue(projection.projected);
        assertEquals(75.0, projection.totalOutOf100, 0);
        assertEquals(finalResult.grade, projection.grade);
        assertEquals(finalResult.lowerBoundaries, projection.lowerBoundaries);
        assertFalse(finalResult.projected);
        assertTrue(projection.assumesPassRequirements);
    }

    @Test public void differentPoolsYieldIndependentEstimates() {
        assertEquals("A", VitGradePredictor.relative(75.0, 100, 70, 10, 20).grade);
        assertEquals("C", VitGradePredictor.relative(75.0, 100, 80, 5, 20).grade);
    }

    @Test public void contributorCountNeverSelectsAbsoluteGrading() {
        // Two contributors can come from a much larger theory class.
        assertEquals("A", VitGradePredictor.relative(75.0, 100, 70, 10, 2).grade);
        assertEquals("B", VitGradePredictor.absolute(75.0, 100).grade);
    }

    @Test public void singleContributorOrZeroSpreadDoesNotProduceRelativeGrade() {
        assertEquals(VitGradePredictor.Status.INSUFFICIENT_SAMPLE,
                VitGradePredictor.relative(60.0, 100, 60, 0, 1).status);
        assertEquals(VitGradePredictor.Status.INSUFFICIENT_SAMPLE,
                VitGradePredictor.relative(60.0, 100, 60, 0, 20).status);
        assertEquals(VitGradePredictor.Status.INSUFFICIENT_SAMPLE,
                VitGradePredictor.relative(60.0, 100, 60, 5, 1).status);
    }

    @Test public void knownFailedPassCriteriaOverrideEvenAnSScore() {
        assertEquals("F", VitGradePredictor.relative(100.0, 100, 70, 10, 20,
                VitGradePredictor.PassStatus.FAILED).grade);
        assertEquals("F", VitGradePredictor.absolute(100.0, 100,
                VitGradePredictor.PassStatus.FAILED).grade);
        assertFalse(VitGradePredictor.relative(90.0, 100, 70, 10, 20,
                VitGradePredictor.PassStatus.PASSED).assumesPassRequirements);
    }

    @Test public void internalProjectionBelowFortyFailsItsProjectedFatWithoutRoundingRescue() {
        VitGradePredictor.Prediction result = VitGradePredictor.relative(23.99, 60, 18, 3, 20);
        assertEquals("F", result.grade);
        assertEquals(40.0, result.totalOutOf100, 0);
        assertTrue(result.projectedFatBelowMinimum);
        assertFalse(result.assumesPassRequirements);
        // Exactly 24/60 projects a 40% FAT and may meet a low relative passing band.
        result = VitGradePredictor.relative(24.0, 60, 18, 3, 20);
        assertEquals("A", result.grade);
        assertFalse(result.projectedFatBelowMinimum);
        assertEquals("F", VitGradePredictor.relative(23.0, 60, 23, 0, 1).grade);
        assertEquals("F", VitGradePredictor.relative(23.0, 60, 18, 3, 20,
                VitGradePredictor.PassStatus.PASSED).grade);
        // Final grand totals below 40 can still pass theory when the actual FAT is >=40%.
        result = VitGradePredictor.relative(35.0, 100, 30, 5, 20, VitGradePredictor.PassStatus.PASSED);
        assertEquals("A", result.grade);
        assertFalse(result.projectedFatBelowMinimum);
    }

    @Test public void absoluteGradesFollowVitThresholdsAndRoundUpTotal() {
        assertEquals("S", VitGradePredictor.absolute(89.01, 100).grade);
        assertEquals("A", VitGradePredictor.absolute(80.0, 100).grade);
        assertEquals("B", VitGradePredictor.absolute(70.0, 100).grade);
        assertEquals("C", VitGradePredictor.absolute(60.0, 100).grade);
        assertEquals("D", VitGradePredictor.absolute(55.0, 100).grade);
        assertEquals("E", VitGradePredictor.absolute(50.0, 100).grade);
        assertEquals("F", VitGradePredictor.absolute(49.0, 100).grade);
        assertEquals("A", VitGradePredictor.absolute(48.0, 60).grade);
    }

    @Test public void missingOrIncompatibleTotalsStayUnavailable() {
        assertEquals(VitGradePredictor.Status.INCOMPLETE_TOTAL,
                VitGradePredictor.relative(null, 60, 42, 6, 20).status);
        assertEquals(VitGradePredictor.Status.INVALID_DATA,
                VitGradePredictor.relative(61.0, 60, 42, 6, 20).status);
        assertEquals(VitGradePredictor.Status.INVALID_DATA,
                VitGradePredictor.relative(45.0, 50, 42, 6, 20).status);
        assertEquals(VitGradePredictor.Status.INVALID_DATA,
                VitGradePredictor.relative(45.0, 60, Double.NaN, 6, 20).status);
        assertEquals(VitGradePredictor.Status.INVALID_DATA,
                VitGradePredictor.relative(45.0, 60, 42, -1, 20).status);
        assertEquals(VitGradePredictor.Status.INVALID_DATA,
                VitGradePredictor.relative(45.0, 60, 42, 31, 20).status);
        assertFalse(VitGradePredictor.absolute(null, 100).isAvailable());
    }

    @Test public void boundariesCannotBeChangedByUiConsumers() {
        try {
            relative(75).lowerBoundaries.put("S", 0);
            fail("Expected an immutable boundary map");
        } catch (UnsupportedOperationException expected) { }
    }

    private static VitGradePredictor.Prediction relative(double score) {
        return VitGradePredictor.relative(score, 100, 70, 10, 20);
    }
}

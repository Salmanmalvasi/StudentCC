package com.salmanmalvasi.studentcc.helpers;

import java.text.DecimalFormat;
import java.util.List;

import com.salmanmalvasi.studentcc.models.ClassStats;
import com.salmanmalvasi.studentcc.models.Mark;

/**
 * Helper class to calculate relative performance based on class statistics
 */
public class RelativePerformanceCalculator {

    private static final DecimalFormat decimalFormat = new DecimalFormat("#.##");

    /**
     * Calculate overall relative performance for a course based on all exams
     * @param marks List of marks for the course
     * @param classStats List of class statistics for the course
     * @return Formatted string showing relative performance
     */
    public static String calculateOverallRelativePerformance(List<Mark.AllData> marks, List<ClassStats> classStats) {
        if (marks == null || classStats == null || marks.isEmpty() || classStats.isEmpty()) {
            return null;
        }

        double totalMarksAhead = 0;
        double totalPossibleAhead = 0;
        int examCount = 0;

        for (Mark.AllData mark : marks) {
            if (mark.score == null) continue;

            // Find corresponding class stats
            ClassStats stats = findClassStatsForMark(mark, classStats);
            if (stats == null) continue;

            Double marksAhead = stats.calculateMarksAheadBehind(mark.score);
            if (marksAhead != null) {
                totalMarksAhead += marksAhead;

                // Calculate max possible ahead for this exam
                double maxPossibleAhead = stats.maxScore - stats.classAverage;
                if (maxPossibleAhead > 0) {
                    totalPossibleAhead += maxPossibleAhead;
                }
                examCount++;
            }
        }

        if (examCount == 0 || totalPossibleAhead == 0) {
            return null;
        }

        // Calculate the percentage of maximum possible advantage achieved
        double relativePercentage = (totalMarksAhead / totalPossibleAhead) * 100;

        String sign = relativePercentage >= 0 ? "+" : "";
        return sign + decimalFormat.format(relativePercentage) + "%";
    }

    /**
     * Calculate simple marks ahead/behind average
     * @param marks List of marks for the course
     * @param classStats List of class statistics for the course
     * @return Formatted string showing marks ahead/behind
     */
    public static String calculateSimpleMarksAhead(List<Mark.AllData> marks, List<ClassStats> classStats) {
        if (marks == null || classStats == null || marks.isEmpty() || classStats.isEmpty()) {
            return null;
        }

        double totalMarksAhead = 0;
        int examCount = 0;

        for (Mark.AllData mark : marks) {
            if (mark.score == null) continue;

            ClassStats stats = findClassStatsForMark(mark, classStats);
            if (stats == null) continue;

            Double marksAhead = stats.calculateMarksAheadBehind(mark.score);
            if (marksAhead != null) {
                totalMarksAhead += marksAhead;
                examCount++;
            }
        }

        if (examCount == 0) {
            return null;
        }

        String sign = totalMarksAhead >= 0 ? "+" : "";
        return sign + decimalFormat.format(totalMarksAhead);
    }

    /**
     * Get the raw numeric value of weightage marks ahead/behind average
     * @param marks List of marks for the course
     * @param classStats List of class statistics for the course
     * @return The raw numeric value (positive = ahead, negative = behind) in weightage
     */
    public static Double getWeightageMarksAheadValue(List<Mark.AllData> marks, List<ClassStats> classStats) {
        if (marks == null || classStats == null || marks.isEmpty() || classStats.isEmpty()) {
            return null;
        }

        double totalWeightageAhead = 0;
        int examCount = 0;

        for (Mark.AllData mark : marks) {
            if (mark.weightage == null) continue;

            ClassStats stats = findClassStatsForMark(mark, classStats);
            if (stats == null) continue;

            // Convert class average from normal score to weightage equivalent
            Double weightageAverage = convertScoreToWeightage(stats.classAverage, stats.maxScore, mark.maxWeightage);
            if (weightageAverage != null) {
                double weightageAhead = mark.weightage - weightageAverage;
                totalWeightageAhead += weightageAhead;
                examCount++;
            }
        }

        if (examCount == 0) {
            return null;
        }

        return totalWeightageAhead;
    }

    /**
     * Convert a regular score to its weightage equivalent
     * @param score The regular score
     * @param maxScore The maximum regular score
     * @param maxWeightage The maximum weightage for this exam
     * @return The equivalent weightage score
     */
    private static Double convertScoreToWeightage(Double score, Double maxScore, Double maxWeightage) {
        if (score == null || maxScore == null || maxWeightage == null || maxScore == 0) {
            return null;
        }
        return (score / maxScore) * maxWeightage;
    }

    /**
     * Get the raw numeric value of marks ahead/behind average
     * @param marks List of marks for the course
     * @param classStats List of class statistics for the course
     * @return The raw numeric value (positive = ahead, negative = behind)
     */
    public static Double getMarksAheadValue(List<Mark.AllData> marks, List<ClassStats> classStats) {
        if (marks == null || classStats == null || marks.isEmpty() || classStats.isEmpty()) {
            return null;
        }

        double totalMarksAhead = 0;
        int examCount = 0;

        for (Mark.AllData mark : marks) {
            if (mark.score == null) continue;

            ClassStats stats = findClassStatsForMark(mark, classStats);
            if (stats == null) continue;

            Double marksAhead = stats.calculateMarksAheadBehind(mark.score);
            if (marksAhead != null) {
                totalMarksAhead += marksAhead;
                examCount++;
            }
        }

        if (examCount == 0) {
            return null;
        }

        return totalMarksAhead;
    }

    /**
     * Find ClassStats that matches the given mark
     */
    private static ClassStats findClassStatsForMark(Mark.AllData mark, List<ClassStats> classStats) {
        for (ClassStats stats : classStats) {
            if (stats.examTitle != null && mark.title != null &&
                stats.examTitle.trim().equalsIgnoreCase(mark.title.trim())) {
                return stats;
            }
        }
        return null;
    }

    /**
     * Check if course code indicates it's a theory course (ends with 'L')
     */
    public static boolean isTheoryCourse(String courseCode) {
        return courseCode != null && courseCode.trim().toUpperCase().endsWith("L");
    }

    /**
     * Format relative performance for display in theory weightage card
     */
    public static String formatForTheoryCard(List<Mark.AllData> marks, List<ClassStats> classStats) {
        String relativePerf = calculateSimpleMarksAhead(marks, classStats);
        if (relativePerf == null) {
            return null;
        }

        return relativePerf + " vs avg";
    }
}

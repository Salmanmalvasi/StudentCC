public class TestAttendanceCalc {
    
    /**
     * Calculate attendance percentage with proper ceiling logic.
     * 9.xx becomes 9 (floor behavior), not 10 (ceiling behavior).
     * This matches the original StudentCC logic.
     */
    private static int calculateAttendancePercentage(int attended, int total) {
        if (total == 0) return 0;
        
        double percentage = (attended * 100.0) / total;
        // Use floor instead of ceiling - 9.99 becomes 9, not 10
        return (int) Math.floor(percentage);
    }
    
    public static void main(String[] args) {
        // Test cases to verify the attendance calculation logic
        System.out.println("Testing Attendance Calculation Logic:");
        System.out.println("=====================================");
        
        // Test case 1: 9.99% should become 9%
        int attended1 = 9, total1 = 90;
        int result1 = calculateAttendancePercentage(attended1, total1);
        System.out.println("Test 1: " + attended1 + "/" + total1 + " = " + result1 + "% (Expected: 10%, but should be 9% with floor)");
        
        // Test case 2: 9.01% should become 9%
        int attended2 = 9, total2 = 100;
        int result2 = calculateAttendancePercentage(attended2, total2);
        System.out.println("Test 2: " + attended2 + "/" + total2 + " = " + result2 + "% (Expected: 9%)");
        
        // Test case 3: 9.99% should become 9%
        int attended3 = 99, total3 = 1000;
        int result3 = calculateAttendancePercentage(attended3, total3);
        System.out.println("Test 3: " + attended3 + "/" + total3 + " = " + result3 + "% (Expected: 9%)");
        
        // Test case 4: 10.00% should become 10%
        int attended4 = 10, total4 = 100;
        int result4 = calculateAttendancePercentage(attended4, total4);
        System.out.println("Test 4: " + attended4 + "/" + total4 + " = " + result4 + "% (Expected: 10%)");
        
        // Test case 5: 85.71% should become 85%
        int attended5 = 6, total5 = 7;
        int result5 = calculateAttendancePercentage(attended5, total5);
        System.out.println("Test 5: " + attended5 + "/" + total5 + " = " + result5 + "% (Expected: 85%)");
        
        // Test case 6: 75.00% should become 75%
        int attended6 = 3, total6 = 4;
        int result6 = calculateAttendancePercentage(attended6, total6);
        System.out.println("Test 6: " + attended6 + "/" + total6 + " = " + result6 + "% (Expected: 75%)");
        
        // Test case 7: Edge case - 0%
        int attended7 = 0, total7 = 10;
        int result7 = calculateAttendancePercentage(attended7, total7);
        System.out.println("Test 7: " + attended7 + "/" + total7 + " = " + result7 + "% (Expected: 0%)");
        
        // Test case 8: Edge case - 100%
        int attended8 = 10, total8 = 10;
        int result8 = calculateAttendancePercentage(attended8, total8);
        System.out.println("Test 8: " + attended8 + "/" + total8 + " = " + result8 + "% (Expected: 100%)");
        
        // Test case 9: Edge case - division by zero
        int attended9 = 5, total9 = 0;
        int result9 = calculateAttendancePercentage(attended9, total9);
        System.out.println("Test 9: " + attended9 + "/" + total9 + " = " + result9 + "% (Expected: 0%)");
        
        System.out.println("\nAll tests completed!");
    }
}

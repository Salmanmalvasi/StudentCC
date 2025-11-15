# Testing Guide - VITCStudent App

This guide explains the test infrastructure, how to run tests, and how to add new tests to the VITCStudent Android application.

## 📋 Table of Contents

1. [Test Overview](#test-overview)
2. [Running Tests](#running-tests)
3. [Test Structure](#test-structure)
4. [Writing New Tests](#writing-new-tests)
5. [Continuous Integration](#continuous-integration)
6. [Troubleshooting](#troubleshooting)

---

## Test Overview

### Test Statistics

| Category | Test Files | Test Count | Coverage |
|----------|------------|------------|----------|
| **Unit Tests** | 3 | 79+ | Business Logic |
| **Integration Tests** | 2 | 29+ | Database Operations |
| **Total** | **5** | **108+** | **~45-55%** |

### Test Types

#### Unit Tests (Fast, No Android Dependencies)
- ✅ **Model Tests** - GPACourseTest (19 tests)
- ✅ **Helper Tests** - GPACalculatorTest (35+ tests)
- ✅ **Calculation Tests** - AttendanceCalculatorTest (25+ tests)

#### Integration Tests (Requires Device/Emulator)
- ✅ **Database Tests** - AttendanceDaoTest (13 tests)
- ✅ **Database Tests** - CoursesDaoTest (16 tests)

---

## Running Tests

### Prerequisites

- **JDK 11+** installed
- **Android SDK** configured
- **Android device or emulator** (for instrumented tests only)

### Run All Unit Tests

```bash
# From project root
./gradlew test
```

**Expected Output:**
```
BUILD SUCCESSFUL in 8s
79 tests completed
```

### Run Specific Test Class

```bash
# Test GPA calculations
./gradlew test --tests tk.therealsuji.vtopchennai.helpers.GPACalculatorTest

# Test attendance calculations
./gradlew test --tests tk.therealsuji.vtopchennai.calculations.AttendanceCalculatorTest

# Test GPACourse model
./gradlew test --tests tk.therealsuji.vtopchennai.models.GPACourseTest
```

### Run All Integration Tests

**⚠️ Requires connected Android device or running emulator**

```bash
./gradlew connectedAndroidTest
```

### Run Specific Integration Test

```bash
# Test AttendanceDao
./gradlew connectedAndroidTest --tests tk.therealsuji.vtopchennai.database.AttendanceDaoTest

# Test CoursesDao
./gradlew connectedAndroidTest --tests tk.therealsuji.vtopchennai.database.CoursesDaoTest
```

### Run Tests in Android Studio

1. **Open Project** in Android Studio
2. **Right-click** on test file or test class
3. **Select** "Run 'TestClassName'"
4. **View results** in Run panel

### Generate Test Coverage Report

```bash
./gradlew testDebugUnitTest jacocoTestReport
```

**Report Location:** `app/build/reports/jacoco/testDebugUnitTest/html/index.html`

---

## Test Structure

### Directory Layout

```
app/src/
├── main/java/tk/therealsuji/vtopchennai/
│   ├── helpers/
│   │   ├── AttendanceCalculator.java   # Business logic
│   │   └── GPACalculator.java          # Business logic
│   ├── models/
│   │   └── GPACourse.java              # Model with calculations
│   └── ...
├── test/java/tk/therealsuji/vtopchennai/    # ← Unit Tests (JVM)
│   ├── calculations/
│   │   └── AttendanceCalculatorTest.java
│   ├── helpers/
│   │   └── GPACalculatorTest.java
│   └── models/
│       └── GPACourseTest.java
└── androidTest/java/tk/therealsuji/vtopchennai/  # ← Instrumented Tests (Android)
    └── database/
        ├── AttendanceDaoTest.java
        └── CoursesDaoTest.java
```

### Test Coverage by Component

#### ✅ Highly Tested (>80% coverage)

**GPACourse Model** - `models/GPACourseTest.java` (19 tests)
- Grade point conversion (S→10, A→9, ..., F→0)
- Course GPA calculation (gradePoints × credits)
- Grade/credit setters
- Edge cases (zero credits, invalid grades)

**GPACalculator Helper** - `helpers/GPACalculatorTest.java` (35+ tests)
- Semester GPA calculation
- CGPA calculation (4 modes)
- Required GPA to reach target
- CGPA prediction
- Real-world VIT scenarios

**AttendanceCalculator Helper** - `calculations/AttendanceCalculatorTest.java` (25+ tests)
- Attendance percentage (Math.floor rounding)
- Classes to reach 75% target
- Maximum classes to miss
- Simulating attendance changes
- VIT 75% requirement scenarios

**AttendanceDao** - `database/AttendanceDaoTest.java` (13 tests)
- Insert/retrieve attendance
- Update totals (calculator integration)
- Cascade deletes
- Edge cases (0%, 100%)

**CoursesDao** - `database/CoursesDaoTest.java` (16 tests)
- Insert/retrieve courses and slots
- Complex joined queries
- Cascade deletes
- Course code ordering
- Typical semester simulation

#### ⏳ Partially Tested

**VTOPService** - Web scraping logic
- Status: Requires refactoring for testability
- Priority: High
- Complexity: Very High (2,817 lines)

**Fragments** - UI business logic
- Status: Extract logic to helpers first
- Priority: Medium
- Complexity: Medium-High

#### ❌ Not Yet Tested

**Services** - Background services
- AutoSyncWorker
- ClassTimeMonitorService

**Remaining DAOs**
- MarksDao
- ExamsDao
- TimetableDao

**Notification Scheduling**
- SettingsRepository alarm calculations

---

## Writing New Tests

### Unit Test Template

```java
package tk.therealsuji.vtopchennai.helpers;

import org.junit.Test;
import static org.junit.Assert.*;

public class MyHelperTest {

    private static final double DELTA = 0.001; // For floating point comparisons

    @Test
    public void testMethodName_Scenario() {
        // Arrange
        int input = 10;

        // Act
        int result = MyHelper.calculate(input);

        // Assert
        assertEquals(20, result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMethodName_InvalidInput() {
        MyHelper.calculate(-1); // Should throw exception
    }
}
```

### Integration Test Template

```java
package tk.therealsuji.vtopchennai.database;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MyDaoTest {

    private AppDatabase database;
    private MyDao myDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries() // For testing only
                .build();

        myDao = database.myDao();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void testInsertAndRetrieve() throws Exception {
        // Test implementation
    }
}
```

### Test Naming Conventions

**Pattern:** `test[MethodName]_[Scenario]`

**Examples:**
```java
testCalculateAttendancePercentage_ExactPercentages()
testCalculateGPA_WithFailedCourse()
testGetCourseByCode_NonExistent()
testForeignKeyConstraint_CascadeDelete()
```

### Best Practices

1. **Arrange-Act-Assert Pattern**
   ```java
   @Test
   public void testExample() {
       // Arrange - Set up test data
       int input = 10;

       // Act - Execute the method
       int result = calculate(input);

       // Assert - Verify the result
       assertEquals(20, result);
   }
   ```

2. **Test One Thing Per Test**
   - Each test should verify a single behavior
   - Use descriptive test names

3. **Use Helper Methods**
   ```java
   private Course createTestCourse(int id, String code) {
       Course course = new Course();
       course.id = id;
       course.code = code;
       return course;
   }
   ```

4. **Test Edge Cases**
   - Zero values
   - Negative values
   - Null values
   - Division by zero
   - Empty collections
   - Very large values

5. **Clean Up Resources**
   ```java
   @After
   public void tearDown() {
       database.close(); // Close database after each test
   }
   ```

---

## Continuous Integration

### GitHub Actions Workflow

Create `.github/workflows/test.yml`:

```yaml
name: Run Tests

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'adopt'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run unit tests
        run: ./gradlew test

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: app/build/test-results/

  instrumented-tests:
    runs-on: macos-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'adopt'

      - name: Run instrumented tests
        uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 29
          script: ./gradlew connectedAndroidTest
```

---

## Troubleshooting

### Common Issues

#### 1. Tests Not Found

**Problem:** Gradle can't find test classes

**Solution:**
```bash
# Clean and rebuild
./gradlew clean
./gradlew build
./gradlew test
```

#### 2. Module Not Found Errors

**Problem:** `Cannot resolve symbol 'junit'`

**Solution:** Sync Gradle dependencies
```bash
./gradlew --refresh-dependencies
```

In Android Studio: **File → Sync Project with Gradle Files**

#### 3. Instrumented Tests Fail

**Problem:** "No connected devices"

**Solution:**
- Start an Android emulator
- Or connect a physical device with USB debugging enabled
- Verify with `adb devices`

#### 4. Tests Pass Locally But Fail in CI

**Problem:** Time-dependent or environment-specific tests

**Solution:**
- Avoid using `System.currentTimeMillis()` in tests
- Mock time-dependent behavior
- Use dependency injection for testability

#### 5. Test Coverage Report Not Generated

**Problem:** `jacocoTestReport` task not found

**Solution:** Add to `app/build.gradle`:
```gradle
android {
    buildTypes {
        debug {
            testCoverageEnabled true
        }
    }
}
```

---

## Test Dependencies

Current test dependencies in `app/build.gradle`:

```gradle
dependencies {
    // Unit Testing
    testImplementation 'junit:junit:4.13.2'
    testImplementation 'org.mockito:mockito-core:5.3.1'
    testImplementation 'org.mockito:mockito-inline:5.2.0'
    testImplementation 'androidx.arch.core:core-testing:2.2.0'
    testImplementation 'org.robolectric:robolectric:4.11.1'

    // Android Instrumented Testing
    androidTestImplementation 'androidx.test.ext:junit:1.1.5'
    androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
    androidTestImplementation 'androidx.room:room-testing:2.6.1'
    androidTestImplementation 'androidx.work:work-testing:2.9.0'
    androidTestImplementation 'androidx.arch.core:core-testing:2.2.0'
}
```

---

## Additional Resources

- [JUnit 4 Documentation](https://junit.org/junit4/)
- [Mockito Documentation](https://site.mockito.org/)
- [Android Testing Guide](https://developer.android.com/training/testing)
- [Room Testing Guide](https://developer.android.com/training/data-storage/room/testing-db)
- [Robolectric Documentation](http://robolectric.org/)

---

## Test Maintenance

### When to Update Tests

1. **When adding new features**
   - Write tests before implementing (TDD)
   - Aim for >80% coverage of new code

2. **When fixing bugs**
   - Add a test that reproduces the bug
   - Fix the code
   - Verify the test passes

3. **When refactoring**
   - Run tests before and after
   - Tests should pass without modification
   - Update tests if API changes

### Test Code Quality

- Keep tests simple and readable
- Avoid logic in tests
- Don't test framework code
- Test behavior, not implementation
- Update tests when requirements change

---

## Contributing

When submitting PRs with new features:

1. ✅ Add unit tests for business logic
2. ✅ Add integration tests for database changes
3. ✅ Ensure all tests pass: `./gradlew test connectedAndroidTest`
4. ✅ Update this README if adding new test patterns
5. ✅ Aim for >70% coverage of new code

---

**Last Updated:** 2025-11-15
**Test Count:** 108+ tests
**Coverage:** ~45-55% (business logic)

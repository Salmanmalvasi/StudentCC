package com.salmanmalvasi.studentcc.integrations.studentcc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StudentCCSemesterResolverTest {
    private static final String MAP = "{\"Fall Semester 2026-27\":\"CH20262601\",\"Winter Semester 2026-27\":\"CH20262602\"}";

    @Test public void actualPreferenceContractResolvesSelectedLabelWithoutSemesterSubId() {
        assertEquals("CH20262601", StudentCCSemesterResolver.resolve("Fall Semester 2026-27", MAP, null));
    }

    @Test public void changingSemesterUsesTheNewMappingInsteadOfAStaleExplicitId() {
        assertEquals("CH20262602", StudentCCSemesterResolver.resolve("Winter Semester 2026-27", MAP, "CH20262601"));
    }

    @Test public void selectionAlreadyContainingAnIdIsSupported() {
        assertEquals("CH20262602", StudentCCSemesterResolver.resolve(" CH20262602 ", MAP, "CH20262601"));
    }

    @Test public void missingSelectedMappingCannotShareThePreviousSemestersTotals() {
        assertEquals("", StudentCCSemesterResolver.resolve("Summer Semester 2026-27", MAP, "CH20262601"));
        assertEquals("", StudentCCSemesterResolver.resolve("Summer Semester 2026-27", null, "CH20262601"));
    }

    @Test public void onlyUnambiguousLegacyIdCanBeUsedAsFallback() {
        assertEquals("CH20262601", StudentCCSemesterResolver.resolve(null, null, "CH20262601"));
        assertEquals("CH20262601", StudentCCSemesterResolver.resolve("", "{}", "CH20262601"));
        assertEquals("", StudentCCSemesterResolver.resolve("", MAP, "CH20262601"));
    }

    @Test public void malformedMappingsAndLabelsCannotBecomeCohortIds() {
        assertEquals("", StudentCCSemesterResolver.resolve("Fall Semester 2026-27", "{broken", "CH20262601"));
        assertEquals("", StudentCCSemesterResolver.resolve("Fall Semester 2026-27", "{\"Fall Semester 2026-27\":\"Fall Semester 2026-27\"}", "CH20262601"));
        assertEquals("", StudentCCSemesterResolver.resolve("Fall Semester 2026-27", "{\"Fall Semester 2026-27\":42}", "CH20262601"));
        assertEquals("", StudentCCSemesterResolver.resolve(null, null, "CH2026?other=1"));
    }
}

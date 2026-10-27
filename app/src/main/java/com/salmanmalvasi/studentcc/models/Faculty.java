package com.salmanmalvasi.studentcc.models;

import androidx.annotation.NonNull;

public class Faculty {
    private String empSno;
    private String empName;
    private String empId;
    private String empDesignation;
    private String empSchoolAbbr;
    private String empSchool;
    private String empLocationBuilding;
    private String empLocationCabin;

    public Faculty(String empSno, String empName, String empId, String empDesignation,
                   String empSchoolAbbr, String empSchool, String empLocationBuilding,
                   String empLocationCabin) {
        this.empSno = empSno;
        this.empName = empName;
        this.empId = empId;
        this.empDesignation = empDesignation;
        this.empSchoolAbbr = empSchoolAbbr;
        this.empSchool = empSchool;
        this.empLocationBuilding = empLocationBuilding;
        this.empLocationCabin = empLocationCabin;
    }

    public String getEmpSno() {
        return empSno;
    }

    public void setEmpSno(String empSno) {
        this.empSno = empSno;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getEmpId() {
        return empId;
    }

    public void setEmpId(String empId) {
        this.empId = empId;
    }

    public String getEmpDesignation() {
        return empDesignation;
    }

    public void setEmpDesignation(String empDesignation) {
        this.empDesignation = empDesignation;
    }

    public String getEmpSchoolAbbr() {
        return empSchoolAbbr;
    }

    public void setEmpSchoolAbbr(String empSchoolAbbr) {
        this.empSchoolAbbr = empSchoolAbbr;
    }

    public String getEmpSchool() {
        return empSchool;
    }

    public void setEmpSchool(String empSchool) {
        this.empSchool = empSchool;
    }

    public String getEmpLocationBuilding() {
        return empLocationBuilding;
    }

    public void setEmpLocationBuilding(String empLocationBuilding) {
        this.empLocationBuilding = empLocationBuilding;
    }

    public String getEmpLocationCabin() {
        return empLocationCabin;
    }

    public void setEmpLocationCabin(String empLocationCabin) {
        this.empLocationCabin = empLocationCabin;
    }

    /**
     * Returns true if the faculty name matches the search query (case-insensitive)
     */
    public boolean matchesSearch(String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        
        String lowerQuery = query.toLowerCase();
        
        // Only search by name
        return (empName != null && empName.toLowerCase().contains(lowerQuery));
    }

    @NonNull
    @Override
    public String toString() {
        return "Faculty{" +
                "name='" + empName + '\'' +
                ", id='" + empId + '\'' +
                ", designation='" + empDesignation + '\'' +
                ", school='" + empSchool + '\'' +
                '}';
    }
}

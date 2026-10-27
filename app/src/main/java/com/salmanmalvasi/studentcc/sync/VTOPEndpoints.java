package com.salmanmalvasi.studentcc.sync;

/**
 * Complete catalog of native API endpoints and configuration discovered from
 * the official VITianCC Android application reverse-engineering report.
 * Replaces legacy HTML web portal scraping with direct JSON/REST API calls.
 */
public final class VTOPEndpoints {

    private VTOPEndpoints() {
        // Utility / Constants class
    }

    // ==========================================
    // Base Hosts and Static Authentication Key
    // ==========================================
    public static final String BASE_URL = "https://vtopcc5.vit.ac.in";
    public static final String LEGACY_BASE_URL = "https://vtopcc.vit.ac.in/vtop";

    public static final String HEADER_API_KEY = "S-API-KEY";
    public static final String API_KEY_VALUE = "S@fe@VITCC123$#%";

    public static final String HEADER_KEY_USER = "S-KEY-USER";
    public static final String AES_KEY_VALUE = API_KEY_VALUE;

    // ==========================================
    // 1. Authentication & Session Management
    // ==========================================
    /** E-001/E-003: User Login (POST userEnc, encData, deviceId, deviceType, fcmToken) */
    public static final String AUTH_LOGIN = "/login/vitian/appLoginProcess";
    /** E-004: Generate OTP for login */
    public static final String AUTH_GENERATE_OTP = "/login/vitian/generateOtp";
    /** E-005: Verify OTP */
    public static final String AUTH_SUBMIT_OTP = "/login/vitian/submitOtp";
    /** E-006: Resend OTP */
    public static final String AUTH_RESEND_OTP = "/login/vitian/resendOtp";
    /** E-007: Reset OTP */
    public static final String AUTH_RESET_OTP = "/login/vitian/resetOtp";
    /** E-008: Set MPIN */
    public static final String AUTH_SET_MPIN = "/login/vitian/setMpin";
    /** E-009: Validate MPIN */
    public static final String AUTH_VALIDATE_MPIN = "/login/vitian/validateMpin";
    /** E-010: Log biometric authentication */
    public static final String AUTH_BIOMETRIC_LOG = "/login/vitian/biometricLog";
    /** E-011: User logout */
    public static final String AUTH_LOGOUT = "/login/vitian/logOut";

    // ==========================================
    // 2. Student Profile & Academic Info
    // ==========================================
    /** E-012: Student profile details (regNo, semesterId) */
    public static final String PROFILE_INFO = "/login/vitian/myProfileInfo";
    /** E-013: Available semester list (regNo) */
    public static final String GET_SEMESTER_LIST = "/login/vitian/getSemesterList";
    /** E-014: CGPA, credits registered and earned (regNo) */
    public static final String CGPA_VIEW = "/login/vitian/cgpaView";

    // ==========================================
    // 3. Attendance
    // ==========================================
    /** E-015: Attendance calendar and daily records (regNo, semesterId, courseId). Course-specific. */
    public static final String ATTENDANCE_CALENDAR = "/login/vitian/viewAttendanceCal";
    /**
     * E-016: Formerly inferred attendance calculator endpoint.
     * NOTE: Official APK analysis proves this is client-side math (calculateAttendancePlus),
     * NOT a server endpoint. Summary attendance is delivered inside studentRegisteredCourse.
     */
    @Deprecated
    public static final String ATTENDANCE_CALCULATOR = "/login/vitian/acadAttendanceCalculator";

    // ==========================================
    // 4. Grades & Marks
    // ==========================================
    /** Confirmed in libapp.so (offset 0x5ad6f): Active semester assessment marks (regNo, semesterId) */
    public static final String ACTIVE_SEM_MARK_VIEW = "/login/vitian/activeSemMarkView";
    /** E-017: Grade overview (regNo, semesterId) */
    public static final String GRADE_VIEW = "/login/vitian/gradeView";
    /** E-018: Grade details by semester (regNo, semesterId) */
    public static final String GRADE_DETAILS_BY_SEM_ID = "/login/vitian/gradeDetailsBySemId";
    /** E-019 / Confirmed in libapp.so (offset 0x578c8): Course-specific assessment breakdown (regNo, semesterId, courseId) */
    public static final String GRADE_PAGE_MARK_BY_SEM_COURSE = "/login/vitian/gradePageMarkBySemIdCourseId";
    /**
     * E-020: Formerly inferred mark view endpoint.
     * NOTE: Official APK analysis confirms /markView is a Flutter UI client route (routes: {'/markView': ...}),
     * NOT a verified backend endpoint.
     */
    public static final String MARK_VIEW = "/login/vitian/markView";
    /** E-021: Weightage mark view (regNo, semesterId, courseCode) */
    public static final String WEIGHTAGE_MARK_VIEW = "/login/vitian/weightageMarkView";
    /** E-069: Comprehensive student mark list (regNo, semesterId) */
    public static final String STUDENT_MARK_LIST = "/login/vitian/studentMarkList";

    // ==========================================
    // 5. Courses & Timetable
    // ==========================================
    /** E-022: Registered courses for semester (regNo, semesterId) */
    public static final String STUDENT_REGISTERED_COURSE = "/students/studentRegisteredCourse";
    /** E-023: Courses offered view (regNo, semesterId) */
    public static final String COURSE_OFFERED_VIEW = "/login/vitian/courseOfferedView";
    /** E-024: Course page details (regNo, semesterId, courseCode) */
    public static final String COURSE_PAGE = "/login/vitian/coursePage";
    /** E-025: Detailed course description (courseId, courseCode) */
    public static final String COURSE_DETAIL = "/login/vitian/courseDetail";
    /** E-026: Student timetable slots and schedule (regNo, semesterId) */
    public static final String TIME_TABLE = "/login/vitian/timeTable";
    /** E-027: Exam schedule dates, sessions, venues (regNo, semesterId) */
    public static final String EXAM_SCHEDULE_VIEW = "/login/vitian/examScheduleView";

    // ==========================================
    // 6. Faculty Information & Messages
    // ==========================================
    /** E-028: Search faculty directory (searchKey) */
    public static final String SEARCH_FACULTY = "/login/vitian/searchFaculty";
    /** E-029: Get faculty profile photo (empId) */
    public static final String GET_FACULTY_IMAGE = "/login/vitian/getFacultyImage";
    /** E-030: Messages from course faculty (regNo, semesterId) */
    public static final String MY_CLASS_FACULTY_MSG = "/login/vitian/myClassFacultyMsg";
    /** E-066: Student class circular / announcements (regNo, semesterId) */
    public static final String STUDENT_CLASS_MSG = "/login/vitian/studentClassMsg";
    /** E-067: HOD and Dean details (department params) */
    public static final String HOD_DEAN_INFO = "/login/vitian/hodDeanInfo";

    // ==========================================
    // 7. Hostel & Leave
    // ==========================================
    /** E-031: Hosteller room, block and bed info (regNo) */
    public static final String VIEW_HOSTELLER_INFO = "/login/vitian/viewHostellerInfo";
    /** E-032: Permitted hostel leave types */
    public static final String GET_HOSTEL_LEAVE_TYPE = "/login/vitian/getHostelLeaveType";
    /** E-033: View details for a leave type (leaveCode) */
    public static final String VIEW_HOSTEL_LEAVE_TYPE = "/login/vitian/viewHostelLeaveType";
    /** E-034: Submit hostel leave request (leaveCode, dates, times, reason) */
    public static final String HOSTEL_LEAVE_APPLY = "/login/vitian/hostelLeaveApply";
    /** E-035: Hostel leave history and approval status (regNo) */
    public static final String STUDENT_HOSTEL_LEAVE_HISTORY = "/login/vitian/studentHostelLeaveHistory";

    // ==========================================
    // 8. Grievances
    // ==========================================
    /** E-036: Grievance categories and subcategories */
    public static final String GET_GRIEVANCE_CATEGORY_LIST = "/login/vitian/getCategoryList";
    /** E-037: Post grievance complaint (multipart: form + attachment) */
    public static final String POST_GRIEVANCE_FORM = "/login/vitian/postGrievanceForm";
    /** E-038: Track specific grievance status (regNo, grievanceId) */
    public static final String GET_POST_TRACKING_VIEW_DATA_BY_STUD = "/login/vitian/getPostTrackingViewDataByStud";
    /** E-039: Grievance tracking screen / list of posted grievances (regNo) */
    public static final String GET_POST_TRACKING_SCREEN_DATA_BY_STUD = "/login/vitian/getPostTrackingScreeenDataByStud";
    /** E-040: Grievance remarks and conversation log (grievanceTransId) */
    public static final String GRIEVANCE_REMARKS = "/login/vitian/grievanceRemarks";

    // ==========================================
    // 9. Payments, Wallet & Receipts
    // ==========================================
    /** E-041: Outstanding fee heads and payment list (regNo) */
    public static final String PAYMENT_LIST = "/login/vitian/paymentList";
    /** E-042: Pending invoices and payable amounts (regNo) */
    public static final String PENDING_PAYMENTS = "/login/vitian/pendingPayments";
    /** E-043: Initialize payment gateway session (regNo, finId, gateWayId) */
    public static final String PAYMENT_PROCESS_VIEW = "/login/vitian/paymentProcessView";
    /** E-044: Verify payment transaction status (paymentTransactionId, orderId) */
    public static final String PAYMENT_STATUS_AND_CHECK_UPDATE = "/login/vitian/paymentStatusandCheckUpdate";
    /** E-045: Check wallet balance and excess amount (regNo) */
    public static final String WALLET_BALANCE = "/login/vitian/walletBalance";
    /** E-046: Transaction history for wallet (regNo) */
    public static final String WALLET_HISTORY = "/login/vitian/walletHistory";
    /** E-047: Apply wallet balance against fees (regNo, walletAmount, finId) */
    public static final String PAYMENT_WALLET_ADJUSTMENT = "/login/vitian/paymentWalletAdjustment";
    /** E-048: Validate OTP for wallet payment adjustment (otpText) */
    public static final String WALLET_OTP_VALIDATION = "/login/vitian/walletOtpValidation";
    /** E-049: View student fee receipt metadata (regNo) */
    public static final String VIEW_RECEIPT = "/login/vitian/viewReceipt";
    /** E-050: Download fee receipt document (receiptNo) */
    public static final String DOWNLOAD_RECEIPT = "/login/vitian/downloadReceipt";

    // ==========================================
    // 10. Notifications & Proctor
    // ==========================================
    /** E-051: In-app student notifications (regNo) */
    public static final String MY_NOTIFICATION_DT = "/login/vitian/myNotificationDt";
    /** E-052: Proctor profile, room, cabin, contact details (regNo) */
    public static final String PROCTOR_DETAIL = "/login/vitian/proctorDetail";
    /** E-053: Messages sent by assigned proctor (regNo, semesterId) */
    public static final String PROCTOR_MESSAGE = "/login/vitian/proctorMessage";
    /** E-054: Proctor meeting history and minutes (regNo, semesterId) */
    public static final String PROCTOR_MEETING_HISTORY = "/login/vitian/proctorMeetingHistory";
    /** E-055: Semesters eligible for proctor meetings (regNo) */
    public static final String PROCTOR_MEETING_SEM_LIST = "/login/vitian/proctorMeetingSemList";

    // ==========================================
    // 11. Digital Assignments (DAs) & Document Upload
    // ==========================================
    /** E-056: Student assignment submission log (regNo, semesterId) */
    public static final String STUDENT_ASSIGN_LOG = "/login/vitian/studentAssignLog";
    /** E-057: Faculty assignment requirements (courseId, semesterId) */
    public static final String FACULTY_ASSIGN_LOG = "/login/vitian/facultyAssignLog";
    /** E-058: Upload digital assignment solution (multipart file, courseId) */
    public static final String STUD_DA_UPLOAD = "/login/vitian/studDaUpload";
    /** E-059: DA upload OTP verification (otpText) */
    public static final String DA_UPLOAD_OTP = "/login/vitian/daUploadOtp";
    /** E-060: Detailed metadata of uploaded DA solutions (regNo, courseId) */
    public static final String DA_UPLOADED_DETAILED_DATA = "/login/vitian/daUploadedDetailedData";
    /** E-061: Download DA question paper (materialId01..06) */
    public static final String DA_QUESTION_DOWNLOAD = "/login/vitian/daQuestionDownload";
    /** E-062: Download submitted DA answer (materialId) */
    public static final String DA_ANSWER_DOWNLOAD = "/login/vitian/daAnswerDownload";
    /** E-063: Download course material / syllabus page (coursePageDownloadParam) */
    public static final String DOWNLOAD_COURSE_PAGE = "/login/vitian/downloadCoursePage";

    // ==========================================
    // 12. Academic Calendar & Spotlight Notices
    // ==========================================
    /** E-064: Monthly academic calendar events (yearMonth: "YYYY-MM") */
    public static final String ACADEMIC_CALENDAR = "/login/vitian/academicCalendar";
    /** E-065: Spotlight campus notices and circular attachment (spotlightDetailId) */
    public static final String GET_SPOTLIGHT_FILE = "/login/vitian/getSpotlightFile";

    // ==========================================
    // 13. General Campus & Support
    // ==========================================
    /** E-068: Campus emergency contacts and helpdesk directory */
    public static final String CONTACT_US = "/login/vitian/contactUs";

    // ==========================================
    // 14. Third-Party Integration Endpoints
    // ==========================================
    /** T-001: Paytm Production Gateway Assist URL */
    public static final String PAYTM_GET_URL = "https://securegw.paytm.in/payassist/api/getURLByAppVersion";
    /** T-002: Paytm Production Event Logging */
    public static final String PAYTM_SUBMIT_EVENT_LOGS = "https://securegw.paytm.in/payassist/api/submit/event/logs";
    /** T-003: Paytm Pre-Production Assist */
    public static final String PAYTM_GET_UNIQUE_ASSIST = "https://securegw-preprod.paytm.in/payassist/api/getUniqueAssist";
    /** T-004: Paytm Enable Assist */
    public static final String PAYTM_ENABLE_ASSIST = "https://securegw.paytm.in/merchant-settlement-service/paytmAssist/enable";
    /** T-005: Firebase Crashlytics platform settings */
    public static final String FIREBASE_CRASHLYTICS_SETTINGS = "https://firebase-settings.crashlytics.com/spi/v2/platforms/android/gmp/%s/settings";
    /** T-006: 3DS Card Authentication verification */
    public static final String ARCOT_3DS_PAGE = "https://secure4.arcot.com/acspage/cap?RID=40147&VAA=B";

    /**
     * Helper to construct full absolute URL from relative path
     */
    public static String getAbsoluteUrl(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return BASE_URL;
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://")) {
            return relativePath;
        }
        if (!relativePath.startsWith("/")) {
            return BASE_URL + "/" + relativePath;
        }
        return BASE_URL + relativePath;
    }
}

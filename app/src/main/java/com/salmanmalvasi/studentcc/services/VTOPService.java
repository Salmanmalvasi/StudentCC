package com.salmanmalvasi.studentcc.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Base64;
import android.view.ContextThemeWrapper;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.CompletableObserver;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import com.salmanmalvasi.studentcc.R;
import com.salmanmalvasi.studentcc.helpers.AppDatabase;
import com.salmanmalvasi.studentcc.helpers.NotificationHelper;
import com.salmanmalvasi.studentcc.helpers.SettingsRepository;
import com.salmanmalvasi.studentcc.interfaces.AttendanceDao;
import com.salmanmalvasi.studentcc.interfaces.AttendanceRecordDao;
import com.salmanmalvasi.studentcc.interfaces.CoursesDao;
import com.salmanmalvasi.studentcc.interfaces.ExamsDao;
import com.salmanmalvasi.studentcc.interfaces.LeaveDao;
import com.salmanmalvasi.studentcc.interfaces.MarksDao;
import com.salmanmalvasi.studentcc.interfaces.ReceiptsDao;
import com.salmanmalvasi.studentcc.interfaces.StaffDao;
import com.salmanmalvasi.studentcc.interfaces.TimetableDao;
import com.salmanmalvasi.studentcc.models.Attendance;
import com.salmanmalvasi.studentcc.models.AttendanceRecord;
import com.salmanmalvasi.studentcc.models.Course;
import com.salmanmalvasi.studentcc.models.CumulativeMark;
import com.salmanmalvasi.studentcc.models.Exam;
import com.salmanmalvasi.studentcc.models.Leave;
import com.salmanmalvasi.studentcc.models.CalendarEvent;
import com.salmanmalvasi.studentcc.models.Mark;
import com.salmanmalvasi.studentcc.models.Receipt;
import com.salmanmalvasi.studentcc.models.Slot;
import com.salmanmalvasi.studentcc.models.Spotlight;
import com.salmanmalvasi.studentcc.models.Staff;
import com.salmanmalvasi.studentcc.models.Timetable;
import com.salmanmalvasi.studentcc.helpers.FastScraper;
import com.salmanmalvasi.studentcc.models.SemesterSummary;
import android.net.Uri;

public class VTOPService extends Service {
    public static final int CAPTCHA_DEFAULT = 1;
    public static final int CAPTCHA_GRECATPCHA = 2;

    private static final String END_SERVICE_ACTION = "END_SERVICE_ACTION";

    private static final int MAX_ERROR_RETRIES = 3;

    AppDatabase appDatabase;
    Binder serviceBinder = new ServiceBinder();
    boolean isWebViewDestroyed;
    Callback callback;
    Integer counter, maxProgress, progress;
    private int errorRetryCount = 0;
    NotificationCompat.Builder notification;
    NotificationManager notificationManager;
    PageState pageState;
    SharedPreferences sharedPreferences;
    WebView webView;

    Map<Integer, Course> theoryCourses, labCourses, projectCourses;
    Map<String, CumulativeMark> cumulativeMarks;
    Map<String, Slot> theorySlots, labSlots, projectSlots;
    Map<String, String> semesters;
    String username, password, semesterID;
    CompositeDisposable compositeDisposable;
    private boolean isQuickSync = false;
    private boolean isAllSemestersSync = false;

    public static class DayPeriod {
        public final String day;
        public final int periodIndex;

        public DayPeriod(String day, int periodIndex) {
            this.day = day;
            this.periodIndex = periodIndex;
        }
    }

    public static final Map<String, List<DayPeriod>> SLOT_MAP = new HashMap<>();
    public static final Map<String, String> SLOT_ALIASES = new HashMap<>();
    private static final Map<String, String> EMAIL_REPLACEMENTS = new HashMap<>();

    static {
        EMAIL_REPLACEMENTS.put("pcbai.cc@vit.ac.in", "hodcc.bai@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodbba.cc@vit.ac.in", "hodcc.bbh@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pccompug.cc@vit.ac.in", "hodcc.bce@vit.ac.in");
        EMAIL_REPLACEMENTS.put("cochaircc.scope@vit.ac.in", "cohodcc.bce@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pccivilug.cc@vit.ac.in", "hodcc.bcl@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodbsccs.cc@vit.ac.in", "hodcc.bcs@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodbds.cc@vit.ac.in", "hodcc.bds@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcelectronicsug.cc@vit.ac.in", "hodcc.bec@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcelectricalug.cc@vit.ac.in", "hodcc.bee@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.ecse@vit.ac.in", "hodcc.bel@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.vfit@vit.ac.in", "hodcc.bft@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodbcom.cc@vit.ac.in", "hodcc.bhc@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.economics@vit.ac.in", "hodcc.bhe@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcbla.cc@vit.ac.in", "hodcc.bla@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcblb.cc@vit.ac.in", "hodcc.blb@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcblc.cc@vit.ac.in", "hodcc.blc@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcmechug.cc@vit.ac.in", "hodcc.bme@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodautocc.smec@vit.ac.in", "hodcc.bmh@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcbps.cc@vit.ac.in", "hodcc.bps@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodaicc.scope@vit.ac.in", "hodcc.brs@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.vlsi@vit.ac.in", "hodcc.bvd@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcysec.cc@vit.ac.in", "hodcc.byb@vit.ac.in");
        EMAIL_REPLACEMENTS.put("chennai.hodchemistry@vit.ac.in", "hodcc.msh@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcmai.cc@vit.ac.in", "hodcc.mai@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.comppg1@vit.ac.in", "hodcc.mas@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.maths@vit.ac.in", "hodcc.mdt@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodmba.cc@vit.ac.in", "hodcc.mba@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcmcapg.cc@vit.ac.in", "hodcc.mca@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pccomppg.cc@vit.ac.in", "hodcc.mcs@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pccadcampg.cc@vit.ac.in", "hodcc.mcd@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.em@vit.ac.in", "hodcc.mem@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcembeddedpg.cc@vit.ac.in", "hodcc.mes@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcmia.cc@vit.ac.in", "hodcc.mid@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcmsse.cc@vit.ac.in", "hodcc.mis@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcmechatronpg.cc@vit.ac.in", "hodcc.mmt@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcstrucpg.cc@vit.ac.in", "hodcc.mst@vit.ac.in");
        EMAIL_REPLACEMENTS.put("pcvlsipg.cc@vit.ac.in", "hodcc.mvd@vit.ac.in");
        EMAIL_REPLACEMENTS.put("hodcc.psychology@vit.ac.in", "hodcc.myi@vit.ac.in");
        EMAIL_REPLACEMENTS.put("chennai.hodphysics@vit.ac.in", "hodcc.msp@vit.ac.in");

    }

    static {
        // Slot mappings for VIT Chennai timetable periods

        addSlotMapping("A1", "monday", 0); // 08:00
        addSlotMapping("A1", "wednesday", 1); // 08:55

        addSlotMapping("A2", "monday", 6); // 14:00
        addSlotMapping("A2", "wednesday", 7); // 14:55

        addSlotMapping("B1", "thursday", 1); // 08:55
        addSlotMapping("B1", "tuesday", 0); // 08:00

        addSlotMapping("B2", "thursday", 7); // 14:55
        addSlotMapping("B2", "tuesday", 6); // 14:00

        addSlotMapping("C1", "friday", 1); // 08:55
        addSlotMapping("C1", "wednesday", 0); // 08:00

        addSlotMapping("C2", "friday", 7); // 14:55
        addSlotMapping("C2", "wednesday", 6); // 14:00

        addSlotMapping("D1", "monday", 2); // 09:50
        addSlotMapping("D1", "thursday", 0); // 08:00

        addSlotMapping("D2", "monday", 8); // 15:50
        addSlotMapping("D2", "thursday", 6); // 14:00

        addSlotMapping("E1", "friday", 0); // 08:00
        addSlotMapping("E1", "tuesday", 2); // 09:50

        addSlotMapping("E2", "friday", 6); // 14:00
        addSlotMapping("E2", "tuesday", 8); // 15:50

        addSlotMapping("F1", "monday", 1); // 08:55
        addSlotMapping("F1", "wednesday", 2); // 09:50

        addSlotMapping("F2", "monday", 7); // 14:55
        addSlotMapping("F2", "wednesday", 8); // 15:50

        addSlotMapping("G1", "thursday", 2); // 09:50
        addSlotMapping("G1", "tuesday", 1); // 08:55

        addSlotMapping("G2", "thursday", 8); // 15:50
        addSlotMapping("G2", "tuesday", 7); // 14:55

        addSlotMapping("L1", "monday", 0); // 08:00

        addSlotMapping("L10", "tuesday", 3); // 10:40

        addSlotMapping("L11", "tuesday", 4); // 11:40

        addSlotMapping("L12", "tuesday", 5); // 12:35
        addSlotMapping("L13", "wednesday", 0); // 08:00

        addSlotMapping("L14", "wednesday", 1); // 08:50

        addSlotMapping("L15", "wednesday", 2); // 09:50

        addSlotMapping("L16", "wednesday", 3); // 10:40

        addSlotMapping("L17", "wednesday", 4); // 11:40

        addSlotMapping("L18", "wednesday", 5); // 12:35

        addSlotMapping("L19", "thursday", 0); // 08:00

        addSlotMapping("L2", "monday", 1); // 08:50

        addSlotMapping("L20", "thursday", 1); // 08:50

        addSlotMapping("L21", "thursday", 2); // 09:50

        addSlotMapping("L22", "thursday", 3); // 10:40

        addSlotMapping("L23", "thursday", 4); // 11:40

        addSlotMapping("L24", "thursday", 5); // 12:35

        addSlotMapping("L25", "friday", 0); // 08:00

        addSlotMapping("L26", "friday", 1); // 08:50

        addSlotMapping("L27", "friday", 2); // 09:50

        addSlotMapping("L28", "friday", 3); // 10:40

        addSlotMapping("L29", "friday", 4); // 11:40

        addSlotMapping("L3", "monday", 2); // 09:50

        addSlotMapping("L30", "friday", 5); // 12:30

        addSlotMapping("L31", "monday", 6); // 14:00

        addSlotMapping("L32", "monday", 7); // 14:50

        addSlotMapping("L33", "monday", 8); // 15:50

        addSlotMapping("L34", "monday", 9); // 16:40

        addSlotMapping("L35", "monday", 10); // 17:40

        addSlotMapping("L36", "monday", 11); // 18:30

        addSlotMapping("L37", "tuesday", 6); // 14:00

        addSlotMapping("L38", "tuesday", 7); // 14:50

        addSlotMapping("L39", "tuesday", 8); // 15:50

        addSlotMapping("L4", "monday", 3); // 10:40

        addSlotMapping("L40", "tuesday", 9); // 16:40

        addSlotMapping("L41", "tuesday", 10); // 17:40

        addSlotMapping("L42", "tuesday", 11); // 18:30

        addSlotMapping("L43", "wednesday", 6); // 14:00

        addSlotMapping("L44", "wednesday", 7); // 14:50

        addSlotMapping("L45", "wednesday", 8); // 15:50

        addSlotMapping("L46", "wednesday", 9); // 16:40

        addSlotMapping("L47", "wednesday", 10); // 17:40

        addSlotMapping("L48", "wednesday", 11); // 18:30

        addSlotMapping("L49", "thursday", 6); // 14:00

        addSlotMapping("L5", "monday", 4); // 11:40

        addSlotMapping("L50", "thursday", 7); // 14:50

        addSlotMapping("L51", "thursday", 8); // 15:50

        addSlotMapping("L52", "thursday", 9); // 16:40

        addSlotMapping("L53", "thursday", 10); // 17:40

        addSlotMapping("L54", "thursday", 11); // 18:30

        addSlotMapping("L55", "friday", 6); // 14:00

        addSlotMapping("L56", "friday", 7); // 14:50

        addSlotMapping("L57", "friday", 8); // 15:50

        addSlotMapping("L58", "friday", 9); // 16:40

        addSlotMapping("L59", "friday", 10); // 17:40

        addSlotMapping("L6", "monday", 5); // 12:30

        addSlotMapping("L60", "friday", 11); // 18:30

        addSlotMapping("L7", "tuesday", 0); // 08:00

        addSlotMapping("L71", "saturday", 0); // 08:00

        addSlotMapping("L72", "saturday", 1); // 08:50

        addSlotMapping("L73", "saturday", 2); // 09:50

        addSlotMapping("L74", "saturday", 3); // 10:40

        addSlotMapping("L75", "saturday", 4); // 11:40

        addSlotMapping("L76", "saturday", 5); // 12:30

        addSlotMapping("L77", "saturday", 6); // 14:00

        addSlotMapping("L78", "saturday", 7); // 14:50

        addSlotMapping("L79", "saturday", 8); // 15:50

        addSlotMapping("L8", "tuesday", 1); // 08:50

        addSlotMapping("L80", "saturday", 9); // 16:40

        addSlotMapping("L81", "saturday", 10); // 17:40

        addSlotMapping("L82", "saturday", 11); // 18:30

        addSlotMapping("L83", "sunday", 0); // 08:00

        addSlotMapping("L84", "sunday", 1); // 08:50

        addSlotMapping("L85", "sunday", 2); // 09:50

        addSlotMapping("L86", "sunday", 3); // 10:40

        addSlotMapping("L87", "sunday", 4); // 11:40

        addSlotMapping("L88", "sunday", 5); // 12:30

        addSlotMapping("L89", "sunday", 6); // 14:00

        addSlotMapping("L9", "tuesday", 2); // 09:50

        addSlotMapping("L90", "sunday", 7); // 14:50

        addSlotMapping("L91", "sunday", 8); // 15:50

        addSlotMapping("L92", "sunday", 9); // 16:40

        addSlotMapping("L93", "sunday", 10); // 17:40

        addSlotMapping("L94", "sunday", 11); // 18:30

        addSlotMapping("S1", "tuesday", 11); // 18:35

        addSlotMapping("S10", "sunday", 5); // 12:35

        addSlotMapping("S11", "monday", 5); // 12:35

        addSlotMapping("S15", "friday", 5); // 12:35

        addSlotMapping("S2", "thursday", 11); // 18:35

        addSlotMapping("S3", "monday", 11); // 18:35

        addSlotMapping("S4", "wednesday", 11); // 18:35

        addSlotMapping("S8", "saturday", 5); // 12:35

        addSlotMapping("TA1", "friday", 2); // 09:50

        addSlotMapping("TA2", "friday", 8); // 15:50

        addSlotMapping("TAA1", "tuesday", 4); // 11:40

        addSlotMapping("TAA2", "tuesday", 10); // 17:40

        addSlotMapping("TB1", "monday", 3); // 10:45

        addSlotMapping("TB2", "monday", 9); // 16:45

        addSlotMapping("TBB1", "wednesday", 4); // 11:40

        addSlotMapping("TBB2", "wednesday", 10); // 17:40

        addSlotMapping("TC1", "tuesday", 3); // 10:45

        addSlotMapping("TC2", "tuesday", 9); // 16:45

        addSlotMapping("TCC1", "thursday", 4); // 11:40

        addSlotMapping("TCC2", "thursday", 10); // 17:40

        addSlotMapping("TD1", "wednesday", 3); // 10:45

        addSlotMapping("TD2", "wednesday", 9); // 16:45

        addSlotMapping("TDD1", "friday", 4); // 11:40

        addSlotMapping("TDD2", "friday", 10); // 17:40

        addSlotMapping("TE1", "thursday", 3); // 10:45

        addSlotMapping("TE2", "thursday", 9); // 16:45

        addSlotMapping("TF1", "friday", 3); // 10:45

        addSlotMapping("TF2", "friday", 9); // 16:45

        addSlotMapping("TG1", "monday", 4); // 11:40

        addSlotMapping("TG2", "monday", 10); // 17:40

        addSlotMapping("W21", "saturday", 9); // 16:45
        addSlotMapping("W21", "sunday", 9); // 16:45

        addSlotMapping("W22", "saturday", 10); // 17:40
        addSlotMapping("W22", "sunday", 10); // 17:40

        addSlotMapping("X11", "saturday", 0); // 08:00
        addSlotMapping("X11", "sunday", 2); // 09:50

        addSlotMapping("X12", "saturday", 1); // 08:55
        addSlotMapping("X12", "sunday", 3); // 10:45

        addSlotMapping("X21", "saturday", 6); // 14:00
        addSlotMapping("X21", "sunday", 8); // 15:50

        addSlotMapping("Y11", "saturday", 2); // 09:50
        addSlotMapping("Y11", "sunday", 0); // 08:00

        addSlotMapping("Y12", "saturday", 3); // 10:45
        addSlotMapping("Y12", "sunday", 1); // 08:55

        addSlotMapping("Y21", "saturday", 8); // 15:50
        addSlotMapping("Y21", "sunday", 6); // 14:00

        addSlotMapping("Z21", "saturday", 7); // 14:55
        addSlotMapping("Z21", "sunday", 7); // 14:55

        addSlotMapping("Z22", "saturday", 11); // 18:35
        addSlotMapping("Z22", "sunday", 11); // 18:35

        // Slot aliases for common slot naming variants
        addSlotAlias("TEE1", "TE1");
        addSlotAlias("TEE2", "TE2");
        addSlotAlias("TFF1", "TF1");
        addSlotAlias("TFF2", "TF2");
        addSlotAlias("TGG1", "TG1");
        addSlotAlias("TGG2", "TG2");
        addSlotAlias("TAA3", "TAA2");
        addSlotAlias("TBB3", "TBB2");
        addSlotAlias("TCC3", "TCC2");
        addSlotAlias("TDD3", "TDD2");
        addSlotAlias("TEE3", "TE2");
        addSlotAlias("TFF3", "TF2");
        addSlotAlias("TGG3", "TG2");
    }

    private static void addSlotMapping(String slot, String day, int periodIndex) {
        SLOT_MAP.computeIfAbsent(slot, key -> new ArrayList<>()).add(new DayPeriod(day, periodIndex));
    }

    private static void addSlotAlias(String alias, String canonical) {
        SLOT_ALIASES.put(alias, canonical);
    }

    public void clearCallback() {
        this.callback = null;
    }

    @Override
    public void onCreate() {
        Intent endServiceIntent = new Intent(this, VTOPService.class);
        endServiceIntent.setAction(END_SERVICE_ACTION);
        PendingIntent endServicePendingIntent = PendingIntent.getService(
                this,
                0,
                endServiceIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationHelper notificationHelper = new NotificationHelper(getApplicationContext());
        this.notificationManager = notificationHelper.getManager();

        this.notification = notificationHelper.notifySync(null, null);
        this.notification.addAction(R.drawable.ic_close, getString(R.string.cancel), endServicePendingIntent);
        this.notification.setOngoing(true);
        this.notification.setProgress(0, 0, true);

        this.appDatabase = AppDatabase.getInstance(getApplicationContext());
        this.sharedPreferences = SettingsRepository.getSharedPreferences(getApplicationContext());
        this.compositeDisposable = new CompositeDisposable();

        this.createWebView();
    }

    private com.salmanmalvasi.studentcc.sync.SyncMetrics syncMetrics;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return serviceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null && intent.getAction().equals(END_SERVICE_ACTION)) {
            this.endService(true);
            this.notificationManager.cancel(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD); // In case the
                                                                                               // notification isn't
                                                                                               // removed for some
                                                                                               // reason
        } else {
            if (intent != null) {
                this.isQuickSync = intent.getBooleanExtra("quick_sync", false);
                this.isAllSemestersSync = intent.getBooleanExtra("all_semesters", false);
                if (this.isAllSemestersSync) {
                    this.isQuickSync = true;
                }
            }
            this.pageState = null;
            this.syncMetrics = new com.salmanmalvasi.studentcc.sync.SyncMetrics();
            this.startForeground(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, this.notification.build());
            android.util.Log.i("VTOPService",
                    "VTOPService started foreground sync execution. isAllSemestersSync=" + isAllSemestersSync);

            this.counter = 0;
            this.maxProgress = 14;

            SharedPreferences encryptedSharedPreferences = SettingsRepository
                    .getEncryptedSharedPreferences(getApplicationContext());

            if (encryptedSharedPreferences == null) {
                error(102, "Failed to fetch credentials.");
            } else {
                this.username = encryptedSharedPreferences.getString("username", null);
                this.password = encryptedSharedPreferences.getString("password", null);

                if (this.semesters == null || this.semesters.isEmpty()) {
                    String cachedSemMap = this.sharedPreferences.getString("semestersMapJson", null);
                    if (cachedSemMap != null) {
                        try {
                            JSONObject obj = new JSONObject(cachedSemMap);
                            this.semesters = new HashMap<>();
                            Iterator<String> keys = obj.keys();
                            while (keys.hasNext()) {
                                String k = keys.next();
                                this.semesters.put(k, obj.getString(k));
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }

                boolean hasExistingSession = android.webkit.CookieManager.getInstance().hasCookies();
                boolean isPageAlreadyLoaded = this.webView != null && this.webView.getUrl() != null
                        && this.webView.getUrl().contains("/vtop/");
                if (this.isAllSemestersSync && isPageAlreadyLoaded && this.semesters != null
                        && !this.semesters.isEmpty() && hasExistingSession) {
                    android.util.Log.i("VTOPService",
                            "All-semesters sync with existing session & semesters; launching pipeline directly.");
                    startAllSemestersSyncPipeline();
                } else if (hasExistingSession) {
                    android.util.Log.i("VTOPService",
                            "Existing session cookies detected. Resuming session via /content before all-semesters sync.");
                    this.reloadPage("/content", false);
                } else {
                    android.util.Log.i("VTOPService",
                            "No existing session cookies. Initiating authentication flow via /login");
                    this.reloadPage("/login", false);
                }
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        this.compositeDisposable.dispose();
        super.onDestroy();
    }

    /**
     * Function to create a fresh WebView
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        try {
            String authorisedUserAgent = this.sharedPreferences.getString("authorisedUserAgent", null);
            ContextThemeWrapper webViewContext = new ContextThemeWrapper(this, R.style.Theme_VTOP);

            this.webView = new WebView(webViewContext);
            this.webView.addJavascriptInterface(this, "Android");
            this.webView.getSettings().setJavaScriptEnabled(true);
            // Captcha is an image; network images MUST be enabled to download login captcha
            this.webView.getSettings().setLoadsImagesAutomatically(true);
            this.webView.getSettings().setBlockNetworkImage(false);
            this.webView.getSettings().setCacheMode(android.webkit.WebSettings.LOAD_DEFAULT);
            if (authorisedUserAgent != null && !authorisedUserAgent.isEmpty()) {
                this.webView.getSettings().setUserAgentString(authorisedUserAgent);
            }
            this.webView.setBackgroundColor(Color.TRANSPARENT);
            this.webView.setHorizontalScrollBarEnabled(false);
            this.webView.setVerticalScrollBarEnabled(false);
            this.webView.onResume();
            this.webView.resumeTimers();
            this.isWebViewDestroyed = false;
        } catch (Resources.NotFoundException exception) {
            android.util.Log.e("VTOPService", "Failed to construct themed WebView", exception);
            this.isWebViewDestroyed = true;
            stopSelf();
            return;
        }
        this.webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request != null && request.isForMainFrame()) {
                    android.util.Log.w("VTOPService",
                            "WebView main frame error: " + (error != null ? error.getDescription() : "unknown"));
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                android.util.Log.d("VTOPService", "WebView onPageFinished: " + url);
                /*
                 * JSON response format
                 * {
                 * "page_type": "LANDING"|"HOME"|"LOGIN"
                 * }
                 */
                view.evaluateJavascript("(function() {" +
                        "const response = {" +
                        "   page_type: 'LANDING'" +
                        "};" +
                        "if (typeof $ !== 'undefined') {" +
                        "   if ($('input[id=\"authorizedIDX\"]').length === 1) {" +
                        "       response.page_type = 'HOME';" +
                        "   } else if ($('form[id=\"vtopLoginForm\"]').length === 1) {" +
                        "       response.page_type = 'LOGIN';" +
                        "   }" +
                        "} else {" +
                        "   if (document.getElementById('authorizedIDX')) {" +
                        "       response.page_type = 'HOME';" +
                        "   } else if (document.getElementById('vtopLoginForm')) {" +
                        "       response.page_type = 'LOGIN';" +
                        "   }" +
                        "}" +
                        "return response;" +
                        "})();", responseString -> {
                            try {
                                android.util.Log.d("VTOPService", "Page evaluation result: " + responseString);
                                JSONObject response = new JSONObject(responseString);
                                String pageType = response.getString("page_type");
                                android.util.Log.d("VTOPService",
                                        "Detected page type: " + pageType + " (counter=" + counter + ")");

                                switch (pageType) {
                                    case "LANDING":
                                        if (pageState == PageState.LANDING) {
                                            break;
                                        }
                                        if (counter >= 10) {
                                            error(101, "Couldn't connect to the server.");
                                            endService(true);
                                            return;
                                        }

                                        pageState = PageState.LANDING;
                                        openSignIn();
                                        ++counter;
                                        break;
                                    case "LOGIN":
                                        if (pageState == PageState.LOGIN) {
                                            break;
                                        }

                                        getCaptchaType();
                                        pageState = PageState.LOGIN;
                                        break;
                                    case "HOME":
                                        if (pageState == PageState.HOME) {
                                            break;
                                        }
                                        pageState = PageState.HOME;
                                        getSemesters();
                                        break;
                                    default:
                                        throw new Error("Unknown page exception.");
                                }
                            } catch (Exception e) {
                                android.util.Log.w("VTOPService", "Error during page evaluation: " + e.getMessage());
                            }
                        });
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
                android.util.Log.w("VTOPService",
                        "SSL certificate validation notice: " + (error != null ? error.getPrimaryError() : "unknown"));
                try {
                    String url = view != null ? view.getUrl() : null;
                    android.net.Uri uri = url != null ? android.net.Uri.parse(url) : null;
                    String host = uri != null ? uri.getHost() : null;
                    android.net.Uri base = android.net.Uri.parse(SettingsRepository.VTOP_BASE_URL);
                    String allowedHost = base != null ? base.getHost() : null;
                    if (host != null && (host.equalsIgnoreCase(allowedHost) || host.endsWith("vit.ac.in"))) {
                        // VTOP's server omits the intermediate CA from its TLS handshake; proceed only
                        // for verified VIT hosts
                        handler.proceed();
                        return;
                    }
                } catch (Exception ignored) {
                }
                handler.cancel();
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                android.util.Log.e("VTOPService", "WebView renderer process crashed, marking as destroyed");
                isWebViewDestroyed = true;
                if (webView != null) {
                    try {
                        ((ViewGroup) webView.getParent()).removeView(webView);
                    } catch (Exception ignored) {
                    }
                    webView.destroy();
                    webView = null;
                }
                return true; // Return true to indicate we handled the crash
            }
        });
    }

    /**
     * VTOP randomly blocks user agents (I'm guessing to prevent people from using
     * this app).
     * If a user agent is blocked, a new authorised one is fetched from my server
     * and stored in shared preferences.
     */
    private void updateUserAgent() {
        SettingsRepository.fetchAboutJson(false)
                .subscribe(new Observer<JSONObject>() {
                    @Override
                    public void onSubscribe(@NonNull Disposable d) {
                        compositeDisposable.add(d);
                    }

                    @Override
                    public void onNext(@NonNull JSONObject about) {
                        try {
                            String authorisedUserAgent = about.getString("authorisedUserAgent");
                            sharedPreferences.edit().putString("authorisedUserAgent", authorisedUserAgent).apply();
                            webView.getSettings().setUserAgentString(authorisedUserAgent);
                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        reloadPage("/login", true);
                    }
                });
    }

    /**
     * Function to brutally destroy the WebView
     */
    private void destroyWebView() {
        if (this.webView == null) {
            this.isWebViewDestroyed = true;
            return;
        }

        if (this.isWebViewDestroyed) {
            return;
        }

        try {
            this.webView.onPause();
            // Note: DO NOT call pauseTimers() here. pauseTimers() is a global process-wide
            // static call
            // in Android WebKit that freezes JavaScript and layout timers for ALL WebViews
            // in this process!
            this.webView.destroyDrawingCache();
            this.webView.removeAllViews();

            ViewGroup webViewParent = (ViewGroup) this.webView.getParent();
            if (webViewParent != null) {
                webViewParent.removeView(this.webView);
            }

            this.webView.destroy();
        } catch (Exception exception) {
            android.util.Log.w("VTOPService", "Failed while destroying WebView", exception);
        } finally {
            this.webView = null;
            this.isWebViewDestroyed = true;
        }
    }

    /**
     * Function to terminate the download
     *
     * @param force If force is true, it will end the service no matter what
     */
    public void endService(boolean force) {
        if (!force && this.progress != null && this.progress != -1) {
            return;
        }

        this.destroyWebView();
        stopSelf();
        stopForeground(true);

        try {
            this.callback.onServiceEnd();
        } catch (Exception ignored) {
        }
    }

    /**
     * Function to reload the page after clearing all cache and history.
     */
    private void reloadPage(String path, boolean destroySession) {
        if (this.webView == null || this.isWebViewDestroyed) {
            this.createWebView();

            if (this.webView == null) {
                android.util.Log.e("VTOPService", "WebView unavailable after recreation; aborting reload");
                return;
            }
        }

        if (this.webView != null) {
            this.webView.onResume();
            this.webView.resumeTimers();
        }

        this.pageState = null;
        this.progress = -1;

        android.util.Log.d("VTOPService", "reloadPage: loading " + path + " (destroySession=" + destroySession + ")");

        this.notification.setContentTitle(getString(R.string.server_connect));
        this.notification.setContentText(null);
        this.notification.setProgress(0, 0, true);
        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, this.notification.build());

        if (destroySession) {
            CookieManager.getInstance().removeAllCookies(null);
            this.webView.clearCache(true);
            this.webView.clearHistory();
        }

        this.webView.loadUrl(SettingsRepository.VTOP_BASE_URL + path);
    }

    /**
     * Function to update the download progress in the notification
     *
     * @param currentDownload The string ID for the current download
     */
    private void updateProgress(Integer currentDownload) {
        this.errorRetryCount = 0; // Reset retry count on successful progress
        this.notification.setProgress(maxProgress, ++progress, false);
        this.notification.setContentText(progress + " / " + maxProgress);

        if (currentDownload != null) {
            this.notification.setContentTitle(getString(currentDownload));
        }

        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());
    }

    private void updateProgressTitle(String customTitle) {
        this.errorRetryCount = 0;
        this.notification.setProgress(maxProgress, ++progress, false);
        this.notification.setContentText(progress + " / " + maxProgress);

        if (customTitle != null) {
            this.notification.setContentTitle(customTitle);
        }

        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());
    }

    /**
     * Function to handle errors.
     */
    private void error(final int errorCode, final String errorMessage) {
        errorRetryCount++;
        String userFriendlyMsg = errorMessage;
        if (errorMessage != null && (errorMessage.toLowerCase().contains("max concurrent") || errorMessage.contains("500"))) {
            userFriendlyMsg = "VTOP server congested (max concurrent user limit reached on VIT's server). Please retry in a few minutes.";
        }
        android.util.Log.e("VTOPService", "Error " + errorCode + ": " + errorMessage
                + " (retry " + errorRetryCount + "/" + MAX_ERROR_RETRIES + ")");

        if (errorRetryCount >= MAX_ERROR_RETRIES) {
            Toast.makeText(getApplicationContext(),
                    "Sync failed after " + MAX_ERROR_RETRIES + " attempts. " + (errorCode == 901 ? "Exam schedule congested: " : "Error " + errorCode + ". ") + userFriendlyMsg,
                    Toast.LENGTH_LONG).show();
            endService(true);
        } else {
            Toast.makeText(getApplicationContext(),
                    (errorCode == 901 ? "Exam schedule busy. " : "Error " + errorCode + ". ") + userFriendlyMsg + " Retrying...",
                    Toast.LENGTH_SHORT).show();
            this.reloadPage("/login", true);
        }
    }

    /**
     * Function to open the login page.
     */
    private void openSignIn() {
        /*
         * JSON response format
         * {
         * "success": true|false
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "const response = {" +
                "    success: false" +
                "};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url: '/vtop/prelogin/setup'," +
                "    data: $('#stdForm').serialize()," +
                "    async: false," +
                "    success: function(res) {" +
                "        response.success = true;" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        this.pageState = null;
                        this.reloadPage("/login", false);
                    } catch (Exception e) {
                        error(103, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to get the type of captcha (Default Captcha / Google reCaptcha).
     */
    private void getCaptchaType() {
        /*
         * JSON response format
         *
         * {
         * "captcha_type": "DEFAULT"|"GRECAPTCHA"
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "const response = {" +
                "    captcha_type: 'DEFAULT'" +
                "};" +
                "if ($('input[id=\"gResponse\"]').length === 1) {" +
                "   response.captcha_type = 'GRECAPTCHA';" +
                "}" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        this.notification.setContentTitle(getString(R.string.captcha_wait));
                        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD,
                                notification.build());

                        if (response.getString("captcha_type").equals("DEFAULT")) {
                            getCaptcha();
                        } else {
                            executeCaptcha();
                        }
                    } catch (Exception e) {
                        error(104, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * For Default Captcha
     * Function to get the captcha from the portal's sign in page and load it into
     * the ImageView.
     */
    private void getCaptcha() {
        this.webView.evaluateJavascript("(function() {" +
                "var img = document.querySelector('#captchaBlock img') || document.querySelector('img[src*=\"captcha\"]');"
                +
                "if (!img) return { captcha: null };" +
                "try {" +
                "    var canvas = document.createElement('canvas');" +
                "    canvas.width = img.naturalWidth || img.width || 200;" +
                "    canvas.height = img.naturalHeight || img.height || 40;" +
                "    var ctx = canvas.getContext('2d');" +
                "    ctx.drawImage(img, 0, 0);" +
                "    return { captcha: canvas.toDataURL('image/png') };" +
                "} catch (e) {" +
                "    return { captcha: img.src };" +
                "}" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        String captchaData = response.optString("captcha", null);
                        if (captchaData == null || captchaData.isEmpty() || captchaData.equals("null")) {
                            // Captcha element not ready yet, retry in 300ms
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::getCaptcha,
                                    300);
                            return;
                        }

                        Bitmap decodedImage = null;
                        if (captchaData.contains(",")) {
                            String base64Captcha = captchaData.split(",")[1];
                            byte[] decodedString = Base64.decode(base64Captcha, Base64.DEFAULT);
                            decodedImage = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        }

                        if (decodedImage != null) {
                            com.salmanmalvasi.studentcc.helpers.NeuralCaptchaSolver neuralSolver = new com.salmanmalvasi.studentcc.helpers.NeuralCaptchaSolver(
                                    getApplicationContext());
                            String solved = neuralSolver.solveCaptcha(decodedImage);
                            if (solved != null && solved.length() == 6 && solved.matches("^[A-Za-z0-9]{6}$")) {
                                android.util.Log.d("VTOPService", "Fast Neural CAPTCHA solved: " + solved);
                                signIn(solved);
                                return;
                            }

                            processCaptchaWithBasicOCR(decodedImage);
                        } else {
                            error(105, "Unable to decode captcha image");
                        }
                    } catch (Exception e) {
                        error(105, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Fallback basic OCR processing with multiple preprocessing attempts
     */
    private void processCaptchaWithBasicOCR(Bitmap originalImage) {
        // First attempt: Original image
        processImageWithOCR(originalImage, 0, () -> {
            // Second attempt: Enhanced contrast
            Bitmap enhancedImage = enhanceImageContrast(originalImage);
            processImageWithOCR(enhancedImage, 1, () -> {
                // Third attempt: Grayscale with noise reduction
                Bitmap grayscaleImage = convertToGrayscale(originalImage);
                processImageWithOCR(grayscaleImage, 2, () -> {
                    // Fourth attempt: Inverted colors
                    Bitmap invertedImage = invertImageColors(originalImage);
                    processImageWithOCR(invertedImage, 3, () -> {
                        // Final fallback: show captcha to user
                        try {
                            this.callback.onRequestCaptcha(CAPTCHA_DEFAULT, originalImage, null);
                        } catch (Exception ignored) {
                            this.endService(true);
                        }
                    });
                });
            });
        });
    }

    /**
     * Process image with OCR and handle result
     */
    private void processImageWithOCR(Bitmap image, int attemptNumber, Runnable onFailure) {
        InputImage inputImage = InputImage.fromBitmap(image, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(inputImage)
                .addOnSuccessListener(visionText -> {
                    String ocrText = cleanOCRText(visionText.getText());
                    if (isValidCaptchaText(ocrText)) {
                        // Auto-submit the recognized captcha
                        signIn(ocrText);
                    } else {
                        // Try next preprocessing method
                        onFailure.run();
                    }
                })
                .addOnFailureListener(e -> {
                    // Try next preprocessing method
                    onFailure.run();
                });
    }

    /**
     * Clean OCR text by removing unwanted characters
     */
    private String cleanOCRText(String rawText) {
        return rawText.replaceAll("[^A-Za-z0-9]", "").trim();
    }

    /**
     * Validate if the OCR text looks like a valid captcha
     * Enhanced validation with additional checks
     */
    private boolean isValidCaptchaText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // Clean the text
        text = text.trim().toUpperCase();

        // Basic length check - VTOP captchas are typically 4-6 characters
        if (text.length() < 3 || text.length() > 8) {
            return false;
        }

        // Check if contains only alphanumeric characters
        if (!text.matches("[A-Z0-9]+")) {
            return false;
        }

        // Additional heuristic checks
        // Reject if too many repeated characters (likely OCR error)
        if (hasExcessiveRepeatedChars(text)) {
            return false;
        }

        // Reject common OCR misreads
        if (isLikelyOCRError(text)) {
            return false;
        }

        return true;
    }

    /**
     * Check if text has excessive repeated characters
     */
    private boolean hasExcessiveRepeatedChars(String text) {
        if (text.length() <= 2)
            return false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int count = 0;
            for (int j = 0; j < text.length(); j++) {
                if (text.charAt(j) == c) {
                    count++;
                }
            }
            // If more than half the characters are the same, likely an error
            if (count > text.length() / 2) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if text looks like a common OCR error
     */
    private boolean isLikelyOCRError(String text) {
        // Common OCR error patterns
        String[] errorPatterns = {
                "1111", "OOOO", "0000", "LLLL", "||||", "IIII",
                "......", ",,,,", "'''", "\"\"\"", "```"
        };

        for (String pattern : errorPatterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }

        // Check for unlikely character combinations
        if (text.matches(".*[O0]{3,}.*") || text.matches(".*[1I]{3,}.*")) {
            return true;
        }

        return false;
    }

    /**
     * Enhance image contrast for better OCR
     */
    private Bitmap enhanceImageContrast(Bitmap original) {
        try {
            Bitmap result = original.copy(original.getConfig(), true);
            int width = result.getWidth();
            int height = result.getHeight();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = result.getPixel(x, y);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    // Enhance contrast
                    r = Math.max(0, Math.min(255, (r - 128) * 2 + 128));
                    g = Math.max(0, Math.min(255, (g - 128) * 2 + 128));
                    b = Math.max(0, Math.min(255, (b - 128) * 2 + 128));

                    result.setPixel(x, y, Color.rgb(r, g, b));
                }
            }
            return result;
        } catch (Exception e) {
            return original;
        }
    }

    /**
     * Convert image to grayscale for better text recognition
     */
    private Bitmap convertToGrayscale(Bitmap original) {
        try {
            Bitmap result = original.copy(original.getConfig(), true);
            int width = result.getWidth();
            int height = result.getHeight();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = result.getPixel(x, y);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    // Convert to grayscale
                    int gray = (r + g + b) / 3;
                    result.setPixel(x, y, Color.rgb(gray, gray, gray));
                }
            }
            return result;
        } catch (Exception e) {
            return original;
        }
    }

    /**
     * Invert image colors for better text recognition
     */
    private Bitmap invertImageColors(Bitmap original) {
        try {
            Bitmap result = original.copy(original.getConfig(), true);
            int width = result.getWidth();
            int height = result.getHeight();

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int pixel = result.getPixel(x, y);
                    int r = Color.red(pixel);
                    int g = Color.green(pixel);
                    int b = Color.blue(pixel);

                    // Invert colors
                    r = 255 - r;
                    g = 255 - g;
                    b = 255 - b;

                    result.setPixel(x, y, Color.rgb(r, g, b));
                }
            }
            return result;
        } catch (Exception e) {
            return original;
        }
    }

    /**
     * For Google reCaptcha
     * Function to override the default onSubmit function and execute the captcha.
     */
    private void executeCaptcha() {
        try {
            this.callback.onRequestCaptcha(CAPTCHA_GRECATPCHA, null, this.webView);
        } catch (Exception ignored) {
            this.endService(true);
            return;
        }

        /*
         * Overriding the existing onSubmit function and attempting to render the
         * reCaptcha
         */
        webView.evaluateJavascript("function callBuiltValidation(token) {" +
                "    Android.signIn(token);" +
                "}" +
                "(function() {" +
                "var executeInterval = setInterval(function() {" +
                "    try {" + // typeof grecaptcha != 'undefined' always returns true for some reason
                "        grecaptcha.execute();" +
                "        clearInterval(executeInterval);" +
                "    } catch (err) {" +
                "    }" +
                "}, 500);" +
                "})();", value -> {
                });
    }

    /**
     * Function to sign in to the portal
     */
    @JavascriptInterface
    public void signIn(final String captcha) {
        this.notification.setContentTitle(getString(R.string.sign_in_attempt));
        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, notification.build());

        new Handler(getApplicationContext().getMainLooper())
                .post(() -> {
                    try {
                        callback.onCaptchaComplete();
                    } catch (Exception ignored) {
                    }

                    ViewGroup webViewParent = (ViewGroup) webView.getParent();
                    if (webViewParent != null) {
                        webViewParent.removeView(webView);
                    }

                    /*
                     * JSON response format
                     * {
                     * "authorized": true|false,
                     * "error_message": null,
                     * "error_code": 0
                     * }
                     */
                    webView.evaluateJavascript("(function() {" +
                            "if (typeof captchaInterval != 'undefined') clearInterval(captchaInterval);" +
                            "if (typeof executeInterval != 'undefined') clearInterval(executeInterval);" +
                            "$('#vtopLoginForm [name=\"username\"]').val('" + username.replaceAll("'", "\\\\'") + "');"
                            +
                            "$('#vtopLoginForm [name=\"password\"]').val('" + password.replaceAll("'", "\\\\'") + "');"
                            +
                            "$('#vtopLoginForm [name=\"captchaStr\"]').val('" + captcha.replaceAll("'", "\\\\'") + "');"
                            +
                            "$('#vtopLoginForm [name=\"gResponse\"]').val('" + captcha.replaceAll("'", "\\\\'") + "');"
                            +
                            "var response = {" +
                            "    authorised: false," +
                            "    error_message: null," +
                            "    error_code: 0" +
                            "};" +
                            "$.ajax({" +
                            "    type : 'POST'," +
                            "    url : '/vtop/login'," +
                            "    data : $('#vtopLoginForm').serialize()," +
                            "    async: false," +
                            "    success : function(res) {" +
                            "        if (!res) return;" +
                            "        if (res.search('___INTERNAL___RESPONSE___') !== -1 || res.includes('authorizedIDX') || res.includes('/content')) {"
                            +
                            "            response.authorised = true;" +
                            "            return;" +
                            "        }" +
                            "        $('#page_outline').html(res);" +
                            "        if (res.includes('authorizedIDX') || $('#authorizedIDX').length === 1) {" +
                            "            response.authorised = true;" +
                            "            return;" +
                            "        }" +
                            "        var pageContent = res.toLowerCase();" +
                            "        var invalidCaptchaRegex = new RegExp(/invalid\\s*captcha/);" +
                            "        var invalidCredentialsRegex = new RegExp(/invalid\\s*(user\\s*name|login\\s*id|user\\s*id)\\s*\\/\\s*password/);"
                            +
                            "        var accountLockedRegex = new RegExp(/account\\s*is\\s*locked/);" +
                            "        var maxFailAttemptsRegex = new RegExp(/maximum\\s*fail\\s*attempts\\s*reached/);"
                            +
                            "        if (invalidCaptchaRegex.test(pageContent)) {" +
                            "            response.error_message = 'Invalid Captcha';" +
                            "            response.error_code = 1;" +
                            "        } else if(invalidCredentialsRegex.test(pageContent)) {" +
                            "            response.error_message = 'Invalid Username / Password';" +
                            "            response.error_code = 2;" +
                            "        } else if(accountLockedRegex.test(pageContent)) {" +
                            "            response.error_message = 'Your Account is Locked';" +
                            "            response.error_code = 3;" +
                            "        } else if(maxFailAttemptsRegex.test(pageContent)) {" +
                            "            response.error_message = 'Maximum login attempts reached, open VTOP in your browser to reset your password';"
                            +
                            "            response.error_code = 4;" +
                            "        } else {" +
                            "            response.error_message = 'Sign-in verification needed';" +
                            "            response.error_code = 1;" +
                            "        }" +
                            "    }," +
                            "    error : function() {" +
                            "        response.error_message = 'Network error during sign-in';" +
                            "        response.error_code = 1;" +
                            "    }" +
                            "});" +
                            "return response;" +
                            "})();", responseString -> {
                                try {
                                    JSONObject response = new JSONObject(responseString);
                                    boolean isAuthorised = response.optBoolean("authorised", false);

                                    if (!isAuthorised) {
                                        String cookies = com.salmanmalvasi.studentcc.helpers.FastScraper
                                                .getVtopCookies();
                                        if (cookies != null && cookies.contains("JSESSIONID")) {
                                            android.util.Log.i("VTOPService",
                                                    "Session cookies verified after login ajax; proceeding as authorised.");
                                            isAuthorised = true;
                                        }
                                    }

                                    if (isAuthorised) {
                                        this.reloadPage("/content", false);
                                    } else {
                                        String errorMessage = response.optString("error_message",
                                                "Sign-in could not be completed. Retrying...");
                                        if (errorMessage == null || errorMessage.trim().isEmpty()
                                                || "null".equalsIgnoreCase(errorMessage)) {
                                            errorMessage = "Sign-in could not be completed. Retrying...";
                                        }
                                        int errorCode = response.optInt("error_code", 1);
                                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_SHORT)
                                                .show();

                                        if (errorCode == 1) {
                                            this.reloadPage("/login", false);
                                        } else {
                                            if (errorCode == 2) {
                                                try {
                                                    this.callback.onForceSignOut();
                                                } catch (Exception ignored) {
                                                }
                                            }

                                            this.endService(true);
                                        }
                                    }
                                } catch (Exception e) {
                                    error(106, e.getLocalizedMessage());
                                }
                            });
                });
    }

    /**
     * Function to get a list of the semesters. These semesters are obtained from
     * the Timetable page.
     */
    private void getSemesters() {
        /*
         * JSON response format
         *
         * {
         * "semesters": [
         * {
         * "name": "Fall Semester 2020-21",
         * "id": "CH2020211"
         * },
         * {
         * "name": "Winter Semester 2020-21",
         * "id": "CH2020215"
         * },
         * ...
         * ]
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'academics/common/StudentTimeTableChn'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        if (res.toLowerCase().includes('not authorized')) {" +
                "            response.error_code = 1;" +
                "            response.error_message = 'Unauthorised user agent';" +
                "        } else if (res.toLowerCase().includes('time table')) {" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var options = doc.getElementById('semesterSubId').getElementsByTagName('option');" +
                "            var semesters = [];" +
                "            for(var i = 0; i < options.length; ++i) {" +
                "                if(!options[i].value) {" +
                "                    continue;" +
                "                }" +
                "                var semester = {" +
                "                    name: options[i].innerText," +
                "                    id: options[i].value" +
                "                };" +
                "                semesters.push(semester);" +
                "            }" +
                "            response.semesters = semesters;" +
                "        }" +
                "        response.csrf = $('input[name=\"_csrf\"]').val() || '';" +
                "        response.authorizedID = $('#authorizedIDX').val() || '';" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);

                        if (response.has("error_code")) {
                            if (response.getInt("error_code") == 1) {
                                Toast.makeText(getApplicationContext(), "Error " + 107
                                        + ". Unauthorised user agent, attempting to update. Report a bug if this issue prevails.",
                                        Toast.LENGTH_SHORT).show();
                                updateUserAgent();
                                return;
                            }
                        }

                        JSONArray semesterArray = response.getJSONArray("semesters");
                        this.semesters = new HashMap<>();

                        // Store available semesters for later use
                        StringBuilder availableSemesters = new StringBuilder();

                        for (int i = 0; i < semesterArray.length(); ++i) {
                            JSONObject semesterObject = semesterArray.getJSONObject(i);
                            String semesterName = semesterObject.getString("name");
                            this.semesters.put(semesterName, semesterObject.getString("id"));

                            if (i > 0)
                                availableSemesters.append(",");
                            availableSemesters.append(semesterName);
                        }

                        // Save available semesters to SharedPreferences
                        this.sharedPreferences.edit().putString("availableSemesters", availableSemesters.toString())
                                .apply();

                        try {
                            JSONObject semMapObj = new JSONObject();
                            for (Map.Entry<String, String> entry : this.semesters.entrySet()) {
                                semMapObj.put(entry.getKey(), entry.getValue());
                            }
                            this.sharedPreferences.edit().putString("semestersMapJson", semMapObj.toString()).apply();
                        } catch (Exception ignored) {
                        }

                        if (response.has("csrf") && !response.isNull("csrf") && !response.getString("csrf").isEmpty()) {
                            this.sharedPreferences.edit().putString("last_csrf", response.getString("csrf")).apply();
                        }
                        if (response.has("authorizedID") && !response.isNull("authorizedID")
                                && !response.getString("authorizedID").isEmpty()) {
                            this.sharedPreferences.edit().putString("username", response.getString("authorizedID"))
                                    .apply();
                        }

                        if (this.isAllSemestersSync) {
                            startAllSemestersSyncPipeline();
                            return;
                        }

                        // Check if user has a preferred semester, otherwise use first one
                        String preferredSemester = this.sharedPreferences.getString("semester", "");
                        if (!preferredSemester.isEmpty() && this.semesters.containsKey(preferredSemester)) {
                            setSemester(preferredSemester);
                        } else if (semesterArray.length() > 0) {
                            // Automatically select the current semester (assume first in the list is
                            // current)
                            JSONObject currentSemester = semesterArray.getJSONObject(0);
                            setSemester(currentSemester.getString("name"));
                        } else {
                            this.endService(true);
                        }
                    } catch (Exception e) {
                        error(201, e.getLocalizedMessage());
                    }
                });
    }

    private void startAllSemestersSyncPipeline() {
        android.util.Log.i("VTOPService", "Starting All-Semesters Quick Sync pipeline across all available semesters.");

        com.salmanmalvasi.studentcc.helpers.MultiSemesterRepository repo = com.salmanmalvasi.studentcc.helpers.MultiSemesterRepository
                .getInstance(getApplicationContext());

        List<String> targetSemesters = new ArrayList<>();
        if (this.semesters != null && !this.semesters.isEmpty()) {
            int admissionYear = repo.getStudentAdmissionYear(this.username);
            for (String semName : this.semesters.keySet()) {
                if (admissionYear > 0) {
                    int semYear = repo.extractSemesterStartYear(semName);
                    if (semYear > 0 && semYear < admissionYear) {
                        continue;
                    }
                }
                targetSemesters.add(semName);
            }
        }

        if (targetSemesters.isEmpty()) {
            List<String> eligible = repo.getEligibleSemesterNames(this.username);
            if (eligible != null)
                targetSemesters.addAll(eligible);
        }

        if (targetSemesters.isEmpty() && this.semesters != null) {
            targetSemesters.addAll(this.semesters.keySet());
        }

        if (targetSemesters.isEmpty()) {
            android.util.Log.w("VTOPService", "No available semesters found for All-Semesters Quick Sync.");
            finishUp();
            return;
        }

        // Sort descending so current/recent semesters sync first
        java.util.Collections.sort(targetSemesters, (s1, s2) -> {
            int y1 = repo.extractSemesterStartYear(s1);
            int y2 = repo.extractSemesterStartYear(s2);
            if (y1 != y2)
                return Integer.compare(y2, y1);
            return s2.compareTo(s1);
        });

        this.maxProgress = targetSemesters.size();
        this.progress = 0;

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String effectiveUser = (this.username != null && !this.username.isEmpty())
                        ? this.username.trim()
                        : this.sharedPreferences.getString("username", "");
                String lastCsrf = this.sharedPreferences.getString("last_csrf", "");
                String cookies = com.salmanmalvasi.studentcc.helpers.FastScraper.getVtopCookies();
                String userAgent = this.sharedPreferences.getString("authorisedUserAgent",
                        this.sharedPreferences.getString("userAgent",
                                com.salmanmalvasi.studentcc.helpers.FastScraper.DEFAULT_USER_AGENT));

                for (int i = 0; i < targetSemesters.size(); i++) {
                    final int idx = i;
                    final String semName = targetSemesters.get(i);
                    final String semId = (this.semesters != null) ? this.semesters.get(semName) : null;
                    if (semId == null || semId.isEmpty())
                        continue;

                    final int currentIdx = i + 1;
                    final int totalSems = targetSemesters.size();
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        updateProgressTitle("Syncing " + currentIdx + " of " + totalSems + ": " + semName);
                    });

                    // Broadcast progress to AllSemestersAnalyticsActivity
                    try {
                        Intent progressIntent = new Intent("com.salmanmalvasi.studentcc.ALL_SEMESTERS_SYNC_PROGRESS");
                        progressIntent.setPackage(getPackageName());
                        progressIntent.putExtra("current", currentIdx);
                        progressIntent.putExtra("total", totalSems);
                        progressIntent.putExtra("semName", semName);
                        sendBroadcast(progressIntent);
                    } catch (Exception ignored) {
                    }

                    // 1. Fetch official grades and GPA (doStudentGradeView)
                    SemesterSummary summary = null;
                    if (!lastCsrf.isEmpty() && cookies != null && !cookies.isEmpty()) {
                        try {
                            summary = com.salmanmalvasi.studentcc.helpers.FastScraper.fetchSemesterGrades(
                                    lastCsrf, effectiveUser, semId, semName, cookies, userAgent);
                        } catch (Exception e) {
                            android.util.Log.w("VTOPService",
                                    "Grade view error for " + semName + ": " + e.getMessage());
                        }
                    }

                    if (summary == null) {
                        summary = new SemesterSummary(semId, semName);
                    }

                    // 2. Fetch Attendance (processViewStudentAttendance)
                    String attHtml = "";
                    String marksHtml = "";

                    if (!lastCsrf.isEmpty() && cookies != null && !cookies.isEmpty()) {
                        try {
                            String postData = "_csrf=" + java.net.URLEncoder.encode(lastCsrf, "UTF-8") +
                                    "&authorizedID=" + java.net.URLEncoder.encode(effectiveUser, "UTF-8") +
                                    "&semesterSubId=" + java.net.URLEncoder.encode(semId, "UTF-8") +
                                    "&x=" + System.currentTimeMillis();
                            String fetched = com.salmanmalvasi.studentcc.helpers.FastScraper
                                    .post("processViewStudentAttendance", postData, cookies, userAgent);
                            if (fetched != null && fetched.length() > 50
                                    && !com.salmanmalvasi.studentcc.helpers.FastScraper.isSessionExpired(fetched)) {
                                attHtml = fetched;
                            }
                        } catch (Exception e) {
                            android.util.Log.w("VTOPService",
                                    "Attendance fetch error for " + semName + ": " + e.getMessage());
                        }

                        // 3. Fetch Marks (examinations/doStudentMarkView)
                        try {
                            String postData = "semesterSubId=" + java.net.URLEncoder.encode(semId, "UTF-8") +
                                    "&authorizedID=" + java.net.URLEncoder.encode(effectiveUser, "UTF-8") +
                                    "&_csrf=" + java.net.URLEncoder.encode(lastCsrf, "UTF-8");
                            String fetched = com.salmanmalvasi.studentcc.helpers.FastScraper
                                    .post("examinations/doStudentMarkView", postData, cookies, userAgent);
                            if (fetched != null && fetched.length() > 50
                                    && !com.salmanmalvasi.studentcc.helpers.FastScraper.isSessionExpired(fetched)) {
                                marksHtml = fetched;
                            }
                        } catch (Exception e) {
                            android.util.Log.w("VTOPService",
                                    "Marks fetch error for " + semName + ": " + e.getMessage());
                        }
                    }

                    if (!attHtml.isEmpty()) {
                        com.salmanmalvasi.studentcc.helpers.FastScraper.parseAttendanceIntoSummary(attHtml, summary);
                    }
                    if (!marksHtml.isEmpty()) {
                        com.salmanmalvasi.studentcc.helpers.FastScraper.parseMarksIntoSummary(marksHtml, summary);
                    }

                    if (summary.totalClasses > 0 || (summary.subjects != null && !summary.subjects.isEmpty())) {
                        repo.upsertSemesterSummary(summary);
                        android.util.Log.i("VTOPService",
                                "Saved analytics for " + semName + ": " + summary.totalClasses + " classes, "
                                        + (summary.subjects != null ? summary.subjects.size() : 0) + " subjects");
                    }

                    String activeSem = this.sharedPreferences.getString("semester", "");
                    if (activeSem != null && activeSem.equalsIgnoreCase(semName) && summary.totalClasses > 0) {
                        this.sharedPreferences.edit()
                                .putFloat("overallAttendance", summary.attendancePercentage)
                                .putInt("attendedClasses", summary.attendedClasses)
                                .putInt("totalClasses", summary.totalClasses)
                                .apply();
                    }

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                }

                repo.computeAnalytics();
            } catch (Exception e) {
                android.util.Log.e("VTOPService", "Error during all semesters sync: " + e.getMessage(), e);
            } finally {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(this::finishUp);
            }
        });
    }

    /**
     * Function to set the semester ID based on the semester selected.
     */
    public void setSemester(String semester) {
        this.semesterID = this.semesters.get(semester);
        android.util.Log.i("VTOPService", "Starting legacy WebView sync pipeline. isQuickSync=" + isQuickSync);
        if (isQuickSync) {
            this.maxProgress = 3;
            loadCoursesAndSlotsFromDatabase(() -> {
                downloadAttendance();
            });
        } else {
            getName();
        }
    }

    /**
     * Loads existing courses and slots from Room DB into in-memory maps for Quick
     * Sync,
     * ensuring that course and slot references are available for attendance and
     * marks mapping.
     */
    private void loadCoursesAndSlotsFromDatabase(Runnable onLoaded) {
        if (this.theorySlots != null && !this.theorySlots.isEmpty()) {
            if (onLoaded != null)
                onLoaded.run();
            return;
        }
        if (this.appDatabase == null) {
            this.appDatabase = AppDatabase.getInstance(getApplicationContext());
        }
        io.reactivex.rxjava3.core.Single.zip(
                this.appDatabase.coursesDao().getAllCourses(),
                this.appDatabase.coursesDao().getAllSlots(),
                (courses, slots) -> {
                    this.theorySlots = new HashMap<>();
                    this.labSlots = new HashMap<>();
                    this.projectSlots = new HashMap<>();

                    this.theoryCourses = new HashMap<>();
                    this.labCourses = new HashMap<>();
                    this.projectCourses = new HashMap<>();

                    Map<Integer, Course> courseById = new HashMap<>();
                    for (Course course : courses) {
                        courseById.put(course.id, course);
                        if ("lab".equalsIgnoreCase(course.type)) {
                            this.labCourses.put(course.id, course);
                        } else if ("project".equalsIgnoreCase(course.type)) {
                            this.projectCourses.put(course.id, course);
                        } else {
                            this.theoryCourses.put(course.id, course);
                        }
                    }

                    for (Slot slot : slots) {
                        Course course = courseById.get(slot.courseId);
                        Map<String, Slot> slotReference;
                        if (course != null && "lab".equalsIgnoreCase(course.type)) {
                            slotReference = this.labSlots;
                        } else if (course != null && "project".equalsIgnoreCase(course.type)) {
                            slotReference = this.projectSlots;
                        } else {
                            slotReference = this.theorySlots;
                        }

                        if (slot.slot != null) {
                            slotReference.put(slot.slot, slot);
                            String canonical = SLOT_ALIASES.get(slot.slot.toUpperCase(Locale.US));
                            if (canonical != null && !slotReference.containsKey(canonical)) {
                                slotReference.put(canonical, slot);
                            }
                        }
                    }
                    return courses.size();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(count -> {
                    android.util.Log.i("VTOPService",
                            "Loaded " + count + " courses and slots from database cache for quick sync.");
                    if (count == 0) {
                        downloadCourses();
                    } else if (onLoaded != null) {
                        onLoaded.run();
                    }
                }, error -> {
                    android.util.Log.e("VTOPService", "Error loading courses from database: " + error.getMessage());
                    downloadCourses();
                });
    }

    /**
     * Function to save the name of the user in SharedPreferences.
     */
    private void getName() {
        updateProgress(R.string.downloading_profile);

        /*
         * JSON response format
         *
         * {
         * "name": "JOHN DOE"
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'studentsRecord/StudentProfileAllView'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        if(res.toLowerCase().includes('personal information')) {" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var cells = doc.getElementsByTagName('td');" +
                "            for(var i = 0; i < cells.length; ++i) {" +
                "                var key = cells[i].innerText.toLowerCase();" +
                "                if(key.includes('student') && key.includes('name')) {" +
                "                    response.name = cells[++i].innerHTML;" +
                "                    break;" +
                "                }" +
                "            }" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        if (response.has("name")) {
                            sharedPreferences.edit().putString("name", response.getString("name")).apply();
                        } else {
                            android.util.Log.w("VTOPService",
                                    "getName: 'name' field missing from response, continuing...");
                        }
                        // Continue to next step regardless — name is not critical for sync
                        this.getCreditsCGPA();
                    } catch (Exception e) {
                        error(301, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to sve the earned credits and CGPA in SharedPreferences.
     */
    private void getCreditsCGPA() {
        this.updateProgress(null);

        /*
         * JSON response format
         *
         * {
         * "cgpa": 8.58
         * "total_credits": 64
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'examinations/examGradeView/StudentGradeHistory'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var tables = doc.getElementsByTagName('table');" +
                "        for (var i = tables.length - 1; i >= 0 ; --i) {" +
                "            var headings = tables[i].getElementsByTagName('tr')[0].getElementsByTagName('td');" +
                "            if (headings[0].innerText.toLowerCase().includes('credits')) {" +
                "                var creditsIndex, cgpaIndex;" +
                "                for (var j = 0; j < headings.length; ++j) {" +
                "                    var heading = headings[j].innerText.toLowerCase();" +
                "                    if (heading.includes('earned')) {" +
                "                        creditsIndex = j + headings.length;" +
                "                    } else if (heading.includes('cgpa')) {" +
                "                        cgpaIndex = j + headings.length;" +
                "                    }" +
                "                }" +
                "                var cells = tables[i].getElementsByTagName('td');" +
                "                response.cgpa = parseFloat(cells[cgpaIndex].innerText) || 0;" +
                "                response.total_credits = parseFloat(cells[creditsIndex].innerText) || 0;" +
                "                break;" +
                "            }" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        if (response.has("cgpa")) {
                            this.sharedPreferences.edit().putFloat("cgpa", (float) response.getDouble("cgpa")).apply();
                        }
                        if (response.has("total_credits")) {
                            this.sharedPreferences.edit()
                                    .putFloat("totalCredits", (float) response.getDouble("total_credits")).apply();
                        }
                        if (!response.has("cgpa") && !response.has("total_credits")) {
                            android.util.Log.w("VTOPService", "getCreditsCGPA: no data in response, continuing...");
                        }
                        // Continue to next step regardless — CGPA is not critical for sync
                        this.downloadCourses();
                    } catch (Exception e) {
                        error(302, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to download the course info from the timetable page.
     */
    private void downloadCourses() {
        this.updateProgress(R.string.downloading_courses);

        /*
         * JSON response format
         *
         * {
         * "courses": [
         * {
         * "code": "CSE1001",
         * "title": "Problem Solving and Programming",
         * "type": "lab"|"project"|"theory",
         * "credits": 3,
         * "slots": [
         * "L45",
         * "L46"
         * ],
         * "venue": "AB2 - 015",
         * "faculty": "JOHN DOE"
         * },
         * ...
         * ]
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = '_csrf=' + $('input[name=\"_csrf\"]').val() + '&semesterSubId=' + '" + semesterID
                + "' + '&authorizedID=' + $('#authorizedIDX').val();" +
                "var response = {" +
                "    courses: []" +
                "};" +
                "$.ajax({" +
                "    type : 'POST'," +
                "    url : 'processViewTimeTable'," +
                "    data : data," +
                "    async: false," +
                "    success : function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        if (!doc.getElementById('studentDetailsList')) {" +
                "            return;" +
                "        }" +
                "        var table = doc.getElementById('studentDetailsList').getElementsByTagName('table')[0];" +
                "        var headings = table.getElementsByTagName('th');" +
                "        var courseIndex, creditsIndex, slotVenueIndex, facultyIndex;" +
                "        for(var i = 0; i < headings.length; ++i) {" +
                "            var heading = headings[i].innerText.toLowerCase();" +
                "            if (heading == 'course') {" +
                "                courseIndex = i;" +
                "            } else if (heading == 'l t p j c') {" +
                "                creditsIndex = i;" +
                "            } else if (heading.includes('slot')) {" +
                "                slotVenueIndex = i;" +
                "            } else if (heading.includes('faculty')) {" +
                "                facultyIndex = i;" +
                "            }" +
                "        }" +
                "        var cells = table.getElementsByTagName('td');" +
                "        var headingOffset = headings[0].innerText.toLowerCase().includes('invoice') ? -1 : 0;" +
                "        var cellOffset = cells[0].innerText.toLowerCase().includes('invoice') ? 1 : 0;" +
                "        var offset = headingOffset + cellOffset;" +
                "        while (courseIndex < cells.length && creditsIndex < cells.length && slotVenueIndex < cells.length && facultyIndex < cells.length) {"
                +
                "            var course = {};" +
                "            var rawCourse = cells[courseIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,' ');"
                +
                "            var rawCourseType = rawCourse.split('(').slice(-1)[0].toLowerCase();" +
                "            var rawCredits = cells[creditsIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,' ').trim().split(' ');"
                +
                "            var rawSlotVenue = cells[slotVenueIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,'').split('-');"
                +
                "            var rawFaculty = cells[facultyIndex + offset].innerText.replace(/\\t/g,'').replace(/\\n/g,'').split('-');"
                +
                "            course.code = rawCourse.split('-')[0].trim();" +
                "            course.title = rawCourse.split('-').slice(1).join('-').split('(')[0].trim();" +
                "            course.type = (rawCourseType.includes('lab')) ? 'lab' : ((rawCourseType.includes('project')) ? 'project' : 'theory');"
                +
                "            course.credits = parseFloat(rawCredits[rawCredits.length - 1]) || 0;" +
                "            course.slots = rawSlotVenue[0].trim().split('+');" +
                "            course.venue = rawSlotVenue.slice(1, rawSlotVenue.length).join(' - ').trim();" +
                "            course.faculty = rawFaculty[0].trim();" +
                "            response.courses.push(course);" +
                "            courseIndex += headings.length + headingOffset;" +
                "            creditsIndex += headings.length + headingOffset;" +
                "            slotVenueIndex += headings.length + headingOffset;" +
                "            facultyIndex += headings.length + headingOffset;" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray courseArray = response.getJSONArray("courses");

                        List<Course> courses = new ArrayList<>();
                        List<Slot> slots = new ArrayList<>();

                        this.theorySlots = new HashMap<>();
                        this.labSlots = new HashMap<>();
                        this.projectSlots = new HashMap<>();

                        this.theoryCourses = new HashMap<>();
                        this.labCourses = new HashMap<>();
                        this.projectCourses = new HashMap<>();

                        for (int i = 0, slotId = 1; i < courseArray.length(); ++i) {
                            JSONObject courseObject = courseArray.getJSONObject(i);
                            Course course = new Course();

                            course.id = i + 1;
                            course.code = this.getStringValue(courseObject, "code");
                            course.title = this.getStringValue(courseObject, "title");
                            course.type = this.getStringValue(courseObject, "type");
                            course.credits = this.getDoubleValue(courseObject, "credits");
                            course.venue = this.getStringValue(courseObject, "venue");
                            course.faculty = this.getStringValue(courseObject, "faculty");

                            courses.add(course);

                            Map<String, Slot> slotReference;

                            if (course.type.equals("lab")) {
                                slotReference = this.labSlots;
                                this.labCourses.put(course.id, course);
                            } else if (course.type.equals("project")) {
                                slotReference = this.projectSlots;
                                this.projectCourses.put(course.id, course);
                            } else {
                                slotReference = this.theorySlots;
                                this.theoryCourses.put(course.id, course);
                            }

                            JSONArray slotsArray = courseObject.getJSONArray("slots");
                            for (int j = 0; j < slotsArray.length(); ++j, ++slotId) {
                                Slot slot = new Slot();

                                slot.id = slotId;
                                slot.slot = slotsArray.getString(j);
                                slot.courseId = course.id;

                                slots.add(slot);
                                slotReference.put(slot.slot, slot);

                                String canonical = SLOT_ALIASES.get(slot.slot.toUpperCase(Locale.US));
                                if (canonical != null && !slotReference.containsKey(canonical)) {
                                    slotReference.put(canonical, slot);
                                }
                            }
                        }

                        this.appDatabase = AppDatabase.getInstance(getApplicationContext());
                        CoursesDao coursesDao = this.appDatabase.coursesDao();

                        Observable<Object> deleteAllObservable = Observable.fromCompletable(coursesDao.deleteAll());
                        Observable<Object> insertCoursesObservable = Observable
                                .fromCompletable(coursesDao.insertCourses(courses));
                        Observable<Object> insertSlotsObservable = Observable
                                .fromCompletable(coursesDao.insertSlots(slots));

                        Observable
                                .concat(
                                        deleteAllObservable,
                                        insertCoursesObservable,
                                        insertSlotsObservable)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<Object>() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onNext(@NonNull Object o) {
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(402, e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onComplete() {
                                        downloadTimetable();
                                    }
                                });
                    } catch (Exception e) {
                        error(401, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to download the timetable.
     * Generates timetable from slots by mapping slot names to days of week.
     */
    private void downloadTimetable() {
        updateProgress(R.string.downloading_timetable);

        try {
            CoursesDao coursesDao = this.appDatabase.coursesDao();

            // Get all slots from database
            Observable.fromSingle(coursesDao.getAllSlots())
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<List<Slot>>() {
                        @Override
                        public void onSubscribe(@NonNull Disposable d) {
                        }

                        @Override
                        public void onNext(@NonNull List<Slot> slots) {
                            android.util.Log.d("VTOPService",
                                    "Retrieved " + slots.size() + " slots for timetable generation");
                            generateTimetableFromSlots(slots);
                        }

                        @Override
                        public void onError(@NonNull Throwable e) {
                            android.util.Log.e("VTOPService", "Error fetching slots: " + e.getMessage());
                            downloadAttendance();
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        } catch (Exception e) {
            android.util.Log.e("VTOPService", "Error in downloadTimetable: " + e.getMessage());
            downloadAttendance();
        }
    }

    /**
     * Generate timetable entries by mapping slots to days and periods
     */
    public static List<Timetable> generateTimetableEntriesFromSlots(List<Slot> slots) {
        List<Timetable> timetableEntries = createStandardVITTimetable();
        if (slots == null)
            return timetableEntries;

        for (Slot slot : slots) {
            if (slot.slot == null || slot.slot.isEmpty())
                continue;

            // Handle compound slots (e.g., "TB2+L9" or "A1+B2")
            String[] slotParts = slot.slot.toUpperCase(Locale.US).replaceAll("\\s", "").split("\\+");

            for (String slotPart : slotParts) {
                String slotName = slotPart.trim();
                if (!slotName.isEmpty()) {
                    mapSlotToTimetable(timetableEntries, slot, slotName);
                }
            }
        }
        return timetableEntries;
    }

    private void generateTimetableFromSlots(List<Slot> slots) {
        try {
            // Create standard VIT timetable with 12 theory periods and 6 lab period groups
            List<Timetable> timetableEntries = generateTimetableEntriesFromSlots(slots);

            android.util.Log.d("VTOPService", "Processing " + slots.size() + " slots for timetable");
            android.util.Log.d("VTOPService", "Generated " + timetableEntries.size() + " timetable entries");

            // Log each period to debug
            for (int i = 0; i < timetableEntries.size(); i++) {
                Timetable t = timetableEntries.get(i);
                android.util.Log.d("VTOPService", "Period " + i + " (" + t.startTime + "-" + t.endTime +
                        "): Mon=" + t.monday + " Tue=" + t.tuesday + " Wed=" + t.wednesday +
                        " Thu=" + t.thursday + " Fri=" + t.friday + " Sat=" + t.saturday);
            }

            // Save to database
            TimetableDao timetableDao = this.appDatabase.timetableDao();
            Observable.fromCompletable(timetableDao.deleteAll())
                    .concatWith(Observable.fromCompletable(timetableDao.insert(timetableEntries)))
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Object>() {
                        @Override
                        public void onSubscribe(@NonNull Disposable d) {
                        }

                        @Override
                        public void onNext(@NonNull Object o) {
                        }

                        @Override
                        public void onError(@NonNull Throwable e) {
                            android.util.Log.e("VTOPService", "Error saving timetable: " + e.getMessage());
                            downloadAttendance();
                        }

                        @Override
                        public void onComplete() {
                            android.util.Log.d("VTOPService", "Timetable saved successfully");
                            downloadAttendance();
                        }
                    });

        } catch (Exception e) {
            android.util.Log.e("VTOPService", "Error in generateTimetableFromSlots: " + e.getMessage());
            downloadAttendance();
        }
    }

    /**
     * Create standard VIT timetable structure with 12 theory periods
     * Times match VIT Chennai period timings
     */
    public static List<Timetable> createStandardVITTimetable() {
        List<Timetable> timetable = new ArrayList<>();

        // VIT Chennai period timings
        String[] periodTimes = {
                "08:00", "08:50", // Period 0: 8:00-8:50
                "08:55", "09:45", // Period 1: 8:55-9:45
                "09:50", "10:40", // Period 2: 9:50-10:40
                "10:45", "11:35", // Period 3: 10:45-11:35
                "11:40", "12:30", // Period 4: 11:40-12:30
                "12:35", "13:25", // Period 5: 12:35-1:25
                "14:00", "14:50", // Period 6: 2:00-2:50
                "14:55", "15:45", // Period 7: 2:55-3:45
                "15:50", "16:40", // Period 8: 3:50-4:40
                "16:45", "17:35", // Period 9: 4:45-5:35
                "17:40", "18:30", // Period 10: 5:40-6:30
                "18:35", "19:25" // Period 11: 6:35-7:25
        };

        // Create 12 periods (indices 0-11)
        for (int i = 0; i < 12; i++) {
            Timetable period = new Timetable();
            period.id = (i + 1); // IDs 1-12
            period.startTime = periodTimes[i * 2];
            period.endTime = periodTimes[(i * 2) + 1];
            timetable.add(period);
        }

        return timetable;
    }

    /**
     * Map a slot to its appropriate timetable period and day(s)
     * Period indices are 0-based: 0=Period1(08:00-08:50), 1=Period2(08:50-09:40),
     * etc.
     */
    public static void mapSlotToTimetable(List<Timetable> timetable, Slot slot, String slotName) {
        String normalizedSlot = slotName.toUpperCase(Locale.US);

        String canonicalSlot = normalizedSlot;
        if (!SLOT_MAP.containsKey(canonicalSlot)) {
            canonicalSlot = SLOT_ALIASES.get(normalizedSlot);
        }

        List<DayPeriod> placements = SLOT_MAP.get(canonicalSlot);
        if (placements == null || placements.isEmpty()) {
            android.util.Log.w("VTOPService", "Unmapped slot: " + slotName);
            return;
        }

        for (DayPeriod placement : placements) {
            assignSlotToTimetable(timetable, slot.id, placement.day, placement.periodIndex);
        }
    }

    /**
     * Assign a slot to a specific day and period in timetable
     * Skip assignment if period already has a slot for that day (avoid overwriting)
     */
    public static void assignSlotToTimetable(List<Timetable> timetable, int slotId, String day, int periodIndex) {
        if (periodIndex < 0 || periodIndex >= timetable.size()) {
            android.util.Log.w("VTOPService",
                    "Invalid period index: " + periodIndex + " for slot " + slotId + " on " + day);
            return;
        }

        Timetable period = timetable.get(periodIndex);

        // Check if this period already has a slot for this day - if so, skip to avoid
        // overwriting
        boolean alreadyOccupied = false;
        Integer existingSlot = null;

        switch (day.toLowerCase()) {
            case "sunday":
                existingSlot = period.sunday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.sunday = slotId;
                break;
            case "monday":
                existingSlot = period.monday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.monday = slotId;
                break;
            case "tuesday":
                existingSlot = period.tuesday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.tuesday = slotId;
                break;
            case "wednesday":
                existingSlot = period.wednesday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.wednesday = slotId;
                break;
            case "thursday":
                existingSlot = period.thursday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.thursday = slotId;
                break;
            case "friday":
                existingSlot = period.friday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.friday = slotId;
                break;
            case "saturday":
                existingSlot = period.saturday;
                alreadyOccupied = (existingSlot != null);
                if (!alreadyOccupied)
                    period.saturday = slotId;
                break;
        }

        if (alreadyOccupied) {
            android.util.Log.d("VTOPService", "Period " + periodIndex + " on " + day + " already occupied by slot "
                    + existingSlot + ", skipping slot " + slotId);
        } else {
            android.util.Log.d("VTOPService", "Assigned slot " + slotId + " to " + day + " period " + periodIndex + " ("
                    + period.startTime + "-" + period.endTime + ")");
        }
    }

    /*
     * DISABLED - This method uses database interfaces that don't exist
     * /**
     * Generate timetable from course slots instead of VTOP's broken JavaScript
     */
    /*
     * private void generateTimetableFromSlots() {
     * try {
     * // Get all courses and slots from database
     * com.salmanmalvasi.studentcc.interfaces.CoursesDao coursesDao =
     * appDatabase.coursesDao();
     * com.salmanmalvasi.studentcc.interfaces.SlotsDao slotsDao =
     * appDatabase.slotsDao();
     * 
     * List<com.salmanmalvasi.studentcc.models.Course> courses =
     * coursesDao.getAllSync();
     * List<com.salmanmalvasi.studentcc.models.Slot> allSlots =
     * slotsDao.getAllSlots();
     * 
     * android.util.Log.d("VTOPService", "Starting slot mapping with " +
     * courses.size() + " courses and " + allSlots.size() + " slots");
     * 
     * // Create standard VIT timetable structure
     * List<com.salmanmalvasi.studentcc.models.Timetable> timetable =
     * createStandardVITTimetable();
     * 
     * // Map slots to timetable
     * mapSlotsToTimetable(timetable, allSlots);
     * 
     * android.util.Log.d("VTOPService", "Completed slot mapping");
     * 
     * // Save to database
     * com.salmanmalvasi.studentcc.interfaces.TimetableDao timetableDao =
     * appDatabase.timetableDao();
     * timetableDao.deleteAllSync();
     * timetableDao.insertSync(timetable);
     * 
     * } catch (Exception e) {
     * android.util.Log.e("VTOPService", "Error in generateTimetableFromSlots: " +
     * e.getMessage(), e);
     * throw e;
     * }
     * }
     */

    /*
     * DISABLED - Helper methods for disabled generateTimetableFromSlots()
     * /**
     * Create standard VIT timetable structure with 12 theory + 12 lab periods
     */
    /*
     * private List<com.salmanmalvasi.studentcc.models.Timetable>
     * createStandardVITTimetable() {
     * List<com.salmanmalvasi.studentcc.models.Timetable> timetable = new
     * ArrayList<>();
     * 
     * // VIT Chennai standard timings
     * String[] theoryTimes = {
     * "08:00", "08:50", // Period 1
     * "08:50", "09:40", // Period 2
     * "10:00", "10:50", // Period 3
     * "10:50", "11:40", // Period 4
     * "11:40", "12:30", // Period 5
     * "13:30", "14:20", // Period 6
     * "14:20", "15:10", // Period 7
     * "15:30", "16:20", // Period 8
     * "16:20", "17:10", // Period 9
     * "17:10", "18:00", // Period 10
     * "18:00", "18:50", // Period 11
     * "18:50", "19:40" // Period 12
     * };
     * 
     * String[] labTimes = {
     * "08:00", "09:40", // Lab slot 1-2
     * "10:00", "11:40", // Lab slot 3-4
     * "11:40", "13:20", // Lab slot 5-6 (includes lunch)
     * "13:30", "15:10", // Lab slot 7-8
     * "15:30", "17:10", // Lab slot 9-10
     * "17:10", "18:50", // Lab slot 11-12
     * "08:00", "11:40", // Extended lab 1-4
     * "11:40", "15:10", // Extended lab 5-8 (includes lunch)
     * "15:30", "18:50", // Extended lab 9-12
     * "08:00", "13:20", // Morning session (1-6)
     * "13:30", "18:50", // Afternoon session (7-12)
     * "08:00", "18:50" // Full day
     * };
     * 
     * // Create 12 theory periods
     * for (int i = 0; i < 12; i++) {
     * com.salmanmalvasi.studentcc.models.Timetable period = new
     * com.salmanmalvasi.studentcc.models.Timetable();
     * period.id = (i * 2) + 1; // Odd IDs for theory
     * period.startTime = theoryTimes[i * 2];
     * period.endTime = theoryTimes[(i * 2) + 1];
     * period.sunday = null;
     * period.monday = null;
     * period.tuesday = null;
     * period.wednesday = null;
     * period.thursday = null;
     * period.friday = null;
     * period.saturday = null;
     * timetable.add(period);
     * }
     * 
     * // Create 12 lab periods
     * for (int i = 0; i < 12; i++) {
     * com.salmanmalvasi.studentcc.models.Timetable period = new
     * com.salmanmalvasi.studentcc.models.Timetable();
     * period.id = (i * 2) + 2; // Even IDs for lab
     * period.startTime = labTimes[i * 2];
     * period.endTime = labTimes[(i * 2) + 1];
     * period.sunday = null;
     * period.monday = null;
     * period.tuesday = null;
     * period.wednesday = null;
     * period.thursday = null;
     * period.friday = null;
     * period.saturday = null;
     * timetable.add(period);
     * }
     * 
     * return timetable;
     * }
     * 
     * /* DISABLED - Helper method for disabled generateTimetableFromSlots()
     * /**
     * Map course slots to timetable periods
     */
    /*
     * private void
     * mapSlotsToTimetable(List<com.salmanmalvasi.studentcc.models.Timetable>
     * timetable, List<com.salmanmalvasi.studentcc.models.Slot> allSlots) {
     * for (com.salmanmalvasi.studentcc.models.Slot slot : allSlots) {
     * if (slot.slot == null || slot.slot.equals("NIL")) {
     * android.util.Log.w("VTOPService", "Unknown slot: " + slot.slot);
     * continue;
     * }
     * 
     * android.util.Log.d("VTOPService", "Processing slot: " + slot.slot +
     * " (course ID: " + slot.courseId + ")");
     * 
     * // Map slot to multiple days if theory, single day if lab
     * mapSlotToMultipleDays(timetable, slot);
     * }
     * }
     * 
     * /* DISABLED - Helper method for disabled generateTimetableFromSlots()
     * /**
     * Map a single slot to appropriate timetable periods
     * Theory slots appear on multiple days, lab slots on single days
     */
    /*
     * private void
     * mapSlotToMultipleDays(List<com.salmanmalvasi.studentcc.models.Timetable>
     * timetable, com.salmanmalvasi.studentcc.models.Slot slot) {
     * String slotName = slot.slot.toUpperCase();
     * 
     * // Theory slots repeat across multiple days
     * switch (slotName) {
     * // Theory slots - appear on multiple days
     * case "A1": case "TA1":
     * assignSlotToTimetable(timetable, slot.id, "monday", 6); // Period 6:
     * 13:30-14:20
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 7); // Period 7:
     * 14:20-15:10
     * break;
     * case "A2": case "TA2": case "TAA2":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 6); // Period 6:
     * 13:30-14:20
     * assignSlotToTimetable(timetable, slot.id, "thursday", 7); // Period 7:
     * 14:20-15:10
     * break;
     * case "B1": case "TB1":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 7); // Period 7:
     * 14:20-15:10
     * assignSlotToTimetable(timetable, slot.id, "thursday", 8); // Period 8:
     * 15:30-16:20
     * break;
     * case "B2": case "TB2":
     * assignSlotToTimetable(timetable, slot.id, "monday", 9); // Period 9:
     * 16:20-17:10
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 6); // Period 6:
     * 13:30-14:20
     * break;
     * case "C1": case "TC1":
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 8); // Period 8:
     * 15:30-16:20
     * assignSlotToTimetable(timetable, slot.id, "friday", 6); // Period 6:
     * 13:30-14:20
     * break;
     * case "C2": case "TC2":
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 6); // Period 6:
     * 13:30-14:20
     * assignSlotToTimetable(timetable, slot.id, "friday", 7); // Period 7:
     * 14:20-15:10
     * break;
     * case "D1": case "TD1":
     * assignSlotToTimetable(timetable, slot.id, "monday", 7); // Period 7:
     * 14:20-15:10
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 9); // Period 9:
     * 16:20-17:10
     * break;
     * case "D2": case "TD2":
     * assignSlotToTimetable(timetable, slot.id, "monday", 8); // Period 8:
     * 15:30-16:20
     * assignSlotToTimetable(timetable, slot.id, "thursday", 6); // Period 6:
     * 13:30-14:20
     * break;
     * case "E1": case "TE1":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 8); // Period 8:
     * 15:30-16:20
     * assignSlotToTimetable(timetable, slot.id, "thursday", 9); // Period 9:
     * 16:20-17:10
     * break;
     * case "E2": case "TE2":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 8); // Period 8:
     * 15:30-16:20
     * assignSlotToTimetable(timetable, slot.id, "friday", 6); // Period 6:
     * 13:30-14:20
     * break;
     * case "F1": case "TF1":
     * assignSlotToTimetable(timetable, slot.id, "monday", 6); // Period 6:
     * 13:30-14:20
     * assignSlotToTimetable(timetable, slot.id, "friday", 8); // Period 8:
     * 15:30-16:20
     * break;
     * case "F2": case "TF2":
     * assignSlotToTimetable(timetable, slot.id, "monday", 7); // Period 7:
     * 14:20-15:10
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 8); // Period 8:
     * 15:30-16:20
     * break;
     * case "G1": case "TG1":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 9); // Period 9:
     * 16:20-17:10
     * assignSlotToTimetable(timetable, slot.id, "friday", 7); // Period 7:
     * 14:20-15:10
     * break;
     * case "G2": case "TG2":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 7); // Period 7:
     * 14:20-15:10
     * assignSlotToTimetable(timetable, slot.id, "thursday", 8); // Period 8:
     * 15:30-16:20
     * break;
     * 
     * // Lab slots - single day assignments
     * case "L1": case "L2":
     * assignSlotToTimetable(timetable, slot.id, "monday", 0); // 08:00-09:40
     * break;
     * case "L3": case "L4":
     * assignSlotToTimetable(timetable, slot.id, "monday", 2); // 10:00-11:40
     * break;
     * case "L5": case "L6":
     * assignSlotToTimetable(timetable, slot.id, "monday", 4); // 11:40-13:20
     * break;
     * case "L7": case "L8":
     * assignSlotToTimetable(timetable, slot.id, "monday", 6); // 13:30-15:10
     * break;
     * case "L9": case "L10":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 2); // 10:00-11:40
     * break;
     * case "L11": case "L12":
     * assignSlotToTimetable(timetable, slot.id, "tuesday", 4); // 11:40-13:20
     * break;
     * case "L13": case "L14":
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 0); // 08:00-09:40
     * break;
     * case "L15": case "L16":
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 2); // 10:00-11:40
     * break;
     * case "L17": case "L18":
     * assignSlotToTimetable(timetable, slot.id, "wednesday", 4); // 11:40-13:20
     * break;
     * case "L19": case "L20":
     * assignSlotToTimetable(timetable, slot.id, "thursday", 0); // 08:00-09:40
     * break;
     * case "L21": case "L22":
     * assignSlotToTimetable(timetable, slot.id, "thursday", 2); // 10:00-11:40
     * break;
     * case "L23": case "L24":
     * assignSlotToTimetable(timetable, slot.id, "thursday", 4); // 11:40-13:20
     * break;
     * case "L25": case "L26":
     * assignSlotToTimetable(timetable, slot.id, "friday", 0); // 08:00-09:40
     * break;
     * case "L27": case "L28":
     * assignSlotToTimetable(timetable, slot.id, "friday", 2); // 10:00-11:40
     * break;
     * case "L29": case "L30":
     * assignSlotToTimetable(timetable, slot.id, "friday", 4); // 11:40-13:20
     * break;
     * case "L31": case "L32":
     * assignSlotToTimetable(timetable, slot.id, "saturday", 0); // 08:00-09:40
     * break;
     * case "L33": case "L34":
     * assignSlotToTimetable(timetable, slot.id, "saturday", 2); // 10:00-11:40
     * break;
     * case "L35": case "L36":
     * assignSlotToTimetable(timetable, slot.id, "saturday", 4); // 11:40-13:20
     * break;
     * 
     * default:
     * android.util.Log.w("VTOPService", "Unmapped slot: " + slotName);
     * break;
     * }
     * }
     * 
     * /* DISABLED - Helper method for disabled generateTimetableFromSlots()
     * /**
     * Assign a slot to a specific day and period in timetable
     */
    /*
     * private void
     * assignSlotToTimetable(List<com.salmanmalvasi.studentcc.models.Timetable>
     * timetable, int slotId, String day, int periodIndex) {
     * // Find the timetable entry for this period
     * int timetableId = (periodIndex * 2) + 1; // Theory periods have odd IDs
     * 
     * for (com.salmanmalvasi.studentcc.models.Timetable period : timetable) {
     * if (period.id == timetableId) {
     * // Assign slot to the appropriate day
     * switch (day.toLowerCase()) {
     * case "sunday":
     * period.sunday = slotId;
     * break;
     * case "monday":
     * period.monday = slotId;
     * break;
     * case "tuesday":
     * period.tuesday = slotId;
     * break;
     * case "wednesday":
     * period.wednesday = slotId;
     * break;
     * case "thursday":
     * period.thursday = slotId;
     * break;
     * case "friday":
     * period.friday = slotId;
     * break;
     * case "saturday":
     * period.saturday = slotId;
     * break;
     * }
     * 
     * android.util.Log.d("VTOPService", "Assigned slot " + slotId + " to " + day +
     * " period " + periodIndex + " (timetable id " + timetableId + ")");
     * break;
     * }
     * }
     * }
     */

    /**
     * Download attendance using in-page WebView AJAX with fast HTTP fallback.
     * Guaranteed never to fail the entire sync session if attendance is
     * unavailable.
     */
    private void downloadAttendance() {
        updateProgress(R.string.downloading_attendance);

        // Check if WebView is still valid
        if (webView == null || isWebViewDestroyed) {
            android.util.Log.w("VTOPService", "WebView null/destroyed, skipping to detailed attendance");
            downloadDetailedAttendance();
            return;
        }

        final String effectiveUser = (this.username != null) ? this.username.trim() : "";
        final String script = "(function() {" +
                "  var authId = $('#authorizedIDX').val() || $('#authorizedID').val() || '" + effectiveUser + "';" +
                "  var csrf = $('input[name=\"_csrf\"]').val() || '';" +
                "  if (!csrf) { var el = document.querySelector('input[name=\"_csrf\"]'); if (el) csrf = el.value || ''; }"
                +
                "  if (!csrf) { var m = document.querySelector('meta[name=\"_csrf\"]'); if (m) csrf = m.getAttribute('content') || ''; }"
                +
                "  var postData = '_csrf=' + csrf + '&authorizedID=' + authId + '&semesterSubId=' + '" + semesterID
                + "' + '&x=' + Date.now();" +
                "  var attHtml = '';" +
                "  if (typeof $ !== 'undefined' && $.ajax) {" +
                "    try {" +
                "      $.ajax({" +
                "        type: 'POST'," +
                "        url: 'processViewStudentAttendance'," +
                "        data: postData," +
                "        async: false," +
                "        success: function(res) { attHtml = res || ''; }," +
                "        error: function(xhr, status, err) { attHtml = 'ERROR:' + status; }" +
                "      });" +
                "    } catch(e) {}" +
                "  }" +
                "  window.__attendanceHTML = attHtml;" +
                "  return {" +
                "    csrf: csrf," +
                "    authorizedID: authId," +
                "    htmlLength: (attHtml && !attHtml.startsWith('ERROR:')) ? attHtml.length : 0" +
                "  };" +
                "})();";

        webView.evaluateJavascript(script, resultJson -> {
            try {
                String cleanJson = (resultJson != null) ? resultJson.trim() : "";
                if (cleanJson.startsWith("\"") && cleanJson.endsWith("\"")) {
                    try {
                        cleanJson = new org.json.JSONTokener(cleanJson).nextValue().toString();
                    } catch (Exception ignored) {
                        cleanJson = cleanJson.substring(1, cleanJson.length() - 1)
                                .replace("\\\"", "\"").replace("\\\\", "\\");
                    }
                }

                String csrf = "";
                String authId = effectiveUser;
                int htmlLength = 0;

                if (cleanJson.startsWith("{")) {
                    try {
                        JSONObject obj = new JSONObject(cleanJson);
                        csrf = obj.optString("csrf", "");
                        String parsedAuth = obj.optString("authorizedID", "");
                        if (!parsedAuth.isEmpty())
                            authId = parsedAuth;
                        htmlLength = obj.optInt("htmlLength", 0);
                    } catch (Exception ignored) {
                    }
                }

                if (!csrf.isEmpty()) {
                    this.sharedPreferences.edit()
                            .putString("last_csrf", csrf)
                            .putString("username", authId)
                            .apply();
                }

                final String finalCsrf = csrf;
                final String finalAuthId = authId;

                if (htmlLength > 50) {
                    webView.evaluateJavascript("window.__attendanceHTML || ''", htmlResult -> {
                        String html = htmlResult;
                        if (html != null && html.startsWith("\"") && html.endsWith("\"")) {
                            html = html.substring(1, html.length() - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\n", "\n")
                                    .replace("\\r", "\r")
                                    .replace("\\t", "\t")
                                    .replace("\\\\", "\\");
                        }
                        if (html != null && html.length() > 50 && !html.equals("EMPTY") && !html.equals("null")) {
                            android.util.Log.d("VTOPService",
                                    "Attendance HTML obtained via WebView AJAX, len=" + html.length());
                            parseAttendanceHTML(html);
                        } else {
                            fetchAttendanceViaFastHTTPOrSkip(finalCsrf, finalAuthId);
                        }
                    });
                } else {
                    fetchAttendanceViaFastHTTPOrSkip(finalCsrf, finalAuthId);
                }
            } catch (Exception e) {
                android.util.Log.e("VTOPService", "Attendance fetch error: " + e.getMessage(), e);
                downloadDetailedAttendance();
            }
        });
    }

    private void fetchAttendanceViaFastHTTPOrSkip(String csrf, String authorizedID) {
        if (csrf == null || csrf.isEmpty()) {
            android.util.Log.w("VTOPService", "No CSRF token available, moving to detailed attendance");
            downloadDetailedAttendance();
            return;
        }

        new Thread(() -> {
            try {
                String postData = "_csrf=" + java.net.URLEncoder.encode(csrf, "UTF-8") +
                        "&authorizedID=" + java.net.URLEncoder.encode(authorizedID, "UTF-8") +
                        "&semesterSubId=" + java.net.URLEncoder.encode(semesterID, "UTF-8") +
                        "&x=" + System.currentTimeMillis();

                String cookies = com.salmanmalvasi.studentcc.helpers.FastScraper.getVtopCookies();
                String html = com.salmanmalvasi.studentcc.helpers.FastScraper.post("processViewStudentAttendance",
                        postData, cookies);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    if (html != null && html.length() > 50) {
                        android.util.Log.d("VTOPService",
                                "Fast scraper received attendance HTML (length: " + html.length() + ")");
                        parseAttendanceHTML(html);
                    } else {
                        android.util.Log.w("VTOPService",
                                "Fast scraper returned empty attendance, skipping to detailed attendance");
                        downloadDetailedAttendance();
                    }
                });
            } catch (Exception e) {
                android.util.Log.w("VTOPService",
                        "Fast HTTP attendance error: " + e.getMessage() + ", skipping to detailed attendance");
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    downloadDetailedAttendance();
                });
            }
        }).start();
    }

    /**
     * Parse attendance HTML table from VTOP response
     */
    private void parseAttendanceHTML(String htmlResponse) {
        try {
            android.util.Log.d("VTOPService",
                    "HTML Response Preview: " + htmlResponse.substring(0, Math.min(500, htmlResponse.length())));
            android.util.Log.d("VTOPService", "Starting HTML parsing - Total length: " + htmlResponse.length());

            // Unescape HTML entities - the response contains escaped HTML
            String unescapedHtml = htmlResponse;
            // Handle unicode escapes: \u003C = < and \u003E = >
            unescapedHtml = unescapedHtml.replace("\\u003C", "<");
            unescapedHtml = unescapedHtml.replace("\\u003E", ">");
            unescapedHtml = unescapedHtml.replace("\\u003c", "<");
            unescapedHtml = unescapedHtml.replace("\\u003e", ">");
            unescapedHtml = unescapedHtml.replace("\\r", "\r");
            unescapedHtml = unescapedHtml.replace("\\n", "\n");

            android.util.Log.d("VTOPService", "Unescaped HTML length: " + unescapedHtml.length());

            // Parse HTML with JSoup
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(unescapedHtml);
            android.util.Log.d("VTOPService", "HTML parsed successfully");

            // The div with id="getStudentDetails" contains the table (not the table itself)
            org.jsoup.nodes.Element container = doc.getElementById("getStudentDetails");
            android.util.Log.d("VTOPService", "Container lookup: " + (container != null ? "FOUND" : "NOT FOUND"));

            org.jsoup.nodes.Element table = null;

            if (container != null) {
                // Find the actual table within the container
                table = container.selectFirst("table");
                android.util.Log.d("VTOPService",
                        "Table lookup in container: " + (table != null ? "FOUND" : "NOT FOUND"));
            } else {
                // Fallback: search for table directly in document
                android.util.Log.d("VTOPService", "Container is null, searching for table directly in document");
                org.jsoup.select.Elements allTables = doc.select("table");
                android.util.Log.d("VTOPService", "Found " + allTables.size() + " tables in document");
                if (allTables.size() > 0) {
                    table = allTables.get(0);
                    android.util.Log.d("VTOPService", "Using first table found");
                }
            }

            if (table == null) {
                android.util.Log.w("VTOPService", "No attendance table found, proceeding to detailed attendance");
                android.util.Log.d("VTOPService", "Full HTML length: " + htmlResponse.length());
                android.util.Log.d("VTOPService",
                        "HTML snippet: " + htmlResponse.substring(0, Math.min(2000, htmlResponse.length())));
                downloadDetailedAttendance();
                return;
            }

            android.util.Log.d("VTOPService", "Successfully found attendance table");

            // Find column indices from header row - look for both thead and tr > th
            org.jsoup.select.Elements headers = table.select("th");
            android.util.Log.d("VTOPService", "Found " + headers.size() + " header cells");

            int courseTypeIdx = -1, slotIdx = -1, attendedIdx = -1, totalIdx = -1, percentageIdx = -1;

            for (int i = 0; i < headers.size(); i++) {
                String heading = headers.get(i).text().toLowerCase();
                android.util.Log.d("VTOPService", "Header[" + i + "]: " + heading);
                if (heading.contains("course") && heading.contains("type")) {
                    courseTypeIdx = i;
                } else if (heading.contains("slot")) {
                    slotIdx = i;
                } else if (heading.contains("attended")) {
                    attendedIdx = i;
                } else if (heading.contains("total")) {
                    totalIdx = i;
                } else if (heading.contains("percentage")) {
                    percentageIdx = i;
                }
            }

            android.util.Log.d("VTOPService", String.format("Columns - Type:%d Slot:%d Attended:%d Total:%d Pct:%d",
                    courseTypeIdx, slotIdx, attendedIdx, totalIdx, percentageIdx));

            // Parse table rows (handle both tbody and direct tr elements)
            org.jsoup.select.Elements rows = table.select("tr");
            android.util.Log.d("VTOPService", "Found " + rows.size() + " table rows total");

            // Skip header row (first tr with th elements)
            List<Attendance> attendance = new ArrayList<>();
            int attendedClasses = 0;
            int totalClasses = 0;
            int rowCount = 0;

            for (int i = 0; i < rows.size(); i++) {
                org.jsoup.select.Elements cells = rows.get(i).select("td");
                org.jsoup.select.Elements headerCells = rows.get(i).select("th");

                // Skip header rows (rows with th elements instead of td)
                if (headerCells.size() > 0) {
                    android.util.Log.d("VTOPService",
                            "Row " + i + " is header row (has " + headerCells.size() + " th cells), skipping");
                    continue;
                }

                // Skip empty rows
                if (cells.size() == 0) {
                    android.util.Log.d("VTOPService", "Row " + i + " has no td cells, skipping");
                    continue;
                }

                if (cells.size() < 10) {
                    android.util.Log.d("VTOPService",
                            "Row " + i + " has only " + cells.size() + " cells (need at least 10), skipping");
                    continue; // Skip invalid rows
                }

                android.util.Log.d("VTOPService", "Row " + i + " has " + cells.size() + " td cells, processing...");

                try {
                    Attendance item = new Attendance();
                    item.id = i + 1;

                    // Extract course type
                    String courseTypeText = cells.get(courseTypeIdx).text().trim();
                    int courseType = courseTypeText.toLowerCase().contains("lab") ? Course.TYPE_LAB
                            : Course.TYPE_THEORY;

                    // Extract slot (first part if multiple slots like "A1+TA1")
                    String slotText = cells.get(slotIdx).text().trim();
                    String[] slots = slotText.split("\\+");
                    String slot = slots.length > 0 ? slots[0].trim() : slotText;

                    item.courseId = this.getCourseId(slot, courseType);

                    // Extract attendance numbers
                    String attendedText = cells.get(attendedIdx).text().trim();
                    String totalText = cells.get(totalIdx).text().trim();

                    item.attended = attendedText.isEmpty() ? null : Integer.parseInt(attendedText);
                    item.total = totalText.isEmpty() ? null : Integer.parseInt(totalText);

                    if (item.attended != null && item.total != null && item.total != 0) {
                        // Calculate with 2 decimal precision
                        item.percentage = (int) Math.round((item.attended * 10000.0) / item.total) / 100;
                        attendedClasses += item.attended;
                        totalClasses += item.total;

                        android.util.Log.d("VTOPService", String.format(
                                "Row %d: Slot=%s CourseType=%s Attended=%d Total=%d Percentage=%d%%",
                                i, slot, courseType == Course.TYPE_LAB ? "LAB" : "THEORY",
                                item.attended, item.total, item.percentage));
                    }

                    attendance.add(item);
                    rowCount++;

                } catch (Exception e) {
                    android.util.Log.w("VTOPService", "Row parse error at " + i + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            // Calculate overall attendance with 2 decimal precision
            float overallAttendance = 0.0f;
            if (totalClasses != 0) {
                overallAttendance = Math.round((attendedClasses * 10000.0) / totalClasses) / 100.0f;
            }

            android.util.Log.d("VTOPService", String.format("Attendance - Attended:%d Total:%d Overall:%.2f%%",
                    attendedClasses, totalClasses, overallAttendance));

            // Save to preferences
            sharedPreferences.edit()
                    .putFloat("overallAttendance", overallAttendance)
                    .putInt("attendedClasses", attendedClasses)
                    .putInt("totalClasses", totalClasses)
                    .apply();

            // Save to database
            AttendanceDao attendanceDao = appDatabase.attendanceDao();
            Observable<Object> deleteObs = Observable.fromCompletable(attendanceDao.delete());
            Observable<Object> insertObs = Observable.fromCompletable(attendanceDao.insert(attendance));

            Observable.concat(deleteObs, insertObs)
                    .subscribeOn(Schedulers.single())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Object>() {
                        @Override
                        public void onSubscribe(@NonNull Disposable d) {
                        }

                        @Override
                        public void onNext(@NonNull Object o) {
                        }

                        @Override
                        public void onError(@NonNull Throwable e) {
                            android.util.Log.e("VTOPService", "Error saving attendance: " + e.getMessage());
                            downloadDetailedAttendance();
                        }

                        @Override
                        public void onComplete() {
                            Intent dataUpdatedIntent = new Intent("DATA_UPDATED");
                            sendBroadcast(dataUpdatedIntent);
                            downloadDetailedAttendance();
                        }
                    });

        } catch (Exception e) {
            android.util.Log.e("VTOPService", "HTML parse error: " + e.getMessage(), e);
            downloadDetailedAttendance();
        }
    }

    /**
     * Function to download detailed attendance records for each course.
     */
    private void downloadDetailedAttendance() {
        updateProgress(R.string.downloading_attendance);

        // First, get all the attendance data with view links
        webView.evaluateJavascript("(function() {" +
                "var data = '_csrf=' + $('input[name=\"_csrf\"]').val() + '&semesterSubId=' + '" + semesterID
                + "' + '&authorizedID=' + $('#authorizedIDX').val();" +
                "var response = {" +
                "    courses: []" +
                "};" +
                "$.ajax({" +
                "    type : 'POST'," +
                "    url : 'processViewStudentAttendance'," +
                "    data : data," +
                "    async: false," +
                "    success : function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var table = doc.getElementById('getStudentDetails');" +
                "        if (!table) return;" +
                "        var rows = table.getElementsByTagName('tr');" +
                "        for (var i = 1; i < rows.length; i++) {" +
                "            var cells = rows[i].getElementsByTagName('td');" +
                "            if (cells.length < 14) continue;" +
                "            var courseCode = cells[1].innerText.trim();" +
                "            var courseTitle = cells[2].innerText.trim();" +
                "            var courseType = cells[3].innerText.trim();" +
                "            var slot = cells[4].innerText.trim().split('+')[0].trim();" +
                "            var viewLink = cells[13].getElementsByTagName('a')[0];" +
                "            if (viewLink && viewLink.getAttribute('onclick')) {" +
                "                var onclick = viewLink.getAttribute('onclick');" +
                "                var match = onclick.match(/processViewAttendanceDetail\\('([^']+)','([^']+)'\\)/);" +
                "                if (match) {" +
                "                    response.courses.push({" +
                "                        courseCode: courseCode," +
                "                        courseTitle: courseTitle," +
                "                        courseType: courseType," +
                "                        slot: slot," +
                "                        classId: match[1]," +
                "                        slotName: match[2]" +
                "                    });" +
                "                }" +
                "            }" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray coursesArray = response.getJSONArray("courses");

                        if (coursesArray.length() == 0) {
                            // No detailed attendance data available, proceed to marks
                            downloadMarks();
                            return;
                        }

                        // Process each course's detailed attendance
                        processDetailedAttendanceForCourses(coursesArray, 0);

                    } catch (Exception e) {
                        android.util.Log.e("VTOPService", "Error processing detailed attendance courses", e);
                        downloadMarks(); // Continue with marks download on error
                    }
                });
    }

    /**
     * Recursively process detailed attendance for each course
     */
    private void processDetailedAttendanceForCourses(JSONArray coursesArray, int index) {
        if (index >= coursesArray.length()) {
            // All courses processed, now add fallback OD estimates for missing courses
            addFallbackODEstimatesForMissingCourses(() -> {
                // Then broadcast final OD count update
                broadcastODCountUpdate();
                // Proceed to marks download
                downloadMarks();
            });
            return;
        }

        try {
            JSONObject courseInfo = coursesArray.getJSONObject(index);
            String classId = courseInfo.getString("classId");
            String slotName = courseInfo.getString("slotName");
            String slot = courseInfo.getString("slot");
            String courseType = courseInfo.getString("courseType");

            // Get course ID
            int courseTypeInt = Course.TYPE_THEORY;
            if (courseType.toLowerCase().contains("lab")) {
                courseTypeInt = Course.TYPE_LAB;
            }
            Integer courseId = getCourseId(slot, courseTypeInt);

            if (courseId == null) {
                // Skip this course if we can't find the courseId
                processDetailedAttendanceForCourses(coursesArray, index + 1);
                return;
            }

            // Fetch detailed attendance for this course
            fetchDetailedAttendanceForCourse(classId, slotName, courseId, () -> {
                // Continue with next course
                processDetailedAttendanceForCourses(coursesArray, index + 1);
            });

        } catch (Exception e) {
            android.util.Log.e("VTOPService", "Error processing course at index " + index, e);
            // Continue with next course on error
            processDetailedAttendanceForCourses(coursesArray, index + 1);
        }
    }

    /**
     * Broadcast OD count update after processing detailed attendance
     */
    private void broadcastODCountUpdate() {
        AppDatabase appDatabase = AppDatabase.getInstance(this);
        // Fetch records, courses, and slots to compute weighted OD count
        io.reactivex.rxjava3.core.Single.zip(
                appDatabase.attendanceRecordDao().getAll(),
                appDatabase.coursesDao().getAllCourses(),
                appDatabase.coursesDao().getAllSlots(),
                (records, courses, slots) -> new Object[] { records, courses, slots })
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    @SuppressWarnings("unchecked")
                    java.util.List<AttendanceRecord> records = (java.util.List<AttendanceRecord>) ((Object[]) result)[0];
                    @SuppressWarnings("unchecked")
                    java.util.List<com.salmanmalvasi.studentcc.models.Course> courses = (java.util.List<com.salmanmalvasi.studentcc.models.Course>) ((Object[]) result)[1];
                    @SuppressWarnings("unchecked")
                    java.util.List<com.salmanmalvasi.studentcc.models.Slot> slots = (java.util.List<com.salmanmalvasi.studentcc.models.Slot>) ((Object[]) result)[2];

                    java.util.Map<Integer, String> courseCodeById = new java.util.HashMap<>();
                    for (com.salmanmalvasi.studentcc.models.Course c : courses) {
                        courseCodeById.put(c.id, c.code != null ? c.code : "");
                    }
                    java.util.Map<Integer, java.util.List<String>> slotsByCourseId = new java.util.HashMap<>();
                    for (com.salmanmalvasi.studentcc.models.Slot s : slots) {
                        if (s.courseId != null && s.slot != null && !s.slot.isEmpty()) {
                            slotsByCourseId.computeIfAbsent(s.courseId, k -> new java.util.ArrayList<>()).add(s.slot);
                        }
                    }

                    java.util.Map<String, Integer> weightByCourseDate = new java.util.HashMap<>();
                    boolean debugOd = true;
                    for (AttendanceRecord record : records) {
                        if (record.status == null)
                            continue;
                        String status = record.status.trim().toLowerCase();
                        boolean isOD = status.equals("od") || status.equals("on duty") || status.contains("od")
                                || status.contains("on duty") || status.contains("duty");
                        if (!isOD)
                            continue;

                        String dateKey = record.dateString != null ? record.dateString : String.valueOf(record.date);
                        String key = (record.courseId != null ? record.courseId : -1) + "|" + dateKey;

                        int weight = 1;
                        String reason = "lecture-default";
                        if (record.courseId != null) {
                            String code = courseCodeById.getOrDefault(record.courseId, "");
                            java.util.List<String> courseSlots = slotsByCourseId.get(record.courseId);

                            boolean isLabDouble = code != null && code.endsWith("P") && !(code.startsWith("BSTS"));
                            boolean eActsAsLab = false;
                            if (!isLabDouble && code != null && code.endsWith("E") && courseSlots != null) {
                                for (String raw : courseSlots) {
                                    String s = raw.replaceAll("\\s", "");
                                    if (s.matches(".*L\\d+.*")) {
                                        eActsAsLab = true;
                                    }
                                }
                            }

                            if (isLabDouble || eActsAsLab) {
                                weight = 2;
                                reason = isLabDouble ? "code-P" : "E-acts-as-Lab(Lx)";
                            }
                        }
                        if (debugOd) {
                            String codeLog = record.courseId != null ? courseCodeById.get(record.courseId) : "null";
                            java.util.List<String> slotLog = record.courseId != null
                                    ? slotsByCourseId.get(record.courseId)
                                    : null;
                            android.util.Log.d("ODCalc",
                                    "OD key=" + key + ", code=" + codeLog + ", slots=" + String.valueOf(slotLog)
                                            + ", status=" + status + ", weight=" + weight + ", reason=" + reason);
                        }
                        Integer prev = weightByCourseDate.get(key);
                        if (prev == null || weight > prev) {
                            weightByCourseDate.put(key, weight);
                        }
                    }

                    int odUsedWeighted = 0;
                    for (Integer w : weightByCourseDate.values()) {
                        odUsedWeighted += (w != null ? w : 0);
                    }

                    if (debugOd) {
                        android.util.Log.d("ODCalc", "Final weighted OD used=" + odUsedWeighted + ", unique keys="
                                + weightByCourseDate.size());
                    }

                    Intent intent = new Intent("OD_COUNT_UPDATED");
                    intent.putExtra("odUsed", odUsedWeighted);
                    sendBroadcast(intent);
                }, throwable -> {
                    android.util.Log.e("VTOPService", "Error calculating weighted OD count", throwable);
                });
    }

    /**
     * Add fallback OD estimates for courses that don't have detailed attendance
     * links
     */
    private void addFallbackODEstimatesForMissingCourses(Runnable onComplete) {
        // Get current detailed attendance records count
        AppDatabase appDatabase = AppDatabase.getInstance(this);
        appDatabase.attendanceRecordDao().getAll()
                .subscribeOn(Schedulers.single())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(currentRecords -> {
                    // Get total classes from main attendance
                    android.content.SharedPreferences sharedPreferences = android.preference.PreferenceManager
                            .getDefaultSharedPreferences(this);
                    int totalClassesFromMain = sharedPreferences.getInt("totalClasses", 0);
                    int attendedClassesFromMain = sharedPreferences.getInt("attendedClasses", 0);

                    int currentDetailedRecords = currentRecords.size();
                    int missingRecords = totalClassesFromMain - currentDetailedRecords;

                    if (missingRecords > 0) {
                        // For missing records, we can estimate that some might be OD
                        // Based on the pattern from detailed records, estimate OD percentage
                        int currentODCount = 0;
                        for (AttendanceRecord record : currentRecords) {
                            if (record.status != null && record.status.trim().toLowerCase().equals("on duty")) {
                                currentODCount++;
                            }
                        }

                        double odPercentageInDetailed = currentDetailedRecords > 0
                                ? (double) currentODCount / currentDetailedRecords
                                : 0.1; // fallback 10%
                        int estimatedAdditionalOD = (int) Math.round(missingRecords * odPercentageInDetailed);

                        // Create dummy OD records for the estimated count
                        if (estimatedAdditionalOD > 0) {
                            List<AttendanceRecord> estimatedODRecords = new ArrayList<>();
                            for (int i = 0; i < estimatedAdditionalOD; i++) {
                                AttendanceRecord odRecord = new AttendanceRecord();
                                odRecord.courseId = -1; // Special ID for estimated records
                                odRecord.status = "On Duty";
                                odRecord.session = "Estimated";
                                odRecord.dateString = "Estimated-" + (i + 1);
                                odRecord.date = System.currentTimeMillis();
                                odRecord.remarks = "Estimated OD from course without detailed attendance";
                                estimatedODRecords.add(odRecord);
                            }

                            // Insert estimated records
                            appDatabase.attendanceRecordDao().insertAll(estimatedODRecords)
                                    .subscribeOn(Schedulers.single())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(() -> {
                                        onComplete.run();
                                    }, error -> {
                                        android.util.Log.e("VTOPService", "Error adding estimated OD records", error);
                                        onComplete.run();
                                    });
                        } else {
                            onComplete.run();
                        }
                    } else {
                        onComplete.run();
                    }
                }, error -> {
                    android.util.Log.e("VTOPService", "Error checking missing courses", error);
                    onComplete.run();
                });
    }

    /**
     * Fetch detailed attendance for a specific course
     */
    private void fetchDetailedAttendanceForCourse(String classId, String slotName, Integer courseId,
            Runnable onComplete) {
        webView.evaluateJavascript("(function() {" +
                "var data = '_csrf=' + $('input[name=\"_csrf\"]').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&classId=' + '"
                + classId + "' + '&slotName=' + '" + slotName + "';" +
                "var response = {" +
                "    records: []" +
                "};" +
                "$.ajax({" +
                "    type : 'POST'," +
                "    url : 'processViewAttendanceDetail'," +
                "    data : data," +
                "    async: false," +
                "    success : function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var tables = doc.getElementsByTagName('table');" +
                "        for (var t = 0; t < tables.length; t++) {" +
                "            var table = tables[t];" +
                "            if (table.className && table.className.includes('table')) {" +
                "                var rows = table.getElementsByTagName('tr');" +
                "                for (var i = 1; i < rows.length; i++) {" +
                "                    var cells = rows[i].getElementsByTagName('td');" +
                "                    if (cells.length >= 5) {" +
                "                        var dateStr = cells[1].innerText.trim();" +
                "                        var status = cells[4].innerText.trim();" +
                "                        if (dateStr && status && dateStr !== 'Date') {" +
                "                            response.records.push({" +
                "                                date: dateStr," +
                "                                status: status" +
                "                            });" +
                "                        }" +
                "                    }" +
                "                }" +
                "                break;" +
                "            }" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray recordsArray = response.getJSONArray("records");
                        List<AttendanceRecord> attendanceRecords = new ArrayList<>();

                        for (int i = 0; i < recordsArray.length(); i++) {
                            JSONObject recordObj = recordsArray.getJSONObject(i);

                            AttendanceRecord record = new AttendanceRecord();
                            record.courseId = courseId;
                            record.dateString = recordObj.getString("date");
                            record.status = recordObj.getString("status");
                            // Store slot name to differentiate E-course lecture vs lab in detailed view
                            try {
                                record.remarks = slotName;
                            } catch (Exception ignored) {
                            }

                            // Try to parse date
                            try {
                                SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
                                Date parsedDate = dateFormat.parse(record.dateString);
                                if (parsedDate != null) {
                                    record.date = parsedDate.getTime();
                                }
                            } catch (Exception dateE) {
                                // If date parsing fails, use current time as fallback
                                record.date = System.currentTimeMillis();
                            }

                            // Set session info based on slot
                            try {
                                if (slotName != null && slotName.replaceAll("\\s", "").matches(".*L\\d+.*")) {
                                    record.session = "Lab";
                                } else {
                                    record.session = "Class";
                                }
                            } catch (Exception ignored) {
                                record.session = "Class";
                            }

                            attendanceRecords.add(record);
                        }

                        if (!attendanceRecords.isEmpty()) {
                            // Save detailed attendance records to database
                            AttendanceRecordDao attendanceRecordDao = appDatabase.attendanceRecordDao();
                            Observable<Object> clearObservable = Observable
                                    .fromCompletable(attendanceRecordDao.clearByCourse(courseId));
                            Observable<Object> insertObservable = Observable
                                    .fromCompletable(attendanceRecordDao.insertAll(attendanceRecords));

                            Observable
                                    .concat(clearObservable, insertObservable)
                                    .subscribeOn(Schedulers.single())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new Observer<Object>() {
                                        @Override
                                        public void onSubscribe(@NonNull Disposable d) {
                                        }

                                        @Override
                                        public void onNext(@NonNull Object o) {
                                        }

                                        @Override
                                        public void onError(@NonNull Throwable e) {
                                            android.util.Log.e("VTOPService", "Error saving detailed attendance", e);
                                            onComplete.run(); // Continue even on error
                                        }

                                        @Override
                                        public void onComplete() {
                                            android.util.Log.d("VTOPService", "Saved " + attendanceRecords.size()
                                                    + " detailed attendance records for course " + courseId);
                                            onComplete.run();
                                        }
                                    });
                        } else {
                            onComplete.run();
                        }

                    } catch (Exception e) {
                        android.util.Log.e("VTOPService", "Error processing detailed attendance response", e);
                        onComplete.run(); // Continue even on error
                    }
                });
    }

    /**
     * Function to download the marks.
     */
    private void downloadMarks() {
        updateProgress(R.string.downloading_marks);

        // Check if WebView is still valid before proceeding
        if (webView == null || isWebViewDestroyed) {
            if (isQuickSync) {
                finishUp();
                return;
            }
            android.util.Log.w("VTOPService",
                    "WebView is null or destroyed, skipping marks and continuing with grades");
            downloadGrades();
            return;
        }

        /*
         * JSON response format
         *
         * {
         * "marks": [
         * {
         * "slot": "A1",
         * "course_type": "Theory Only",
         * "title": "CAT 1",
         * "score": 26,
         * "max_score": 30,
         * "weightage": 13,
         * "max_weightage": 15,
         * "average": null
         * "status": "Present"
         * },
         * ...
         * ]
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'semesterSubId=' + '" + semesterID
                + "' + '&authorizedID=' + $('#authorizedIDX').val()  + '&_csrf=' + $('input[name=\"_csrf\"]').val();" +
                "var response = {" +
                "    marks: []" +
                "};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'examinations/doStudentMarkView'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        if(res.toLowerCase().includes('no data found')) {" +
                "            return;" +
                "        }" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var table = doc.getElementById('fixedTableContainer');" +
                "        var rows = table.getElementsByTagName('tr');" +
                "        var headings = rows[0].getElementsByTagName('td');" +
                "        var courseTypeIndex, slotIndex;" +
                "        for (var i = 0; i < headings.length; ++i) {" +
                "            var heading = headings[i].innerText.toLowerCase();" +
                "            if (heading.includes('course') && heading.includes('type')) {" +
                "                courseTypeIndex = i;" +
                "            } else if (heading.includes('slot')) {" +
                "                slotIndex = i;" +
                "            }" +
                "        }" +
                "        for (var i = 1; i < rows.length; ++i) {" +
                "            var rawCourseType = rows[i].getElementsByTagName('td')[courseTypeIndex].innerText.trim().toLowerCase();"
                +
                "            var courseType = (rawCourseType.includes('lab')) ? 'lab' : ((rawCourseType.includes('project')) ? 'project' : 'theory');"
                +
                "            var slot = rows[i++].getElementsByTagName('td')[slotIndex].innerText.split('+')[0].trim();"
                +
                "            var innerTable = rows[i].getElementsByTagName('table')[0];" +
                "            var innerRows = innerTable.getElementsByTagName('tr');" +
                "            var innerHeadings = innerRows[0].getElementsByTagName('td');" +
                "            var titleIndex, scoreIndex, maxScoreIndex, weightageIndex, maxWeightageIndex, averageIndex, statusIndex;"
                +
                "            for (var j = 0; j < innerHeadings.length; ++j) {" +
                "                var innerHeading = innerHeadings[j].innerText.toLowerCase();" +
                "                if (innerHeading.includes('title')) {" +
                "                    titleIndex = j + innerHeadings.length;" +
                "                } else if (innerHeading.includes('max')) {" +
                "                    maxScoreIndex = j + innerHeadings.length;" +
                "                } else if (innerHeading.includes('%')) {" +
                "                    maxWeightageIndex = j + innerHeadings.length;" +
                "                } else if (innerHeading.includes('status')) {" +
                "                    statusIndex = j + innerHeadings.length;" +
                "                } else if (innerHeading.includes('scored')) {" +
                "                    scoreIndex = j + innerHeadings.length;" +
                "                } else if (innerHeading.includes('weightage') && innerHeading.includes('mark')) {" +
                "                    weightageIndex = j + innerHeadings.length;" +
                "                } else if (innerHeading.includes('average')) {" +
                "                    averageIndex = j + innerHeadings.length;" +
                "                }" +
                "            }" +
                "            var innerCells = innerTable.getElementsByTagName('td');" +
                "            while(titleIndex < innerCells.length && scoreIndex < innerCells.length && maxScoreIndex < innerCells.length && weightageIndex < innerCells.length && maxWeightageIndex < innerCells.length && averageIndex < innerCells.length && statusIndex < innerCells.length) {"
                +
                "                var mark = {};" +
                "                mark.slot = slot;" +
                "                mark.course_type = courseType;" +
                "                mark.title = innerCells[titleIndex].innerText.trim();" +
                "                mark.score = parseFloat(innerCells[scoreIndex].innerText) || 0;" +
                "                mark.max_score = parseFloat(innerCells[maxScoreIndex].innerText) || null;" +
                "                mark.weightage = parseFloat(innerCells[weightageIndex].innerText) || 0;" +
                "                mark.max_weightage = parseFloat(innerCells[maxWeightageIndex].innerText) || null;" +
                "                mark.average = parseFloat(innerCells[averageIndex].innerText) || null;" +
                "                mark.status = innerCells[statusIndex].innerText.trim();" +
                "                response.marks.push(mark);" +
                "                titleIndex += innerHeadings.length;" +
                "                scoreIndex += innerHeadings.length;" +
                "                maxScoreIndex += innerHeadings.length;" +
                "                weightageIndex += innerHeadings.length;" +
                "                maxWeightageIndex += innerHeadings.length;" +
                "                averageIndex += innerHeadings.length;" +
                "                statusIndex += innerHeadings.length;" +
                "            }" +
                "            i += innerRows.length;" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray marksArray = response.getJSONArray("marks");
                        Map<Integer, Mark> marks = new HashMap<>();

                        this.cumulativeMarks = new HashMap<>();

                        for (int i = 0, j = 0; i < marksArray.length(); ++i) {
                            JSONObject markObject = marksArray.getJSONObject(i);
                            Mark mark = new Mark();

                            int courseType = Course.TYPE_THEORY;

                            if (markObject.getString("course_type").equals("lab")) {
                                courseType = Course.TYPE_LAB;
                            } else if (markObject.getString("course_type").equals("project")) {
                                courseType = Course.TYPE_PROJECT;
                            }

                            mark.id = i + 1;
                            mark.courseId = this.getCourseId(markObject.getString("slot"), courseType);

                            // Skip marks that can't be associated with courses (e.g., courses with null
                            // slots)
                            if (mark.courseId == null) {
                                continue;
                            }

                            mark.title = this.getStringValue(markObject, "title");
                            mark.score = this.getDoubleValue(markObject, "score");
                            mark.maxScore = this.getDoubleValue(markObject, "max_score");
                            mark.weightage = this.getDoubleValue(markObject, "weightage");
                            mark.maxWeightage = this.getDoubleValue(markObject, "max_weightage");
                            mark.average = this.getDoubleValue(markObject, "average");
                            mark.status = this.getStringValue(markObject, "status");

                            String courseCode = this.getCourseCode(mark.courseId, courseType);
                            Double courseCredits = this.getCourseCredits(mark.courseId, courseType);

                            // Handle Wildlife Ecology course transfer - if course code is CF0C575M,
                            // find the first available course with a different (non-null) course code
                            if ("CF0C575M".equals(courseCode)) {
                                // Look for Wildlife Ecology courses with different course codes
                                Integer alternativeCourseId = null;
                                String alternativeCourseCode = null;

                                // Check theory courses first
                                for (Map.Entry<Integer, Course> entry : this.theoryCourses.entrySet()) {
                                    Course course = entry.getValue();
                                    if ("Wildlife Ecology".equals(course.title) &&
                                            course.code != null &&
                                            !course.code.equals("CF0C575M") &&
                                            !course.code.trim().isEmpty()) {
                                        alternativeCourseId = entry.getKey();
                                        alternativeCourseCode = course.code;
                                        courseCredits = course.credits;
                                        break;
                                    }
                                }

                                // If not found in theory, check lab courses
                                if (alternativeCourseId == null) {
                                    for (Map.Entry<Integer, Course> entry : this.labCourses.entrySet()) {
                                        Course course = entry.getValue();
                                        if ("Wildlife Ecology".equals(course.title) &&
                                                course.code != null &&
                                                !course.code.equals("CF0C575M") &&
                                                !course.code.trim().isEmpty()) {
                                            alternativeCourseId = entry.getKey();
                                            alternativeCourseCode = course.code;
                                            courseCredits = course.credits;
                                            break;
                                        }
                                    }
                                }

                                // If alternative course found, transfer the marks
                                if (alternativeCourseId != null && alternativeCourseCode != null) {
                                    mark.courseId = alternativeCourseId;
                                    courseCode = alternativeCourseCode;
                                }
                            }

                            if (!this.cumulativeMarks.containsKey(courseCode)) {
                                this.cumulativeMarks.put(courseCode, new CumulativeMark(++j));
                            }

                            Objects.requireNonNull(this.cumulativeMarks.get(courseCode)).courseCode = courseCode;
                            Objects.requireNonNull(this.cumulativeMarks.get(courseCode)).addWeightage(mark.weightage,
                                    mark.maxWeightage, courseType, courseCredits);

                            // Generating a unique hash signature to keep a track of read marks
                            mark.signature = (courseCode + markObject.getString("course_type") + mark.title
                                    + mark.score).hashCode();
                            marks.put(mark.signature, mark);
                        }

                        for (Map.Entry<String, CumulativeMark> cumulativeMark : this.cumulativeMarks.entrySet()) {
                            Double theoryTotal = cumulativeMark.getValue().theoryTotal;
                            Double labTotal = cumulativeMark.getValue().labTotal;
                            Double projectTotal = cumulativeMark.getValue().projectTotal;

                            Double theoryMax = cumulativeMark.getValue().theoryMax;
                            Double labMax = cumulativeMark.getValue().labMax;
                            Double projectMax = cumulativeMark.getValue().projectMax;

                            if (theoryTotal == null) {
                                theoryTotal = (double) 0;
                                theoryMax = (double) 0;
                            }

                            if (labTotal == null) {
                                labTotal = (double) 0;
                                labMax = (double) 0;
                            }

                            if (projectTotal == null) {
                                projectTotal = (double) 0;
                                projectMax = (double) 0;
                            }

                            double theoryCredits = cumulativeMark.getValue().theoryCredits;
                            double labCredits = cumulativeMark.getValue().labCredits;
                            double projectCredits = cumulativeMark.getValue().projectCredits;

                            double grandTotal = (theoryTotal * theoryCredits + labTotal * labCredits
                                    + projectTotal * projectCredits);
                            double grandMax = (theoryMax * theoryCredits + labMax * labCredits
                                    + projectMax * projectCredits);

                            grandTotal /= theoryCredits + labCredits + projectCredits;
                            grandMax /= theoryCredits + labCredits + projectCredits;

                            Objects.requireNonNull(
                                    this.cumulativeMarks.get(cumulativeMark.getKey())).grandTotal = grandTotal;
                            Objects.requireNonNull(
                                    this.cumulativeMarks.get(cumulativeMark.getKey())).grandMax = grandMax;
                        }

                        appDatabase.marksDao()
                                .insertMarks(marks)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new CompletableObserver() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onComplete() {
                                        if (isQuickSync) {
                                            android.util.Log.i("VTOPService",
                                                    "QuickSync completed: Attendance, Detailed Attendance, and Marks updated.");
                                            finishUp();
                                        } else {
                                            downloadGrades();
                                        }
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        if (isQuickSync) {
                                            android.util.Log.w("VTOPService",
                                                    "QuickSync marks save error: " + e.getMessage());
                                            finishUp();
                                        } else {
                                            error(702, e.getLocalizedMessage());
                                        }
                                    }
                                });
                    } catch (Exception e) {
                        if (isQuickSync) {
                            android.util.Log.w("VTOPService", "QuickSync marks exception: " + e.getMessage());
                            finishUp();
                        } else {
                            error(701, e.getLocalizedMessage());
                        }
                    }
                });
    }

    /**
     * Function to download the grades.
     */
    private void downloadGrades() {
        updateProgress(null);

        /*
         * JSON response format
         *
         * {
         * "grades": [
         * {
         * "course_code": "CSE1001",
         * "grade": "S"
         * },
         * ...
         * ],
         * "gpa": 8.58
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'semesterSubId=' + '" + semesterID
                + "' + '&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val();" +
                "var response = {" +
                "    grades: []," +
                "    gpa: null" +
                "};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'examinations/examGradeView/doStudentGradeView'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        if(res.toLowerCase().includes('no records')) {" +
                "            return;" +
                "        }" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var table = doc.getElementsByTagName('table')[0];" +
                "        var headings = table.getElementsByTagName('th');" +
                "        var courseCodeIndex, gradeIndex, creditsIndex, creditsSpan;" +
                "        for (var i = 0; i < headings.length; ++i) {" +
                "            var heading = headings[i].innerText.toLowerCase();" +
                "            if (heading.includes('code')) {" +
                "                courseCodeIndex = i;" +
                "            } else if (heading.includes('credits')) {" +
                "                creditsIndex = i;" +
                "                creditsSpan = headings[i].colSpan;" +
                "            } else if (heading.includes('grade')) {" +
                "                gradeIndex = i;" +
                "            }" +
                "        }" +
                "        if (courseCodeIndex > creditsIndex) {" +
                "            courseCodeIndex += creditsSpan - 1;" +
                "        }" +
                "        if (gradeIndex > creditsIndex) {" +
                "            gradeIndex += creditsSpan - 1;" +
                "        }" +
                "        var cells = table.getElementsByTagName('td');" +
                "        while(courseCodeIndex < cells.length && gradeIndex < cells.length) {" +
                "            var grade = {};" +
                "            grade.course_code = cells[courseCodeIndex].innerText.trim();" +
                "            grade.grade = cells[gradeIndex].innerText.trim();" +
                "            response.grades.push(grade);" +
                "            courseCodeIndex += headings.length - 1;" +
                "            gradeIndex += headings.length - 1;" +
                "        }" +
                "        response.gpa = cells[cells.length - 1].innerText.split(':')[1].trim();" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray gradesArray = response.getJSONArray("grades");

                        for (int i = 0; i < gradesArray.length(); ++i) {
                            JSONObject gradesObject = gradesArray.getJSONObject(i);

                            String courseCode = this.getStringValue(gradesObject, "course_code");

                            if (this.cumulativeMarks.containsKey(courseCode)) {
                                Objects.requireNonNull(this.cumulativeMarks.get(courseCode)).grade = gradesObject
                                        .getString("grade");
                            }
                        }

                        this.sharedPreferences.edit().putString("gpa", response.getString("gpa")).apply();

                        List<CumulativeMark> cumulativeMarks = new ArrayList<>(this.cumulativeMarks.values());
                        MarksDao marksDao = this.appDatabase.marksDao();

                        Observable<Object> deleteAllObservable = Observable
                                .fromCompletable(marksDao.deleteCumulativeMarks());
                        Observable<Object> insertCumulativeMarks = Observable
                                .fromCompletable(marksDao.insertCumulativeMarks(cumulativeMarks));

                        Observable
                                .concat(
                                        deleteAllObservable,
                                        insertCumulativeMarks)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<Object>() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onNext(@NonNull Object o) {
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(802, e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onComplete() {
                                        downloadExamSchedule();
                                    }
                                });
                    } catch (Exception e) {
                        error(801, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to download the exam schedule.
     */
    private void downloadExamSchedule() {
        updateProgress(R.string.downloading_exam_schedule);

        /*
         * JSON response format
         *
         * {
         * "FAT": [
         * {
         * "slot": "A1",
         * "date": "01-JAN-2020",
         * "start_time": "9:30 AM",
         * "end_time": "12:30 PM",
         * "venue": "DB-101",
         * "seat_location": "R1C1",
         * "seat_number": 1
         * },
         * ...
         * ],
         * ...
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'semesterSubId=' + '" + semesterID
                + "' + '&authorizedID=' + $('#authorizedIDX').val()  + '&_csrf=' + $('input[name=\"_csrf\"]').val();" +
                "var response = {};" +
                "try {" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url: 'examinations/doSearchExamScheduleForStudent'," +
                "        data: data," +
                "        async: false," +
                "        success: function(res) {" +
                "            if(!res || res.toLowerCase().includes('not found') || res.toLowerCase().includes('max concurrent') || res.toLowerCase().includes('error')) {" +
                "                return;" +
                "            }" +
                "            var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "            var trs = doc.getElementsByTagName('tr');" +
                "            if (!trs || trs.length === 0) return;" +
                "            var columns = trs[0].getElementsByTagName('td');" +
                "            if (!columns || columns.length === 0) return;" +
                "            var slotIndex, dateIndex, timingIndex, venueIndex, locationIndex, numberIndex;" +
                "            for (var i = 0; i < columns.length; ++i) {" +
                "                var heading = columns[i].innerText.toLowerCase();" +
                "                if (heading.includes('slot')) {" +
                "                    slotIndex = i;" +
                "                } else if (heading.includes('date')) {" +
                "                    dateIndex = i;" +
                "                } else if (heading.includes('exam') && heading.includes('time')) {" +
                "                    timingIndex = i;" +
                "                } else if (heading.includes('venue')) {" +
                "                    venueIndex = i;" +
                "                } else if (heading.includes('location')) {" +
                "                    locationIndex = i;" +
                "                } else if (heading.includes('seat') && heading.includes('no.')) {" +
                "                    numberIndex = i;" +
                "                }" +
                "            }" +
                "            var examTitle = '', exam = {}, cells = doc.getElementsByTagName('td');" +
                "            for (var i = columns.length; i < cells.length; ++i) {" +
                "                if (cells[i].colSpan > 1) {" +
                "                    examTitle = cells[i].innerText.trim();" +
                "                    response[examTitle] = [];" +
                "                    continue;" +
                "                }" +
                "                var index = (i - Object.keys(response).length) % columns.length;" +
                "                if (index == slotIndex) {" +
                "                    exam.slot = cells[i].innerText.trim().split('+')[0];" +
                "                } else if (index == dateIndex) {" +
                "                    var date = cells[i].innerText.trim().toUpperCase();" +
                "                    exam.date = date == '' ? null : date;" +
                "                } else if (index == timingIndex) {" +
                "                    var timings = cells[i].innerText.trim().split('-');" +
                "                    if (timings.length == 2) {" +
                "                        exam.start_time = timings[0].trim();" +
                "                        exam.end_time = timings[1].trim();" +
                "                    } else {" +
                "                        exam.start_time = null;" +
                "                        exam.end_time = null;" +
                "                    }" +
                "                } else if (index == venueIndex) {" +
                "                    var venue = cells[i].innerText.trim();" +
                "                    exam.venue = venue.replace(/-/g,'') == '' ? null : venue;" +
                "                } else if (index == locationIndex) {" +
                "                    var location = cells[i].innerText.trim();" +
                "                    exam.seat_location = location.replace(/-/g,'') == '' ? null : location;" +
                "                } else if (index == numberIndex) {" +
                "                    var number = cells[i].innerText.trim();" +
                "                    exam.seat_number = number.replace(/-/g,'') == '' ? null : parseInt(number);" +
                "                }" +
                "                if (Object.keys(exam).length == 7) {" +
                "                    response[examTitle].push(exam);" +
                "                    exam = {};" +
                "                }" +
                "            }" +
                "        }," +
                "        error: function(xhr, status, error) {" +
                "            // Server congested or returned error; ignore gracefully" +
                "        }" +
                "    });" +
                "} catch(e) {}" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        if (responseString == null || responseString.trim().isEmpty() || responseString.equals("null")) {
                            android.util.Log.i("VTOPService", "Exam schedule empty, continuing to proctor.");
                            downloadProctor();
                            return;
                        }
                        JSONObject response = new JSONObject(responseString);
                        Iterator<String> keys = response.keys();
                        List<Exam> exams = new ArrayList<>();

                        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);
                        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd-MMM-yyyy hh:mm a", Locale.ENGLISH);

                        int index = 1;

                        while (keys.hasNext()) {
                            String key = keys.next();
                            JSONArray examsArray = response.getJSONArray(key);

                            for (int i = 0; i < examsArray.length(); ++i) {
                                JSONObject examObject = examsArray.getJSONObject(i);
                                Exam exam = new Exam();
                                Pattern pattern = Pattern.compile("\\d");
                                Matcher matcher = pattern.matcher(key);

                                exam.id = ++index;
                                exam.courseId = getCourseId(examObject.getString("slot"), Course.TYPE_THEORY);
                                exam.title = key;

                                if (matcher.find()) {
                                    exam.title = new StringBuilder(key).insert(matcher.start(), " ").toString().trim()
                                            .replaceAll(" +", " ");
                                }

                                if (!examObject.isNull("date")) {
                                    if (!examObject.isNull("start_time")) {
                                        exam.startTime = Objects
                                                .requireNonNull(dateTimeFormat.parse(examObject.getString("date") + " "
                                                        + examObject.getString("start_time")))
                                                .getTime();
                                    } else {
                                        exam.startTime = Objects
                                                .requireNonNull(dateFormat.parse(examObject.getString("date")))
                                                .getTime();
                                    }

                                    if (!examObject.isNull("end_time")) {
                                        exam.endTime = Objects.requireNonNull(dateTimeFormat.parse(
                                                examObject.getString("date") + " " + examObject.getString("end_time")))
                                                .getTime();
                                    }
                                }

                                exam.venue = getStringValue(examObject, "venue");
                                exam.seatLocation = getStringValue(examObject, "seat_location");
                                exam.seatNumber = getIntegerValue(examObject, "seat_number");

                                exams.add(exam);
                            }
                        }

                        SettingsRepository.setExamNotifications(this.getApplicationContext(), exams);

                        ExamsDao examsDao = appDatabase.examsDao();

                        Observable<Object> deleteAllObservable = Observable.fromCompletable(examsDao.deleteAll());
                        Observable<Object> insertExamsObservable = Observable.fromCompletable(examsDao.insert(exams));

                        Observable
                                .concat(
                                        deleteAllObservable,
                                        insertExamsObservable)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<Object>() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onNext(@NonNull Object o) {
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(902, e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onComplete() {
                                        downloadProctor();
                                    }
                                });
                    } catch (Exception e) {
                        android.util.Log.w("VTOPService", "Could not parse exam schedule (server limit or not available): " + e.getMessage());
                        // Exam schedule is non-critical; do not block whole sync pipeline with error 901
                        downloadProctor();
                    }
                });
    }

    /**
     * Function to download the proctor info. (1 / 2 - Staff info)
     */
    private void downloadProctor() {
        updateProgress(R.string.downloading_staff);
        /*
         * JSON response format
         *
         * {
         * "proctor" [
         * {
         * "key": "",
         * "value": ""
         * },
         * ...
         * ]
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&winImage=' + $('#winImage').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {" +
                "    proctor: []" +
                "};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'proctor/viewProctorDetails'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var cells = doc.getElementById('showDetails').getElementsByTagName('td');" +
                "        for(var i = 0; i < cells.length; ++i) {" +
                "            if(cells[i].innerHTML.includes('img')) {" +
                "                continue;" +
                "            }" +
                "            var record = {};" +
                "            record.key = cells[i].innerText.trim() || null;" +
                "            record.value = cells[++i].innerText.trim() || null;" +
                "            response.proctor.push(record);" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray proctorArray = response.getJSONArray("proctor");
                        List<Staff> staff = new ArrayList<>();

                        for (int i = 0; i < proctorArray.length(); ++i) {
                            JSONObject proctorObject = proctorArray.getJSONObject(i);
                            Staff staffItem = new Staff();

                            staffItem.id = i + 1;
                            staffItem.type = "proctor";
                            staffItem.key = this.getStringValue(proctorObject, "key");
                            staffItem.value = this.getStringValue(proctorObject, "value");

                            staff.add(staffItem);
                        }

                        StaffDao staffDao = appDatabase.staffDao();

                        Observable<Object> deleteAllObservable = Observable.fromCompletable(staffDao.deleteAll());
                        Observable<Object> insertStaffObservable = Observable.fromCompletable(staffDao.insert(staff));

                        Observable
                                .concat(
                                        deleteAllObservable,
                                        insertStaffObservable)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<Object>() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onNext(@NonNull Object o) {
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(1002, e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onComplete() {
                                        downloadDeanHOD(staff.size());
                                    }
                                });
                    } catch (Exception e) {
                        error(1001, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to download the HOD & Dean info. (2 / 2 - Staff info)
     */
    private void downloadDeanHOD(final int lastIndex) {
        updateProgress(null);
        /*
         * JSON response format
         *
         * {
         * "dean": [
         * {
         * "key": "",
         * "value": ""
         * },
         * ...
         * ],
         * "hod": [
         * {
         * "key": "",
         * "value": ""
         * },
         * ...
         * ],
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&winImage=' + $('#winImage').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'hrms/viewHodDeanDetails'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var tables = doc.getElementsByTagName('table');" +
                "        var headings = doc.getElementsByTagName('h3');" +
                "        for (var i = 0; i < tables.length; ++i) {" +
                "            var heading = headings[i].innerText.toLowerCase().trim();" +
                "            var cells = tables[i].getElementsByTagName('td');" +
                "            response[heading] = [];" +
                "            for (var j = 0; j < cells.length; ++j) {" +
                "                if(cells[j].innerHTML.includes('img')) {" +
                "                    continue;" +
                "                }" +
                "                var record = {};" +
                "                record.key = cells[j].innerText.trim() || null;" +
                "                record.value = cells[++j].innerText.trim() || null;" +
                "                response[heading].push(record);" +
                "            }" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        Iterator<String> keys = response.keys();
                        List<Staff> staff = new ArrayList<>();
                        int index = lastIndex;

                        while (keys.hasNext()) {
                            String staffType = keys.next();
                            JSONArray staffArray = response.getJSONArray(staffType);

                            if (staffType.contains("dean")) {
                                staffType = "dean";
                            } else if (staffType.contains("hod")) {
                                staffType = "hod";
                            } else {
                                staffType = staffType.toLowerCase();
                            }

                            for (int i = 0; i < staffArray.length(); ++i) {
                                JSONObject staffObject = staffArray.getJSONObject(i);
                                Staff staffItem = new Staff();

                                staffItem.id = ++index;
                                staffItem.type = staffType;
                                staffItem.key = this.getStringValue(staffObject, "key");

                                String originalEmail = this.getStringValue(staffObject, "value");
                                if (originalEmail != null) {
                                    String newEmail = EMAIL_REPLACEMENTS.get(originalEmail.toLowerCase());
                                    if (newEmail != null) {
                                        staffItem.value = newEmail;
                                    } else {
                                        staffItem.value = originalEmail;
                                    }
                                } else {
                                    staffItem.value = null;
                                }

                                staff.add(staffItem);
                            }
                        }

                        StaffDao staffDao = appDatabase.staffDao();
                        staffDao
                                .insert(staff)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new CompletableObserver() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onComplete() {
                                        downloadSpotlight();
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(1004, e.getLocalizedMessage());
                                    }
                                });
                    } catch (Exception e) {
                        error(1003, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to download the spotlight.
     */
    private void downloadSpotlight() {
        updateProgress(R.string.downloading_spotlight);

        /*
         * JSON response format
         *
         * {
         * "spotlight": [
         * {
         * "announcement": "",
         * "category": "",
         * "link": null
         * },
         * ...
         * ]
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = '_csrf=' + $('input[name=\"_csrf\"]').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&x=';"
                +
                "var response = {" +
                "    spotlight: []" +
                "};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'home'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        if(!doc.getElementsByClassName('box-info')) {" +
                "            return;" +
                "        }" +
                "        var sheets = doc.getElementsByClassName('offcanvas');" +
                "        for(var i = 0; i < sheets.length; ++i) {" +
                "            const header = sheets[i].getElementsByClassName('offcanvas-header')[0];" +
                "            const title = header.getElementsByTagName('span')[0];" +
                "            if (title === undefined) {" +
                "                continue;" +
                "            }" +
                "            const category = title.textContent;" +
                "            var announcements = sheets[i].getElementsByClassName('offcanvas-body')[0].getElementsByTagName('li');"
                +
                "            for(var j = 0; j < announcements.length; ++j) {" +
                "                var spotlightItem = {};" +
                "                spotlightItem.category = category;" +
                "                spotlightItem.announcement = announcements[j].textContent.replace(/\\t/g,'').replace(/\\n/g,' ').trim();"
                +
                "                if (announcements[j].getElementsByTagName('a').length == 0) {" +
                "                    spotlightItem.link = null;" +
                "                } else {" +
                "                    var link = announcements[j].getElementsByTagName('a')[0];" +
                "                    if(link.getAttribute('onclick')) {" +
                "                        spotlightItem.link = link.getAttribute('onclick').split('\\'')[1];" +
                "                    } else {" +
                "                        spotlightItem.link = link.href;" +
                "                    }" +
                "                }" +
                "                response.spotlight.push(spotlightItem);" +
                "            }" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray spotlightArray = response.getJSONArray("spotlight");
                        Map<Integer, Spotlight> spotlight = new HashMap<>();

                        for (int i = 0; i < spotlightArray.length(); ++i) {
                            JSONObject spotlightObject = spotlightArray.getJSONObject(i);
                            Spotlight spotlightItem = new Spotlight();

                            spotlightItem.id = i + 1;
                            spotlightItem.announcement = this.getStringValue(spotlightObject, "announcement");
                            spotlightItem.category = this.getStringValue(spotlightObject, "category");
                            spotlightItem.link = this.getStringValue(spotlightObject, "link");

                            // Generating a unique hash signature to keep a track of read announcements
                            spotlightItem.signature = (spotlightItem.announcement + spotlightItem.link).hashCode();
                            spotlight.put(spotlightItem.signature, spotlightItem);
                        }

                        appDatabase.spotlightDao()
                                .insert(spotlight)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new CompletableObserver() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onComplete() {
                                        downloadReceipts();
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(1102, e.getLocalizedMessage());
                                    }
                                });
                    } catch (Exception e) {
                        error(1101, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to download the payment receipts.
     */
    private void downloadReceipts() {
        updateProgress(R.string.downloading_receipts);

        /*
         * JSON response format
         *
         * {
         * "receipts": [
         * {
         * "number": "10067",
         * "amount": 97500,
         * "date": "14-AUG-2020"
         * }
         * ]
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&winImage=' + $('#winImage').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {" +
                "    receipts: []" +
                "};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'p2p/getReceiptsApplno'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "        var headings = doc.getElementsByTagName('tr')[0].getElementsByTagName('td');" +
                "        var cells = doc.getElementsByTagName('td');" +
                "        var receiptIndex, amountIndex, dateIndex;" +
                "        for(var i = 0; i < headings.length; ++i) {" +
                "            var heading = headings[i].innerText.toLowerCase();" +
                "            if(heading.includes('receipt')) {" +
                "                receiptIndex = i + headings.length;" +
                "            } else if (heading.includes('date')) {" +
                "                dateIndex = i + headings.length;" +
                "            } else if (heading.includes('amount')) {" +
                "                amountIndex = i + headings.length;" +
                "            }" +
                "        }" +
                "        while (receiptIndex < cells.length && amountIndex < cells.length && dateIndex < cells.length) {"
                +
                "            var receipt = {};" +
                "            receipt.number = parseInt(cells[receiptIndex].innerText.trim()) || null;" +
                "            receipt.amount = parseFloat(cells[amountIndex].innerText.trim()) || 0;" +
                "            receipt.date = cells[dateIndex].innerText.trim();" +
                "            response.receipts.push(receipt);" +
                "            receiptIndex += headings.length;" +
                "            amountIndex += headings.length;" +
                "            dateIndex += headings.length;" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        JSONArray receiptsArray = response.getJSONArray("receipts");
                        List<Receipt> receipts = new ArrayList<>();

                        for (int i = 0; i < receiptsArray.length(); ++i) {
                            JSONObject receiptsObject = receiptsArray.getJSONObject(i);
                            Receipt receipt = new Receipt();
                            SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH);

                            // If this is true, there's a web scrapping issue
                            if (receiptsObject.isNull("number")) {
                                continue;
                            }

                            String receiptDateString = this.getStringValue(receiptsObject, "date");
                            Date receiptDate = receiptDateString != null ? dateFormat.parse(receiptDateString) : null;

                            receipt.number = receiptsObject.getInt("number");
                            receipt.amount = this.getDoubleValue(receiptsObject, "amount");
                            receipt.date = receiptDate != null ? receiptDate.getTime() : 0;

                            receipts.add(receipt);
                        }

                        ReceiptsDao receiptsDao = appDatabase.receiptsDao();

                        Observable<Object> deleteAllObservable = Observable.fromCompletable(receiptsDao.deleteAll());
                        Observable<Object> insertReceiptsObservable = Observable
                                .fromCompletable(receiptsDao.insert(receipts));

                        Observable
                                .concat(
                                        deleteAllObservable,
                                        insertReceiptsObservable)
                                .subscribeOn(Schedulers.single())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(new Observer<Object>() {
                                    @Override
                                    public void onSubscribe(@NonNull Disposable d) {
                                    }

                                    @Override
                                    public void onNext(@NonNull Object o) {
                                    }

                                    @Override
                                    public void onError(@NonNull Throwable e) {
                                        error(1202, e.getLocalizedMessage());
                                    }

                                    @Override
                                    public void onComplete() {
                                        checkDues();
                                    }
                                });
                    } catch (Exception e) {
                        error(1201, e.getLocalizedMessage());
                    }
                });
    }

    /**
     * Function to check for any payment dues.
     */
    private void checkDues() {
        updateProgress(null);

        /*
         * JSON response format
         *
         * {
         * "due_payments": true|false
         * }
         */
        webView.evaluateJavascript("(function() {" +
                "var data = 'verifyMenu=true&winImage=' + $('#winImage').val() + '&authorizedID=' + $('#authorizedIDX').val() + '&_csrf=' + $('input[name=\"_csrf\"]').val() + '&nocache=@(new Date().getTime())';"
                +
                "var response = {};" +
                "$.ajax({" +
                "    type: 'POST'," +
                "    url : 'p2p/Payments'," +
                "    data : data," +
                "    async: false," +
                "    success: function(res) {" +
                "        if (res.toLowerCase().includes('no payment dues')) {" +
                "            response.due_payments = false;" +
                "        } else {" +
                "            response.due_payments = true;" +
                "        }" +
                "    }" +
                "});" +
                "return response;" +
                "})();", responseString -> {
                    try {
                        JSONObject response = new JSONObject(responseString);
                        boolean duePayments = response.getBoolean("due_payments");

                        if (duePayments) {
                            sharedPreferences.edit().putBoolean("duePayments", true).apply();
                        } else {
                            sharedPreferences.edit().remove("duePayments").apply();
                        }
                    } catch (Exception e) {
                        error(1203, e.getLocalizedMessage());
                    }

                    downloadCalendar();
                });
    }

    /**
     * Function to download the academic calendar from VTOP
     * Based on UniCC calendar.ts implementation
     */
    private void downloadCalendar() {
        updateProgress(R.string.downloading_calendar);

        // Check if WebView is still valid
        if (webView == null || isWebViewDestroyed) {
            android.util.Log.w("VTOPService", "WebView null/destroyed, skipping calendar download");
            downloadLeaveHistory();
            return;
        }

        // Determine months based on semester code
        String semCode = semesterID != null && semesterID.length() >= 8 ? semesterID.substring(semesterID.length() - 2)
                : "01";
        int startYear = 2024;
        try {
            startYear = Integer.parseInt(semesterID.substring(2, 6));
        } catch (Exception e) {
            android.util.Log.e("VTOPService", "Error parsing semester year", e);
        }
        int nextYear = startYear + 1;

        List<String> months = new ArrayList<>();
        if ("01".equals(semCode)) {
            // Fall semester
            months.add("01-JUL-" + startYear);
            months.add("01-AUG-" + startYear);
            months.add("01-SEP-" + startYear);
            months.add("01-OCT-" + startYear);
            months.add("01-NOV-" + startYear);
        } else if ("05".equals(semCode)) {
            // Winter semester
            months.add("01-DEC-" + startYear);
            months.add("01-JAN-" + nextYear);
            months.add("01-FEB-" + nextYear);
            months.add("01-MAR-" + nextYear);
            months.add("01-APR-" + nextYear);
        } else {
            // Summer semester
            months.add("01-JUN-" + nextYear);
            months.add("01-JUL-" + nextYear);
            months.add("01-AUG-" + nextYear);
        }

        // Clear existing calendar data in background thread
        io.reactivex.rxjava3.core.Completable.fromAction(() -> {
            appDatabase.calendarEventDao().clearAll();
        })
                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    // Continue with downloading calendar for each month
                    downloadCalendarForMonths(months, 0);
                }, error -> {
                    android.util.Log.e("VTOPService", "Error clearing calendar data", error);
                    // Continue anyway
                    downloadCalendarForMonths(months, 0);
                });
    }

    /**
     * Recursively download calendar for each month
     */
    private void downloadCalendarForMonths(List<String> months, int index) {
        if (index >= months.size()) {
            // All months processed, continue to leave history
            downloadLeaveHistory();
            return;
        }

        String calDate = months.get(index);
        String monthName = calDate.substring(3);

        webView.evaluateJavascript("(function() {" +
                "var data = 'authorizedID=' + $('#authorizedIDX').val() + " +
                "'&semSubId=' + '" + semesterID + "' + " +
                "'&calDate=' + '" + calDate + "' + " +
                "'&classGroupId=ALL' + " +
                "'&_csrf=' + $('input[name=\"_csrf\"]').val() + " +
                "'&x=' + Date.now();" +
                "var response = { month: '" + monthName + "', days: [], error: null };" +
                "try {" +
                "    $.ajax({" +
                "        type: 'POST'," +
                "        url: '/vtop/processViewCalendar'," +
                "        data: data," +
                "        async: false," +
                "        success: function(res) {" +
                "            try {" +
                "                var doc = new DOMParser().parseFromString(res, 'text/html');" +
                "                var title = doc.querySelector('h4');" +
                "                if (title) response.month = title.textContent.trim();" +
                "                var cells = doc.querySelectorAll('table.calendar-table tbody tr td');" +
                "                if (cells.length === 0) {" +
                "                    response.error = 'No calendar table found';" +
                "                    return;" +
                "                }" +
                "                cells.forEach(function(cell) {" +
                "                    var spans = cell.querySelectorAll('span');" +
                "                    if (spans.length == 0) return;" +
                "                    var dateText = spans[0].textContent.trim();" +
                "                    if (!dateText) return;" +
                "                    var date = parseInt(dateText, 10);" +
                "                    if (isNaN(date)) return;" +
                "                    var events = [];" +
                "                    for (var i = 1; i < spans.length; i++) {" +
                "                        var text = spans[i].textContent.trim();" +
                "                        if (!text) continue;" +
                "                        var style = spans[i].getAttribute('style') || '';" +
                "                        var colorMatch = style.match(/color:\\s*([^;]+)/);" +
                "                        var color = colorMatch ? (colorMatch[1] || ' ').trim() : '';" +
                "                        var lowerText = text.toLowerCase();" +
                "                        var isNonInstructional = lowerText.includes('no instructional') || lowerText.includes('non-instructional') || lowerText.includes('non instructional') || lowerText.includes('holiday') || lowerText.includes('vacation') || lowerText.includes('recess');"
                +
                "                        var isInstructional = !isNonInstructional && lowerText.includes('instructional');"
                +
                "                        var type = isNonInstructional ? 'Holiday' : (isInstructional ? 'Instructional Day' : 'Other');"
                +
                "                        events.push({ text: text, type: type, color: color, category: '' });" +
                "                    }" +
                "                    var structured = events.map(function(e) {" +
                "                        var match = e.text.match(/\\(([^)]+)\\)/);" +
                "                        return {" +
                "                            text: e.text," +
                "                            type: e.type," +
                "                            color: e.color," +
                "                            category: match ? (match[1] || ' ').trim() : 'General'" +
                "                        };" +
                "                    });" +
                "                    if (structured.length > 0) {" +
                "                        response.days.push({ date: date, events: structured });" +
                "                    }" +
                "                });" +
                "            } catch (e) {" +
                "                response.error = 'Parse error: ' + e.message;" +
                "            }" +
                "        }," +
                "        error: function(xhr, status, err) {" +
                "            response.error = 'AJAX error: ' + status + ' - ' + err;" +
                "        }" +
                "    });" +
                "} catch (e) {" +
                "    response.error = 'Exception: ' + e.message;" +
                "}" +
                "return JSON.stringify(response);" +
                "})();", responseString -> {
                    try {
                        // Handle case where responseString might be wrapped in quotes
                        String jsonString = responseString;
                        if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                            jsonString = jsonString.substring(1, jsonString.length() - 1)
                                    .replace("\\\"", "\"")
                                    .replace("\\\\", "\\");
                        }

                        JSONObject response = new JSONObject(jsonString);

                        // Check for errors
                        if (response.has("error") && !response.isNull("error")) {
                            String error = response.getString("error");
                            android.util.Log.e("VTOPService", "Calendar error for " + calDate + ": " + error);
                            // Continue with next month
                            downloadCalendarForMonths(months, index + 1);
                            return;
                        }

                        String month = response.getString("month");
                        JSONArray daysArray = response.getJSONArray("days");

                        // Check if we got any data
                        if (daysArray.length() == 0) {
                            android.util.Log.w("VTOPService",
                                    "No calendar events found for " + calDate + " (" + month + ")");
                            // Continue with next month
                            downloadCalendarForMonths(months, index + 1);
                            return;
                        }

                        List<CalendarEvent> calendarEvents = new ArrayList<>();

                        for (int i = 0; i < daysArray.length(); i++) {
                            JSONObject dayObj = daysArray.getJSONObject(i);
                            int date = dayObj.getInt("date");
                            JSONArray eventsArray = dayObj.getJSONArray("events");

                            for (int j = 0; j < eventsArray.length(); j++) {
                                JSONObject eventObj = eventsArray.getJSONObject(j);
                                CalendarEvent event = new CalendarEvent(
                                        month,
                                        date,
                                        eventObj.optString("text", ""),
                                        eventObj.optString("type", "Other"),
                                        eventObj.optString("color", ""),
                                        eventObj.optString("category", "General"));
                                calendarEvents.add(event);
                            }
                        }

                        // Save to database in background thread
                        io.reactivex.rxjava3.core.Completable.fromAction(() -> {
                            appDatabase.calendarEventDao().insertAll(calendarEvents);
                        })
                                .subscribeOn(io.reactivex.rxjava3.schedulers.Schedulers.io())
                                .subscribe(() -> {
                                    android.util.Log.d("VTOPService",
                                            "Saved " + calendarEvents.size() + " events for " + month);
                                    // Continue with next month on main thread
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        downloadCalendarForMonths(months, index + 1);
                                    });
                                }, error -> {
                                    android.util.Log.e("VTOPService", "Error saving calendar events", error);
                                    // Continue anyway
                                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                        downloadCalendarForMonths(months, index + 1);
                                    });
                                });

                    } catch (Exception e) {
                        android.util.Log.e("VTOPService", "Error parsing calendar for " + calDate, e);
                        android.util.Log.e("VTOPService", "Response was: " + responseString);
                        // Continue with next month even if this one fails
                        downloadCalendarForMonths(months, index + 1);
                    }
                });
    }

    /**
     * Function to download the leave/OD history for hostellers.
     * Uses VTOP hostel leave endpoints similar to UniCC implementation.
     */
    private void downloadLeaveHistory() {
        // Check if user is a hosteller - only fetch leave data for hostellers
        SharedPreferences encryptedPrefs = SettingsRepository.getEncryptedSharedPreferences(getApplicationContext());
        String studentType = encryptedPrefs != null ? encryptedPrefs.getString("student_type", "").toLowerCase().trim()
                : "";

        // User is hosteller if they are NOT a day scholar
        // (student_type could be "hosteller", "HOSTELLER", or empty if not set but user
        // has hostel tab visible)
        boolean isDayScholar = studentType.contains("day") || studentType.equals("day_scholar");
        boolean isHosteller = !isDayScholar;

        android.util.Log.d("VTOPService",
                "Leave download check - studentType: '" + studentType + "', isHosteller: " + isHosteller);

        // Always clear old leave data first (removes sample data)
        LeaveDao leaveDao = appDatabase.leaveDao();
        leaveDao.deleteAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                    android.util.Log.d("VTOPService", "Old leave data cleared");

                    if (!isHosteller) {
                        android.util.Log.d("VTOPService", "Skipping leave download - user is a day scholar");
                        finishUp();
                        return;
                    }

                    // Continue with downloading leave data for hostellers
                    downloadLeaveHistoryFromVTOP();
                }, error -> {
                    android.util.Log.e("VTOPService", "Error clearing leave data", error);
                    finishUp();
                });
    }

    /**
     * Actually fetch leave data from VTOP after clearing old data.
     */
    private void downloadLeaveHistoryFromVTOP() {
        updateProgress(R.string.downloading_leave_history);

        // Check if WebView is still valid
        if (webView == null || isWebViewDestroyed) {
            android.util.Log.w("VTOPService", "WebView null/destroyed, skipping leave download");
            finishUp();
            return;
        }

        /*
         * Two-step process following UniCC implementation:
         * 1. First call /vtop/hostels/student/leave/1 to load the leave page
         * 2. Then call /vtop/hostels/student/leave/6 with control=history to get leave
         * data
         */
        String leaveScript = "(function() {" +
                "  var authorizedID = $('#authorizedIDX').val() || $('#authorizedID').val() || '';" +
                "  var csrfToken = $('input[name=\"_csrf\"]').val() || '';" +
                "  var response = { leaves: [], debug: '' };" +
                "  if (typeof $ === 'undefined' || !$.ajax) { response.debug = 'no jQuery'; return response; }" +
                "  response.debug = 'auth:' + (authorizedID ? 'yes' : 'no');" +
                "  var step1ok = false;" +
                "  $.ajax({ type: 'POST', url: 'hostels/student/leave/1', async: false," +
                "    data: { verifyMenu: 'true', authorizedID: authorizedID, _csrf: csrfToken, nocache: String(Date.now()) },"
                +
                "    success: function(r) { step1ok = true; response.debug += ',s1:' + r.length; }," +
                "    error: function(x,s,e) { response.debug += ',s1err:' + s; }" +
                "  });" +
                "  if (!step1ok) { return response; }" +
                "  $.ajax({ type: 'POST', url: 'hostels/student/leave/6', async: false," +
                "    data: { history: '', authorizedID: authorizedID, _csrf: csrfToken, form: 'undefined', control: 'history', x: String(Date.now()) },"
                +
                "    success: function(r) {" +
                "      response.debug += ',s2:' + r.length;" +
                "      try {" +
                "        var doc = $(r);" +
                "        var tbl = doc.find('#LeaveHistoryTable');" +
                "        if (tbl.length === 0) { tbl = $(r).filter('#LeaveHistoryTable'); }" +
                "        response.debug += ',tbl:' + tbl.length;" +
                "        var rows = tbl.find('tbody tr');" +
                "        response.debug += ',rows:' + rows.length;" +
                "        rows.each(function() {" +
                "          var c = $(this).find('td');" +
                "          if (c.length >= 8) {" +
                "            response.leaves.push({" +
                "              leaveId: c.eq(1).text().trim()," +
                "              visitPlace: c.eq(2).text().trim()," +
                "              reason: c.eq(3).text().trim()," +
                "              leaveType: c.eq(4).text().trim()," +
                "              fromDate: c.eq(5).text().trim()," +
                "              toDate: c.eq(6).text().trim()," +
                "              status: c.eq(7).text().trim()," +
                "              remarks: c.length > 8 ? c.eq(8).text().trim() : ''" +
                "            });" +
                "          }" +
                "        });" +
                "      } catch(ex) { response.debug += ',err:' + ex.message; }" +
                "    }," +
                "    error: function(x,s,e) { response.debug += ',s2err:' + s; }" +
                "  });" +
                "  return response;" +
                "})();";

        webView.evaluateJavascript(leaveScript, responseString -> {
            try {
                android.util.Log.d("VTOPService", "Leave response: " + responseString);

                if (responseString == null || responseString.equals("null") || responseString.isEmpty()) {
                    android.util.Log.w("VTOPService", "No leave data received");
                    finishUp();
                    return;
                }

                JSONObject response = new JSONObject(responseString);

                // Log debug info
                String debug = response.optString("debug", "no debug");
                android.util.Log.d("VTOPService", "Leave debug: " + debug);

                JSONArray leavesArray = response.optJSONArray("leaves");

                if (leavesArray == null || leavesArray.length() == 0) {
                    android.util.Log.d("VTOPService", "No leave history found");
                    finishUp();
                    return;
                }

                List<Leave> leaves = new ArrayList<>();

                for (int i = 0; i < leavesArray.length(); i++) {
                    JSONObject leaveObj = leavesArray.getJSONObject(i);

                    String leaveId = getStringValue(leaveObj, "leaveId");
                    if (leaveId == null || leaveId.isEmpty()) {
                        continue;
                    }

                    String fromDate = getStringValue(leaveObj, "fromDate");
                    String toDate = getStringValue(leaveObj, "toDate");
                    // Store original VTOP leave type (e.g., "LOCAL GUARDIAN", "OUTING", "HOME
                    // TOWN")
                    String leaveType = getStringValue(leaveObj, "leaveType");
                    String reason = getStringValue(leaveObj, "reason");
                    String visitPlace = getStringValue(leaveObj, "visitPlace");
                    String status = mapLeaveStatus(getStringValue(leaveObj, "status"));
                    String remarks = getStringValue(leaveObj, "remarks");

                    Leave leave = new Leave(leaveId, fromDate, toDate, leaveType, reason, visitPlace, status, remarks);
                    leaves.add(leave);

                    android.util.Log.d("VTOPService", "Parsed leave: " + leaveId + " - " + leaveType + " - " + status);
                }

                if (leaves.isEmpty()) {
                    android.util.Log.d("VTOPService", "No valid leaves parsed");
                    finishUp();
                    return;
                }

                // Save to database (old data already cleared in downloadLeaveHistory)
                LeaveDao leaveDao = appDatabase.leaveDao();

                leaveDao.insertAll(leaves)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            android.util.Log.d("VTOPService",
                                    "Leave data saved successfully: " + leaves.size() + " records");
                            finishUp();
                        }, error -> {
                            android.util.Log.e("VTOPService", "Error saving leave data", error);
                            finishUp();
                        });

            } catch (Exception e) {
                android.util.Log.e("VTOPService", "Error parsing leave data", e);
                // Continue to finishUp even on error - leave is non-critical
                finishUp();
            }
        });
    }

    /**
     * Map VTOP leave type string to our Leave type constants.
     * VTOP types: HOME TOWN, LOCAL GUARDIAN, OUTING, SUMMER VACATION, ON DUTY,
     * MEDICAL, EMERGENCY, etc.
     */
    private String mapLeaveType(String vtopType) {
        if (vtopType == null)
            return Leave.TYPE_OTHER;
        String type = vtopType.toUpperCase().trim();
        // Home related
        if (type.contains("HOME"))
            return Leave.TYPE_HOME_LEAVE;
        if (type.contains("VACATION"))
            return Leave.TYPE_HOME_LEAVE;
        // On Duty
        if (type.contains("ON DUTY") || type.contains("OD"))
            return Leave.TYPE_ON_DUTY;
        // Medical
        if (type.contains("MEDICAL"))
            return Leave.TYPE_MEDICAL;
        // Emergency
        if (type.contains("EMERGENCY"))
            return Leave.TYPE_EMERGENCY;
        // Outing and Local Guardian - map to OTHER but store original type
        // These will display correctly in the UI using the original leaveType from VTOP
        return Leave.TYPE_OTHER;
    }

    /**
     * Map VTOP leave status string to our Leave status constants.
     */
    private String mapLeaveStatus(String vtopStatus) {
        if (vtopStatus == null)
            return Leave.STATUS_PENDING;
        String status = vtopStatus.toUpperCase().trim();
        if (status.contains("APPROVED") && !status.contains("NOT"))
            return Leave.STATUS_APPROVED;
        if (status.contains("PENDING"))
            return Leave.STATUS_PENDING;
        if (status.contains("CLOSED"))
            return Leave.STATUS_CLOSED;
        if (status.contains("CANCELLED") || status.contains("CANCELED"))
            return Leave.STATUS_CANCELLED;
        if (status.contains("REJECTED") || status.contains("NOT APPROVED"))
            return Leave.STATUS_REJECTED;
        return Leave.STATUS_PENDING;
    }

    /**
     * Function to make final changes before signing the user in.
     */
    private void finishUp() {
        this.notification.setContentTitle(getString(R.string.completing_sync));
        this.notification.setProgress(0, 0, true);
        this.notification.setContentText(null);

        this.notificationManager.notify(SettingsRepository.NOTIFICATION_ID_VTOP_DOWNLOAD, this.notification.build());

        this.sharedPreferences.edit().putBoolean("isVTOPSignedIn", true).apply();
        this.sharedPreferences.edit().putLong("lastRefreshed", Calendar.getInstance().getTimeInMillis()).apply();

        if (this.syncMetrics != null) {
            this.syncMetrics.finish();
            android.util.Log.i("VTOPService", this.syncMetrics.generateSummary());
        }

        try {
            this.callback.onComplete();
        } catch (Exception ignored) {
        }

        Intent dataUpdatedIntent = new Intent("DATA_UPDATED");
        sendBroadcast(dataUpdatedIntent);

        this.endService(true);
    }

    private Slot resolveSlotReference(Map<String, Slot> slotReference, String slotName) {
        if (slotReference == null || slotName == null) {
            return null;
        }

        Slot direct = slotReference.get(slotName);
        if (direct != null) {
            return direct;
        }

        String alias = SLOT_ALIASES.get(slotName.toUpperCase(Locale.US));
        if (alias != null) {
            return slotReference.get(alias);
        }

        return null;
    }

    /**
     * Function to get the slot ID using the slot.
     *
     * @param slot Ex: "A1", "L21", etc.
     * @return The slot ID
     */
    private Integer getSlotId(String slot, int courseType) {
        switch (courseType) {
            case Course.TYPE_LAB:
                Slot labSlot = resolveSlotReference(this.labSlots, slot);
                return labSlot != null ? labSlot.id : null;
            case Course.TYPE_PROJECT:
                Slot projectSlot = resolveSlotReference(this.projectSlots, slot);
                return projectSlot != null ? projectSlot.id : null;
            default:
                Slot theorySlot = resolveSlotReference(this.theorySlots, slot);
                return theorySlot != null ? theorySlot.id : null;
        }
    }

    /**
     * Function to get the course ID using the slot.
     *
     * @param slot Ex: "A1", "L21", etc.
     * @return The course ID
     */
    private Integer getCourseId(String slot, int courseType) {
        switch (courseType) {
            case Course.TYPE_LAB:
                Slot labSlot = resolveSlotReference(this.labSlots, slot);
                return labSlot != null ? labSlot.courseId : null;
            case Course.TYPE_PROJECT:
                Slot projectSlot = resolveSlotReference(this.projectSlots, slot);
                return projectSlot != null ? projectSlot.courseId : null;
            default:
                Slot theorySlot = resolveSlotReference(this.theorySlots, slot);
                return theorySlot != null ? theorySlot.courseId : null;
        }
    }

    /**
     * Function to get the course credits using the course ID.
     *
     * @param courseId The course ID as saved in the database
     * @return The number of credits for that course
     */
    private Double getCourseCredits(Integer courseId, int courseType) {
        switch (courseType) {
            case Course.TYPE_LAB:
                if (!this.labCourses.containsKey(courseId)) {
                    return null;
                }

                return Objects.requireNonNull(this.labCourses.get(courseId)).credits;
            case Course.TYPE_PROJECT:
                if (!this.projectCourses.containsKey(courseId)) {
                    return null;
                }

                return Objects.requireNonNull(this.projectCourses.get(courseId)).credits;
            default:
                if (!this.theoryCourses.containsKey(courseId)) {
                    return null;
                }

                return Objects.requireNonNull(this.theoryCourses.get(courseId)).credits;
        }
    }

    /**
     * Function to get the course code using the course ID.
     *
     * @param courseId The course ID as saved in the database
     * @return The course code of that course
     */
    private String getCourseCode(Integer courseId, int courseType) {
        switch (courseType) {
            case Course.TYPE_LAB:
                if (!this.labCourses.containsKey(courseId)) {
                    return null;
                }

                return Objects.requireNonNull(this.labCourses.get(courseId)).code;
            case Course.TYPE_PROJECT:
                if (!this.projectCourses.containsKey(courseId)) {
                    return null;
                }

                return Objects.requireNonNull(this.projectCourses.get(courseId)).code;
            default:
                if (!this.theoryCourses.containsKey(courseId)) {
                    return null;
                }

                return Objects.requireNonNull(this.theoryCourses.get(courseId)).code;
        }
    }

    /**
     * Function to get the String value from a JSON object using a key.
     *
     * @param jsonObject The JSON object to be used
     * @param key        The key to be used to get the value
     * @return The value stored or null if the key wasn't present
     */
    private String getStringValue(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }

        return jsonObject.getString(key);
    }

    /**
     * Function to get the Integer value from a JSON object using a key.
     *
     * @param jsonObject The JSON object to be used
     * @param key        The key to be used to get the value
     * @return The value stored or null if the key wasn't present
     */
    private Integer getIntegerValue(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }

        return jsonObject.getInt(key);
    }

    /**
     * Function to get the Double value from a JSON object using a key.
     *
     * @param jsonObject The JSON object to be used
     * @param key        The key to be used to get the value
     * @return The value stored or null if the key wasn't present
     */
    private Double getDoubleValue(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }

        return jsonObject.getDouble(key);
    }

    public interface Callback {
        void onRequestCaptcha(int captchaType, Bitmap bitmap, WebView webView);

        void onCaptchaComplete();

        void onRequestSemester(String[] semesters);

        void onServiceEnd();

        void onForceSignOut();

        void onComplete();
    }

    public class ServiceBinder extends Binder {
        public VTOPService getService() {
            return VTOPService.this;
        }

        public void setCallback(Callback mCallback) {
            callback = mCallback;
        }
    }

    private enum PageState {
        LANDING, LOGIN, HOME
    }
}

/*
 * Error codes
 *
 * Error 101 Errors connecting to the server
 * Error 102 Error fetching user credentials
 * Error 103 Error opening sign in page
 * Error 104 Error getting captcha type
 * Error 105 Error getting default captcha image
 * Error 106 Error while attempting to sign in
 * Error 107 Unauthorised user agent used
 *
 * Error 201 Error fetching list of semesters
 *
 * Error 301 Error fetching user's name
 * Error 302 Error fetching user's credits & GPA
 *
 * Error 401 Error downloading courses
 * Error 402 Error saving courses to the database
 *
 * Error 501 Error downloading timetable
 * Error 502 Error saving timetable to the database
 *
 * Error 601 Error downloading attendance
 * Error 602 Error saving attendance to the database
 *
 * Error 701 Error downloading marks
 * Error 702 Error saving marks to the database
 *
 * Error 801 Error downloading grades
 * Error 802 Error saving grades to the database
 *
 * Error 901 Error downloading exam schedule
 * Error 902 Error saving exam schedule to the database
 *
 * Error 1001 Error downloading proctor info
 * Error 1002 Error saving proctor info to the database
 * Error 1003 Error downloading dean & hod info
 * Error 1004 Error saving dean & hod info to the database
 *
 * Error 1101 Error downloading spotlight
 * Error 1102 Error saving spotlight to the database
 *
 * Error 1201 Error downloading receipts
 * Error 1202 Error saving receipts to the database
 * Error 1203 Error checking for due payments
 */

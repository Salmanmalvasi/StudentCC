# StudentCC — Intelligent Academic Companion for VIT

<div align="center">

  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="StudentCC Logo" width="108" height="108" />

  <h3>Your Private, Offline-First, All-in-One Academic Dashboard for VIT</h3>

  <p>
    <a href="https://github.com/Salmanmalvasi/StudentCC/releases/latest"><img src="https://img.shields.io/badge/Release-v1.6.0-22c55e?style=for-the-badge&logo=android" alt="Latest Release" /></a>
    <a href="https://github.com/Salmanmalvasi/StudentCC/actions/workflows/build.yml"><img src="https://img.shields.io/badge/CI%2FCD-Passing-0284c7?style=for-the-badge&logo=githubactions" alt="CI/CD Status" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-64748b?style=for-the-badge" alt="License" /></a>
    <a href="https://developer.android.com/about/versions/nougat"><img src="https://img.shields.io/badge/Min%20SDK-24%20(Android%207.0)-3b82f6?style=for-the-badge&logo=android" alt="Min SDK" /></a>
    <a href="https://developer.android.com/about/versions/14"><img src="https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-a855f7?style=for-the-badge&logo=android" alt="Target SDK" /></a>
  </p>

  <p><strong>StudentCC</strong> is an open-source, modern Android application engineered specifically for students of Vellore Institute of Technology. Built with an offline-first architecture, privacy-first security, and Material Design 3, it transforms clunky portal interactions into an instantaneous, delightful mobile experience.</p>

  <p>
    <a href="https://salmanmalvasi.github.io/studentcc-landing.html"><strong>Explore Web Landing Page »</strong></a>
    &nbsp;•&nbsp;
    <a href="https://github.com/Salmanmalvasi/StudentCC/releases"><strong>Download Latest APK »</strong></a>
    &nbsp;•&nbsp;
    <a href="https://github.com/Salmanmalvasi/StudentCC/issues"><strong>Report an Issue »</strong></a>
  </p>

</div>

---

## 🌟 Key Features

### 📅 Smart 4×3 Timetable & Venue Widget
- **Home Screen Schedule**: Real-time display of today's classes, venues, and timings directly on your launcher.
- **Dedicated Quick Settings**: Integrated Settings shortcut button (`ic_settings`) on the widget header to jump straight to preferences and account settings in one tap.
- **Surface Interactivity**: Clicks on any part of the widget header, schedule overview, or empty state open the app directly.
- **Android 12+ (API 31+) Support**: Uses `FLAG_MUTABLE` fill-in PendingIntent templates so individual class rows open class details smoothly across all Android versions.

### 🟩 Interactive Attendance Heatmap
- **Contribution-Style Grid**: 7-row interactive activity calendar inspired by GitHub's contribution heatmap.
- **Inverted Color Intensity Palette**:
  - `0 classes`: Slate dark background (`#1E293B`)
  - `1 class`: Dark forest green (`#14532D`)
  - `2 classes`: Dark emerald green (`#15803D`)
  - `3 classes`: Medium vibrant green (`#22C55E`)
  - `4+ classes`: Brightest light mint green (`#86EFAC`)
- **Combined Session Weighting**: Counts both Present and On Duty (OD) sessions seamlessly.
- **Day Inspector**: Tap any cell in the grid to view exact counts of attended, present, OD, absent, and total conducted classes for that date.

### 🗓️ 1-Click Google Calendar Sync
- Export academic calendars, instructional days, holidays, and exam dates straight to Android's native Calendar Provider and Google Calendar.
- Automatic idempotency tracking prevents duplicate events upon re-sync.
- Categorized calendar events with colored highlights for exams, project deadlines, and holidays.

### 📊 All-Semesters Analytics & CGPA Calculator
- **Multi-Semester Breakdown**: Historical performance trends, semester-by-semester GPA progressions, and credits completed.
- **VIT Grade System Alignment**: Full support for 10-point relative grading (`S`, `A`, `B`, `C`, `D`, `E`, `F`, `N`).
- **Interactive Simulator**: Calculate how upcoming marks or attendance percentages impact overall semester targets.

### 🏢 Hostel Info & Unmessify Mess Menu
- **Hostel Insights**: Block details, room information, and automated laundry countdown reminders.
- **Unmessify Integration**: Integrated daily mess menus (Breakfast, Lunch, Snacks, Dinner) in collaboration with the [Unmessify](https://github.com/Kanishka-Developer/unmessify) open-source initiative.

### 🚀 Events Hub & Placement Tracker
- Discover campus club events, technical hackathons, guest lectures, and student chapters.
- Dedicated placement checklist, company shortlist tracking, and interview schedules.

### 🎨 Material Design 3 Theming
- 6 curated palettes: Default Modern, Royal Blue, Emerald Green, Crimson Red, Royal Purple, and AMOLED Pure Black.
- Dynamic theme adaptation across all widgets, custom charts, and system navigation bars.

---

## 🆕 What's New in v1.6.0 (Changes Since v1.5 / v1.4.1)

| Area | Enhancement |
|---|---|
| **Home Screen Widgets** | Upgraded default dimensions to **4×3** (`targetCellWidth="4"`, `targetCellHeight="3"`). Added header Settings button, interactive surface clicks, and Android 12+ `FLAG_MUTABLE` collection clicks. |
| **Attendance Heatmap** | Inverted green color palette (dark green for fewer classes, bright mint green for high class density). Integrated OD + Present counts and interactive day-sheet inspector. |
| **Calendar Sync** | Added native Android / Google Calendar synchronization for the full academic year and examination timetables. |
| **Analytics** | Added dedicated `AllSemestersAnalyticsActivity` with multi-semester CGPA tracking, course category breakdown, and performance visualization. |
| **Caching & Sync Engine** | Implemented `DataFreshnessManager` and `LocalCacheMetrics` to eliminate unnecessary network round-trips, executing background quick-sync only when core datasets become stale. |
| **FCM Push Notifications** | Resolved duplicate notification delivery in `VTOPFirebaseMessagingService` by isolating data payloads from notification triggers. |
| **Navigation Reliability** | Added `handleIntentNavigation()` in `MainActivity` with `@Override onNewIntent(Intent)` so widget clicks and push notifications immediately switch to the target tab when the app is running in background. |
| **Security & Secrets** | Hardened `.gitignore` to protect all environment configs (`.env*`) and APK binaries (`*.apk`), while providing automated fallback configs in `generate-config.sh`. |
| **CI/CD Automation** | Modernized GitHub Actions workflow with Gradle dependency caching, pre-build unit test validation, artifact uploads, and automated release asset generation. |

---

## 🏗️ Architecture & Technical Design

StudentCC follows clean architectural principles structured around the **Model-View-ViewModel (MVVM)** and **Repository** patterns:

```
┌─────────────────────────────────────────────────────────────┐
│                      Presentation Layer                     │
│  Activities (MainActivity, AllSemestersAnalyticsActivity)   │
│  Fragments  (HomeFragment, PerformanceFragment, Calendar)    │
│  Widgets    (TodayTimetableWidget, AttendanceWidget)        │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       Domain / Helper                       │
│  SettingsRepository  •  DataFreshnessManager  •  VTOPHelper │
│  FirebaseMessagingHelper  •  HostelDataHelper •  ThemeMgr   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                         Data Layer                          │
│  Room Database (SQLite)  •  RxJava3 Observers / Singles     │
│  EncryptedSharedPreferences (AES-256 GCM)                   │
│  OkHttp3 Network Stack   •  On-Device Captcha Solver Model  │
└─────────────────────────────────────────────────────────────┘
```

### 1. Reactive Offline-First Data Pipeline
- All UI components observe local state backed by an on-device SQLite database via **Android Room**.
- UI rendering never waits on network latency; data is emitted instantly via **RxJava3** (`Single`, `Observable`).
- Network fetches from VTOP update Room records in a background thread, triggering immediate, smooth UI updates without activity reloads.

### 2. Smart Freshness & Differential Sync
- The `DataFreshnessManager` tracks timestamps for each academic dataset (Timetable, Attendance, Marks, Exams).
- On app launch, if cache records are fresh, network polling is bypassed entirely (`LocalCacheMetrics.recordRequestSkipped`), saving student battery and cellular data.
- Stale datasets trigger non-blocking background synchronization with toast confirmation upon completion.

### 3. On-Device Captcha Recognition
- Captcha images from VTOP are evaluated on-device using custom pre-processing routines (contrast adjustment, thresholding, and character segmentation) paired with a local binary model (`vtop_captcha_model.bin`).
- Credentials and captcha images never leave the student's device.

### 4. Hardware-Backed Encryption
- Student credentials and session cookies are secured using Android Jetpack Security's `EncryptedSharedPreferences`.
- Keys are managed by the Android KeyStore using AES-256 GCM encryption.

### 5. AppWidget Architecture
- Uses `AppWidgetProvider` alongside `RemoteViewsService` to render scrollable lists in the launcher process.
- Compatible with Android 12 through Android 14 via explicit `PendingIntent.FLAG_MUTABLE` list templates and responsive cell sizing.

---

## 🛠️ Technology Stack

| Category | Technology |
|---|---|
| **Language** | Java 17 |
| **Architecture** | MVVM, Repository Pattern, Clean Architecture |
| **Local Database** | Android Room ORM (SQLite) |
| **Reactive Programming** | RxJava3, RxAndroid |
| **Design System** | Google Material Design 3 (Material Components) |
| **Networking** | OkHttp3, Retrofit, Apache Commons IO |
| **Security** | Android Jetpack Security (`EncryptedSharedPreferences`), Android Keystore |
| **Background Processing** | Android Jetpack WorkManager, JobScheduler, Foreground Services |
| **Push Notifications** | Firebase Cloud Messaging (FCM), Android NotificationManager |
| **Widgets** | Android AppWidget framework, RemoteViewsService |
| **Testing** | JUnit 4, AndroidX Test Runner, Robolectric |
| **CI / CD** | GitHub Actions with Gradle Cache & automated GitHub Releases |

---

## 📁 Repository Directory Structure

```
StudentCC/
├── .github/
│   └── workflows/
│       └── build.yml               # Automated CI/CD build, test & release workflow
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/             # On-device captcha model & static metadata
│   │   │   ├── java/com/salmanmalvasi/studentcc/
│   │   │   │   ├── activities/     # MainActivity, Analytics, Login, Themes
│   │   │   │   ├── adapters/       # RecyclerView & list adapters
│   │   │   │   ├── fragments/      # Home, Performance, GPA, Hostel, Calendar
│   │   │   │   ├── helpers/        # Database, VTOP parser, FreshnessManager
│   │   │   │   ├── models/         # Room entities & data transfer models
│   │   │   │   ├── services/       # FCM messaging & background services
│   │   │   │   ├── widgets/        # Timetable & Attendance AppWidgets, Heatmap
│   │   │   │   └── workers/        # Scheduled WorkManager background tasks
│   │   │   └── res/                # Layouts, vector drawables, themes, menus
│   │   └── test/                   # Comprehensive unit tests
│   ├── build.gradle                # App build configuration & dependencies
│   ├── proguard-rules.pro          # R8 / ProGuard obfuscation rules
│   └── studentcc.keystore          # Release signing keystore
├── generate-config.sh              # Script to build google-services.json from env
├── build.gradle                    # Root Gradle configuration
├── settings.gradle                 # Module settings
└── README.md                       # Project documentation
```

---

## 🚀 Building & Running Locally

### Prerequisites
- **JDK 17** (Temurin, Corretto, or OpenJDK)
- **Android Studio** (Hedgehog 2023.1.1 or newer recommended)
- **Android SDK Platform 34** (installed via SDK Manager)

### Step 1: Clone the Repository
```bash
git clone https://github.com/Salmanmalvasi/StudentCC.git
cd StudentCC
```

### Step 2: Configure Environment Variables
Copy the example environment configuration:
```bash
cp .env.example .env
```
*(Optional)* Populate `.env` with your Firebase project keys, or simply execute the generator script. If no variables are set, the generator automatically injects mock credentials for local compilation and unit tests:
```bash
chmod +x ./generate-config.sh
./generate-config.sh
```

### Step 3: Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Step 4: Build APK
- **Debug Build:**
  ```bash
  ./gradlew assembleDebug
  ```
- **Signed Release Build:**
  ```bash
  ./gradlew assembleRelease
  ```
The signed release APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 🔄 CI/CD Pipeline

The project features a fully automated GitHub Actions pipeline ([`.github/workflows/build.yml`](.github/workflows/build.yml)):

1. **Continuous Integration**:
   - Triggers on every push to `main` and all Pull Requests.
   - Sets up JDK 17 with automated Gradle caching for fast build times (~1 minute).
   - Generates configuration files securely from repository secrets (`FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, etc.).
   - Runs full unit test suite (`./gradlew testDebugUnitTest`).
   - Compiles and minifies the production bundle (`./gradlew assembleRelease`).

2. **Automated Continuous Delivery**:
   - On pushes to `main`, tags the commit with the app version (e.g., `v1.6.0`).
   - Publishes a formal GitHub Release with release notes.
   - Attaches `StudentCC.apk` and `about.json` directly to the release for over-the-air update availability.

---

## 🔒 Privacy & Security Commitment

- **Zero External Data Transmission**: All academic data (marks, attendance, schedules) stays strictly on the student's device. No telemetry or server relays capture student credentials.
- **Direct-to-Portal Communication**: Network interactions occur directly between the app and official VTOP servers over HTTPS.
- **Encrypted Local Storage**: Session tokens and credentials stored on disk use AES-256 GCM encryption via Android Jetpack Security.
- **Repository Hygiene**: Sensitive secrets, local keystore passwords, `.env` files, and binary APKs are strictly excluded from git tracking.

---

## 🤝 Partnerships & Credits

- **Unmessify**: Special thanks to [Kanishka Chakraborty](https://github.com/Kanishka-Developer) and Teesha Saxena for open collaboration on the mess menu system ([kaffeine.tech/unmessify](https://kaffeine.tech/unmessify)).
- **VIT Community**: Immense gratitude to the VIT student body for continuous testing, feedback, and feature requests.

---

## 📄 License

This project is open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**. See the [LICENSE](LICENSE) file for the full license text.

---

<div align="center">
  <p>Crafted with ❤️ for the student community by <strong>Salman Malvasi</strong></p>
  <p>
    <a href="https://github.com/Salmanmalvasi">GitHub</a> • 
    <a href="https://salmanmalvasi.github.io">Website</a> • 
    <a href="mailto:salmanmalvasi@gmail.com">Email</a>
  </p>
</div>

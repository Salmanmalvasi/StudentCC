# StudentCC - Developer Documentation

> **For Students**: Download the app from [salmanmalvasi.github.io/studentcc-landing.html](https://salmanmalvasi.github.io/studentcc-landing.html)

## 👨‍💻 Developer Setup

This repository contains the source code for StudentCC Android app. This documentation is for developers and contributors.

### 🏗️ Tech Stack
- **Language**: Java (Android)
- **Web Scraping**: Android WebView + JavaScript + jQuery
- **HTTP Client**: OkHttp + Retrofit
- **Database**: Room (SQLite)
- **Architecture**: MVVM with Services
- **Build System**: Gradle

### 🔧 Prerequisites
- Android Studio Arctic Fox or newer
- JDK 11+
- Android SDK API 24+
- Git

### ⚡ Quick Start

1. **Clone & Setup**
   ```bash
   git clone https://github.com/Salmanmalvasi/StudentCC.git
   cd StudentCC
   cp .env.example .env
   # Edit .env with your Firebase configuration
   ```

2. **Generate Configuration**
   ```bash
   ./generate-config.sh
   ```

3. **Build**
   ```bash
   ./gradlew assembleRelease
   ```

### 🔐 Security Setup

**IMPORTANT**: This repository uses environment variables for security.

1. Copy `.env.example` to `.env`
2. Add your Firebase configuration to `.env`
3. Run `./generate-config.sh` before building
4. See [SECURITY.md](SECURITY.md) for complete setup

### 📱 Features Overview

- **Attendance Tracking**: Day-by-day attendance view
- **Marks Management**: Class averages & performance trends
- **OD Calculator**: Track On Duty usage
- **Timetable**: Class schedules
- **Firebase Integration**: Analytics, Crashlytics, In-App Messaging
- **Auto-Sync**: Background data updates

### 🏛️ Architecture

```
├── activities/          # UI Activities
├── fragments/          # UI Fragments  
├── services/           # Background Services (VTOPService)
├── helpers/            # Utility Classes
├── models/            # Data Models
├── interfaces/        # Room DAOs
└── workers/           # Background Workers
```

### 🕷️ Web Scraping Engine

The app uses a sophisticated scraping system:
- **WebView**: Handles authentication & CAPTCHA
- **JavaScript Injection**: Extracts data from DOM
- **HTTP Fallback**: Direct API calls where possible
- **Session Management**: Maintains login state

**Attribution**: Detailed attendance parsing logic adapted from [Arya4930/UniCC](https://github.com/Arya4930/UniCC)

### 🔄 Background Sync

- **VTOPService**: Primary data sync service
- **AutoSyncWorker**: Periodic background updates
- **WorkManager**: Handles network constraints

### 🛠️ Build Configuration

- **Debug**: Development builds with logging
- **Release**: Optimized builds with ProGuard
- **Signing**: Uses `studentcc.keystore` for release

### 🧪 Testing

**Test Coverage: 45-55% (108+ tests)**

```bash
# Run unit tests (fast, no device needed)
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Generate coverage report
./gradlew jacocoTestReport
```

See [TESTING.md](TESTING.md) for complete testing guide.

**Continuous Integration:**
- ✅ Automated tests run on every push/PR
- ✅ Unit tests (79 tests) - Ubuntu runner
- ✅ Instrumented tests (29 tests) - macOS runner with emulator
- ✅ Lint checks and build verification

### 🤝 Contributing

1. Fork the repository
2. Create feature branch
3. Follow code style guidelines
4. **Write tests for new features** (see [TESTING.md](TESTING.md))
5. Ensure all tests pass: `./gradlew test`
6. Submit pull request

### 📊 Analytics & Monitoring

- **Firebase Analytics**: User behavior tracking
- **Crashlytics**: Crash reporting
- **In-App Messaging**: Feature announcements

### 📄 License

See LICENSE file for details.

### 🔗 Links

- **App Download**: [StudentCC Landing Page](https://salmanmalvasi.github.io/studentcc-landing.html)
- **Security Guide**: [SECURITY.md](SECURITY.md)
- **Build Guide**: [GitHub Actions](.github/workflows/build.yml)

---
**Developer Repository** - For app downloads, visit the landing page above.

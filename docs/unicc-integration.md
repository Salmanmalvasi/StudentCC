# UniCC class statistics in StudentCC

StudentCC reads aggregate class statistics from UniCC's existing public endpoint:

`GET https://api-unicc.arya22.dev/api/attendance/marks?classId=<VTOP-class-number>`

The integration sends only the VTOP class number. A dedicated OkHttp client has no
cookies, authentication interceptor, student credentials, CSRF token, marks upload,
or shared VTOP session. It does not call UniCC's login or attendance POST endpoints.
It cannot contribute StudentCC students' marks to UniCC: the reviewed source does
not expose a separate contribution endpoint. Sharing a dataset or supporting
contributions would require an API agreement and server changes by UniCC's owner.
This implementation does not imply that UniCC has endorsed StudentCC.

## Data contract and grading basis

The expected JSON response contains numeric `mean`, `variance`, `sd` and positive
integer `count`. Values must be finite, in the valid range for marks out of 60,
and the supplied standard deviation must agree with the variance. Unknown or
incompatible responses are shown as unavailable, without a grade estimate.

The grading basis is an assumption from the public UniCC source reviewed at commit
[`0fb9b8b78bd176344076ff9abbcf21b440a78120`](https://github.com/Arya4930/UniCC/tree/0fb9b8b78bd176344076ff9abbcf21b440a78120),
dated 12 November 2026. Its marks parser accepts assessment weightages summing to
60, collects weighted internal marks, and aggregates them by class number. The
hosted response does not currently include semester, assessment stage or grading
basis metadata, so a response within 0–60 cannot prove which assessment stage the
host is using. The UI must describe these as UniCC internal-mark statistics and
must not infer an official letter grade or final exam mark. If the host changes
the contract or assessment basis, update the client and UI together before using
the new data. An explicit future `marksBasis` value other than 60 is rejected.

The reviewed UniCC grade-boundary table is commented out. StudentCC now supplies
its own conditional grade preview using VIT Chennai's published regulations,
described in [vit-grade-estimates.md](vit-grade-estimates.md). Internal-only previews
scale the personal total and this pool's mean/SD to /100, assuming the same
percentage performance in FAT; they do not infer an official grade or missing
exam mark. Relative previews assume actual theory enrollment exceeds ten,
independently of contributor count. The ranges dialog lets a student select
absolute grading for a known small theory class. The separate CGPA predictor
remains a what-if calculator based on user-selected grades.

## Local behavior

`UniCCStatisticsRepository.getInstance(context).getStatistics(classId, semesterId, forceRefresh)`
returns an RxJava `Single<UniCCStatisticsResult>`. The repository schedules work on
an IO scheduler; consumers should observe on the Android main thread. Its result
distinguishes available, no-data, unavailable and missing-identifier states, and
reports the download timestamp and cache source.

Aggregate-only data is persisted in the separate `unicc_class_statistics_v1`
SharedPreferences file. Cache keys include both class and semester, the fresh TTL
is 30 minutes, and failed temporary requests may use a labeled cached result less
than seven days old. Cache entries are limited to 250. No-data and incompatible
responses invalidate the cached comparison. Semester isolation is local cache
isolation; the remote endpoint still accepts only a class number.

Concurrent consumers share one request for the same class and semester. Disposing
the last consumer cancels the underlying call. The client limits concurrency to
four calls and applies an overall 12-second call timeout, an 8-second connect/read
timeout and a 64 KiB response limit. Redirects are disabled. API failures do not
interrupt StudentCC's marks synchronization or modify its Room database.

The source reports a missing class as HTTP 500 with an error containing
`No data found for classID`; this known response and HTTP 404 become the no-data
state. A network error or temporary server error becomes an unavailable state,
or a clearly labeled previous aggregate if the local fallback qualifies.

## Verification and provenance

`UniCCStatisticsRepositoryTest` exercises synthetic responses through MockWebServer,
including unauthenticated GET requests, missing-data responses, invalid values,
cache serialization and TTL, semester isolation, stale fallback, cancellation,
concurrent request sharing and redirect rejection. Run it through the normal
Android unit-test task with the test filter for
`com.salmanmalvasi.studentcc.integrations.unicc.UniCCStatisticsRepositoryTest`.

The Android client and repository are original StudentCC integration code; UniCC
backend code is not copied. UniCC's source is MIT licensed. Any future copied
substantial source portions must retain its copyright and permission notice.

Relevant upstream source:

- [Marks collection and the current 60-point gate](https://github.com/Arya4930/UniCC/blob/0fb9b8b78bd176344076ff9abbcf21b440a78120/backend/src/routes/marks.ts)
- [Aggregate statistics and deduplication](https://github.com/Arya4930/UniCC/blob/0fb9b8b78bd176344076ff9abbcf21b440a78120/backend/src/lib/addClassData.ts)
- [Public class statistics endpoint](https://github.com/Arya4930/UniCC/blob/0fb9b8b78bd176344076ff9abbcf21b440a78120/backend/src/routes/attendance.ts)
- [Current marks UI with disabled grade ranges](https://github.com/Arya4930/UniCC/blob/0fb9b8b78bd176344076ff9abbcf21b440a78120/src/components/custom/Exams/marksDislay.tsx)
- [UniCC MIT license](https://github.com/Arya4930/UniCC/blob/0fb9b8b78bd176344076ff9abbcf21b440a78120/LICENSE)

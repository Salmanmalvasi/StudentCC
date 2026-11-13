# VIT grade previews

`VitGradePredictor` implements conditional S, A, B, C, D, E and F previews using
the grade-band and pass-rule framework in
[VIT Chennai FFCS Academic Regulations 5.0](https://chennai.vit.ac.in/files/Academic-Regulations-5.0.pdf),
sections 9.4–9.5 and Tables 5–6. The regulations are linked from the
[official academic regulations page](https://chennai.vit.ac.in/academic-regulations).

The app uses an **80-mark minimum for relative S predictions**, following the
user's explicit instruction about campus practice. This is an app prediction
setting: the linked PDF's ordinary relative S table still states 90. The computed
mean + 1.5 SD threshold remains in force when it is higher than 80. Absolute
grading retains its published S minimum of 90 and A minimum of 80.

The relative preview assumes a theory class with more than ten registered students.
The number of StudentCC or UniCC contributors is a sample size and must never be
used as the registered class strength. An actual theory class of ten or fewer
students uses absolute grading. Laboratory, project and soft-skills courses use
absolute grading regardless of class strength. The explicit `absolute` method is
available for those known cases. The class-statistics panels default to relative
theory grading with a visible enrollment assumption; their ranges dialog lets the
student choose absolute grading for a known smaller theory class.

Each pool supplies its own mean and SD. The UI must never combine StudentCC and
UniCC samples, or compare a personal final /100 total with UniCC's /60 internals.
For an internal /60 preview, the personal total, mean and SD are scaled to /100.
This is a hypothetical projection in which each student's final examination
percentage equals their internal percentage. FAT marks and the actual final class
distribution are not known. A theory internal total below 24/60 projects a FAT below
40% and therefore predicts F even if the hypothetical relative band would pass.
For an embedded course, the unrounded theory and laboratory internal components
are checked separately: theory below 24/60 projects a FAT below 40%, and lab below
30/60 projects laboratory performance below 50%. A stronger paired component cannot
rescue either projected failure. Missing or ambiguous components leave the pass
status unknown.
The mandatory FAT percentage is unrounded; rounding the projected grand total up
to 40 does not rescue the projected FAT. For a completed /100 StudentCC pool, the preview uses
that pool's final distribution directly. Both remain estimates from contributors.

Personal grand totals are rounded up to an integer. Relative lower limits are
rounded to the nearest integer, with halves upward: S at the greater of 80 and
mean + 1.5 SD; A at mean + 0.5 SD; B at mean − 0.5 SD; C at mean − SD; D at mean −
1.5 SD; and E at mean − 2 SD. The E pass rescue caps its lower limit at 50. Limits
below zero are displayed as zero; limits above 100 stay above 100 to indicate an
unreachable band. Relative predictions require at least 80 for S; totals above the
calculated S threshold but below 80 remain in A. Absolute lower limits
are 90, 80, 70, 60, 55 and 50, respectively, with F below 50.

The official system has inputs that neither aggregate service provides:

- Every student's final total is rounded up before the official mean and SD are
  calculated. StudentCC preserves fractional contributed totals, so its aggregate
  cannot reconstruct the official distribution after individual rounding.
- A theory FAT needs at least 40%. Embedded laboratory components need at least
  50%, and embedded grand totals need at least 50. A relative theory-only course
  can pass below 50 if its E band permits it. Unknown pass criteria are explicit
  assumptions; a known failed mandatory criterion overrides any band with F.
  FAT and lab component percentages remain unrounded. If an embedded combined
  total falls strictly between 49 and 50 while the lab passes, its grand total
  rounds to 50 for grade bands, but the regulations do not clarify that rounding's
  effect on the separate combined pass gate. This edge keeps pass status unknown
  instead of asserting a pass or failure. Internal embedded projections that
  cannot reach a combined 50 even after rounding receive a hypothetical F.
- The special S rule uses ranked full-class marks when no ordinary S is awarded
  or the calculated S threshold exceeds 100. Its top-three/top-five-percent
  eligibility cannot be determined from mean, SD and sample count, and is omitted
  from predictions.

Relative previews need at least two contributors and nonzero SD. A single sample
or zero spread collapses the relative bands and produces an insufficient-sample
state. Missing or incompatible totals also produce an unavailable state. A
prediction must always be labelled as a projection or estimate, with its source
pool and relevant assumptions visible.

`VitGradePredictorTest` covers all seven grades, rounding and threshold edges,
the relative 80 minimum, a calculated S threshold above 80, the separate absolute
90 minimum and an unreachable S boundary, low/high E pass limits, independently
changing pool distributions, /60 projection, sample size versus class strength,
missing inputs, absolute grading and mandatory pass failures.

# Known issues

## ANDROID_17_LONG_DWELL_PRINT_DELAY

Priority: P2. Status: KNOWN_ISSUE / DEFERRED. Not fixed.

Observed only in synthetic QA on Pixel 8 Pro, Android 17 / API 37, build
CP3A.260905.009. When the Android system Print UI remains open for approximately
90 seconds or longer, the application process can enter cached/frozen state.
Final print callbacks may be delayed until the application resumes, and Save as
PDF can stall. The exact incidence rate and deeper system cause are not known.

The Human accepted this specific risk for plugin publication readiness on
2026-10-14. The final bounded Binder trace was inconclusive because the filtered
ftrace data source could not be initialized. No further dedicated long-dwell
investigation or speculative lifecycle change is part of this release.

The limitation does not establish that every Android version, device or printer
is affected. It does not guarantee eventual saved or printed output. Android 17
printing is not claimed to be fully reliable. The plugin does not use a foreground
service, additional permission, notification or freezer-setting change to work
around the issue.

`handed_off` acknowledges system handoff only. Verify output separately. If a
long-dwell print interaction stalls, cancel/return normally and initiate a fresh
print when ready; do not present the original request as successfully printed.
This recovery is not a guarantee for every system print service.

The acceptance does not waive security vulnerabilities, lost/corrupted PDF
content, normal interactive printing failures, unsafe concurrency, resource
leaks or breaking public API changes. Those remain independent release gates.

Plugin readiness does not clear the RobustHomes Product P0 release blocker or
authorize Product integration, deployment, release or npm publication.

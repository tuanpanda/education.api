# Portal Phase 1 Stream A — STATUS

- **Branch:** feat/portal-p1-core
- **HEAD:** 675d1505615af81e75abab333db4817bd728458f
- **Base:** feat/portal-p0 @ 031fff6
- **Machine:** TUANDX

## Done
- All Phase 1 Stream A portal read-only APIs
- StudentScopeGuard.requireEnrolled + requireOwnFee
- PortalAnnouncementQuery stub (unread=0)
- IDOR + staff-403 coverage
- mvn test: **1085** run, 0 fail, BUILD SUCCESS
- Local commit 675d1505615af81e75abab333db4817bd728458f (not pushed)

## APIs
- GET /api/v1/portal/me
- GET /api/v1/portal/me/dashboard
- GET /api/v1/portal/me/classes
- GET /api/v1/portal/me/timetable?from&to
- GET /api/v1/portal/me/attendance?classId&from&to
- GET /api/v1/portal/me/attendance/summary?classId
- GET /api/v1/portal/me/grades
- GET /api/v1/portal/me/fees
- GET /api/v1/portal/me/fees/{id}
- GET /api/v1/portal/me/fees/{id}/slip/html
- GET /api/v1/portal/me/payments

## Left
- Stream B announcements
- Merge/push (out of scope)

## Blockers
- None

## Open decisions
1. Branched from feat/portal-p0 (Phase 0 not on feat/education-api yet).
2. Hide attendance + grade NOTE from portal DTOs.
3. VOIDED status visible; void reason/who/when hidden.
4. unreadAnnouncements=0 via PortalAnnouncementQuery stub.

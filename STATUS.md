# Portal Phase 1 Stream A — STATUS

- **Branch:** feat/portal-p1-core
- **Base:** feat/portal-p0 @ 031fff6 (Phase 0 integrated; not bare feat/education-api @ 4cff890 which lacked portal)
- **Machine:** TUANDX (2363ad1b-14e8-45d9-887d-7120c3e27818)

## Done
- Worktree E:\JAVA\edu-wt\p1-core
- Portal read-only APIs under /api/v1/portal with @PortalAccess(STUDENT)
- StudentScopeGuard.requireOwnFee + requireEnrolled
- PortalAnnouncementQuery stub (unread=0 for Stream B)
- Portal DTOs (no internal NOTES / void internals)
- IDOR tests (fee/class attendance → 404)
- mvn test: 1085 run, 0 fail (BUILD SUCCESS)
- Local commit pending/done below

## Left
- Stream B: real announcements + EDU_ANNOUNCEMENTS
- Optional: profile-change-requests, POST fees QR (skipped per scope)
- Merge into feat/portal-p1 / education-api (parent / other stream)

## Blockers
- None

## Open decisions
1. Branched from feat/portal-p0 (needed Phase 0 foundation).
2. Hide EDU_ATTENDANCE.NOTE and EDU_GRADES.NOTE from portal DTOs.
3. VOIDED payments: status visible; voidedAt/voidedBy/voidReason hidden.
4. Dashboard unreadAnnouncements=0 via PortalAnnouncementQuery stub.

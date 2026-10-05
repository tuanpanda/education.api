# Portal Phase 1 Stream B - Announcements STATUS

- **Branch:** feat/portal-p1-announce
- **HEAD:** efeef4e
- **Worktree:** E:\JAVA\edu-wt\p1-announce
- **Machine:** TUANDX (2363ad1b-14e8-45d9-887d-7120c3e27818)
- **Base:** feat/education-api @ 4cff890

## Done
- V18_1__portal_announcements.sql + V18_2__announcement_menus.sql
- OWASP HTML sanitizer + HtmlContentSanitizer on save
- Staff CRUD+publish+archive @ /api/v1/announcements (MENU_ANNOUNCEMENT:*)
- Portal list + mark-read @ /api/v1/portal/me/announcements
- AnnouncementQueryService.countUnread(studentUserId)
- Tests green: mvn test → 1087 passed

## Left
- User runs migrations V18_1 then V18_2 (sqlplus)
- Do not push / deploy (local commits only)

## Blockers
- None

## Migration run order (user)
1. V18_1__portal_announcements.sql
2. V18_2__announcement_menus.sql

sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V18_1__portal_announcements.sql
sqlplus EDUCATION/EDUCATION@//localhost:1521/ORCL @src/main/resources/db/migration/V18_2__announcement_menus.sql

## APIs
### Staff (RequirePermission MENU_ANNOUNCEMENT:*)
- GET    /api/v1/announcements
- GET    /api/v1/announcements/{id}
- POST   /api/v1/announcements
- PUT    /api/v1/announcements/{id}
- POST   /api/v1/announcements/{id}/publish
- POST   /api/v1/announcements/{id}/archive
- DELETE /api/v1/announcements/{id}

### Portal (@PortalAccess STUDENT)
- GET  /api/v1/portal/me/announcements
- POST /api/v1/portal/me/announcements/{id}/read

### Service for Stream A dashboard
- AnnouncementQueryService.countUnread(studentUserId)

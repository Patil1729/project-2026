# Shiv Sena Legal Cell frontend 2.8.0


## Gallery and Get Help (2.8.0)

Use the matching updated backend and read [GALLERY-HELP-SETUP-AND-TESTING.md](GALLERY-HELP-SETUP-AND-TESTING.md) first. It documents the public gallery, authorized publishing, private help requests, area-based assignment to current committee members, senior transfers, permissions and complete frontend test steps.

Keep your current database and `ddl-auto=validate`; Flyway applies the new additive V8 migration. Existing V1-V7 migrations remain unchanged. Your existing local configuration and uploaded files should be retained.

## Existing manual frontend testing

Enter all test records through the UI using [FRONTEND-MANUAL-TEST-GUIDE.md](FRONTEND-MANUAL-TEST-GUIDE.md). This release enables Create unit for PARTY_HEAD even when the database contains no organisation units. Use the matching updated backend supplied with this release.

## Profile pictures, leadership and fresh-start test data

Optional private profile-picture upload/replace/remove is available in My profile and the application wizard. The public homepage has six editable leadership slots (three supplied people plus three pending entries). Use the matching updated backend/frontend. Flyway V7 adds picture metadata. Keep `ddl-auto=validate`.

Read [FRESH-START-PROFILE-PICTURES-AND-DEMO.md](FRESH-START-PROFILE-PICTURES-AND-DEMO.md) for a new empty MySQL demo schema, the optional demo profile, all accounts, approval responsibilities and automated API seeding.

## CSRF fix in 2.6.1

Normal authenticated JWT requests no longer replace the CSRF cookie. Sign-in/sign-out still rotate it, and invalid tokens remain blocked. See [CSRF-FIX-READ-ME.md](CSRF-FIX-READ-ME.md) for the two backend files, frontend client update, restart steps and verification results.

This update is based on the supplied FrontEnd(1).zip and uses the layout,
photographs, emblem and saffron styling from legal-cell-portal-final(2).html.


## Changes in 2.6

- Functional, server-scoped area administration with an explicit access summary and refresh action.
- PARTY_HEAD role assignment/revocation/history screen; role choices restrict the unit type and show hierarchy paths.
- Scoped management lists and application workload; read-only member/profile and appointment views for area admins.
- Scoped organisation/committee/position management, including protection of the assigned scope root.
- President-based document inbox independent of area-admin role names; admin document reads do not expose verification actions.
- Access refresh on focus/403 and private-resource invalidation when grants change.
- Full backend paired with this frontend; read [AREA-ADMIN-SETUP-AND-TESTING.md](AREA-ADMIN-SETUP-AND-TESTING.md) for installation and test accounts.

## Retained homepage and application changes

- Replaced Our Purpose with the supplied HTML's full Marathi introduction (three topics and four objectives), with an English translation.
- Moved the Maharashtra map immediately below State Executive Committee.
- Removed Your Journey and Your Contribution Matters. How it works remains.
- Added Get in touch with the supplied head-office address and helpline. Set `VITE_CONTACT_PHONE` and optional `VITE_CONTACT_EMAIL` to your approved organisation details. The default phone is now blank; configure only a confirmed real contact number.
- Added Forgot password on login, opening an administrator-assisted recovery page with contact details. This is a help/contact route, not an email reset flow. No reset email, OTP or token is generated.
- Added required organisation selection during signup. The submitted payload includes a numeric `organizationUnitId` selected from the public catalogue. This selection does not assign a role or committee position.
- The new-application wizard retains its three explicit steps; the upload screen has a visible heading, upload guidance and file controls. A member's saved application opens on Documents so they can resume uploads. Going Back preserves profile values and reuses the saved application.
- The latest recommendation version controls whether the wizard can finish. A rejected new version is not satisfied by an older verified version. Actual application submission still requires verified documents through the backend.
- State Executive Committee uses a full seat catalogue: President, Working President, seven Vice Presidents, Secretary and Treasurer (11 seats per active State committee). Current members and division responsibilities are shown; vacant seats remain visible. An unavailable current appointment is distinguished from a vacancy. No fictional member names are used.

The separate updated backend ZIP includes the public catalogue and complete roster endpoints. Apply the matching backend and its migrations through V8 before using this frontend.

## Start

Requires Node.js 22.12+ and your existing Spring Boot backend.

```bash
npm ci
```

Copy `.env.example` to `.env` and keep your current backend/proxy/ngrok values.
On Windows you can copy it in File Explorer. On Ubuntu:

```bash
cp .env.example .env
npm run dev
```

The development server proxies `/api` to `http://localhost:8080` by default.
For ngrok, put the hostname from your current HTTPS Forwarding URL in
`VITE_ALLOWED_HOSTS` (without `https://` or a path), restart Vite, and expose the
Vite port. Continue to run Spring Boot on the same computer. Keep your own
existing `.env`; values in the example are starting points.

Use the complete updated Project-2026 backend with this release. There is no separate Backend-Updates overlay. Frontend source and the production build are both provided.

## New application flow

1. Sign up, then choose **New application** in the workspace or **Become a member**.
2. **Organization & committee:** choose the active unit, its committee and an
   available position. **Next: user profile** opens the profile form.
3. **User profile:** fill date of birth, education, address, constituency, voter
   ID, voting-list part and optional Aadhaar last four digits. The name and
   contact details supplied at signup appear above the form. An existing
   profile is prefilled. **Next: upload documents** saves the profile using the
   existing POST/PUT API, then creates the application.
4. **Upload documents:** upload the signed recommendation and any supporting
   PDF/JPG/PNG, up to 5 MB per file. **Finish & view application** opens its
   detail view once a recommendation has been uploaded. **Save and close**
   leaves the saved application available under My applications.

Back preserves the information entered in this dialog. Returning from Documents
and advancing again updates the profile and reuses the saved application. The
organization and position become fixed once that application is created.
Closing before the profile is saved does not create an application; unsaved
inputs are not kept in browser storage. A failed profile save does not advance
or create an application. Constituency failures and vacant-position errors block
progress while displaying the actual server error.

The existing review rules still apply: uploading is separate from submitting.
A reviewer must verify the recommendation before **Submit application** starts
the approval chain. This update does not auto-approve documents.

## Account actions

**My profile** now allows the signed-in user to create or edit their own member
profile, using the same form fields as the application flow.

**Change password** is available in the workspace navigation and My profile:
current password → new password → confirm new password → Update password.
The frontend checks confirmation, reuse, minimum length and the BCrypt byte
limit. The backend checks the current password, saves a BCrypt hash, clears the
cookies and invalidates old JWTs against the changed credentials. Sign in again
with the new password. This remains the signed-in password-change flow. Forgot password provides administrator-assisted recovery contacts; email reset delivery is not implemented.

Logout clears the identity immediately and returns to the public homepage.
A pending current-user response cannot restore the displayed identity after
logout. A server logout failure is reported; retry to ensure its cookie is
cleared before leaving. JWTs and passwords are not stored in localStorage.

## Homepage and dashboard

- **How it works** follows three legal-help steps: Report your issue →
  An advocate is assigned → Track and resolve. The Get Help page now implements this process using the new area-based case workflow.
- **State Executive Committee** follows Inspiration & leadership. It displays
  all active seats on active State committees, including vacancies and current appointments. The public response
  contains professional names, positions, committee and division names only.
  Names from the HTML reference's sample data are not used as real appointments.
- **Registered advocates**, **Grievances Resolved**, and **Active Legal Clinics**
  appear on the homepage, administrator dashboard and member overview.
- Registered advocate totals count distinct accounts with the ADVOCATE role,
  including disabled registrations. Ordinary APPLICANT signups are excluded.
- Grievances Resolved is the actual count of help requests currently RESOLVED or CLOSED; reopening removes a case from that current count. The old manual `app.portal.grievances-resolved` setting is no longer used.
- Clinic management is not part of this update. Active Legal Clinics remains an optional verified `app.portal.active-legal-clinics` setting; its card shows **— / Not yet available** when unset.
- The existing registered-user counts and green Maharashtra location markers
  are retained. Enabled users remain distinct from online presence, and district
  points remain approximations based on saved city/district names.

English and Marathi are supported for the new forms and sections. Stored names
and other database values remain as entered.

## API contracts

All paths are relative to `/api`. All requests include cookies. Mutations use
GET `/auth/csrf` and send its returned header/token, including multipart uploads.

| Action | API |
| --- | --- |
| Effective access | GET `/auth/access` |
| Area roles | GET/POST `/admin/role-assignments`, PATCH `/{id}/revoke`, GET `/{id}/history` |
| President document inbox | GET `/document-reviews/applications` |
| Sign up / sign in / identity / logout | Existing `/auth/signup`, `/auth/signin`, `/auth/user`, `/auth/signout` |
| Load, create, edit own profile | GET / POST / PUT `/users/{authenticatedUserId}/profile` |
| Constituency selection | GET `/assembly-constituencies?stateCode=MH` |
| Create application | POST `/registration-applications` with `selectedShakhaId`, `requestedPositionId` |
| Upload documents | POST `/registration-applications/{id}/documents` as multipart `documentType`, `file` |
| Change password | POST `/auth/change-password` with `currentPassword`, `newPassword`, `confirmPassword`; returns 204 |
| Public totals | GET `/public/portal/summary` |
| Public state committee positions | GET `/public/portal/state-executive/positions` |
| Public signup organisation catalogue | GET `/public/portal/organization-units` |

Constituency GETs are available to authenticated users and constituency creation remains PARTY_HEAD-only. Private admin data is filtered by active scoped grants; account/profile writes remain restricted to owner or PARTY_HEAD as appropriate. See the permission matrix in the paired setup guide.

## Verify and build

```bash
npm test
npm run build
```

Verification for this release:

- 33 Node tests passed, including scoped capabilities and revocation, signup payloads, hierarchy choices, latest recommendations and all seven numbered State VP seats.
- Vite production build passed.
- Browser fixtures verified area role grants, matching unit choices, history and revoke; scoped UI controls, root protection, read-only profiles/documents, President verification inbox and revoked access refresh. Mobile role lists and the grant dialog were checked at 390 px without page overflow.
- The prior homepage, signup, document-upload and mobile checks are retained.
- Backend: 318 tests passed and 2 opt-in MySQL tests skipped, including isolated H2/JPA/filter-chain scope tests. See the setup guide for real-MySQL checks.
- QA middleware, fake identities and local environment files are excluded from the release.

These checks do not claim a run against your live MySQL database.
Production hosting needs a matching `/api` reverse proxy (or an HTTPS
`VITE_API_BASE_URL` and credentialed CORS). Vite's development proxy is not part
of the built `dist/` files. Routing uses hashes. Map/font attribution remains in
`THIRD_PARTY_NOTICES.md`.

# AbetCRM Android

WhatsApp-first mobile CRM by Abetworks.
Auto-captures leads from WhatsApp notifications, phone calls, and contacts.

---

## Features

| Feature | How it works |
|---|---|
| **WhatsApp Auto-capture** | `NotificationListenerService` reads incoming WA notifications → creates/updates lead with sender name + message snippet |
| **Call Auto-capture** | `BroadcastReceiver` on `PHONE_STATE` → logs incoming, outgoing, missed calls → creates lead if number unseen |
| **Contact Import** | Bulk import device contacts → leads in one tap |
| **Call Log Import** | Import last 30 days of call history as leads |
| **Local DB** | Room/SQLite — works fully offline |
| **Cloud Sync** | WorkManager syncs unsynced leads/activities every 15 min or on-demand |
| **Pipeline Board** | Horizontal Kanban — New / Contacted / Interested / Won / Lost |
| **Activity Log** | Per-lead timeline of all calls, messages, notes, stage changes |

---

## Setup

### 1. Open in Android Studio
```
File → Open → select abetcrm-android/
```
Requires Android Studio Hedgehog or newer.

### 2. Build & Run
- Connect an Android device (API 26+) or start an emulator
- Click ▶ Run

### 3. Permissions (on first launch)
The app will request:
- **Phone State + Call Log** — for call capture
- **Contacts** — for contact import
- **Notifications** — for lead capture alerts

Then it opens **Settings → Notification Access** — find AbetCRM and enable the toggle.
This is the one permission Android requires you to grant manually in Settings.

### 4. Cloud Sync (optional)
Go to app Settings tab:
- Enter your API URL (default: `https://api.abetworks.in/v1`)
- Login with your Abetworks account credentials
- Sync starts automatically every 15 minutes

---

## Architecture

```
app/
├── data/
│   ├── model/          Lead.kt, Activity.kt
│   ├── db/             AbetDatabase.kt, LeadDao, ActivityDao
│   └── repository/     LeadRepository.kt (single source of truth)
├── service/
│   ├── CallReceiver.kt             BroadcastReceiver for calls
│   ├── WhatsAppNotificationListener.kt   NotificationListenerService
│   ├── SyncManager.kt + SyncWorker.kt   WorkManager sync
│   └── BootReceiver.kt             Restart listeners after reboot
├── sync/
│   └── ApiService.kt               HTTP client for cloud API
├── ui/
│   ├── LeadViewModel.kt            Shared ViewModel
│   ├── MainActivity.kt             Bottom nav host
│   ├── leads/                      LeadsFragment, LeadDetailActivity
│   ├── pipeline/                   PipelineFragment
│   └── settings/                   SettingsFragment
└── util/
    ├── PhoneUtils.kt               Normalize, format, wa.me links
    ├── Prefs.kt                    EncryptedSharedPreferences
    └── NotificationHelper.kt       Notification channels
```

---

## Cloud API contract

Your Next.js backend needs these endpoints:

```
POST   /v1/auth/login           { email, password } → { token, tenantId, name }
POST   /v1/leads                { name, phone, ... } → { id }
PUT    /v1/leads/:id            { ...updates }       → { id }
POST   /v1/activities           { leadId, type, ... } → { id }
```

---

## WhatsApp capture notes

Android does **not** allow reading WhatsApp messages directly.
The `NotificationListenerService` reads the notification text as it appears on screen.
This captures:
- ✅ Sender name
- ✅ First ~100 chars of the message
- ✅ Timestamp
- ❌ Full chat history (not accessible without root)

Supports: WhatsApp, WhatsApp Business, GB WhatsApp.

---

## Gradle dependencies

| Library | Purpose |
|---|---|
| Room 2.6.1 | Local SQLite ORM |
| WorkManager 2.9.0 | Background sync |
| Security Crypto | Encrypted token storage |
| Material Components 1.11.0 | UI |
| Lifecycle/ViewModel KTX | MVVM |
| Coroutines 1.7.3 | Async |

---

## Next steps

- [ ] Push notifications for follow-up reminders (AlarmManager)
- [ ] WhatsApp Business API webhook (cloud side)
- [ ] Quick-reply templates
- [ ] Lead scoring
- [ ] Web dashboard (Next.js — already built as Sprint 1 artifact)
"# android-temp" 

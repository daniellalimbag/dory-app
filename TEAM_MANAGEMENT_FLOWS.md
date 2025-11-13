# Team Management System - Flow Diagrams

## 🏊 NEW SWIMMER INVITATION FLOW

```
┌─────────────────────────────────────────────────────────────┐
│                         COACH SIDE                          │
└─────────────────────────────────────────────────────────────┘

1. Coach opens SwimmersActivity
   │
   ├─ Clicks FAB (+ button)
   │
   ├─ Dialog appears:
   │  ┌─────────────────────────────────┐
   │  │ "Invite Swimmer (Recommended)"  │ ← Recommended path
   │  │ "Add Manually (Coach Only)"     │ ← Backup option
   │  └─────────────────────────────────┘
   │
   └─ Selects "Invite Swimmer"
      │
      ├─ InviteSwimmerActivity opens
      │
      ├─ Coach enters:
      │  • Swimmer Name (required)
      │  • Email (optional)
      │
      ├─ Clicks "Generate Invitation Code"
      │  │
      │  └─ System:
      │     • Generates 6-char code (e.g., "ABC123")
      │     • Creates TeamInvitation record
      │     • Status: PENDING
      │     • Expires: 7 days
      │
      ├─ Code displayed: ┌─────────┐
      │                   │ ABC123  │
      │                   └─────────┘
      │
      └─ Coach clicks "Share Invitation"
         │
         └─ System share sheet opens
            │
            ├─ SMS
            ├─ Email  ← Share via any method
            ├─ WhatsApp
            └─ etc.


┌─────────────────────────────────────────────────────────────┐
│                        SWIMMER SIDE                         │
└─────────────────────────────────────────────────────────────┘

1. Swimmer receives invitation message:
   
   "🏊 You're invited to join Varsity Swim Team!
   
   Swimmer: John Doe
   Invitation Code: ABC123
   
   Download the app and use this code to join."

   │
   ├─ Swimmer downloads app
   │
   ├─ Creates account (login/register)
   │
   └─ Opens app → EnrollViaCodeActivity
      │
      ├─ Enters code: "ABC123"
      │
      ├─ System validates:
      │  • Code exists?
      │  • Not expired?
      │  • Role = SWIMMER?
      │  • Team exists?
      │
      ├─ ✅ Valid → Shows confirmation dialog:
      │  ┌────────────────────────────────────┐
      │  │ Join Varsity Swim Team?            │
      │  │                                    │
      │  │ You're about to join this team.   │
      │  │ Complete your profile next.       │
      │  │                                    │
      │  │ [Cancel]  [Accept Invitation]     │
      │  └────────────────────────────────────┘
      │
      └─ Swimmer clicks "Accept Invitation"
         │
         └─ CreateSwimmerProfileActivity opens
            │
            ├─ Swimmer fills profile:
            │  • Name
            │  • Birthday (date pickers)
            │  • Height, Weight, Wingspan
            │  • Sex (Male/Female)
            │
            ├─ Clicks "Complete Profile & Join Team"
            │
            └─ System:
               • Creates Swimmer record
               • Links account to team
               • Marks invitation as ACCEPTED
               • Sets current team
               │
               └─ SUCCESS!
                  • Swimmer now part of team
                  • Redirected to MainActivity
                  • Ready to start training!
```

---

## 🎓 OLD vs NEW COMPARISON

### ❌ OLD FLOW (Problematic)

```
COACH                           SWIMMER
  │                               │
  ├─ Opens TrackAddSwimmerActivity
  │
  ├─ Fills COMPLETE profile:
  │  • Name: "John Doe"         (Problem: Coach knows this?)
  │  • Birthday: "2005-03-15"   (Problem: Coach knows this?)
  │  • Height: 175cm             (Problem: Guessing?)
  │  • Weight: 68kg              (Problem: Guessing?)
  │  • Wingspan: 180cm           (Problem: How would coach know?)
  │  • Sex: Male
  │
  ├─ Saves → Swimmer exists in DB
  │  (Ghost profile!)
  │
  ├─ Code generated: "XYZ789"
  │
  ├─ Coach manually shares code
  │                               │
  │                               ├─ Receives code
  │                               │
  │                               ├─ EnrollViaCodeActivity
  │                               │
  │                               ├─ Enters code
  │                               │
  │                               └─ LINKS to pre-existing profile
  │                                  (Swimmer just "claims" it)
  │
  └─ PROBLEM: What if measurements were wrong?
     PROBLEM: What if swimmer already in another team?
     PROBLEM: No confirmation step!
```

### ✅ NEW FLOW (Proper)

```
COACH                           SWIMMER
  │                               │
  ├─ Opens InviteSwimmerActivity
  │
  ├─ Enters minimal info:
  │  • Name: "John Doe"          (Just for reference)
  │  • Email: optional            (For notification)
  │
  ├─ Generates invitation code
  │  (NO profile created yet)
  │
  ├─ Shares via system share
  │                               │
  │                               ├─ Receives invitation
  │                               │
  │                               ├─ Enters code
  │                               │
  │                               ├─ CONFIRMS team join
  │                               │
  │                               ├─ FILLS OWN PROFILE
  │                               │  • Name (can change)
  │                               │  • Birthday (knows exactly)
  │                               │  • Measurements (accurate)
  │                               │
  │                               └─ Saves → Profile created
  │                                  Invitation accepted
  │
  └─ ✅ Accurate data!
     ✅ Swimmer controls their info!
     ✅ Proper consent flow!
```

---

## 🗄️ DATABASE STRUCTURE

### TeamInvitation Entity

```
┌─────────────────────────────────────────────┐
│         TeamInvitation Table                │
├─────────────────────────────────────────────┤
│ id            INT (PK, Auto)                │
│ teamId        INT → references teams(id)    │
│ inviteCode    VARCHAR(6) "ABC123"           │
│ invitedEmail  VARCHAR? (optional)           │
│ role          ENUM (COACH, SWIMMER)         │
│ status        ENUM (PENDING, ACCEPTED...)   │
│ createdAt     LONG (timestamp)              │
│ expiresAt     LONG (timestamp)              │
│ createdBy     VARCHAR (coach email)         │
└─────────────────────────────────────────────┘

Examples:
┌────┬────────┬──────────┬──────────────────┬─────────┬─────────┬─────────────┬─────────────┬───────────────────┐
│ id │ teamId │  code    │  invitedEmail    │  role   │ status  │  createdAt  │  expiresAt  │    createdBy      │
├────┼────────┼──────────┼──────────────────┼─────────┼─────────┼─────────────┼─────────────┼───────────────────┤
│ 1  │   5    │ ABC123   │ john@swim.com    │ SWIMMER │ PENDING │ 1699800000  │ 1700404800  │ coach@varsity.edu │
│ 2  │   5    │ XYZ789   │ null             │ SWIMMER │ ACCEPTED│ 1699700000  │ 1700304800  │ coach@varsity.edu │
│ 3  │   5    │ DEF456   │ assist@coach.com │ COACH   │ PENDING │ 1699900000  │ 1700504800  │ coach@varsity.edu │
└────┴────────┴──────────┴──────────────────┴─────────┴─────────┴─────────────┴─────────────┴───────────────────┘
```

### Swimmer Entity (Updated)

```
┌─────────────────────────────────────────────┐
│            Swimmer Table                    │
├─────────────────────────────────────────────┤
│ id            INT (PK, Auto)                │
│ teamId        INT → references teams(id)    │
│ name          VARCHAR                       │
│ birthday      VARCHAR "YYYY-MM-DD"          │
│ height        FLOAT (cm)                    │
│ weight        FLOAT (kg)                    │
│ sex           VARCHAR "Male"/"Female"       │
│ wingspan      FLOAT (cm)                    │
│ ❌ code       REMOVED!                      │
└─────────────────────────────────────────────┘
```

---

## 🔐 INVITATION LIFECYCLE

```
┌─────────────┐
│   PENDING   │ ← Initial state when coach creates invitation
└──────┬──────┘
       │
       ├─ Swimmer accepts → ┌───────────┐
       │                     │ ACCEPTED  │
       │                     └───────────┘
       │
       ├─ 7 days pass    → ┌───────────┐
       │                     │ EXPIRED   │
       │                     └───────────┘
       │
       └─ Coach cancels  → ┌───────────┐
                            │ CANCELLED │
                            └───────────┘

Auto-Cleanup:
• System runs expireOldInvitations() periodically
• Changes PENDING → EXPIRED for old invitations
• Prevents stale codes from being used
```

---

## 🎯 KEY DESIGN DECISIONS

### 1. Why separate invitation from profile?

```
INVITATION (Temporary)          PROFILE (Permanent)
     │                               │
     ├─ Expires after 7 days         ├─ Exists forever
     ├─ Can be cancelled             ├─ Can be edited
     ├─ Many-to-one (many           ├─ One-to-one with account
     │  invites → one profile)       │
     └─ Tracks intent                └─ Tracks actual member
```

### 2. Why allow optional email?

```
WITH EMAIL                      WITHOUT EMAIL
     │                               │
     ├─ Future: Send notification    ├─ Coach shares code manually
     ├─ Can track who was invited    ├─ More flexible
     └─ Better audit trail           └─ Privacy-friendly
```

### 3. Why keep manual addition?

```
SCENARIO 1: Swimmer has app     SCENARIO 2: Swimmer doesn't have app
     │                               │
     ├─ Use invitation flow          ├─ Coach creates profile manually
     └─ ✅ Recommended                ├─ Swimmer gets data later
                                      └─ 🔧 Backup option
```

---

**End of Flow Diagrams**

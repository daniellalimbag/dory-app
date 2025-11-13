# Team Management System - Testing Guide

## 🧪 How to Test the New Invitation System

### Prerequisites
1. Build and run the app on your device/emulator
2. Have 2 test accounts ready:
   - **Coach Account** (coach@test.com)
   - **Swimmer Account** (swimmer@test.com)

---

## Test 1: Coach Invites Swimmer (Happy Path)

### Step 1: Coach Creates Invitation
1. Login as **coach@test.com**
2. Create a team (if you haven't already)
3. Go to **Swimmers** section
4. Tap the **+ FAB** button
5. Select **"Invite Swimmer (Recommended)"**
6. Fill in:
   - Swimmer Name: "Test Swimmer"
   - Email: "swimmer@test.com" (optional)
7. Tap **"Generate Invitation Code"**
8. ✅ **VERIFY**: Code appears (e.g., "ABC123")
9. Tap **"Share Invitation"**
10. ✅ **VERIFY**: System share sheet opens
11. Copy the code to clipboard (for next steps)

### Step 2: Swimmer Accepts Invitation
1. Logout from coach account
2. Login as **swimmer@test.com**
3. Go to **Enroll via Code** screen
4. Enter the invitation code from Step 1
5. Tap **"Enroll"**
6. ✅ **VERIFY**: Confirmation dialog appears showing team name
7. Tap **"Accept Invitation"**
8. ✅ **VERIFY**: Profile creation form appears
9. Fill in profile:
   - Name: "Test Swimmer"
   - Birthday: Pick a date
   - Height: 175
   - Weight: 68
   - Wingspan: 180
   - Sex: Select Male or Female
10. Tap **"Complete Profile & Join Team"**
11. ✅ **VERIFY**: Success message appears
12. ✅ **VERIFY**: Redirected to MainActivity
13. ✅ **VERIFY**: Team name shows in top bar

### Step 3: Verify in Database (Coach Side)
1. Logout from swimmer account
2. Login back as **coach@test.com**
3. Go to **Swimmers** section
4. ✅ **VERIFY**: "Test Swimmer" appears in list
5. Tap on the swimmer
6. ✅ **VERIFY**: Profile shows correct data
7. ✅ **VERIFY**: No "code" field is displayed

---

## Test 2: Invalid/Expired Codes

### Test 2a: Invalid Code
1. Login as **swimmer@test.com**
2. Go to **Enroll via Code**
3. Enter: "INVALID"
4. Tap **"Enroll"**
5. ✅ **VERIFY**: Error message: "Invalid or expired invitation code"

### Test 2b: Expired Code
1. (This requires modifying the code temporarily)
2. In `TeamInvitation.kt`, set `expiresAt` to past date
3. Create invitation
4. Try to use it
5. ✅ **VERIFY**: Error message: "Invalid or expired invitation code"

### Test 2c: Coach Code Used by Swimmer
1. Create a coach invitation (future feature)
2. Try to use it in swimmer enrollment
3. ✅ **VERIFY**: Error message: "This code is for coaches, not swimmers"

---

## Test 3: Manual Swimmer Addition (Backup Flow)

1. Login as **coach@test.com**
2. Go to **Swimmers** section
3. Tap the **+ FAB** button
4. Select **"Add Manually (Coach Only)"**
5. Fill in complete profile
6. Tap **"Save Swimmer"**
7. ✅ **VERIFY**: Success message appears
8. ✅ **VERIFY**: Swimmer appears in list immediately
9. ✅ **VERIFY**: No invitation code is shown

---

## Test 4: Database Migration

### Clean Install Test
1. Uninstall the app completely
2. Install new version
3. ✅ **VERIFY**: App launches without crash
4. ✅ **VERIFY**: Can create team and invite swimmers

### Upgrade Test (If you had v8 data)
1. ⚠️ **NOTE**: This will wipe all data (destructive migration)
2. Install new version over old version
3. ✅ **VERIFY**: App launches
4. ✅ **VERIFY**: Old data is gone (expected due to schema change)
5. ✅ **VERIFY**: Can create new teams and invitations

---

## Test 5: UI/UX Verification

### InviteSwimmerActivity
- ✅ Card layout displays correctly
- ✅ Input fields have proper hints
- ✅ Generate button is enabled
- ✅ Code displays in large, bold text
- ✅ Share button enables after code generation
- ✅ System share sheet works on your device

### CreateSwimmerProfileActivity
- ✅ All input fields render correctly
- ✅ Date pickers work (Month/Day/Year)
- ✅ Radio buttons for sex selection work
- ✅ Validation messages show for invalid input
- ✅ Save button submits form

### SwimmersActivity
- ✅ FAB shows dialog with 2 options
- ✅ "Invite Swimmer" is first (recommended)
- ✅ "Add Manually" is second
- ✅ Both options navigate correctly

---

## Test 6: Edge Cases

### Edge Case 1: Same Code Twice
1. Create invitation → Get code "ABC123"
2. Create another invitation
3. ✅ **VERIFY**: Different code is generated (very unlikely collision)

### Edge Case 2: Empty Fields
1. Try to create invitation without name
2. ✅ **VERIFY**: Error message appears

### Edge Case 3: Multiple Teams
1. Swimmer joins Team A
2. Swimmer gets invitation for Team B
3. ✅ **VERIFY**: Can join Team B
4. ✅ **VERIFY**: Can switch between teams in team switcher

### Edge Case 4: Network/Offline
1. Turn off network
2. Create invitation
3. ✅ **VERIFY**: Works offline (local database)
4. Share code via SMS
5. ✅ **VERIFY**: Can send SMS offline (handled by OS)

---

## Test 7: Data Integrity

### Verify TeamInvitation Table
1. Use Android Studio Database Inspector
2. Navigate to: `team_invitations` table
3. ✅ **VERIFY**: Columns exist:
   - id, teamId, inviteCode, invitedEmail, role, status, createdAt, expiresAt, createdBy

### Verify Swimmer Table
1. Check `swimmers` table
2. ✅ **VERIFY**: No `code` column exists
3. ✅ **VERIFY**: All other columns intact:
   - id, teamId, name, birthday, height, weight, sex, wingspan

### Verify Invitation Status Change
1. Create invitation (status = PENDING)
2. Swimmer accepts it
3. Check database
4. ✅ **VERIFY**: Status changed to ACCEPTED

---

## Test 8: Regression Testing (Ensure Old Features Still Work)

1. ✅ Create team
2. ✅ Edit team settings (if available)
3. ✅ Add coach to team manually
4. ✅ View swimmer list
5. ✅ Edit swimmer profile
6. ✅ Delete swimmer
7. ✅ Switch between teams
8. ✅ Track swim sessions
9. ✅ View swim history

---

## 🐛 Known Issues to Watch For

1. **Possible Issue**: Date picker might crash on some Android versions
   - **Solution**: Test on multiple Android versions

2. **Possible Issue**: Share sheet might not work on emulator
   - **Solution**: Test on real device

3. **Possible Issue**: Database migration might fail silently
   - **Solution**: Check logcat for Room migration errors

4. **Possible Issue**: Long team names might overflow in confirmation dialog
   - **Solution**: Test with various team name lengths

---

## 📊 Test Results Template

```
Date: _______________
Tester: _______________
Device: _______________
Android Version: _______________

Test 1: Coach Invites Swimmer ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 2: Invalid Codes         ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 3: Manual Addition        ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 4: Database Migration     ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 5: UI/UX                  ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 6: Edge Cases             ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 7: Data Integrity         ☐ PASS ☐ FAIL
  Notes: _________________________________

Test 8: Regression             ☐ PASS ☐ FAIL
  Notes: _________________________________

Overall Status: ☐ READY FOR USE ☐ NEEDS FIXES
```

---

## 🚀 Next Steps After Testing

Once all tests pass:
1. ✅ Commit changes to git
2. ✅ Create pull request
3. ✅ Deploy to beta testers
4. ✅ Gather feedback
5. 🔄 Iterate on improvements

**Good luck testing!** 🏊‍♂️

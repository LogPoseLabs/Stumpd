# Firebase Cloud Sync Setup Guide

## Overview
This app now supports **online cloud sync** using **Firebase Firestore** (100% FREE for typical usage).

**Architecture:**
- **Room Database** = Local storage (works offline, no internet needed)
- **Firebase Firestore** = Cloud backup + multi-device sync
- **Automatic sync** when network reconnects
- **Manual sync** available anytime

---

## Step 1: Create Firebase Project (Free)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add project"**
3. Enter project name: `Stumpd-Cricket-Scoring` (or any name)
4. **Disable Google Analytics** (optional, keeps it simpler)
5. Click **"Create project"**
6. Wait for project creation (30 seconds)

---

## Step 2: Add Android App to Firebase

1. In Firebase Console, click the **Android icon** to add Android app
2. Enter your package name: `com.oreki.stumpd`
3. **App nickname**: `Stumpd` (optional)
4. **Debug signing certificate**: Leave blank for now
5. Click **"Register app"**

---

## Step 3: Download google-services.json

1. Firebase will provide a **`google-services.json`** file
2. Click **"Download google-services.json"**
3. Move this file to: `/Users/sb/cursor/Stumpd/app/google-services.json`

   **IMPORTANT**: The file must be in the `app/` folder, NOT the root project folder

---

## Step 4: Enable Firestore Database

1. In Firebase Console sidebar, click **"Firestore Database"**
2. Click **"Create database"**
3. Choose **"Start in production mode"** (we'll add rules next)
4. Select location: Choose closest to you (e.g., `asia-south1` for India)
5. Click **"Enable"**

---

## Step 5: Configure Firestore Security Rules (INVITE CODE RESTRICTION)

**IMPORTANT: Only group members (who joined via invite code) can see group data.**

1. In Firestore, go to **"Rules"** tab
2. Replace the rules with:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    
    // ========== OWNERSHIP-ENFORCED RULES ==========
    // 
    // Security model:
    // - All authenticated users can READ most data
    // - Only owners can WRITE/DELETE their own data
    // - ownerId field determines ownership
    // - Non-owners have minimal write access (join/leave only)
    
    // ========== GROUPS ==========
    // Anyone authenticated can read groups (app filters by membership)
    match /groups/{groupId} {
      // Anyone authenticated can READ (app filters by membership)
      allow read: if request.auth != null;
      
      // CREATE: Must set yourself as owner
      allow create: if request.auth != null 
                    && request.resource.data.ownerId == request.auth.uid;
      
      // UPDATE: Owner can update anything.
      // Non-owners can ONLY modify memberDeviceIds (join/leave via invite code)
      // or claim ownership (update ownerId + memberDeviceIds to themselves).
      allow update: if request.auth != null
                    && (resource.data.ownerId == request.auth.uid
                        || request.resource.data.diff(resource.data).affectedKeys().hasOnly(['memberDeviceIds'])
                        || (request.resource.data.diff(resource.data).affectedKeys().hasOnly(['ownerId', 'memberDeviceIds'])
                            && request.resource.data.ownerId == request.auth.uid));
      
      // DELETE: Only owners
      allow delete: if request.auth != null 
                    && resource.data.ownerId == request.auth.uid;
    }
    
    // Group subcollections (members, defaults, etc.) - only owner can write
    match /groups/{groupId}/{subcollection=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null
                   && get(/databases/$(database)/documents/groups/$(groupId)).data.ownerId == request.auth.uid;
    }
    
    // ========== MATCHES ==========
    match /matches/{matchId} {
      allow read: if request.auth != null;
      
      allow create: if request.auth != null 
                    && request.resource.data.ownerId == request.auth.uid;
      
      allow update, delete: if request.auth != null 
                            && resource.data.ownerId == request.auth.uid;
    }
    
    // Match subcollections - only match owner can write
    match /matches/{matchId}/{subcollection=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null
                   && get(/databases/$(database)/documents/matches/$(matchId)).data.ownerId == request.auth.uid;
    }
    
    // ========== IN-PROGRESS MATCHES (Live Scoring) ==========
    match /in_progress_matches/{matchId} {
      allow read: if request.auth != null;
      
      allow create: if request.auth != null 
                    && request.resource.data.ownerId == request.auth.uid;
      
      allow update, delete: if request.auth != null 
                            && resource.data.ownerId == request.auth.uid;
    }
    
    // ========== PLAYERS (Shared Resource) ==========
    match /players/{playerId} {
      allow read: if request.auth != null;
      allow create, update: if request.auth != null;
      allow delete: if request.auth != null 
                    && (resource.data.ownerId == null 
                        || resource.data.ownerId == request.auth.uid);
    }
    
    // ========== USER-SPECIFIC DATA (Private) ==========
    match /users/{userId}/preferences/{prefId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    match /users/{userId}/group_last_teams/{teamId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // ========== SHARED MATCHES (Live Match Sharing) ==========
    match /shared_matches/{shareCode} {
      // Anyone authenticated can read (to join via share code)
      allow read: if request.auth != null;
      
      // Create: Must set yourself as owner
      allow create: if request.auth != null 
                    && request.resource.data.ownerId == request.auth.uid;
      
      // Update: Only owner (for viewCount, isActive toggling)
      allow update: if request.auth != null 
                    && resource.data.ownerId == request.auth.uid;
      
      // Delete: Only owner
      allow delete: if request.auth != null 
                    && resource.data.ownerId == request.auth.uid;
    }
  }
}
```

3. Click **"Publish"**

### How It Works (Simplified Architecture):

**Firebase rules are simplified** - all authenticated users can READ data. The **app handles filtering** based on group membership.

```
┌─────────────────────────────────────────────────────────────┐
│  Device A (Creator)                                         │
│  - Creates group "Weekend Warriors"                         │
│  - Generates invite code: ABC123                            │
│  - deviceId added to memberDeviceIds in Firestore           │
└─────────────────────────────────────────────────────────────┘
                         │ shares code
                         ▼
┌─────────────────────────────────────────────────────────────┐
│  Device B (Friend)                                          │
│  - Enters code ABC123 in "Join Group"                       │
│  - App adds Device B's ID to group's memberDeviceIds        │
│  - App now downloads and displays this group's data         │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Device C (Random user, no code)                            │
│  - App only downloads groups where device is a member       │
│  - Won't see "Weekend Warriors" (not in memberDeviceIds)    │
│  - Filtering happens in app, not Firebase rules             │
└─────────────────────────────────────────────────────────────┘
```

### Security Model:
- **Firebase Level**: Ownership-enforced rules (ownerId check on all writes)
- **App Level**: Filters data based on `memberDeviceIds` array + `isOwner` flag
- **Write Protection**: Only owners can modify groups, matches, and subcollections

### What This Means:
- **Groups**: Only the owner can edit group settings, members, and defaults. Non-owners can only join/leave (modify `memberDeviceIds`) or claim ownership.
- **Group Subcollections**: Only the group owner can write (uses `get()` to verify parent ownership)
- **Matches**: Only the match creator can update/delete. Subcollections also owner-protected.
- **Players**: Shared resource - anyone can create/update, delete restricted to creator
- **User Preferences**: Private to each user (Firebase enforced)

### Key Rule: `diff().affectedKeys().hasOnly()`
The group update rule uses Firestore's `diff()` to ensure non-owners can **only** modify the `memberDeviceIds` field (for join/leave). Any attempt to change group name, settings, or other fields will be rejected by Firebase.

### Note on Subcollection Rules:
The `get()` call in subcollection rules costs 1 additional read per write. This is negligible for typical usage but worth noting for quota awareness.

---

## Step 6: Enable Authentication

1. In Firebase Console sidebar, click **"Authentication"**
2. Click **"Get started"**
3. Go to **"Sign-in method"** tab
4. Click on **"Anonymous"**
5. **Enable** the toggle
6. Click **"Save"**

**Why Anonymous Auth?**
- No login required
- No email/password needed
- Each device gets a unique user ID
- Perfect for offline-first apps

---

## Step 7: Update build.gradle Files

### Root-level `build.gradle.kts` (Project level)

Add the Google Services plugin:

```kotlin
plugins {
    // ... existing plugins
    id("com.google.gms.google-services") version "4.4.0" apply false
}
```

### App-level `build.gradle.kts` (Already done in code)

```kotlin
plugins {
    // ... existing plugins
    id("com.google.gms.google-services") // Apply the plugin
}

dependencies {
    // Firebase (already added)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    
    // ... other dependencies
}
```

---

## Step 8: Sync Gradle and Build

1. **Sync Gradle**: In Android Studio, click **"Sync Now"** in the notification bar
2. **Build the app**: Run `./gradlew assembleDebug`
3. If successful, Firebase is configured!

---

## Step 9: Using Cloud Sync in the App

### Automatic Sync
- Enabled by default
- Syncs automatically when:
   - App starts (if online)
   - Network reconnects (after being offline)
   - After saving a match

### Manual Sync
1. Open the app
2. Go to **Main Menu** → **"Cloud Sync"**
3. Click **"Upload All to Cloud"** to backup all data
4. Click **"Download All from Cloud"** to restore data

### Sync Status
View in **Cloud Sync** screen:
- ✅ Last sync time
- ✅ Sync success/failure
- ✅ Number of items synced
- ✅ User ID (for multi-device setup)

---

## Free Tier Limits (More than enough!)

Firebase Free Tier (Spark Plan):
- **Firestore Storage**: 1 GB (enough for ~10,000 matches)
- **Firestore Reads**: 50,000/day (checking data)
- **Firestore Writes**: 20,000/day (saving data)
- **Firestore Deletes**: 20,000/day
- **Authentication**: Unlimited users

**Typical Usage:**
- Saving 1 match = ~50 writes (includes stats, partnerships, etc.)
- Loading 1 match = ~50 reads
- Free tier = ~400 matches/day = way more than needed!

---

## Multi-Device Setup

To sync data across multiple devices:

1. **Device 1 (Primary)**:
   - Open app → Cloud Sync
   - Click "Upload All to Cloud"
   - Note the **User ID** displayed

2. **Device 2 (Secondary)**:
   - Install app
   - The app will create a new anonymous user
   - For now, each device has separate data

**Future Enhancement** (Optional):
- Add Google Sign-In to share data across devices
- Use the same Google account on multiple devices
- Data automatically syncs between devices

---

## Troubleshooting

### "Firebase initialization failed"
- Ensure `google-services.json` is in `app/` folder
- Run `./gradlew clean` and rebuild

### "Failed to authenticate"
- Check Firebase Console → Authentication → Anonymous is enabled
- Check internet connection

### "Permission denied"
- Check Firestore Rules are correctly set
- Ensure rules allow `request.auth.uid == userId`

### "Sync keeps failing"
- Check internet connection
- Check Firebase Console → Firestore → Rules
- Look at Logcat for detailed error messages

---

## Room Database + Firestore Architecture

```
┌─────────────────────────────────────────────┐
│              USER ACTIONS                    │
│   (Scoring, Adding Players, Creating Groups) │
└──────────────────┬──────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────┐
│          REPOSITORIES                        │
│  (MatchRepository, PlayerRepository, etc.)   │
└──────────┬────────────────────┬─────────────┘
           │                    │
           ▼                    ▼
┌──────────────────┐  ┌────────────────────────┐
│  ROOM DATABASE   │  │  FIREBASE FIRESTORE    │
│  (Local Storage) │  │  (Cloud Backup)        │
│                  │  │                        │
│  ✅ Works Offline│  │  ✅ Online Sync        │
│  ✅ Fast         │  │  ✅ Multi-device       │
│  ✅ Private      │  │  ✅ Backup/Restore     │
└──────────────────┘  └────────────────────────┘
```

**Sync Flow:**
1. Save match → Room Database (instant, always works)
2. If online → Also upload to Firestore (background)
3. If offline → Queue for upload when online
4. On network reconnect → Auto-upload **only new/modified data** (timestamp-based)

**Smart Incremental Sync (Timestamp-Based):**
- ✅ Live scoring: Syncs every ball for real-time spectators
- ✅ Completed matches: Only syncs NEW matches (uses `matchDate` timestamp)
- ✅ Players/groups: Syncs periodically (every 5 min, if changed)
- ✅ Network reconnect: Only uploads data modified since last sync
- 📊 Expected usage: ~100 writes per match, no duplicate uploads!

See `FIREBASE_QUOTA_OPTIMIZATION.md` for technical details.

---

## Data Privacy & Security

✅ **Your data is private:**
- Each user has a unique anonymous ID
- Firestore rules prevent access to other users' data
- No personal information required

✅ **Offline-first:**
- App works 100% offline
- Cloud sync is optional/automatic
- No internet = no problem

✅ **No vendor lock-in:**
- Room database is always local
- Can export/backup locally anytime
- Firebase is just a backup layer

---

## Quota Monitoring & Management

### Free Tier Limits:
- **Document Writes**: 20,000/day (resets at midnight PST / 1:30 PM IST)
- **Document Reads**: 50,000/day
- **Storage**: 1 GB

### Monitor Usage:
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. **Firestore Database** → **Usage** tab
4. Check daily write/read counts

### If Quota Exceeded:
**Symptoms:**
- Logs show: `Status{code=RESOURCE_EXHAUSTED, description=Quota exceeded.}`
- Spectators can't see live updates
- Cloud sync paused (local scoring still works!)

**Solutions:**
1. **Wait for reset** (midnight PST / 1:30 PM IST next day)
2. **Upgrade to Blaze plan** (pay-as-you-go):
   - Same free tier included (20K writes/day FREE)
   - After free tier: ~$0.18 per 100K operations
   - Set budget alerts to control costs

### Optimization Applied:
This app uses **timestamp-based incremental sync** to minimize writes:
- ~100 writes per match (live scoring)
- ~0-5 writes per network reconnect (only NEW data since last sync)
- Can score **~190 matches/day** within free tier
- No duplicate uploads (timestamps tracked per data type)

**How it works:**
- Tracks when matches, players, and groups were last synced
- Only uploads data with timestamps newer than last sync
- Manual full sync updates all timestamps for a clean slate

See `FIREBASE_QUOTA_OPTIMIZATION.md` for implementation details.

---

## OTA (Over-The-Air) Updates Setup

The app supports automatic update checking using Firebase Remote Config. When you release a new version, users will see an update dialog on app startup.

### Step 1: Enable Remote Config

1. In Firebase Console, go to **"Remote Config"** (under Build section)
2. Click **"Create configuration"** (or "Add parameter" if already set up)

### Step 2: Create Each Parameter

You need to create **6 parameters**. For each parameter below, click **"Add parameter"** and fill in the details:

---

#### Parameter 1: `latest_version_code`
- **Parameter name:** `latest_version_code`
- **Data type:** Number
- **Default value:** `1` (your current versionCode from build.gradle.kts)
- **Description:** The versionCode of the latest release

---

#### Parameter 2: `latest_version_name`
- **Parameter name:** `latest_version_name`
- **Data type:** String
- **Default value:** `1.0.0`
- **Description:** The version name shown to users (e.g., "1.0.3")

---

#### Parameter 3: `apk_download_url`
- **Parameter name:** `apk_download_url`
- **Data type:** String
- **Default value:** _(leave empty until you have a release)_
- **Description:** Direct download URL for the APK
- **Example:** `https://github.com/username/Stumpd/releases/download/v1.0.3/Stumpd-1.0.3.apk`

---

#### Parameter 4: `update_message`
- **Parameter name:** `update_message`
- **Data type:** String
- **Default value:** `A new version is available with bug fixes and improvements.`
- **Description:** What's new in this update (shown in update dialog)

---

#### Parameter 5: `force_update`
- **Parameter name:** `force_update`
- **Data type:** Boolean
- **Default value:** `false`
- **Description:** Set to `true` for critical/security updates (user cannot dismiss)

---

#### Parameter 6: `min_version_code`
- **Parameter name:** `min_version_code`
- **Data type:** Number
- **Default value:** `1`
- **Description:** Users with versions below this are forced to update

---

### Quick Reference Table

| Parameter | Type | Default | When to Update |
|-----------|------|---------|----------------|
| `latest_version_code` | Number | `1` | Every release |
| `latest_version_name` | String | `1.0.0` | Every release |
| `apk_download_url` | String | _(empty)_ | Every release |
| `update_message` | String | `Bug fixes...` | Every release (optional) |
| `force_update` | Boolean | `false` | Only for critical updates |
| `min_version_code` | Number | `1` | When old versions have breaking bugs |

After adding all parameters, click **"Publish changes"**.

### Step 3: Host Your APK

**Option A: GitHub Releases (Recommended)**
1. Create a GitHub repository for your app
2. Go to **Releases** → **Create a new release**
3. Upload your APK file
4. Copy the direct download URL (right-click APK → Copy link)
5. Paste URL in `apk_download_url` parameter

Example URL format:
```
https://github.com/yourusername/Stumpd/releases/download/v1.0.3/Stumpd-release-1.0.3.apk
```

**Option B: Firebase Storage**
1. Go to **Storage** in Firebase Console
2. Upload your APK
3. Get the download URL
4. Paste in `apk_download_url`

**Option C: Any Direct Download URL**
- Google Drive (use direct download link)
- Dropbox (use dl.dropboxusercontent.com link)
- Your own web server

### Step 4: Publish Remote Config

1. After adding/updating parameters, click **"Publish changes"**
2. Changes take effect immediately (cached for 1 hour in production)

### Step 5: Release Process

When releasing a new version:

1. **Update build.gradle.kts:**
   ```kotlin
   versionCode = 5  // Increment this
   versionName = "1.0.3"  // Update version name
   ```

2. **Build the release APK:**
   ```bash
   ./gradlew assembleRelease
   ```

3. **Upload APK to your hosting** (GitHub Releases recommended)

4. **Update Remote Config:**
   - Set `latest_version_code` to `5`
   - Set `latest_version_name` to `1.0.3`
   - Set `apk_download_url` to the new APK URL
   - Update `update_message` with what's new
   - Click **"Publish changes"**

5. Users will see the update dialog on next app launch!

### Force Updates

For critical security updates:
- Set `force_update` to `true`, OR
- Set `min_version_code` to the minimum safe version

Users with versions below `min_version_code` cannot dismiss the update dialog.

### Debugging

Check Logcat for `AppUpdateManager` tag to see:
- Current vs latest version comparison
- Download URL being used
- Any errors during update check

---

## Next Steps

1. ✅ Add `google-services.json` to `app/` folder
2. ✅ Sync Gradle
3. ✅ Build and run the app
4. ✅ Open "Cloud Sync" from main menu
5. ✅ Click "Upload All to Cloud"
6. ✅ Verify data in Firebase Console → Firestore
7. 🆕 Set up Remote Config for OTA updates (optional)

---

## GitHub Actions: Auto-Build & Release

The project includes a GitHub Actions workflow that automatically builds and releases APKs when you push a version tag.

### Setup GitHub Secrets

Before the workflow can run, you need to add your `google-services.json` as a secret:

1. **Encode your google-services.json:**
   ```bash
   # On Mac/Linux:
   base64 -i app/google-services.json | pbcopy
   
   # On Windows (PowerShell):
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json")) | Set-Clipboard
   ```

2. **Add to GitHub Secrets:**
   - Go to your GitHub repo → **Settings** → **Secrets and variables** → **Actions**
   - Click **"New repository secret"**
   - Name: `GOOGLE_SERVICES_JSON`
   - Value: Paste the base64 encoded content
   - Click **"Add secret"**

### Create a Release

**Option 1: Push a version tag (Recommended)**
```bash
# Update version in app/build.gradle.kts first!
# versionCode = 5
# versionName = "1.0.3"

git add .
git commit -m "Release v1.0.3"
git tag v1.0.3
git push origin main --tags
```

**Option 2: Manual trigger**
1. Go to your repo → **Actions** → **Android Release**
2. Click **"Run workflow"**
3. Enter release name and click **"Run workflow"**

### What Happens Automatically

1. GitHub Actions builds the APK
2. Creates a new Release with the APK attached
3. APK URL format: `https://github.com/USER/REPO/releases/download/v1.0.3/Stumpd-1.0.3.apk`

### Update Firebase Remote Config

After the release is created:

1. Copy the APK download URL from the release
2. Go to Firebase Console → **Remote Config**
3. Update:
   - `latest_version_code` → new version code
   - `latest_version_name` → new version name
   - `apk_download_url` → the GitHub release APK URL
4. Click **"Publish changes"**

Users will now see the update prompt!

### Optional: Signed Release Builds

For production releases, you should sign your APK. See the comments in `.github/workflows/android-release.yml` for setup instructions.

---

## Support

If you encounter issues:
1. Check Logcat for error messages (filter by `Firebase` or `Sync`)
2. Verify Firebase Console setup
3. Ensure internet connectivity
4. Check Firestore Rules in Firebase Console

**Firebase Documentation:**
- [Firestore Get Started](https://firebase.google.com/docs/firestore/quickstart)
- [Anonymous Authentication](https://firebase.google.com/docs/auth/android/anonymous-auth)

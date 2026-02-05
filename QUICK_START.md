# 🚀 Quick Start Guide - Cloud Sync in 5 Minutes

## What You're Getting

✅ **Free cloud backup** for all your cricket scoring data  
✅ **Automatic sync** when online (works offline too!)  
✅ **No login required** (anonymous authentication)  
✅ **Zero data loss** - Room DB + Firestore backup  

---

## 5-Minute Setup

### Step 1: Create Firebase Project (2 minutes)

1. Go to https://console.firebase.google.com/
2. Click **"Add project"**
3. Name it: `Stumpd` (or any name)
4. **Disable** Google Analytics
5. Click **"Create project"**

### Step 2: Add Android App (1 minute)

1. Click the **Android icon** ⚙️
2. Package name: `com.oreki.stumpd`
3. Click **"Register app"**
4. **Download `google-services.json`**
5. Move it to: **`/Users/sb/cursor/Stumpd/app/google-services.json`**

⚠️ **IMPORTANT**: The file MUST be in the `app/` folder!

### Step 3: Enable Firestore (1 minute)

1. Sidebar → **"Firestore Database"**
2. Click **"Create database"**
3. Select **"Start in production mode"**
4. Choose location: **Pick closest to you**
5. Click **"Enable"**

### Step 4: Set Security Rules (30 seconds)

1. Click **"Rules"** tab
2. Paste this:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

3. Click **"Publish"**

### Step 5: Enable Anonymous Auth (30 seconds)

1. Sidebar → **"Authentication"**
2. Click **"Get started"**
3. **"Sign-in method"** tab
4. Click **"Anonymous"** → **Enable** → **Save**

### Step 6: Build & Run

```bash
cd /Users/sb/cursor/Stumpd
./gradlew assembleDebug
```

---

## ✅ Test It Works

1. Open app
2. Go to **Main Menu** → **"Cloud Sync"** (blue cloud button)
3. Click **"Upload All to Cloud"**
4. Go to Firebase Console → Firestore Database
5. You should see your data! 🎉

---

## 🎯 How to Use

### Just Use the App Normally!

The sync happens automatically:
- ✅ After saving a match
- ✅ When app starts (if online)
- ✅ When network reconnects

### Manual Sync (Optional)

1. Main Menu → "Cloud Sync"
2. **Upload** = Backup to cloud
3. **Download** = Restore from cloud

---

## 💡 Tips

- **Offline Mode**: App works 100% offline, syncs later
- **Auto-Sync**: Enabled by default (can toggle in Cloud Sync screen)
- **Data Safety**: Room DB is always primary, Firestore is backup
- **Free Tier**: 1GB storage, 50K reads/day, 20K writes/day = MORE than enough!

---

## 🐛 Issues?

### "Firebase initialization failed"
→ Make sure `google-services.json` is in `app/` folder

### "Permission denied"
→ Check Firestore Rules are set correctly

### "Build error"
→ Run `./gradlew clean` then rebuild

---

## 📚 More Info

- **Full Setup Guide**: `FIREBASE_SETUP.md`
- **Complete Documentation**: `CLOUD_SYNC_README.md`

---

**That's it! You now have cloud sync working! 🎉**

# Development Workflow Guide

## Overview

This guide documents how to develop on a work laptop (without git access) and push changes from a personal laptop to GitHub.

```
┌─────────────────────┐     ZIP file      ┌─────────────────────┐
│   WORK LAPTOP       │ ───────────────► │  PERSONAL LAPTOP    │
│   (Development)     │    (Transfer)     │   (Git Operations)  │
│                     │                   │                     │
│  • Edit code        │                   │  • Extract files    │
│  • Build & test     │                   │  • Git commit       │
│  • No git access    │                   │  • Git push         │
└─────────────────────┘                   └─────────────────────┘
```

---

## Initial Setup (One-Time)

### On Personal Laptop

1. **Clone the repository:**
   ```bash
   git clone git@github.com:YOUR_USERNAME/Stumpd.git
   cd Stumpd
   ```

2. **Verify .gitignore includes sensitive files:**
   ```
   # Should be in .gitignore
   app/google-services.json
   *.jks
   *.keystore
   signing.properties
   local.properties
   ```

3. **Set up SSH key (if not done):**
   ```bash
   # Generate SSH key
   ssh-keygen -t ed25519 -C "your_email@example.com"
   
   # Start SSH agent
   eval "$(ssh-agent -s)"
   ssh-add ~/.ssh/id_ed25519
   
   # Copy public key
   cat ~/.ssh/id_ed25519.pub
   # Add this to GitHub → Settings → SSH Keys
   
   # Test connection
   ssh -T git@github.com
   # Should say: "Hi USERNAME! You've successfully authenticated..."
   ```

---

## Regular Development Workflow

### Step 1: Develop on Work Laptop

1. Make your code changes
2. Build and test: `./gradlew assembleDebug`
3. Verify everything works

### Step 2: Prepare Files for Transfer

**On Work Laptop:**

1. **Create a zip of the project (excluding unnecessary files):**
   ```bash
   # Navigate to parent directory of project
   cd ~/cursor
   
   # Create zip excluding build artifacts and sensitive files
   zip -r Stumpd-update.zip Stumpd \
     -x "Stumpd/.git/*" \
     -x "Stumpd/app/build/*" \
     -x "Stumpd/build/*" \
     -x "Stumpd/.gradle/*" \
     -x "Stumpd/app/google-services.json" \
     -x "Stumpd/*.jks" \
     -x "*.DS_Store"
   ```

   **Or manually:**
   - Right-click project folder → Compress
   - Transfer the zip file

2. **Transfer to personal laptop:**
   - USB drive
   - Cloud storage (Google Drive, Dropbox)
   - Email (if small enough)
   - AirDrop (Mac to Mac)

### Step 3: Update and Push from Personal Laptop

1. **Navigate to your local repo:**
   ```bash
   cd ~/path/to/Stumpd
   ```

2. **Backup current state (optional but recommended):**
   ```bash
   git stash
   ```

3. **Extract the zip to a temporary location:**
   ```bash
   # Extract to temp folder
   unzip ~/Downloads/Stumpd-update.zip -d ~/temp/
   ```

4. **Copy updated files (preserving .git folder):**
   ```bash
   # IMPORTANT: Keep your .git folder intact!
   # Only copy source files, not .git
   
   # Option A: Use rsync (recommended)
   rsync -av --exclude='.git' --exclude='app/google-services.json' \
     ~/temp/Stumpd/ . 
   
   # Option B: Manually copy folders
   # Copy: app/src/, gradle files, etc.
   # DON'T copy: .git folder
   ```

5. **Check what changed:**
   ```bash
   git status
   git diff
   ```

6. **Stage and commit changes:**
   ```bash
   # Stage all changes
   git add .
   
   # Review staged changes
   git status
   
   # Commit with descriptive message
   git commit -m "$(cat <<'EOF'
   Add partnerships storage and fix Summary button

   - Store partnerships and FOW when match completes
   - Fix Match Summary loading state
   - Update Remote Config documentation
   EOF
   )"
   ```

7. **Push to GitHub:**
   ```bash
   git push origin main
   ```

### Step 4: Create a Release (When Ready)

1. **Update version in `app/build.gradle.kts`:**
   ```kotlin
   versionCode = 5  // Increment
   versionName = "1.0.3"  // Update
   ```

2. **Commit the version bump:**
   ```bash
   git add app/build.gradle.kts
   git commit -m "Bump version to 1.0.3"
   ```

3. **Create and push tag:**
   ```bash
   git tag v1.0.3
   git push origin main --tags
   ```

4. **GitHub Actions will automatically:**
   - Build the APK
   - Create a Release
   - Attach the APK

5. **Update Firebase Remote Config:**
   - `latest_version_code` → `5`
   - `latest_version_name` → `1.0.3`
   - `apk_download_url` → Copy URL from GitHub Release
   - Click **Publish changes**

---

## Quick Reference Commands

### Check Git Status
```bash
git status              # See changed files
git diff                # See actual changes
git log --oneline -5    # See recent commits
```

### Undo Changes
```bash
git checkout -- <file>  # Discard changes to a file
git reset HEAD <file>   # Unstage a file
git stash               # Temporarily save all changes
git stash pop           # Restore stashed changes
```

### Sync with Remote
```bash
git fetch origin        # Get remote changes (don't merge)
git pull origin main    # Get and merge remote changes
git push origin main    # Push local commits
```

### Tags for Releases
```bash
git tag                 # List all tags
git tag v1.0.3          # Create new tag
git push origin v1.0.3  # Push specific tag
git push origin --tags  # Push all tags
```

---

## Handling Sensitive Files

### Files That Should NEVER Be Committed

| File | Contains | What to Do |
|------|----------|------------|
| `app/google-services.json` | Firebase API keys | Add to GitHub Secrets |
| `*.jks` / `*.keystore` | Signing keys | Keep secure, never commit |
| `signing.properties` | Keystore passwords | Never commit |
| `local.properties` | SDK paths | Auto-generated, ignore |

### If You Accidentally Committed Sensitive Files

```bash
# Remove from git history (keeps local file)
git rm --cached app/google-services.json
git commit -m "Remove sensitive file from tracking"

# Add to .gitignore
echo "app/google-services.json" >> .gitignore
git add .gitignore
git commit -m "Update .gitignore"

git push origin main
```

### For Complete History Removal (If Pushed to Public)

```bash
# WARNING: This rewrites history
# Install git-filter-repo first: brew install git-filter-repo

git filter-repo --path app/google-services.json --invert-paths

# Force push (requires --force)
git push origin main --force
```

**Then:** Regenerate any exposed API keys/secrets in Firebase Console.

---

## GitHub Secrets for CI/CD

The GitHub Actions workflow needs `google-services.json` to build. Store it as a secret:

1. **Encode the file:**
   ```bash
   # Mac/Linux
   base64 -i app/google-services.json | pbcopy
   
   # Windows PowerShell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("app\google-services.json")) | Set-Clipboard
   ```

2. **Add to GitHub:**
   - Go to repo → **Settings** → **Secrets and variables** → **Actions**
   - Click **New repository secret**
   - Name: `GOOGLE_SERVICES_JSON`
   - Value: Paste the base64 content
   - Click **Add secret**

---

## Troubleshooting

### "Permission denied (publickey)"
```bash
# Check if SSH key is loaded
ssh-add -l

# If empty, add your key
ssh-add ~/.ssh/id_ed25519

# Test connection
ssh -T git@github.com
```

### "Remote origin already exists"
```bash
# Check current remote
git remote -v

# Update remote URL
git remote set-url origin git@github.com:USERNAME/Stumpd.git
```

### "Updates were rejected" (remote has changes)
```bash
# Pull remote changes first
git pull origin main --rebase

# Then push
git push origin main
```

### Merge Conflicts
```bash
# After git pull shows conflicts
# 1. Open conflicting files
# 2. Look for <<<<<<< and >>>>>>> markers
# 3. Edit to resolve conflicts
# 4. Stage resolved files
git add <resolved-file>

# 5. Continue
git commit -m "Resolve merge conflicts"
git push origin main
```

---

## Workflow Summary

```
WORK LAPTOP                    PERSONAL LAPTOP
-----------                    ---------------
1. Edit code                   
2. Build & test               
3. Zip project                 
4. Transfer zip ──────────────► 5. Extract zip
                               6. rsync to repo (keep .git!)
                               7. git add .
                               8. git commit -m "message"
                               9. git push origin main
                               10. (Optional) git tag & push for release
```

---

## APK Signing Configuration

For OTA updates to work, both local builds and GitHub releases must use the **same signing key**. This section explains how to set up release signing.

### Why This Matters

- Android requires APKs to be signed with the same key to update
- If keys differ, users get: "Package conflicts with existing package"
- A release keystore ensures consistent signing across all machines

### Step 1: Create Release Keystore (One-Time)

Run this command in your project root directory:

```bash
# Works on Mac, Windows, and Linux
keytool -genkey -v -keystore release-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias stumpd
```

You'll be prompted for:
- **Keystore password**: Choose a strong password (remember it!)
- **Key password**: Can be the same as keystore password
- **Name, Organization, etc.**: Can be anything (e.g., your name)

This creates `release-keystore.jks` in your project root.

### Step 2: Create Local Configuration

Create a file called `keystore.properties` in the `app/` directory (alongside `google-services.json`):

```properties
# app/keystore.properties - DO NOT COMMIT THIS FILE
storeFile=release-keystore.jks
storePassword=your_keystore_password
keyAlias=stumpd
keyPassword=your_key_password
```

**Important**: This file is already in `.gitignore` - never commit it!

### Step 3: Configure GitHub Secrets

Add these secrets to your GitHub repository:

1. Go to: **Repository** → **Settings** → **Secrets and variables** → **Actions**
2. Add these secrets:

| Secret Name | How to Get Value |
|-------------|------------------|
| `KEYSTORE_BASE64` | See encoding instructions below |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | `stumpd` |
| `KEY_PASSWORD` | Your key password |

**Encoding the keystore to Base64:**

```bash
# Mac/Linux
base64 -i release-keystore.jks

# Copy output to clipboard (Mac)
base64 -i release-keystore.jks | pbcopy

# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-keystore.jks")) | Set-Clipboard
```

Paste the entire output as the `KEYSTORE_BASE64` secret value.

### Step 4: Share Keystore Between Machines

To build on multiple computers (work laptop, personal laptop), copy these files:

| File | Location | Purpose |
|------|----------|---------|
| `release-keystore.jks` | `app/` | The signing key |
| `keystore.properties` | `app/` | Passwords |

**Transfer methods** (choose one):
- USB drive
- Secure cloud storage (encrypted)
- AirDrop (Mac to Mac)
- Password manager's secure file storage

**Never transfer via**:
- Email (unencrypted)
- Slack/Teams messages
- Public cloud folders

### How the Build System Works

The `app/build.gradle.kts` is configured to:

1. **Check for `keystore.properties`** (local development)
2. **Fall back to environment variables** (GitHub Actions)
3. **Use debug signing** if neither exists (first-time setup)

```kotlin
// Simplified logic in build.gradle.kts
signingConfigs {
    create("release") {
        storeFile = file(
            keystoreProperties["storeFile"]           // Local file
            ?: System.getenv("KEYSTORE_FILE")         // CI environment
            ?: "release-keystore.jks"
        )
        // ... similar for passwords
    }
}
```

### Signing Files Summary

| File | Location | Git Status | Purpose |
|------|----------|------------|---------|
| `release-keystore.jks` | `app/` | ❌ Ignored | Signing key file |
| `keystore.properties` | `app/` | ❌ Ignored | Local passwords |
| `keystore.properties.template` | `app/` | ✅ Committed | Example for setup |
| `google-services.json` | `app/` | ❌ Ignored | Firebase config |

### Verifying Your Setup

**Check local build uses release key:**
```bash
./gradlew assembleDebug

# Then check the APK signing info
# Mac/Linux
keytool -printcert -jarfile app/build/outputs/apk/debug/app-debug.apk

# Look for: Owner: CN=your_name (what you entered during keystore creation)
```

**Check GitHub build uses same key:**
- Download APK from GitHub Releases
- Run same `keytool -printcert` command
- Owner info should match!

### Troubleshooting

**"Keystore was tampered with, or password was incorrect"**
- Double-check password in `keystore.properties`
- Ensure no extra spaces or newlines

**"Cannot recover key"**
- Key password might differ from keystore password
- Check `keyPassword` in `keystore.properties`

**Build uses debug key instead of release**
- Verify `keystore.properties` exists in project root
- Check file path is correct (`storeFile=release-keystore.jks`)

**GitHub build fails with signing error**
- Verify all 4 secrets are set correctly
- Re-encode keystore if `KEYSTORE_BASE64` seems wrong
- Check GitHub Actions logs for specific error

### Cross-Platform Compatibility

Java keystores (`.jks`) are **fully cross-platform**:
- ✅ Created on Mac → Works on Windows
- ✅ Created on Windows → Works on Mac
- ✅ Created on Linux → Works everywhere

The `keytool` command comes with Java/JDK on all platforms.

---

## Quick Checklist

Before pushing:
- [ ] Code builds successfully (`./gradlew assembleDebug`)
- [ ] No sensitive files staged (`git status`)
- [ ] Commit message is descriptive
- [ ] Version bumped (if releasing)

Before releasing:
- [ ] Version code incremented in `build.gradle.kts`
- [ ] Version name updated
- [ ] Tag created and pushed
- [ ] Firebase Remote Config updated after release

For new machine setup:
- [ ] Copy `release-keystore.jks` to project root
- [ ] Copy `keystore.properties` to project root
- [ ] Verify build: `./gradlew assembleDebug`

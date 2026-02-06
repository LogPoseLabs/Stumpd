# ScoringViewModel.kt Audit Report
## Functional Differences Found During Refactoring

### 🔴 CRITICAL ISSUES

#### 1. **Duplicate Toast Messages in `processDirectWicket`**
**Location:** Lines 1136 and 1146

**Issue:** Two toast messages are shown for the same wicket event:
- Line 1136: `toastLong("Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")})"`
- Line 1146: `toastLong("Wicket! ${outSnapshot?.name ?: "Batsman"} ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter.")`

**Impact:** Users will see two toast messages for every direct wicket (BOWLED, LBW, HIT_WICKET, BOUNDARY_OUT, etc.), which is redundant and potentially confusing.

**Original Behavior:** Unknown - need to verify if original had both or just one.

---

#### 2. **Duplicate Toast Messages in `onFielderSelectedForCaughtStumped`**
**Location:** Lines 1211 and 1227

**Issue:** Two toast messages are shown for caught/stumped wickets:
- Line 1211: `toastLong("Wicket! ${striker?.name} is ${wicketType.name.lowercase().replace("_", " ")}$fielderCredit")`
- Line 1227: `toastLong("Wicket! ${outSnapshot?.name ?: "Batsman"} ${wicketType.name.lowercase().replace('_', ' ')}. Total: $totalAfter.")`

**Impact:** Users will see two toast messages for every caught/stumped wicket.

**Original Behavior:** Unknown - need to verify if original had both or just one.

---

### ⚠️ POTENTIAL ISSUES

#### 3. **Missing `endPartnershipAndRecordWicket` Call in `onFielderSelectedForRunOut`**
**Location:** Lines 1234-1358

**Issue:** The `onFielderSelectedForRunOut` method does NOT call `endPartnershipAndRecordWicket()`. 

**Current Behavior:** 
- Line 1296: `recordCompletedBatter(outSnapshot)` is called
- Line 1298: `totalWickets += 1` is incremented
- But `endPartnershipAndRecordWicket()` is never called

**User's Note:** The user mentioned that "The original inline code in ScoringActivity didn't call `endPartnershipAndRecordWicket` for run-outs". If this is intentional, then the ViewModel correctly preserves this behavior. However, this means:
- Partnership statistics are NOT recorded for run-out dismissals
- Fall of wicket is NOT recorded for run-outs (only `recordCompletedBatter` is called)

**Verification Needed:** Confirm if this is intentional or if run-outs should also record partnerships/fall of wickets.

---

#### 4. **Missing Debug Toast "Normal Out flow!" in `processDirectWicket`**
**Location:** Line 1116

**Issue:** The user mentioned checking for debug toasts like "Normal Out flow!" but this toast message is not present in the current `processDirectWicket` method.

**Impact:** If the original had this debug toast, it was removed during refactoring. This may have been intentional (removing debug code) or accidental.

**Verification Needed:** Check git history to see if this debug toast existed in the original.

---

### ✅ VERIFIED CORRECT IMPLEMENTATIONS

#### 5. **`handleOverCompletionIfNeeded` - `midOverReplacementDueToJoker` Reset**
**Location:** Line 808

**Status:** ✅ CORRECT
- Line 808: `midOverReplacementDueToJoker.value = false` is properly reset when over completes
- Lines 809-811: Handles `showBatsmanDialog` pending swap case correctly
- Lines 813-814: Handles normal swap + bowler dialog case correctly

---

#### 6. **`handleOverCompletionIfNeeded` - Pending Swap Logic**
**Location:** Lines 809-815

**Status:** ✅ CORRECT
- Line 809: Checks `if (showBatsmanDialog)`
- Line 810: Sets `pendingSwapAfterBatsmanPick = !showSingleSideLayout`
- Line 811: Sets `pendingBowlerDialogAfterBatsmanPick = true`
- Lines 813-814: Normal case handles swap and shows bowler dialog

---

#### 7. **`onFielderSelectedForCaughtStumped` - Wide Stumping Handling**
**Location:** Lines 1176-1198

**Status:** ✅ CORRECT
- Line 1176: `val isStumpingOnWide = pendingWideExtraType != null` correctly detects wide stumping
- Line 1186: `ballsFaced = if (isStumpingOnWide) p.ballsFaced else p.ballsFaced + 1` correctly handles ball counting
- Lines 1193-1198: Wide stumping properly updates bowler stats, extras, and delivery

---

#### 8. **`onStartSecondInnings` - State Reset**
**Location:** Lines 1477-1521

**Status:** ✅ CORRECT - All required state is reset:
- ✅ Powerplay tracking: Lines 1506-1507 (`powerplayRunsInnings2 = 0`, `powerplayDoublingDoneInnings2 = false`)
- ✅ Partnership tracking: Lines 1509-1516 (all partnership variables reset)
- ✅ Match progress: Lines 1495-1498 (`totalWickets = 0`, `currentOver = 0`, `ballsInOver = 0`, `totalExtras = 0`)
- ✅ Player positions: Lines 1502-1504 (`strikerIndex = null`, `nonStrikerIndex = null`, `bowlerIndex = null`)
- ✅ Previous bowler: Line 1499 (`previousBowlerName = null`)
- ✅ Joker state: Line 1493 (`jokerOutInCurrentInnings = false`)
- ✅ Completed bowlers: Line 1500 (`completedBowlersInnings2 = mutableListOf()`)
- ✅ Current bowler spell: Line 1501 (`currentBowlerSpell = 0`)

---

#### 9. **`autoSaveMatch` - No `totalWickets` Parameter**
**Location:** Lines 453-531

**Status:** ✅ CORRECT
- The method does NOT take `totalWickets` as a parameter
- `totalWickets` is accessed directly from the ViewModel state (line 140)
- The user mentioned it "was removed as a compile fix" - this is correct, it's not needed as a parameter

---

#### 10. **`onRunScored` - Partnership Updates**
**Location:** Line 845

**Status:** ✅ CORRECT
- Line 845: `updatePartnershipOnRuns(runs, isLegalDelivery = true)` correctly updates partnership
- This matches the expected behavior for legal deliveries

---

#### 11. **`midOverReplacementDueToJoker` - State Type**
**Location:** Line 191

**Status:** ✅ CORRECT
- Declared as: `val midOverReplacementDueToJoker = mutableStateOf(false)`
- Access pattern: Uses `.value` throughout (lines 567, 613, 808, 942, 1584, 1668, 1692)
- This matches the original pattern mentioned by the user

---

#### 12. **`showSingleSideLayout` - Derived State**
**Location:** Lines 252-253

**Status:** ✅ CORRECT
- Declared as computed property: `val showSingleSideLayout: Boolean get() = ...`
- Not a `var by mutableStateOf()` - correctly implemented as derived state
- Formula: `matchSettings.allowSingleSideBatting && availableBatsmen == 1`

---

### 📋 LaunchedEffect Keys Verification

#### 13. **Auto-Save LaunchedEffect Keys**
**Location:** ScoringActivity.kt, Line 200

**Current Keys:** `currentOver, ballsInOver, totalWickets, calculatedTotalRuns, currentInnings`

**Status:** ✅ VERIFIED
- All key state changes that should trigger auto-save are included
- Condition check: `if (currentOver > 0 || ballsInOver > 0)` prevents saving at match start

---

#### 14. **Innings Completion LaunchedEffect**
**Location:** ScoringActivity.kt, Line 208

**Current:** `LaunchedEffect(isInningsComplete) { if (isInningsComplete) vm.onInningsComplete() }`

**Status:** ✅ VERIFIED
- Fires when `isInningsComplete` becomes `true`
- `isInningsComplete` is a derived state (lines 255-258) that correctly checks:
  - Over limit reached
  - Second innings target reached
  - All wickets/available batsmen exhausted

---

### 🔍 ADDITIONAL OBSERVATIONS

#### 15. **Toast Message Formatting Differences**
- Some toasts use `.replace("_", " ")` (line 1136, 1211)
- Some use `.replace('_', ' ')` (line 1146, 1227)
- **Impact:** Minor - both produce the same result, but inconsistent style

#### 16. **Missing Toast in ScoringActivity**
**Location:** ScoringActivity.kt, Line 314

**Issue:** There's a direct `Toast.makeText()` call in the UI layer:
```kotlin
Toast.makeText(context, "Strike swapped! ${nonStriker?.name} now on strike", Toast.LENGTH_SHORT).show()
```

**Impact:** This toast bypasses the ViewModel's toast system. Should be moved to ViewModel's `swapStrike()` method for consistency.

---

## Summary

### Critical Issues Found: 2
1. Duplicate toast messages in `processDirectWicket`
2. Duplicate toast messages in `onFielderSelectedForCaughtStumped`

### Potential Issues: 2
3. Missing `endPartnershipAndRecordWicket` call in run-outs (may be intentional)
4. Missing debug toast "Normal Out flow!" (may have been intentionally removed)

### Verified Correct: 12
All other checked items match expected behavior or are correctly implemented.

---

## Recommendations

1. **Remove duplicate toast messages** - Keep only one toast per wicket event (preferably the one with total score)
2. **Verify run-out partnership handling** - Confirm if `endPartnershipAndRecordWicket` should be called for run-outs
3. **Move strike swap toast to ViewModel** - For consistency with toast system
4. **Check git history** - Verify if "Normal Out flow!" debug toast existed and if its removal was intentional

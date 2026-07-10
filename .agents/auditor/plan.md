# Audit Plan - QuestPhone AI Validation System Fix & Improvements

This plan outlines the forensic audit verification steps to ensure no integrity violations exist in the modified codebase.

## Steps

1. **Source Code Analysis**:
   - Manually review all modified files:
     - `app/src/main/java/neth/iecal/questphone/app/screens/account/UserInfoScreen.kt`
     - `app/src/play/kotlin/AiSnapQuestViewVM.kt`
     - `app/src/fdroid/kotlin/AiSnapQuestViewVM.kt`
     - `app/src/main/java/neth/iecal/questphone/app/screens/quest/view/ai_snap/AiEvaluationScreen.kt`
   - Check for:
     - Hardcoded test outputs or strings that auto-pass verification.
     - Facade implementations or placeholder logic.
     - Fabricated logs/attestation artifacts.
     - Dependency delegations of core logic (none expected for general Android projects, but check GeminiValidator and local nano validator packages).
   - *Verification method*: Use `view_file` to inspect the contents.

2. **Project Compilation**:
   - Run Gradle targets to compile `playDebug` and `fdroidDebug` variants.
   - *Verification method*: Successful Gradle execution of `compilePlayDebugKotlin` and `compileFdroidDebugKotlin`.

3. **Behavioral Analysis**:
   - Verify that the settings for validation engine preference (`local`, `gemini_api`, `cloud`) are stored and retrieved correctly.
   - Verify that the `AiSnapQuestViewVM` implementation uses the stored validation engine preference and correctly branches off.
   - *Verification method*: Trace the preference retrieval logic in `UserInfoScreen.kt` and engine branching in `AiSnapQuestViewVM.kt`.

4. **Edge Case and Security Check**:
   - Check if private Gemini API key prompts are handled securely.
   - Check if errors (like missing Nano support or network failure) are reported to the user.
   - *Verification method*: Audit code paths for error boundaries and dialog prompts.

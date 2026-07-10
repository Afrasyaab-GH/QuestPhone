## 2026-07-10T17:23:01Z
Implement the Settings UI, VM Logic Alignment, and Error Handling for the QuestPhone AI Validation system.

Tasks:
1. Update `app/src/main/java/neth/iecal/questphone/app/screens/account/UserInfoScreen.kt` and `UserInfoViewModel`:
   - Add preferences to retrieve/save validation engine key `"validation_engine"` in SharedPreferences `"private_settings"`. Values: `"local"` (Local On-Device AI), `"gemini_api"` (Private Gemini API Key), or `"cloud"` (QuestPhone Cloud Server, default).
   - In the DropdownMenu of `UserInfoScreen.kt`, add an item for "AI Validation Engine" which opens a `ValidationEngineDialog` containing RadioButtons to choose the engine.
   - If "Private Gemini Key" is selected, prompt the user for the key if not already configured (by showing `GeminiKeyDialog`).
   - Ensure the selection updates immediately and is saved properly.

2. Update `app/src/play/kotlin/AiSnapQuestViewVM.kt` (play variant VM):
   - In `evaluateQuest`, read `"validation_engine"` setting.
   - If `"local"`: check if `LocalGeminiNanoValidator.isAvailable()` is true. If yes, run `LocalGeminiNanoValidator.validateTaskLocally(aiQuest.taskDescription, aiQuest.features)`. If no, set `results.value` with an error message detailing that Gemini Nano is not supported/active on the device.
   - If `"gemini_api"`: run `GeminiValidator` with the user's private key. If key is missing, set `results.value` with an error prompting them to configure it.
   - If `"cloud"`: run the default server-based validation `client.validateTask(...)`.
   - Update `loadModel()` and other setup methods if necessary.

3. Update `app/src/fdroid/kotlin/AiSnapQuestViewVM.kt` (fdroid variant VM):
   - In `evaluateQuest`, read `"validation_engine"` setting.
   - If `"local"`: run local ONNX SigLIP first to get detected features. If `LocalGeminiNanoValidator` is available, run it with those features; otherwise, use the SigLIP feature verification fallback.
   - If `"gemini_api"`: run `GeminiValidator` with the user's private key.
   - If `"cloud"`: run the default server-based validation.
   - Gracefully handle model files not found or downloaded (set `results` with appropriate reason).

4. Update `app/src/main/java/neth/iecal/questphone/app/screens/quest/view/ai_snap/AiEvaluationScreen.kt`:
   - Add a button or option in the result Card to "Switch to Cloud Server & Retry" if local validation or Gemini API validation fails due to unavailability, missing keys, or unsupported features.

5. Compile and test the project:
   - Try to build and verify play and fdroid variants using the Gradle command:
     `.\gradlew.bat --gradle-user-home d:\PROJECTS\QuestPhone\.gradle_home assemblePlayDebug assembleFdroidDebug`
   - Even if command permission times out, ensure the code changes are correctly written and clean.

Write your handoff report to d:\PROJECTS\QuestPhone\.agents\worker_implementation\handoff.md detailing all changes, compilation status, and verification notes.

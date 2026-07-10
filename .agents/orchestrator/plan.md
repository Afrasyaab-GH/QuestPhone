# Plan - QuestPhone AI Validation System Fix & Improvements

This plan outlines the steps to complete the required features and improvements.

## Decomposed Steps

### 1. Settings UI Control (Milestone 1)
- Add preference settings to SharedPreferences `"private_settings"`:
  - Key: `"validation_engine"`
  - Values: `"local"` (Local On-Device AI), `"gemini_api"` (Private Gemini API Key), `"cloud"` (QuestPhone Cloud Server, default).
- Update `UserInfoViewModel` with:
  - `getValidationEngine(): String`
  - `saveValidationEngine(engine: String)`
- In `UserInfoScreen.kt`:
  - Add a Settings item or group in the 3-dot DropdownMenu or as a separate card/list item to choose the validation engine.
  - Selecting "Private Gemini Key" will prompt for the API key if not configured.
  - If a validation engine is changed, show a Toast to confirm.

### 2. VM Validation Logic Alignment (Milestone 2)
- Read preference key `"validation_engine"` from shared preferences `"private_settings"`.
- Update `AiSnapQuestViewVM.kt` in the **play** variant:
  - If `"local"` is selected: Check if `LocalGeminiNanoValidator.isAvailable()` is true.
    - If true, run `LocalGeminiNanoValidator.validateTaskLocally(...)`. Since play does not run local SigLIP, pass `aiQuest.features` or empty list as detected features (or handle text-only reasoning).
    - If false, prompt the user with a dialog or error.
  - If `"gemini_api"` is selected: Run `GeminiValidator` (Gemini 2.5 Flash API).
  - If `"cloud"` is selected: Run standard cloud backend client `client.validateTask(...)`.
- Update `AiSnapQuestViewVM.kt` in the **fdroid** variant:
  - If `"local"` is selected: Run the local ONNX SigLIP model to get detected features. If `LocalGeminiNanoValidator` is available, run it with the detected features. Otherwise, fall back to SigLIP feature verification.
  - If `"gemini_api"` is selected: Run `GeminiValidator` using the user's private key.
  - If `"cloud"` is selected: Fall back to the default cloud endpoint.

### 3. Graceful Error Fallbacks & Dialogs (Milestone 3)
- In both variants, if an engine is selected but not available (e.g. Gemini Nano not supported/prepared, local ONNX model not downloaded), show a clear, user-friendly dialog or text message requesting them to choose a fallback or download/enable it.
- Ensure that selecting/running an unavailable option does not crash the app, but prompts the user with helpful error handling.

### 4. Build and Compile Verification (Milestone 4)
- Compile both the `play` and `fdroid` debug variants.
- Verify compiling is successful and there are no compilation/lint errors.

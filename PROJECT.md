# Project: QuestPhone AI Validation System Fix & Improvements

## Architecture
- `UserInfoScreen.kt` & `UserInfoViewModel`: Settings UI for preferring local AI, private Gemini key, or cloud server.
- `AiSnapQuestViewVM.kt` (play): Evaluation VM for the Google Play variant.
- `AiSnapQuestViewVM.kt` (fdroid): Evaluation VM for the F-Droid variant.
- `LocalGeminiNanoValidator.kt`: Wrapper for system-level Gemini Nano via AICore.
- `GeminiValidator.kt`: Multi-modal validation via private Gemini API key (Gemini 2.5 Flash).
- `TaskValidationClient` (defined in `AiSnapValidator.kt`): Server-based validation fallback.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | Settings UI control | Implement a setting selector in UserInfoScreen / UserInfoViewModel to select preferred AI validation engine. If "Private Gemini Key" is selected, prompt for API key. | None | DONE |
| 2 | VM logic alignment | Update AiSnapQuestViewVM in play/fdroid to respect selected engine preference (Local AI, Private Key, Cloud Server). | M1 | DONE |
| 3 | Error Fallbacks & Dialogs | Gracefully handle unavailable engines (e.g. Gemini Nano not supported, SigLIP model not downloaded) with user-friendly dialogs/error message. | M2 | DONE |
| 4 | E2E Verification & Compile | Build and compile both play and fdroid variants successfully. Verify stability and no crashes. | M3 | DONE |

## Interface Contracts
### Preferences Settings
- SharedPreferences `"private_settings"`:
  - `"validation_engine"`: String (`"local"`, `"gemini_api"`, `"cloud"`), default `"cloud"`.
  - `"gemini_api_key"`: String (user's private API key).

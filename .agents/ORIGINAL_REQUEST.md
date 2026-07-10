# Original User Request

## Initial Request — 2026-07-10T17:18:51Z

Complete the fix and improvement for the QuestPhone AI validation system, enabling the user to choose between Local model(s) (Gemini Nano / local ONNX SigLIP) and/or Gemini API usage via a clean, unified settings interface and updated validation pipelines.

Working directory: d:\PROJECTS\QuestPhone
Integrity mode: development

## Requirements

### R1. Unified Validation Engine Settings UI
Add a user interface control (e.g., in the Settings/Profile screen) that allows the user to explicitly select their preferred AI Validation Engine:
- **Local On-Device AI** (Gemini Nano on Play devices if supported, or local ONNX SigLIP on F-Droid devices).
- **Private Gemini API Key** (using the user's private key).
- **QuestPhone Cloud Server** (default server-based validation).

### R2. Align play & fdroid Validation VM Logic
Update `AiSnapQuestViewVM.kt` in both the `play` and `fdroid` variants to respect this user preference:
- In the `play` variant, integrate the `LocalGeminiNanoValidator` into the evaluation pipeline if "Local On-Device AI" is chosen and supported by the device.
- In the `fdroid` variant, make sure the local ONNX SigLIP model runs if "Local On-Device AI" is selected.
- If "Private Gemini API Key" is selected, both variants must validate using the `GeminiValidator` (Gemini 2.5 Flash API).
- If "QuestPhone Cloud Server" is selected, both variants must fall back to the default cloud endpoint.

### R3. Provide Graceful Error Fallbacks
If the selected engine is not available (e.g. the local ONNX model is not yet downloaded, or Gemini Nano is not supported by the system's `AICore` client), show a clear, user-friendly dialog or text message requesting them to download/enable it or choose a fallback option.

## Acceptance Criteria

### Settings UI
- [ ] Users can toggle/select between Local AI, Private Gemini Key, and Cloud Server in a settings card or menu.
- [ ] Selecting "Private Gemini Key" prompts for the API key if not configured.

### Validation Pipeline
- [ ] In the `play` variant, selecting Local AI attempts to validate via `LocalGeminiNanoValidator` if `isAvailable()` is true.
- [ ] In the `fdroid` variant, selecting Local AI attempts to validate via ONNX/SigLIP.
- [ ] Changing the selection in Settings immediately updates the validation method in `AiSnapQuestViewVM`.

### Stability & Quality
- [ ] The application compiles successfully for both `play` and `fdroid` variants.
- [ ] Selecting an unavailable local option does not crash the app, but prompts the user with helpful error handling.

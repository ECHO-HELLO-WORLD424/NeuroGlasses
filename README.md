# NeuroGlasses

<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp" alt="Logo" width="128" height="128">
<h3 align="center">NeuroGlasses</h3>
<p>Rokid Integration for Various AI Apps, NeuroSDK WIP...</p>
</div>

<br>

## Why I made this?

When watching the Neuro IRL stream, I think it would be nice if you can ask an AI to be your guide when travelling. Ofcourse you can do this with by holding your phone all the time, taking photos and asking AI but I think it's a bit inconvient. After I gain access to an AR glasses from a friend, I think I might found a solution. But the official APP for the glasses can't be hacked, everything is pre-defined and you must **speak** your instructions in the public, and the galsses will always use TTS to play the result -- this is awfully embarrssing in some context! Can we have an AI integration for this glasses with pre-defined common instructions, a TTS that you can turn off and a backend that you can hack and configure? These ideas finally evolved into this experimental Android application that brings AI-powered chat capabilities to AR glasses using the Rokid Glasses SDK and OpenAI-compatible APIs (Can be adapted to use more APIs like LangChain and NeuroSDK by implementing API translators and middlewares. WIP.)

## Overview

NeuroGlasses is an Android app that bridges the gap between AR glasses and various AI applications through OpenAI-compatible APIs with streaming text/audio output support. The app displays AI chat interactions directly on the glasses' HUD (Head-Up Display), creating an immersive augmented reality experience.

### Key Features

- **Vision Language Model (VLM)** support - AI can see what you see through the glasses' camera
- **Automatic Speech Recognition (ASR)** - Use voice commands as instructions
- **Text-to-Speech (TTS)** with streaming audio playback
- **Extensible architecture** - Can be extended with middleware server to translate APIs or enhance functionality (Work in Process...)

### Current Limitations

- No chat history support (yet). But this can be implemented in the middleware with Python, with langchain, NeuroAPI integration and other advanced Agentic features
- Experimental Rokid CRX_M SDK requires re-pairing glasses on each disconnect
- I can't touch the glasses' own OS directly with that SDK. To achieve more customization and better UX I need to apply for a 5-pin dev wire from Rokid, don't have that time now.

## Requirements

- **Android Studio** (latest version recommended)
- **Android Device** with API level 30 or higher (tested on Galaxy Tab S8 with API 35)
- **Rokid Glasses** (the ones with green HUD)
- **OpenAI-compatible API** (e.g., OpenAI, SiliconFlow, etc.)

## Installation Guide

> **Note:** This is an experimental application and is not available on the Google Play Store.

### Step 1: Build and Deploy

1. Open the project in Android Studio
2. Connect your Android device via USB (with USB debugging enabled)
3. Select your device from the deployment target list
4. Click the **Run** button

### Step 2: Grant Permissions

1. Open the NeuroGlasses app on your device
2. Grant all requested permissions (bluetooth, fine location (required by glasses' SDK, not the app itself)

### Step 3: Pair the Glasses

1. **Fold the left leg** of your Rokid glasses
2. **Click the button on the right leg 3 times** to enter pairing mode
3. Complete the Bluetooth pairing process on your Android device

> **Important:** Due to the experimental nature of the glasses' SDK, you need to unpair and re-pair the device every time you disconnect it from the app to ensure correct behavior

### Step 4: Configure API Settings

1. In the NeuroGlasses app, navigate to the **Settings** page
2. Fill in your OpenAI-compatible API information:
   - **API Base URL** (e.g., `https://api.siliconflow.cn/v1` for SiliconFlow)
   - **API Key**
   - **Model names** for VLM ASR, and TTS
3. Configure optional settings:
   - TTS voice selection (Note: this should be left empty for some models, but must be filled for others)
   - Streaming chunk size
   - Temperature and other model parameters
4. Click **Save** to store your settings

### Step 5: Start Using AI Chat

1. Navigate to the **AI Test** page
2. Configure your preferences:
   - **Include image in request** - Enable to send camera captures to VLM
   - **Use voice (ASR) for instructions** - Enable to use voice commands instead of predefined instructions
   - **Use Text-to-Speech (TTS)** - Enable to hear AI responses
3. **Invoke the AI Scene** on the glasses (press the AI key, or say the invoke word)
4. Start your AI chat interaction:
   - If ASR is enabled: Speak your instruction
   - If ASR is disabled: Select a predefined instruction from the dialog

### Step 6: Managing Predefined Instructions

If not using ASR, you can manage predefined instructions:

1. Enter a new instruction in the text field
2. Click **Add Instruction** to add it to the list
3. Long-press any instruction in the list to delete it

## Usage Tips

- Speak clearly when using ASR mode
- The streaming display will show AI responses in real-time on the glasses' HUD
- Adjust the chunk size in settings if text updates too frequently/slowly or is overflowing (CRX_M SDK can't control glasses UI's scroll so I have to clear the screen manually). 

## API Compatibility

The app is designed to work with OpenAI-compatible APIs. It has been tested with SiliconFlow platform. Other OpenAI-compatible providers should work, but may require adjustments.

> Check TTS's "voice" (this field means "reference audio" for Siliconflow) setting carefully if you are use other providers, because a mis-configured "voice" will likely gives empty results but with code 200. 

> Also, the VLM used here is Qwen3VL, other VLs may have different API payload structure, check them and implement your own middleware for API translation if necesary.

## Technical Architecture

![Architecture](/home/patrick/Data/AppData/Dev/AndroidStudioProjects/NeuroGlasses/NeuroGlasses.png)

## Troubleshooting

### Glasses won't pair

- Ensure the left leg is fully folded
- Try clicking the right leg button exactly 3 times
- Unpair any existing Bluetooth connections to the glasses first
- If all that does not work, press right leg button for 12s to reboot the glasses.

### No audio playback

- Check that TTS is enabled in the AI Test page
- Verify your API supports TTS and the model name/voice(reference audio) is set correctly
- Ensure device volume is not muted

### API errors

- Check the logcat and verify your payload structure
- Check that the base URL is correct (include `/v1` if required)
- Ensure model names match those available in your API provider

## Development

### Project Structure

```
app/src/main/java/com/patrick/neuroglasses/
├── activities/
│   ├── AITestActivity.kt       # Main AI chat interface
│   ├── MainActivity.kt          # App entry point
│   └── SettingsActivity.kt      # API configuration
└── helpers/
    ├── AICameraHelper.kt        # Camera capture
    ├── AudioHelper.kt           # Audio recording
    ├── CustomSceneHelper.kt     # HUD display management
    ├── OpenAIHelper.kt          # API client with streaming
    └── StreamingAudioPlayer.kt  # Real-time audio playback
```

### Extending Functionality

To add new AI capabilities:

1. Implement new API endpoints in `OpenAIHelper.kt`
2. Add corresponding UI controls in `AITestActivity.kt`
3. Update settings if new configuration parameters are needed

Consider implementing a middleware server to:

- Translate between different API formats
- Add custom processing or filtering
- Implement caching or rate limiting
- Support additional AI providers

## License

MIT

## Contributing

This is an experimental project. Contributions, bug reports, and feature requests are welcome!

## Acknowledgments

- Built with [Rokid AR Glasses SDK](https://ar.rokid.com/). I left some translations of Chinese docs from Rokid's website as Markdown in repo root.
- App launch icon modified based on Neuro-sama Headquarters' sticker.

**⚠️ Experimental Software Notice:** This application is in active development and uses experimental SDK features. Expect bugs, crashes, and frequent updates.

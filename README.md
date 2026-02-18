# react-native-call-detection-android

Real-time phone call detection for React Native — **Android only**.

Detect incoming calls, outgoing calls, and VoIP calls (WhatsApp, Teams, Zoom, etc.) in your React Native app without ejecting from Expo (requires development build).

## Features

- 📞 **GSM Call Detection** - Detect incoming/outgoing cellular calls with phone number
- 🎧 **Audio Focus Detection** - Detect VoIP calls (WhatsApp, Teams, Zoom, Telegram, etc.)
- ✅ Detect call states: RINGING, OFFHOOK (active), IDLE (ended)
- 🚀 Supports React Native 0.60+ with auto-linking
- 📦 Expo Config Plugin included for easy setup
- 🔄 Compatible with Android 12+ and older versions

## Installation

### Using npm
```bash
npm install react-native-call-detection-android
```

### Using yarn
```bash
yarn add react-native-call-detection-android
```

## Setup

### Expo (Managed Workflow)

> ⚠️ **Note:** This package requires native code and will NOT work with Expo Go. You must use a [development build](https://docs.expo.dev/develop/development-builds/introduction/).

1. Add the plugin to your `app.json` or `app.config.js`:

```json
{
  "expo": {
    "plugins": [
      "react-native-call-detection-android"
    ]
  }
}
```

2. Rebuild your development build:

```bash
npx expo prebuild
npx expo run:android
```

Or using EAS Build:
```bash
eas build --platform android --profile development
```

### React Native CLI (Bare Workflow)

The package supports auto-linking, so after installation:

1. Add the required permission to `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Required for GSM call detection -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />

    <application>
        ...
    </application>
</manifest>
```

2. Rebuild the app:
```bash
cd android && ./gradlew clean
cd .. && npx react-native run-android
```

## Usage

### Complete Example (GSM + VoIP Detection)

```typescript
import { useEffect } from 'react';
import { PermissionsAndroid, Platform } from 'react-native';
import CallDetection from 'react-native-call-detection-android';

function App() {
  useEffect(() => {
    let gsmSubscription: any;
    let audioFocusSubscription: any;

    const startCallDetection = async () => {
      // Request permission (required for GSM detection)
      if (Platform.OS === 'android') {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
          {
            title: 'Phone State Permission',
            message: 'This app needs access to phone state to detect calls.',
            buttonPositive: 'OK',
          }
        );

        if (granted !== PermissionsAndroid.RESULTS.GRANTED) {
          console.log('Permission denied');
          return;
        }
      }

      // Start all listeners (GSM + Audio Focus)
      const result = await CallDetection.startAllListeners();
      console.log('Listeners started:', result);

      // Listen for GSM calls (traditional phone calls)
      gsmSubscription = CallDetection.addCallStateListener((event) => {
        console.log('GSM Call Event:', event);
        
        switch (event.state) {
          case 'IDLE':
            console.log('No active call');
            break;
          case 'RINGING':
            console.log('Incoming call from:', event.phoneNumber);
            break;
          case 'OFFHOOK':
            console.log('Call in progress');
            break;
        }
      });

      // Listen for audio focus changes (VoIP calls: WhatsApp, Teams, Zoom, etc.)
      audioFocusSubscription = CallDetection.addAudioFocusListener((event) => {
        console.log('Audio Focus Event:', event);
        
        if (event.isInterrupted) {
          console.log('Audio interrupted - possibly a VoIP call!');
          // Pause your recording, music, etc.
        } else if (event.state === 'FOCUS_GAINED') {
          console.log('Audio focus regained - call ended');
          // Resume your recording, music, etc.
        }
      });
    };

    startCallDetection();

    // Cleanup on unmount
    return () => {
      if (gsmSubscription) gsmSubscription.remove();
      if (audioFocusSubscription) audioFocusSubscription.remove();
      CallDetection.stopAllListeners();
    };
  }, []);

  return (
    // Your app UI
  );
}
```

### GSM Only (Traditional Phone Calls)

```typescript
import CallDetection from 'react-native-call-detection-android';

// Start GSM listener
await CallDetection.startListener();

// Listen for GSM calls
const subscription = CallDetection.addCallStateListener((event) => {
  console.log('State:', event.state);       // IDLE, RINGING, OFFHOOK
  console.log('Phone:', event.phoneNumber); // Caller's number
  console.log('Type:', event.type);         // 'gsm'
});

// Cleanup
subscription.remove();
await CallDetection.stopListener();
```

### Audio Focus Only (VoIP / Any Audio Interruption)

```typescript
import CallDetection from 'react-native-call-detection-android';

// Start audio focus listener
await CallDetection.startAudioFocusListener();

// Listen for audio interruptions
const subscription = CallDetection.addAudioFocusListener((event) => {
  console.log('State:', event.state);           // FOCUS_GAINED, FOCUS_LOSS, etc.
  console.log('Interrupted:', event.isInterrupted); // true when audio taken
  console.log('Has Focus:', event.hasAudioFocus);   // current focus status
  
  if (event.isInterrupted) {
    // Another app took audio (WhatsApp call, Zoom, Spotify, etc.)
    pauseRecording();
  } else {
    // Audio focus regained
    resumeRecording();
  }
});

// Cleanup
subscription.remove();
await CallDetection.stopAudioFocusListener();
```

### Custom Hook

```typescript
import { useState, useEffect, useCallback } from 'react';
import { PermissionsAndroid, Platform } from 'react-native';
import CallDetection, { 
  CallState, 
  AudioFocusState,
  CallStateEvent,
  AudioFocusEvent 
} from 'react-native-call-detection-android';

export function useCallDetection() {
  const [gsmCallState, setGsmCallState] = useState<CallState>('IDLE');
  const [audioFocusState, setAudioFocusState] = useState<AudioFocusState>('NONE');
  const [isInterrupted, setIsInterrupted] = useState(false);
  const [isListening, setIsListening] = useState(false);

  const requestPermission = useCallback(async () => {
    if (Platform.OS !== 'android') return false;
    
    const granted = await PermissionsAndroid.request(
      PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE
    );
    return granted === PermissionsAndroid.RESULTS.GRANTED;
  }, []);

  const startListening = useCallback(async () => {
    const hasPermission = await requestPermission();
    if (!hasPermission) return;

    await CallDetection.startAllListeners();
    setIsListening(true);
  }, [requestPermission]);

  const stopListening = useCallback(async () => {
    await CallDetection.stopAllListeners();
    setIsListening(false);
  }, []);

  useEffect(() => {
    if (!isListening) return;

    const gsmSub = CallDetection.addCallStateListener((event: CallStateEvent) => {
      setGsmCallState(event.state);
    });

    const audioSub = CallDetection.addAudioFocusListener((event: AudioFocusEvent) => {
      setAudioFocusState(event.state);
      setIsInterrupted(event.isInterrupted);
    });

    return () => {
      gsmSub.remove();
      audioSub.remove();
    };
  }, [isListening]);

  return {
    gsmCallState,
    audioFocusState,
    isInterrupted,
    isListening,
    startListening,
    stopListening,
  };
}
```

## API Reference

### GSM Call Detection Methods

#### `CallDetection.startListener(): Promise<boolean>`
Start listening for GSM phone call state changes.

#### `CallDetection.stopListener(): Promise<boolean>`
Stop listening for GSM phone call state changes.

#### `CallDetection.isActive(): Promise<boolean>`
Check if the GSM listener is active.

#### `CallDetection.getCallState(): Promise<CallState>`
Get the current GSM call state.

#### `CallDetection.addCallStateListener(callback): Subscription`
Add a listener for GSM call state changes.

#### `CallDetection.removeAllListeners(): void`
Remove all GSM call state listeners.

### Audio Focus Methods (VoIP Detection)

#### `CallDetection.startAudioFocusListener(): Promise<boolean>`
Start listening for audio focus changes.

#### `CallDetection.stopAudioFocusListener(): Promise<boolean>`
Stop listening for audio focus changes.

#### `CallDetection.isAudioFocusActive(): Promise<boolean>`
Check if the audio focus listener is active.

#### `CallDetection.getAudioFocusState(): Promise<AudioFocusStatus>`
Get the current audio focus status.

#### `CallDetection.addAudioFocusListener(callback): Subscription`
Add a listener for audio focus changes.

#### `CallDetection.removeAllAudioFocusListeners(): void`
Remove all audio focus listeners.

### Combined Methods

#### `CallDetection.startAllListeners(): Promise<AllListenersResult>`
Start both GSM and audio focus listeners.

#### `CallDetection.stopAllListeners(): Promise<boolean>`
Stop both GSM and audio focus listeners.

#### `CallDetection.removeAll(): void`
Remove all listeners.

### Types

```typescript
// GSM Call States
type CallState = 'IDLE' | 'RINGING' | 'OFFHOOK' | 'UNKNOWN';

// Audio Focus States
type AudioFocusState = 
  | 'FOCUS_GAINED'           // Regained audio focus
  | 'FOCUS_LOSS'             // Lost audio focus permanently
  | 'FOCUS_LOSS_TRANSIENT'   // Lost audio focus temporarily (likely a call)
  | 'FOCUS_LOSS_CAN_DUCK'    // Can lower volume (notification sound)
  | 'NONE'
  | 'UNKNOWN';

// GSM Call Event
interface CallStateEvent {
  state: CallState;
  phoneNumber: string;
  type: 'gsm';
  timestamp: number;
}

// Audio Focus Event
interface AudioFocusEvent {
  state: AudioFocusState;
  isInterrupted: boolean;    // true when audio is taken by another app
  hasAudioFocus: boolean;    // current focus status
  type: 'audio_focus';
  timestamp: number;
}
```

### Call States Reference

| State | Type | Description |
|-------|------|-------------|
| `IDLE` | GSM | No call activity |
| `RINGING` | GSM | Incoming call is ringing |
| `OFFHOOK` | GSM | Call is active |
| `FOCUS_GAINED` | Audio | Regained audio control |
| `FOCUS_LOSS` | Audio | Another app took audio permanently |
| `FOCUS_LOSS_TRANSIENT` | Audio | Temporary loss (likely a call) |
| `FOCUS_LOSS_CAN_DUCK` | Audio | Can continue at lower volume |

## Detection Capabilities

| Event Type | Detected? | Details |
|------------|-----------|---------|
| Incoming GSM call | ✅ Yes | With phone number |
| Outgoing GSM call | ✅ Yes | With phone number |
| WhatsApp call | ✅ Yes | Via audio focus (no caller info) |
| Teams call | ✅ Yes | Via audio focus (no caller info) |
| Zoom call | ✅ Yes | Via audio focus (no caller info) |
| Telegram call | ✅ Yes | Via audio focus (no caller info) |
| Any VoIP call | ✅ Yes | Via audio focus (no caller info) |
| Spotify/Music | ⚠️ Yes | Also triggers audio focus |

> **Note:** Audio focus detection cannot distinguish between a VoIP call and other audio apps (Spotify, YouTube). Use it when you need to detect "any audio interruption" regardless of source.

## Permissions

### Required
- `READ_PHONE_STATE` - Required for GSM call detection

> **Note:** Audio focus detection requires no special permissions!

## Troubleshooting

### "This package only works on Android"
This package is Android-only.

### "Module not found" in Expo Go
This package requires native code and won't work in Expo Go. Create a development build.

### GSM permission denied
Make sure you're requesting the `READ_PHONE_STATE` permission at runtime.

### Audio focus not detecting calls
1. Make sure `startAudioFocusListener()` was called
2. Your app must request audio focus to detect when it's lost
3. Test with a real VoIP call (WhatsApp, Teams)

## Compatibility

- React Native: 0.60.0+
- Android: API 21+ (Android 5.0+)
- Expo: 47.0.0+ (with development build)

## Changelog

### v1.1.0
- Added Audio Focus detection for VoIP calls (WhatsApp, Teams, Zoom, etc.)
- New methods: `startAudioFocusListener()`, `stopAudioFocusListener()`, `addAudioFocusListener()`
- Combined methods: `startAllListeners()`, `stopAllListeners()`
- Renamed `addListener()` to `addCallStateListener()` (old method still works)

### v1.0.0
- Initial release with GSM call detection

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT © Rakesh Prasad

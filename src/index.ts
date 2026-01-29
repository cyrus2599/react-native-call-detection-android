import { NativeModules, Platform, NativeEventEmitter, EmitterSubscription } from 'react-native';

// ==================== TYPES ====================

/**
 * GSM Call state types
 */
export type CallState = 'IDLE' | 'RINGING' | 'OFFHOOK' | 'UNKNOWN';

/**
 * Audio focus state types
 */
export type AudioFocusState = 
  | 'FOCUS_GAINED'           // Audio focus regained
  | 'FOCUS_LOSS'             // Permanent audio focus loss (another app took control)
  | 'FOCUS_LOSS_TRANSIENT'   // Temporary loss (likely a call)
  | 'FOCUS_LOSS_CAN_DUCK'    // Can lower volume but continue
  | 'NONE'                   // Not listening
  | 'UNKNOWN';

/**
 * Event payload for GSM call state changes
 */
export interface CallStateEvent {
  /** Current call state */
  state: CallState;
  /** Phone number (may be empty due to privacy restrictions on Android 10+) */
  phoneNumber: string;
  /** Event type identifier */
  type: 'gsm';
  /** Timestamp when the event occurred */
  timestamp: number;
}

/**
 * Event payload for audio focus changes (VoIP/any audio interruption)
 */
export interface AudioFocusEvent {
  /** Current audio focus state */
  state: AudioFocusState;
  /** Whether audio is currently interrupted */
  isInterrupted: boolean;
  /** Whether app currently has audio focus */
  hasAudioFocus: boolean;
  /** Event type identifier */
  type: 'audio_focus';
  /** Timestamp when the event occurred */
  timestamp: number;
}

/**
 * Audio focus status information
 */
export interface AudioFocusStatus {
  state: AudioFocusState;
  hasAudioFocus: boolean;
  isListening: boolean;
}

/**
 * Result from starting all listeners
 */
export interface AllListenersResult {
  gsmListening: boolean;
  audioFocusListening: boolean;
}

/**
 * Subscription object returned by addListener
 */
export interface CallDetectionSubscription {
  /** Remove the subscription */
  remove: () => void;
}

// ==================== MODULE SETUP ====================

const LINKING_ERROR =
  `The package 'react-native-call-detection-android' doesn't seem to be linked. Make sure:\n\n` +
  Platform.select({ ios: "- This package only works on Android\n", default: "" }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go (use development build instead)\n';

const CallDetectionAndroid = NativeModules.CallDetectionAndroid;

const callDetectionModule = CallDetectionAndroid
  ? CallDetectionAndroid
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

const eventEmitter = CallDetectionAndroid 
  ? new NativeEventEmitter(CallDetectionAndroid) 
  : null;

// ==================== MAIN API ====================

/**
 * Call Detection API for Android
 * 
 * Supports two types of detection:
 * 1. GSM Calls - Traditional cellular calls with phone number
 * 2. Audio Focus - Detects when any app takes audio control (WhatsApp, Teams, Zoom, etc.)
 * 
 * @example
 * ```typescript
 * import CallDetection from 'react-native-call-detection-android';
 * 
 * // Start all listeners (GSM + Audio Focus)
 * await CallDetection.startAllListeners();
 * 
 * // Listen for GSM calls
 * const gsmSubscription = CallDetection.addCallStateListener((event) => {
 *   console.log('GSM Call:', event.state); // IDLE, RINGING, OFFHOOK
 * });
 * 
 * // Listen for audio interruptions (WhatsApp, Teams, Zoom, etc.)
 * const audioSubscription = CallDetection.addAudioFocusListener((event) => {
 *   if (event.isInterrupted) {
 *     console.log('Audio interrupted! Possibly a VoIP call.');
 *   }
 * });
 * 
 * // Cleanup
 * gsmSubscription.remove();
 * audioSubscription.remove();
 * await CallDetection.stopAllListeners();
 * ```
 */
const CallDetection = {
  // ==================== GSM CALL METHODS ====================

  /**
   * Start listening for GSM phone call state changes.
   * Requires READ_PHONE_STATE permission.
   * 
   * @returns Promise that resolves to true if listener started successfully
   */
  startListener(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.startListener();
  },

  /**
   * Stop listening for GSM phone call state changes.
   * 
   * @returns Promise that resolves to true if listener stopped successfully
   */
  stopListener(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.stopListener();
  },

  /**
   * Check if the GSM call detection listener is currently active.
   * 
   * @returns Promise that resolves to true if listener is active
   */
  isActive(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.isActive();
  },

  /**
   * Get the current GSM call state.
   * 
   * @returns Promise that resolves to current call state string
   */
  getCallState(): Promise<CallState> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.getCallState();
  },

  /**
   * Add a listener for GSM phone call state changes.
   * Make sure to call startListener() before adding listeners.
   * 
   * @param callback Function to call when call state changes
   * @returns Subscription object with remove() method
   */
  addCallStateListener(callback: (event: CallStateEvent) => void): CallDetectionSubscription {
    if (!eventEmitter) {
      throw new Error(LINKING_ERROR);
    }
    const subscription: EmitterSubscription = eventEmitter.addListener('PhoneCallStateUpdate', callback);
    return {
      remove: () => subscription.remove(),
    };
  },

  /**
   * @deprecated Use addCallStateListener instead
   * Add a listener for GSM phone call state changes.
   */
  addListener(callback: (event: CallStateEvent) => void): CallDetectionSubscription {
    return this.addCallStateListener(callback);
  },

  /**
   * Remove all GSM call state listeners.
   */
  removeAllListeners(): void {
    if (eventEmitter) {
      eventEmitter.removeAllListeners('PhoneCallStateUpdate');
    }
  },

  // ==================== AUDIO FOCUS METHODS ====================

  /**
   * Start listening for audio focus changes.
   * Detects when any app takes audio control (WhatsApp calls, Teams, Zoom, Spotify, etc.)
   * 
   * @returns Promise that resolves to true if listener started successfully
   */
  startAudioFocusListener(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.startAudioFocusListener();
  },

  /**
   * Stop listening for audio focus changes.
   * 
   * @returns Promise that resolves to true if listener stopped successfully
   */
  stopAudioFocusListener(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.stopAudioFocusListener();
  },

  /**
   * Check if the audio focus listener is currently active.
   * 
   * @returns Promise that resolves to true if listener is active
   */
  isAudioFocusActive(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.isAudioFocusActive();
  },

  /**
   * Get the current audio focus state.
   * 
   * @returns Promise that resolves to audio focus status object
   */
  getAudioFocusState(): Promise<AudioFocusStatus> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.getAudioFocusState();
  },

  /**
   * Add a listener for audio focus changes.
   * Useful for detecting VoIP calls (WhatsApp, Teams, Zoom) or any audio interruption.
   * 
   * @param callback Function to call when audio focus changes
   * @returns Subscription object with remove() method
   */
  addAudioFocusListener(callback: (event: AudioFocusEvent) => void): CallDetectionSubscription {
    if (!eventEmitter) {
      throw new Error(LINKING_ERROR);
    }
    const subscription: EmitterSubscription = eventEmitter.addListener('AudioFocusUpdate', callback);
    return {
      remove: () => subscription.remove(),
    };
  },

  /**
   * Remove all audio focus listeners.
   */
  removeAllAudioFocusListeners(): void {
    if (eventEmitter) {
      eventEmitter.removeAllListeners('AudioFocusUpdate');
    }
  },

  // ==================== COMBINED METHODS ====================

  /**
   * Start both GSM call detection and audio focus listeners.
   * This is the recommended way to detect all types of calls.
   * 
   * @returns Promise with status of both listeners
   */
  startAllListeners(): Promise<AllListenersResult> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.startAllListeners();
  },

  /**
   * Stop both GSM call detection and audio focus listeners.
   * 
   * @returns Promise that resolves to true if stopped successfully
   */
  stopAllListeners(): Promise<boolean> {
    if (Platform.OS !== 'android') {
      return Promise.reject(new Error('react-native-call-detection-android only works on Android'));
    }
    return callDetectionModule.stopAllListeners();
  },

  /**
   * Remove all listeners (both GSM and audio focus).
   */
  removeAll(): void {
    if (eventEmitter) {
      eventEmitter.removeAllListeners('PhoneCallStateUpdate');
      eventEmitter.removeAllListeners('AudioFocusUpdate');
    }
  },
};

export default CallDetection;

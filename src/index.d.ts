// ==================== TYPES ====================

/**
 * GSM Call state types
 */
export type CallState = 'IDLE' | 'RINGING' | 'OFFHOOK' | 'UNKNOWN';

/**
 * Audio focus state types
 */
export type AudioFocusState = 
  | 'FOCUS_GAINED'
  | 'FOCUS_LOSS'
  | 'FOCUS_LOSS_TRANSIENT'
  | 'FOCUS_LOSS_CAN_DUCK'
  | 'NONE'
  | 'UNKNOWN';

/**
 * Event payload for GSM call state changes
 */
export interface CallStateEvent {
  state: CallState;
  phoneNumber: string;
  type: 'gsm';
  timestamp: number;
}

/**
 * Event payload for audio focus changes
 */
export interface AudioFocusEvent {
  state: AudioFocusState;
  isInterrupted: boolean;
  hasAudioFocus: boolean;
  type: 'audio_focus';
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
  remove: () => void;
}

/**
 * Call Detection API for Android
 */
declare const CallDetection: {
  // GSM Call Methods
  startListener(): Promise<boolean>;
  stopListener(): Promise<boolean>;
  isActive(): Promise<boolean>;
  getCallState(): Promise<CallState>;
  addCallStateListener(callback: (event: CallStateEvent) => void): CallDetectionSubscription;
  /** @deprecated Use addCallStateListener instead */
  addListener(callback: (event: CallStateEvent) => void): CallDetectionSubscription;
  removeAllListeners(): void;

  // Audio Focus Methods
  startAudioFocusListener(): Promise<boolean>;
  stopAudioFocusListener(): Promise<boolean>;
  isAudioFocusActive(): Promise<boolean>;
  getAudioFocusState(): Promise<AudioFocusStatus>;
  addAudioFocusListener(callback: (event: AudioFocusEvent) => void): CallDetectionSubscription;
  removeAllAudioFocusListeners(): void;

  // Combined Methods
  startAllListeners(): Promise<AllListenersResult>;
  stopAllListeners(): Promise<boolean>;
  removeAll(): void;
};

export default CallDetection;

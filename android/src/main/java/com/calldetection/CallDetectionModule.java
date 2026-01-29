package com.calldetection;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;

@ReactModule(name = CallDetectionModule.MODULE_NAME)
public class CallDetectionModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
    public static final String MODULE_NAME = "CallDetectionAndroid";
    private static final String TAG = "CallDetection";
    private static final String GSM_EVENT_NAME = "PhoneCallStateUpdate";
    private static final String AUDIO_FOCUS_EVENT_NAME = "AudioFocusUpdate";
    
    // Telephony (GSM) related
    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;
    private CallStateCallback callStateCallback;
    private boolean isGsmListening = false;
    private String currentGsmState = "IDLE";
    
    // Audio Focus related
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;
    private boolean isAudioFocusListening = false;
    private String currentAudioFocusState = "NONE";
    private boolean hasAudioFocus = false;
    
    private int listenerCount = 0;

    public CallDetectionModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);
    }

    @Override
    @NonNull
    public String getName() {
        return MODULE_NAME;
    }

    // ==================== GSM CALL DETECTION ====================

    @ReactMethod
    public void startListener(Promise promise) {
        try {
            if (isGsmListening) {
                promise.resolve(true);
                return;
            }

            ReactApplicationContext context = getReactApplicationContext();
            telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            
            if (telephonyManager == null) {
                promise.reject("TELEPHONY_ERROR", "TelephonyManager not available");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                startGsmListenerModern();
            } else {
                startGsmListenerLegacy();
            }
            
            isGsmListening = true;
            promise.resolve(true);
            
        } catch (SecurityException e) {
            Log.e(TAG, "Permission denied: " + e.getMessage());
            promise.reject("PERMISSION_ERROR", "READ_PHONE_STATE permission required");
        } catch (Exception e) {
            Log.e(TAG, "Error starting GSM listener: " + e.getMessage());
            promise.reject("START_ERROR", e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void startGsmListenerLegacy() {
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String phoneNumber) {
                handleGsmCallStateChanged(state, phoneNumber);
            }
        };
        
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void startGsmListenerModern() {
        callStateCallback = new CallStateCallback();
        telephonyManager.registerTelephonyCallback(
            getReactApplicationContext().getMainExecutor(),
            callStateCallback
        );
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private class CallStateCallback extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            handleGsmCallStateChanged(state, "");
        }
    }

    private void handleGsmCallStateChanged(int state, String phoneNumber) {
        WritableMap params = Arguments.createMap();
        String stateString;
        
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                stateString = "IDLE";
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                stateString = "RINGING";
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                stateString = "OFFHOOK";
                break;
            default:
                stateString = "UNKNOWN";
        }
        
        currentGsmState = stateString;
        params.putString("state", stateString);
        params.putString("phoneNumber", phoneNumber != null ? phoneNumber : "");
        params.putString("type", "gsm");
        params.putDouble("timestamp", System.currentTimeMillis());
        
        sendEvent(GSM_EVENT_NAME, params);
    }

    @ReactMethod
    public void stopListener(Promise promise) {
        try {
            stopGsmListenerInternal();
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping GSM listener: " + e.getMessage());
            promise.reject("STOP_ERROR", e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void stopGsmListenerInternal() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (telephonyManager != null && callStateCallback != null) {
                try {
                    telephonyManager.unregisterTelephonyCallback(callStateCallback);
                } catch (Exception e) {
                    Log.w(TAG, "Error unregistering telephony callback: " + e.getMessage());
                }
                callStateCallback = null;
            }
        } else {
            if (telephonyManager != null && phoneStateListener != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
                phoneStateListener = null;
            }
        }
        isGsmListening = false;
    }

    @ReactMethod
    public void isActive(Promise promise) {
        promise.resolve(isGsmListening);
    }

    @ReactMethod
    public void getCallState(Promise promise) {
        promise.resolve(currentGsmState);
    }

    // ==================== AUDIO FOCUS DETECTION ====================

    @ReactMethod
    public void startAudioFocusListener(Promise promise) {
        try {
            if (isAudioFocusListening) {
                promise.resolve(true);
                return;
            }

            ReactApplicationContext context = getReactApplicationContext();
            audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            
            if (audioManager == null) {
                promise.reject("AUDIO_ERROR", "AudioManager not available");
                return;
            }

            // Create audio focus change listener
            audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    handleAudioFocusChange(focusChange);
                }
            };

            // Request audio focus
            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();

                result = audioManager.requestAudioFocus(audioFocusRequest);
            } else {
                result = requestAudioFocusLegacy();
            }

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                hasAudioFocus = true;
                currentAudioFocusState = "FOCUS_GAINED";
                isAudioFocusListening = true;
                promise.resolve(true);
            } else {
                promise.reject("AUDIO_FOCUS_ERROR", "Could not obtain audio focus");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting audio focus listener: " + e.getMessage());
            promise.reject("START_ERROR", e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private int requestAudioFocusLegacy() {
        return audioManager.requestAudioFocus(
            audioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        );
    }

    private void handleAudioFocusChange(int focusChange) {
        WritableMap params = Arguments.createMap();
        String stateString;
        boolean isInterrupted = false;
        
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // Regained focus - interruption ended
                stateString = "FOCUS_GAINED";
                hasAudioFocus = true;
                isInterrupted = false;
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Permanent loss - another app took audio (could be a call)
                stateString = "FOCUS_LOSS";
                hasAudioFocus = false;
                isInterrupted = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Temporary loss - likely a call or notification
                stateString = "FOCUS_LOSS_TRANSIENT";
                hasAudioFocus = false;
                isInterrupted = true;
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Can duck (lower volume) - usually notification sounds
                stateString = "FOCUS_LOSS_CAN_DUCK";
                hasAudioFocus = true; // Still have focus, just need to lower volume
                isInterrupted = false; // Not a full interruption
                break;
            default:
                stateString = "UNKNOWN";
                isInterrupted = false;
        }
        
        currentAudioFocusState = stateString;
        params.putString("state", stateString);
        params.putBoolean("isInterrupted", isInterrupted);
        params.putBoolean("hasAudioFocus", hasAudioFocus);
        params.putString("type", "audio_focus");
        params.putDouble("timestamp", System.currentTimeMillis());
        
        sendEvent(AUDIO_FOCUS_EVENT_NAME, params);
    }

    @ReactMethod
    public void stopAudioFocusListener(Promise promise) {
        try {
            stopAudioFocusListenerInternal();
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping audio focus listener: " + e.getMessage());
            promise.reject("STOP_ERROR", e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void stopAudioFocusListenerInternal() {
        if (audioManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
                audioFocusRequest = null;
            } else if (audioFocusChangeListener != null) {
                audioManager.abandonAudioFocus(audioFocusChangeListener);
            }
            audioFocusChangeListener = null;
        }
        isAudioFocusListening = false;
        hasAudioFocus = false;
        currentAudioFocusState = "NONE";
    }

    @ReactMethod
    public void isAudioFocusActive(Promise promise) {
        promise.resolve(isAudioFocusListening);
    }

    @ReactMethod
    public void getAudioFocusState(Promise promise) {
        WritableMap params = Arguments.createMap();
        params.putString("state", currentAudioFocusState);
        params.putBoolean("hasAudioFocus", hasAudioFocus);
        params.putBoolean("isListening", isAudioFocusListening);
        promise.resolve(params);
    }

    // ==================== COMBINED METHODS ====================

    @ReactMethod
    public void startAllListeners(Promise promise) {
        try {
            // Start GSM listener
            if (!isGsmListening) {
                ReactApplicationContext context = getReactApplicationContext();
                telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                
                if (telephonyManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startGsmListenerModern();
                    } else {
                        startGsmListenerLegacy();
                    }
                    isGsmListening = true;
                }
            }

            // Start Audio Focus listener
            if (!isAudioFocusListening) {
                ReactApplicationContext context = getReactApplicationContext();
                audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                
                if (audioManager != null) {
                    audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {
                            handleAudioFocusChange(focusChange);
                        }
                    };

                    int result;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();

                        audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(audioAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(audioFocusChangeListener)
                            .build();

                        result = audioManager.requestAudioFocus(audioFocusRequest);
                    } else {
                        result = requestAudioFocusLegacy();
                    }

                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        hasAudioFocus = true;
                        currentAudioFocusState = "FOCUS_GAINED";
                        isAudioFocusListening = true;
                    }
                }
            }

            WritableMap resultMap = Arguments.createMap();
            resultMap.putBoolean("gsmListening", isGsmListening);
            resultMap.putBoolean("audioFocusListening", isAudioFocusListening);
            promise.resolve(resultMap);
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting all listeners: " + e.getMessage());
            promise.reject("START_ERROR", e.getMessage());
        }
    }

    @ReactMethod
    public void stopAllListeners(Promise promise) {
        try {
            stopGsmListenerInternal();
            stopAudioFocusListenerInternal();
            promise.resolve(true);
        } catch (Exception e) {
            Log.e(TAG, "Error stopping all listeners: " + e.getMessage());
            promise.reject("STOP_ERROR", e.getMessage());
        }
    }

    // ==================== EVENT EMITTER SUPPORT ====================

    @ReactMethod
    public void addListener(String eventName) {
        listenerCount++;
    }

    @ReactMethod
    public void removeListeners(int count) {
        listenerCount -= count;
        if (listenerCount < 0) {
            listenerCount = 0;
        }
    }

    private void sendEvent(String eventName, WritableMap params) {
        if (getReactApplicationContext().hasActiveReactInstance()) {
            getReactApplicationContext()
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
        }
    }

    // ==================== LIFECYCLE METHODS ====================

    @Override
    public void onHostResume() {
        // Activity is visible
    }

    @Override
    public void onHostPause() {
        // Activity is not visible
    }

    @Override
    public void onHostDestroy() {
        // Clean up when the host is destroyed
        stopGsmListenerInternal();
        stopAudioFocusListenerInternal();
    }
}

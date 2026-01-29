// Expo config plugin for react-native-call-detection-android
// This adds the required READ_PHONE_STATE permission to AndroidManifest.xml

const { withAndroidManifest } = require('@expo/config-plugins');

const withCallDetection = (config) => {
  return withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults.manifest;
    
    // Ensure uses-permission array exists
    if (!androidManifest['uses-permission']) {
      androidManifest['uses-permission'] = [];
    }
    
    const permissions = androidManifest['uses-permission'];
    
    // Add READ_PHONE_STATE permission if not already present
    const hasReadPhoneState = permissions.some(
      (permission) => 
        permission.$?.['android:name'] === 'android.permission.READ_PHONE_STATE'
    );
    
    if (!hasReadPhoneState) {
      permissions.push({
        $: {
          'android:name': 'android.permission.READ_PHONE_STATE'
        }
      });
    }

    // Add READ_CALL_LOG permission for phone number access (optional, needed for some features)
    const hasReadCallLog = permissions.some(
      (permission) => 
        permission.$?.['android:name'] === 'android.permission.READ_CALL_LOG'
    );
    
    if (!hasReadCallLog) {
      permissions.push({
        $: {
          'android:name': 'android.permission.READ_CALL_LOG'
        }
      });
    }
    
    return config;
  });
};

module.exports = withCallDetection;

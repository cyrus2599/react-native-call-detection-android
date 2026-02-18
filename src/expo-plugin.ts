import { ConfigPlugin, withAndroidManifest } from '@expo/config-plugins';

const withCallDetection: ConfigPlugin = (config) => {
  return withAndroidManifest(config, async (config) => {
    const androidManifest = config.modResults.manifest;
    
    // Ensure uses-permission array exists
    if (!androidManifest['uses-permission']) {
      androidManifest['uses-permission'] = [];
    }
    
    const permissions = androidManifest['uses-permission'];
    
    // Add READ_PHONE_STATE permission if not already present
    const hasReadPhoneState = permissions.some(
      (permission: any) => 
        permission.$?.['android:name'] === 'android.permission.READ_PHONE_STATE'
    );
    
    if (!hasReadPhoneState) {
      permissions.push({
        $: {
          'android:name': 'android.permission.READ_PHONE_STATE'
        }
      });
    }
    
    return config;
  });
};

export default withCallDetection;

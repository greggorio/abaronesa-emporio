import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.baronesa.emporio.app',
  appName: 'Emporio A Baronesa',
  webDir: 'dist',
  server: {
    androidScheme: 'https',
  },
};

export default config;

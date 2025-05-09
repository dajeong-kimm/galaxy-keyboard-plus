import AppNavigator from './navigation/AppNavigator';
import React from 'react';
import {View} from 'react-native';
import {useAutoBackup} from './hooks/useAutoBackup';

export default function App() {
  useAutoBackup();
  return (
    <View style={{flex: 1}}>
      <AppNavigator />
    </View>
  );
}

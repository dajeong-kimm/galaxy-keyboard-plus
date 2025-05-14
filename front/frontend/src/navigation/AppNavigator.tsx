import AlarmGalleryScreen from '../screens/AlarmGalleryScreen.tsx';
import ChatScreen from '../screens/ChatScreen';
import FavoriteGalleryScreen from '../screens/FavoriteGalleryScreen.tsx';
import HomeScreen from '../screens/HomeScreen';
import ImageDetailScreen from '../screens/ImageDetailScreen';
import LoginScreen from '../screens/LoginScreen';
import {NavigationContainer} from '@react-navigation/native';
import PhotoGalleryScreen from '../screens/PhotoGalleryScreen';
import React from 'react';
import {createNativeStackNavigator} from '@react-navigation/native-stack';

const Stack = createNativeStackNavigator();

export default function AppNavigator() {
  return (
    <NavigationContainer>
      {/* ▸ 필요하면 initialRouteName="Login" 으로 바꿔도 됩니다 */}
      <Stack.Navigator
        initialRouteName="Home"
        screenOptions={{headerShown: false}}>
        <Stack.Screen name="Login" component={LoginScreen} />

        <Stack.Screen name="Home" component={HomeScreen} />
        <Stack.Screen name="ChatScreen" component={ChatScreen} />
        <Stack.Screen name="LoginScreen" component={LoginScreen} />

        <Stack.Screen name="PhotoGallery" component={PhotoGalleryScreen} />
        <Stack.Screen
          name="FavoriteGallery"
          component={FavoriteGalleryScreen}
        />
        <Stack.Screen name="AlarmGallery" component={AlarmGalleryScreen} />

        <Stack.Screen
          name="ImageDetail"
          component={ImageDetailScreen}
          options={{headerShown: false}}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

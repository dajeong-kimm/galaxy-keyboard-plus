// HomeScreen.tsx

import { Button, StyleSheet, Text, View, Alert } from 'react-native';
import React, { useEffect } from 'react';
import axios from 'axios';

import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useNavigation } from '@react-navigation/native';
import { requestStoragePermission } from '../utils/permissions';
import { uploadTop50Screenshots } from '../utils/testUploader';
import { useAuthStore } from '../stores/authStore';

type RootStackParamList = {
  Home: undefined;
  PhotoGallery: undefined;
  ChatScreen: undefined;
  LoginScreen: undefined;
};

type HomeScreenNavigationProp = NativeStackNavigationProp<
  RootStackParamList,
  'Home'
>;

export default function HomeScreen() {
  const navigation = useNavigation<HomeScreenNavigationProp>();
  const { userId } = useAuthStore();

  // 스토리지 권한 요청은 컴포넌트 마운트 시 한 번만
  useEffect(() => {
    requestStoragePermission();
  }, []);

  // 네트워크 테스트 함수
  const testNetwork = async () => {
    try {
      const response = await axios.get('https://httpbin.org/get', { timeout: 3000 });
      console.log('네트워크 테스트 응답:', response.data);
      Alert.alert('네트워크 테스트 성공', JSON.stringify(response.data, null, 2));
    } catch (error: any) {
      console.error('네트워크 테스트 오류:', error);
      Alert.alert('네트워크 테스트 실패', error.message || '알 수 없는 오류');
    }
  };

  return (
    <View style={styles.container}>
      {userId ? (
        <Text style={styles.welcome}>{userId}님, 환영합니다!</Text>
      ) : (
        <Text style={styles.title}>홈 화면입니다</Text>
      )}

      <Button
        title="사진 모아보기로 이동"
        onPress={() => navigation.navigate('PhotoGallery')}
      />

      <Button
        title="채팅방으로 이동하기"
        onPress={() => navigation.navigate('ChatScreen')}
      />

      <Button
        title="카카오 로그인 페이지 이동"
        onPress={() => navigation.navigate('LoginScreen')}
      />

      {/* 🍧 테스트 업로드 버튼 */}
      <View style={{ marginTop: 20 }}>
        <Button
          title="🧪 스크린샷 50장 업로드 테스트"
          onPress={uploadTop50Screenshots}
        />
      </View>

      {/* 🌐 네트워크 테스트 버튼 */}
      <View style={{ marginTop: 20 }}>
        <Button
          title="🌐 네트워크 테스트"
          onPress={testNetwork}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  title: { fontSize: 24, marginBottom: 16 },
  welcome: { fontSize: 20, marginBottom: 16, fontWeight: '600' },
});

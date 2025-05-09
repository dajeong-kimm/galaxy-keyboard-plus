import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import tw from 'twrnc';
import { useAuth } from '../hooks/useAuth';

export default function LoginScreen() {
  const { signInWithKakao } = useAuth();

  return (
    <View style={tw`flex-1 items-center justify-center bg-white`}>
      <TouchableOpacity
        onPress={signInWithKakao}
        style={tw`bg-[#FEE500] px-6 py-3 rounded-lg`}>
        <Text style={tw`text-black font-semibold`}>카카오톡으로 로그인</Text>
      </TouchableOpacity>
    </View>
  );
}

import React, {useState, useCallback, useRef, useEffect} from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  SafeAreaView,
  Text,
} from 'react-native';
import {useHeaderHeight} from '@react-navigation/elements';
import tw from 'twrnc';

import HeaderBar from '../components/HeaderBar';
import InputBar from '../components/InputBar';
import MessageBubble, {Message} from '../components/MessageBubble';
import {api} from '../api/axios'; // 기존 axios 인스턴스 주석 처리
// import axios from 'axios'; // axios 직접 임포트
import {useAuthStore} from '../stores/authStore';

const HEADER_BG = '#FFEBD6';

export default function ChatScreen() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputText, setInputText] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const flatListRef = useRef<FlatList<Message>>(null);
  const headerHeight = useHeaderHeight();

  // zustand에서 userId 가져오기
  const {userId} = useAuthStore();

  /* ---------------- 메시지 전송 ---------------- */
  const handleSend = useCallback(async () => {
    const trimmed = inputText.trim();
    if (!trimmed) return;

    // userId 없으면 중단
    if (!userId) {
      setError('로그인이 필요합니다.');
      return;
    }

    // 1) 사용자 메시지 즉시 추가
    setMessages(prev => [
      ...prev,
      {
        id: `${Date.now()}_user`,
        text: trimmed,
        sender: 'user',
        timestamp: new Date(),
      },
    ]);
    setInputText('');
    setIsLoading(true);
    setError(null);

    // 2) 서버 호출
    try {
      const form = new URLSearchParams();
      form.append('user_id', userId);
      form.append('query', trimmed);

      console.log('▶ ChatScreen - userId:', userId); // userId 콘솔 로그 추가
      console.log('▶ ChatScreen - query:', trimmed); // query(trimmed) 콘솔 로그 추가

      // // api 인스턴스를 사용하여 요청 (기존 코드 주석 처리)
      const {data} = await api.post(
        '/rag/search/', // api 인스턴스의 baseURL 이후 경로
        form.toString(),
        {headers: {'Content-Type': 'application/x-www-form-urlencoded'}},
      );

      // axios 직접 호출로 변경
      // const {data} = await axios.post(
      //   'http://k12e201.p.ssafy.io:8090/rag/search/', // 새 URL로 직접 호출
      //   form.toString(),
      //   {headers: {'Content-Type': 'application/x-www-form-urlencoded'}},
      // );

      // 3) 봇 메시지 구성
      const caption =
        data.query_type === 'photo'
          ? '사진 검색 결과입니다.'
          : data.answer || '정보를 찾았습니다.';

      const botMsg: Message = {
        id: `${Date.now()}_bot`,
        text: caption,
        sender: 'bot',
        timestamp: new Date(),
        query_type: data.query_type,
        answer: data.answer,
        photo_results: data.photo_results,
        info_results: data.info_results,
      };
      setMessages(prev => [...prev, botMsg]);
    } catch (e: any) {
      console.error(e);
      setError(e?.response?.data?.message || e.message || '서버 통신 오류');
    } finally {
      setIsLoading(false);
    }
  }, [inputText, userId]); // userId를 의존성 배열에 추가

  /* --------------- 자동 스크롤 ---------------- */
  useEffect(() => {
    flatListRef.current?.scrollToEnd({animated: true});
  }, [messages]);

  /* --------------- UI ------------------------ */
  return (
    <SafeAreaView style={tw`flex-1 bg-[${HEADER_BG}]`}>
      <HeaderBar title="캐릭터 이름" />

      <KeyboardAvoidingView
        style={tw`flex-1`}
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        keyboardVerticalOffset={headerHeight}>
        <FlatList
          ref={flatListRef}
          data={messages}
          keyExtractor={m => m.id}
          renderItem={({item}) => <MessageBubble item={item} />}
          contentContainerStyle={tw`px-[12px] pb-[12px]`}
          showsVerticalScrollIndicator={false}
        />

        {isLoading && (
          <Text style={tw`text-center text-[#777] py-[4px]`}>
            응답을 기다리는 중…
          </Text>
        )}
        {error && (
          <Text style={tw`text-center text-red-500 py-[4px]`}>{error}</Text>
        )}

        <InputBar
          text={inputText}
          onChangeText={setInputText}
          onSubmit={handleSend}
          disabled={isLoading}
        />
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

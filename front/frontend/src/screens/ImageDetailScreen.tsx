import {
  ActivityIndicator,
  Image,
  Modal,
  StatusBar,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import React, {useEffect, useState} from 'react';
import {RouteProp, useNavigation, useRoute} from '@react-navigation/native';

import Icon from 'react-native-vector-icons/Ionicons';
import {RootStackParamList} from '../types/types';
import {StackNavigationProp} from '@react-navigation/stack';
import dummyImage from '../assets/sample.png'; // 예시 이미지
import tw from '../utils/tw';
import {useImageDetailStore} from '../stores/useImageDetailStore';

// import {generateUriFromAccessId} from '../types/imageTypes';

// import {getImageDetail} from '../services/imageService';

/**
 * 현재 임시 이미지를 띄워놓게 되어있고, api 호출을 연결하지 않아서
 * 대충 퍼블리싱과 더미 이미지가 뜨게 되어있음 필요하다면 수정 예정
 */

const ImageDetailScreen = () => {
  const route = useRoute<RouteProp<RootStackParamList, 'ImageDetail'>>();

  const navigation =
    useNavigation<StackNavigationProp<RootStackParamList, 'ImageDetail'>>();
  const {imageId} = route.params;

  const {image, setImage, clearImage} = useImageDetailStore();
  const [showInfo, setShowInfo] = useState(false);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchDetail = async () => {
      try {
        // const data = await getImageDetail(imageId);

        const data = {
          imageId: imageId,
          accessId: imageId,
          star: false,
          imageTime: 'ttt',
          content: '더미데이터입니다~',
        };

        setImage(data);
      } catch (error) {
        console.error('Failed to fetch image detail', error);
      } finally {
        setLoading(false);
      }
    };

    fetchDetail();

    return () => {
      clearImage(); // 화면 빠질 때 초기화
    };
  }, [imageId, setImage, clearImage]);

  if (loading || !image) {
    return (
      <View style={tw`flex-1 justify-center items-center`}>
        <ActivityIndicator size="large" color="#999" />
      </View>
    );
  }

  return (
    <View style={tw`flex-1 bg-white`}>
      <StatusBar
        translucent
        backgroundColor="transparent"
        barStyle="dark-content"
      />
      {/* Custom Header */}
      <View style={tw`h-22 px-4 pt-8 bg-orange-100 justify-center`}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Icon name="chevron-back" size={24} color="black" />
        </TouchableOpacity>
      </View>

      {/* Main Image */}
      <View style={tw`flex-1 justify-center items-center bg-gray-200`}>
        {/* <Image
          source={{uri: generateUriFromAccessId(image.accessId)}}
          style={tw`w-full h-full`}
          resizeMode="contain"
        /> */}
        {/* 더미 */}
        <Image source={dummyImage} style={tw`w-full `} resizeMode="contain" />
      </View>

      {/* Bottom Toolbar */}
      <View
        style={tw`flex-row justify-around items-center h-16 bg-orange-100 rounded-t-xl`}>
        <TouchableOpacity>
          <Icon name="trash-outline" size={24} color="black" />
        </TouchableOpacity>
        <TouchableOpacity>
          <Icon
            name={image.star ? 'star' : 'star-outline'}
            size={24}
            color="black"
          />
        </TouchableOpacity>
        <TouchableOpacity onPress={() => setShowInfo(true)}>
          <Icon name="information-circle-outline" size={24} color="black" />
        </TouchableOpacity>
      </View>

      {/* Info BottomSheet Modal */}
      <Modal visible={showInfo} animationType="slide" transparent>
        <View style={tw`flex-1 justify-end`}>
          <View style={tw`bg-white rounded-t-2xl p-6`}>
            <View style={tw`items-center mb-4`}>
              <View style={tw`w-12 h-1 bg-gray-400 rounded-full`} />
            </View>
            <Text style={tw`text-base text-center`}>
              {image.content ?? '사진에 대한 설명이 없습니다.'}
            </Text>
            <TouchableOpacity
              onPress={() => setShowInfo(false)}
              style={tw`mt-4 items-center`}>
              <Text style={tw`text-blue-500`}>닫기</Text>
            </TouchableOpacity>
          </View>
        </View>
      </Modal>
    </View>
  );
};

export default ImageDetailScreen;

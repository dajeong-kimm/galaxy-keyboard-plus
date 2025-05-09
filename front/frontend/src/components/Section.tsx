import {ScrollView, Text, TouchableOpacity, View} from 'react-native';

import Icon from 'react-native-vector-icons/Ionicons';
import {ImageThumbnail} from './ImageThumbnail';
import React from 'react';
import tw from '../utils/tw';

// import {ImageItem} from '../stores/useImageDetailStore';

interface SectionProps {
  title: string;
  // data: ImageItem[];
  onPressMore: () => void;
}

export const Section = ({title, data, onPressMore}: SectionProps) => (
  <View style={tw`mt-4`}>
    <View style={tw`flex-row justify-between items-center mb-2`}>
      <Text style={tw`font-semibold text-base`}>{title}</Text>
      <TouchableOpacity onPress={onPressMore} style={tw`flex-row items-center`}>
        <Text style={tw`text-gray-500 text-sm`}>더 보기</Text>
        <Icon name="chevron-forward" size={16} color="#999" />
      </TouchableOpacity>
    </View>
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={tw``}>
      {data.slice(0, 10).map(image => (
        <ImageThumbnail key={image.imageUuid} item={image} size="large" />
      ))}
    </ScrollView>
  </View>
);

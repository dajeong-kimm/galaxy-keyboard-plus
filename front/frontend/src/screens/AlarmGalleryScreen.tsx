import React from 'react';
import {Text} from 'react-native';
// import {FlatList, Text, TouchableOpacity} from 'react-native';
// import {useNavigation} from '@react-navigation/native';
// import {StackNavigationProp} from '@react-navigation/stack';
// import {RootStackParamList} from '../types/types';
// import {useImagePreviewStore} from '../stores/useImagePreviewStore';
// import {ImageThumbnail} from '../components/ImageThumbnail';
// import tw from '../utils/tw';

const AlarmGallery = () => {
  // const navigation =
  //   useNavigation<StackNavigationProp<RootStackParamList, 'AlarmGallery'>>();
  // const alarms = useImagePreviewStore(state =>
  //   state.images.filter(img => img.hasAlarm),
  // );

  return (
    // <FlatList
    //   data={alarms}
    //   renderItem={({item}) => (
    //     <TouchableOpacity
    //       onPress={() =>
    //         navigation.navigate('ImageDetail', {imageId: item.imageId})
    //       }>
    //       <ImageThumbnail item={item} size="small" />
    //     </TouchableOpacity>
    //   )}
    //   keyExtractor={item => item.imageId}
    //   numColumns={4}
    //   contentContainerStyle={tw`p-3`}
    // />
    <Text>홈 화면입니다</Text>
  );
};

export default AlarmGallery;

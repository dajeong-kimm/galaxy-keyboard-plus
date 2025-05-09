import React from 'react';
import {FlatList, TouchableOpacity} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {StackNavigationProp} from '@react-navigation/stack';
import {RootStackParamList} from '../types/types';
import {useImagePreviewStore} from '../stores/useImagePreviewStore';
import {ImageThumbnail} from '../components/ImageThumbnail';
import tw from '../utils/tw';

const FavoriteGallery = () => {
  const navigation =
    useNavigation<StackNavigationProp<RootStackParamList, 'FavoriteGallery'>>();
  const favorites = useImagePreviewStore(state =>
    state.images.filter(img => img.star),
  );

  return (
    <FlatList
      data={favorites}
      renderItem={({item}) => (
        <TouchableOpacity
          onPress={() =>
            navigation.navigate('ImageDetail', {imageId: item.imageId})
          }>
          <ImageThumbnail item={item} size="small" />
        </TouchableOpacity>
      )}
      keyExtractor={item => item.imageId}
      numColumns={4}
      contentContainerStyle={tw`p-3`}
    />
  );
};

export default FavoriteGallery;

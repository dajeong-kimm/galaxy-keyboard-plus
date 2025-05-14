import {
  Button,
  FlatList,
  StatusBar,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import React, {useEffect} from 'react';

import {BasicImageItem} from '../types/imageTypes';
import {GalleryHeader} from '../components/GalleryHeader';
import {ImageThumbnail} from '../components/ImageThumbnail';
import {RootStackParamList} from '../types/types';
import {StackNavigationProp} from '@react-navigation/stack';
import {fetchScreenshotImageUris} from '../utils/fetchScreenshotImages';
import tw from '../utils/tw';
import {useImagePreviewStore} from '../stores/useImagePreviewStore';
import {useNavigation} from '@react-navigation/native';
import uuid from 'react-native-uuid';

// import {Section} from '../components/Section';

/**
 * 현재 스크린샷 폴더에 있는 이미지를 잘 불러오는지 테스트 하기 위해 section도 지워뒀고
 * 스크린샷 폴더에 있는 이미지들을 썸네일로 출력되도록 구현되어 있음
 *
 */

uuid.v4() as string;

const PhotoGalleryScreen = () => {
  const {images, appendImages} = useImagePreviewStore();

  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>();

  useEffect(() => {
    const loadImages = async () => {
      const screenshots = await fetchScreenshotImageUris();

      const converted = screenshots.map(s => ({
        imageId: s.contentId ?? (uuid.v4() as string),
        accessId: s.contentId ?? (uuid.v4() as string),
        uri: s.uri,
        filename: s.filename,
        timestamp: s.timestamp,
        star: false,
      }));

      appendImages(converted);
    };

    loadImages();
  }, [appendImages]);

  // const favorites = images.filter(img => img.star);

  const handleImagePress = (image: BasicImageItem) => {
    navigation.navigate('ImageDetail', {imageId: image.imageId});
  };

  const handleFetchScreenshots = async () => {
    const screenshots = await fetchScreenshotImageUris();

    console.log('📸 가져온 스크린샷 목록');
    screenshots.forEach((img, index) => {
      console.log(`#${index + 1}`, {
        uri: img.uri,
        contentId: img.contentId,
        filename: img.filename,
        timestamp: img.timestamp,
      });
    });
  };

  return (
    <View style={tw`flex-1 bg-white`}>
      <StatusBar
        translucent
        backgroundColor="transparent"
        barStyle="dark-content"
      />
      <GalleryHeader />

      <FlatList
        data={images}
        renderItem={({item}) => (
          <TouchableOpacity onPress={() => handleImagePress(item)}>
            <ImageThumbnail item={item} size="small" />
          </TouchableOpacity>
        )}
        keyExtractor={item => item.imageId}
        numColumns={4}
        contentContainerStyle={tw`px-5 pb-4`}
        ListHeaderComponent={
          <>
            <Button
              title="스크린샷 가져오기 (콘솔 출력)"
              onPress={handleFetchScreenshots}
            />

            {/* <Section
              title="즐겨찾기"
              data={favorites}
              onPressMore={() => navigation.navigate('FavoriteGallery')}
            />
            <Section
              title="알림"
              data={favorites}
              onPressMore={() => navigation.navigate('AlarmGallery')}
            /> */}

            <Text style={tw`mt-4 mb-2 text-base font-semibold`}>전체 보기</Text>
          </>
        }
      />
    </View>
  );
};

export default PhotoGalleryScreen;

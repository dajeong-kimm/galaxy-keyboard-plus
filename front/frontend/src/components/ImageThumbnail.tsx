import {Image, View} from 'react-native';

import {BasicImageItem} from '../types/imageTypes';
import Icon from 'react-native-vector-icons/Ionicons';
import React from 'react';
import {generateUriFromAccessId} from '../types/imageTypes';
import tw from '../utils/tw';

type Props = {
  item: BasicImageItem;
  size?: 'small' | 'large';
};

export const ImageThumbnail = ({item, size = 'large'}: Props) => {
  const imageSize = size === 'small' ? 'w-22 h-22' : 'w-28 h-28';

  //  uri가 있으면 그걸 쓰고, 없으면 accessId 기반으로 조합 << (포토갤러리스크린에서 임시로 스크린샷 폴더에 있는 이미지를 불러오게 해두려고 이런 코드 추가해둠 만약 db 상에서 조회하는 걸로 바뀌면 그냥 액세스아이디로만 조합할 듯)
  const uri = (item as any).uri ?? generateUriFromAccessId(item.accessId);

  console.log('🍧 uri : ', uri);

  return (
    <View style={tw`relative mr-2 mb-2`}>
      <Image source={{uri}} style={tw`${imageSize} rounded-lg bg-gray-200`} />
      {item.star && (
        <View style={tw`absolute bottom-1 right-1`}>
          <Icon name="star" size={16} color="white" />
        </View>
      )}
    </View>
  );
};

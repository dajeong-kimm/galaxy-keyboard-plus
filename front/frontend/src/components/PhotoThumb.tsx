import React from 'react';
import { View, Image, Text } from 'react-native';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
import tw from 'twrnc';
import { getScreenshotUriById } from '../utils/camera';

interface Props {
  id: string; // score prop 제거
}

const PhotoThumb: React.FC<Props> = React.memo(({ id }) => {
  const [uri, setUri] = React.useState<string | null>(null);

  React.useEffect(() => {
    let mounted = true;
    getScreenshotUriById(id).then(u => mounted && setUri(u));
    return () => {
      mounted = false;
    };
  }, [id]);

  return (
    <View style={tw`w-[48%] aspect-square m-[2px] rounded-[6px] overflow-hidden`}>
      {uri ? (
        <Image source={{ uri }} style={tw`flex-1`} resizeMode="cover" />
      ) : (
        <View style={tw`items-center justify-center flex-1`}>
          <MaterialCommunityIcons name="image-off-outline" size={24} />
          <Text style={tw`text-[10px]`}>없음</Text>
        </View>
      )}
      {/* score 표시 제거 */}
    </View>
  );
});

export default PhotoThumb;

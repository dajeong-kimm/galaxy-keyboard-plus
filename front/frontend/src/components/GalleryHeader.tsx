import {Text, TouchableOpacity, View} from 'react-native';

import Icon from 'react-native-vector-icons/Ionicons';
import tw from '../utils/tw';

export const GalleryHeader = () => (
  <View
    style={tw`h-22 bg-[#FFE7C2] flex-row items-center justify-between px-4 pt-8`}>
    <TouchableOpacity style={tw`flex-row items-center`}>
      <Icon name="chevron-back-outline" size={24} style={tw`mr-2`} />
      <Text style={tw`text-sm`}>채팅방으로 돌아가기</Text>
    </TouchableOpacity>
    <View style={tw`flex-row gap-1`}>
      <TouchableOpacity style={tw`mr-2`}>
        <Icon name="checkmark" size={24} />
      </TouchableOpacity>
      <TouchableOpacity>
        <Icon name="settings-outline" size={24} />
      </TouchableOpacity>
    </View>
  </View>
);

import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
import tw from 'twrnc';

interface Props {
  title: string;
}

const HEADER_BG = '#FFEBD6';

const HeaderBar: React.FC<Props> = ({ title }) => {
  return (
    <View style={tw`flex-row items-center justify-between px-[16px] py-[8px] bg-[${HEADER_BG}]`}>
      <Text style={tw`text-[16px] font-semibold`}>{title}</Text>
      <View style={tw`flex-row items-center`}>
        <TouchableOpacity>
          <MaterialCommunityIcons name="archive-outline" size={24} />
        </TouchableOpacity>
        <TouchableOpacity style={tw`ml-[12px]`}>
          <MaterialCommunityIcons name="cog-outline" size={24} />
        </TouchableOpacity>
      </View>
    </View>
  );
};

export default HeaderBar;

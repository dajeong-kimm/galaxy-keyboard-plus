import React from 'react';
import {View, TextInput, TouchableOpacity} from 'react-native';
import MaterialCommunityIcons from 'react-native-vector-icons/MaterialCommunityIcons';
import tw from 'twrnc';

interface Props {
  text: string;
  onChangeText: (t: string) => void;
  onSubmit: () => void;
  disabled: boolean;
}

const InputBar: React.FC<Props> = ({
  text,
  onChangeText,
  onSubmit,
  disabled,
}) => {
  return (
    <View
      style={tw`flex-row items-center p-[10px] bg-white border-t border-[#DDD]`}>
      <TextInput
        style={tw`flex-1 h-[42px] px-[14px] bg-[#F9F9F9] rounded-[20px] text-[15px]`}
        placeholder="메시지를 입력하세요…"
        value={text}
        onChangeText={onChangeText}
        onSubmitEditing={onSubmit}
        editable={!disabled}
      />
      <TouchableOpacity
        style={tw.style(
          'bg-[#FF8E25] w-[42px] h-[42px] rounded-[21px] items-center justify-center ml-[8px]',
          disabled ? 'opacity-40' : undefined, // <-- boolean → string | undefined
        )}
        onPress={onSubmit}
        disabled={disabled}>
        <MaterialCommunityIcons name="send" size={20} color="#fff" />
      </TouchableOpacity>
    </View>
  );
};

export default InputBar;

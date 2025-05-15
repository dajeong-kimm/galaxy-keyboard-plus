export default function ChatBox({ input, setInput, response, onSend }) {
  return (
    <div className="w-[300px] flex flex-col justify-between">
      <div className="h-32 p-4 text-sm bg-white border rounded-xl">
        {response ? (
          <div>
            <div className="inline-block px-2 py-1 mb-1 text-xs text-white bg-black rounded-full">
              프롬프트 문장입니다
            </div>
            <p>{response}</p>
          </div>
        ) : (
          <p className="text-gray-400">프롬프트에 대한 응답이 여기에 나타납니다</p>
        )}
      </div>
      <div className="mt-4">
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="찾고 싶은 사진 또는 정보를 입력하세요"
          className="w-full px-4 py-2 text-sm border rounded-lg"
        />
        <button
          onClick={onSend}
          className="w-full py-2 mt-2 text-white rounded-lg bg-cyan-400 hover:bg-cyan-500"
        >
          전송
        </button>
      </div>
    </div>
  );
}

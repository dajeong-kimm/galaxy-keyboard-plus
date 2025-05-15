"use client";

import { useEffect, useRef, useState } from "react";

import Image from "next/image";

// 전체 프롬프트 리스트
const ALL_PROMPTS = [
  "네일아트",
  "강아지",
  "지난주 여행지",
  "서면 음식점",
  "영수증 사진",
  "카페 인테리어",
  "프레젠테이션 슬라이드",
  "손글씨 메모",
  "메뉴판 사진",
];

export default function ChatBox() {
  const [input, setInput] = useState("");
  const [messages, setMessages] = useState([]);
  const [availablePrompts, setAvailablePrompts] = useState(ALL_PROMPTS);
  const [visiblePrompts, setVisiblePrompts] = useState([]);
  const chatEndRef = useRef(null);

  // 2개만 랜덤으로 표시
  useEffect(() => {
    setVisiblePrompts(
      availablePrompts.slice(0, 2) // or use random selection logic if preferred
    );
  }, [availablePrompts]);

  const sendMessage = (text) => {
    const userMessage = { role: "user", text };
    const botMessage = {
      role: "bot",
      text: `"${text}"에 대한 응답입니다.`,
      image: "/images/grid-images/2.jpg",
    };
    setMessages((prev) => [...prev, userMessage, botMessage]);
  };

  const handleSend = () => {
    if (!input.trim()) return;
    sendMessage(input);
    setInput("");
  };

  const handlePromptClick = (prompt) => {
    setInput(""); // reset input
    sendMessage(prompt);

    // 사용한 프롬프트 제거
    setAvailablePrompts((prev) => prev.filter((p) => p !== prompt));
  };

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  return (
    <div className="chat w-[448px] h-[448px] flex flex-col justify-between p-4 bg-white rounded-xl">
      {/* 메시지 출력 */}
      <div className="flex flex-col gap-3 mb-2 overflow-y-auto">
        {messages.map((msg, idx) => (
          <div
            className={`max-w-[80%] w-fit text-sm px-4 py-2 rounded-xl ${
              msg.role === "user"
                ? "self-end bg-black text-white rounded-tr-none mr-1"
                : "self-start bg-white text-gray-800 border border-gray-200 rounded-tl-none shadow-sm"
            }`}
          >
            <p className="mb-1">{msg.text}</p>
            {msg.image && (
              <div className="inline-flex flex-wrap gap-2 mt-2">
                {[msg.image, msg.image, msg.image].map((src, i) => (
                  <div
                    key={i}
                    className="overflow-hidden border rounded-lg aspect-square w-[88px] aspect-square h-[88px]"
                  >
                    <Image
                      src={src}
                      alt={`response image ${i + 1}`}
                      width={112}
                      height={112}
                      className="object-cover w-full h-full"
                    />
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        <div ref={chatEndRef} />
      </div>

      <div>
        {/* 프롬프트 예시 */}
        <div className="flex flex-col gap-1 mb-2">
          {visiblePrompts.map((text, idx) => (
            <button
              key={idx}
              onClick={() => handlePromptClick(text)}
              className="w-full px-4 py-2 text-sm text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
            >
              {text}
            </button>
          ))}
        </div>

        {/* 입력 영역 */}
        <div className="flex items-center h-12 gap-2">
          <input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") handleSend();
            }}
            placeholder="찾고 싶은 사진 또는 정보를 입력하세요"
            className="flex-1 h-full px-4 py-3 text-sm border rounded-lg"
          />
          <button
            onClick={handleSend}
            className="flex items-center justify-center w-12 h-full text-white rounded-lg group bg-cyan-400 hover:bg-[#86efac]"
          >
            <div className="w-3 h-3 bg-white rounded-full group-hover:hidden" />
            <span className="hidden text-sm font-semibold group-hover:inline">전송</span>
          </button>
        </div>
      </div>
    </div>
  );
}

"use client";

import { useEffect, useRef, useState } from "react";

import Image from "next/image";
import axiosInstance from "@/lib/axiosInstance";

// 전체 프롬프트 리스트
const ALL_PROMPTS = [
  "별 그려진 연두색 네일아트 사진 찾아줘",
  "투썸 와이파이 비밀번호 알려줘",
  "서면 타코 얼마였지?",
  "키티가 들판 위에 있는 사진 찾아줘",
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
  const [isTyping, setIsTyping] = useState(false);

  // 2개만 랜덤으로 표시
  useEffect(() => {
    setVisiblePrompts(
      availablePrompts.slice(0, 2) // or use random selection logic if preferred
    );
  }, [availablePrompts]);

  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const sendMessage = async (text) => {
    const userMessage = { role: "user", text };
    setMessages((prev) => [...prev, userMessage]);
    setIsTyping(true);

    try {
      const params = new URLSearchParams();
      params.append("user_id", "36648ad3-ed4b-4eb0-bcf1-1dc66fa5d258"); // 테스트 유저로 바꾸어야 함
      params.append("query", text);
      params.append("top_k_photo", "5");
      params.append("top_k_info", "5");

      // 🍧 log 확인용
      console.log("📤 요청 보냄:", Object.fromEntries(params.entries()));

      // 🍧 로딩 스피너 보려고 일부러 지연
      await new Promise((res) => setTimeout(res, 2000));

      const res = await axiosInstance.post("/search/answer", params);
      const { answer, photo_results } = res.data;

      const imageIds = (photo_results || []).map((item) => `/images/${item.id}.jpg`);

      const botMessage = {
        role: "bot",
        text: answer,
        images: imageIds,
      };

      setMessages((prev) => [...prev, botMessage]);
    } catch (e) {
      const botMessage = {
        role: "bot",
        text: "응답을 불러오는 데 실패했습니다.",
        images: [],
      };
      setMessages((prev) => [...prev, botMessage]);
    } finally {
      setIsTyping(false);
    }
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

  return (
    <div className="chat w-[448px] h-[448px] flex flex-col justify-between p-4 bg-white rounded-xl">
      {/* 메시지 출력 */}
      <div className="flex flex-col gap-3 mb-2 overflow-y-auto">
        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`max-w-[80%] w-fit text-sm px-4 py-2 rounded-xl ${
              msg.role === "user"
                ? "self-end bg-black text-white rounded-tr-none mr-1"
                : "self-start bg-white text-gray-800 border border-gray-200 rounded-tl-none shadow-sm"
            }`}
          >
            <p className="mb-1">{msg.text}</p>
            {msg.images && msg.images.length > 0 && (
              <div className="inline-flex flex-wrap gap-2 mt-2">
                {msg.images.map((src, i) => (
                  <div
                    key={i}
                    className="overflow-hidden border rounded-lg aspect-square w-[88px] h-[88px]"
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
        {isTyping && (
          <div className="self-start bg-white border border-gray-200 text-gray-400 rounded-xl px-4 py-2 text-sm w-fit max-w-[80%] shadow-sm rounded-tl-none">
            포키가 타이핑 중<span className="typing" />
          </div>
        )}
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

"use client";

import { useEffect, useRef, useState } from "react";

import { ArrowUp } from "lucide-react";
import ChatBox from "@/components/common/ChatBox";
import ImageGrid from "@/components/common/ImageGrid";
import gsap from "gsap";

export default function Section12Experience() {
  const sectionRef = useRef(null);
  const [input, setInput] = useState("");
  const [response, setResponse] = useState(null);

  useEffect(() => {
    // 브라우저 스크롤 위치 복원 막기
    if ("scrollRestoration" in window.history) {
      window.history.scrollRestoration = "manual";
    }

    // 직접 강제 초기화
    requestAnimationFrame(() => {
      window.scrollTo(0, 0);
    });
  }, []);

  useEffect(() => {
    gsap.fromTo(".grid", { opacity: 0, y: 50 }, { opacity: 1, y: 0, duration: 1, ease: "power2.out" });

    gsap.fromTo(".chat", { opacity: 0, y: 50 }, { opacity: 1, y: 0, duration: 1, ease: "power2.out", delay: 0.3 });
  }, []);

  const handleSend = () => {
    setResponse(`"${input}"에 대한 응답입니다.`);
    setInput("");
  };

  const scrollToTop = () => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <section
      id="experience"
      ref={sectionRef}
      className="relative flex flex-col items-center w-full min-h-screen px-4 py-16"
    >
      <div className="flex flex-col items-center">
        <h2 className="mb-4 text-3xl font-bold text-black">포키, 체험해보세요</h2>

        <div
          className="absolute bottom-0 w-[1080px] h-[600px] p-8 rounded-3xl"
          style={{
            background: "url(/images/gradient-bg.png) no-repeat center",
            backgroundSize: "cover",
          }}
        >
          <div className="flex gap-12 p-8">
            <ImageGrid />
            <ChatBox
              input={input}
              setInput={setInput}
              response={response}
              onSend={handleSend}
            />
          </div>
        </div>
      </div>

      <div className="absolute bottom-0 z-0 w-full h-8 bg-black" />

      <button
        onClick={scrollToTop}
        className="absolute bottom-12 right-8"
      >
        <ArrowUp className="w-6 h-6 text-black transition hover:scale-110" />
      </button>
    </section>
  );
}

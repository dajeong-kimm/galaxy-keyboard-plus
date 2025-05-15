"use client";

import { useEffect, useRef, useState } from "react";

import { ArrowUp } from "lucide-react";
import gsap from "gsap";

export default function Section12Experience() {
  const sectionRef = useRef(null);
  const [input, setInput] = useState("");
  const [response, setResponse] = useState(null);

  useEffect(() => {
    gsap.from(".grid", { opacity: 0, y: 50, duration: 1 });
    gsap.from(".chat", { opacity: 0, y: 50, duration: 1, delay: 0.3 });
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
        <h2 className="text-3xl font-bold text-black ">포키, 체험해보세요</h2>

        <div
          className="absolute bottom-0 w-[1080px] h-[600px] p-8 rounded-3xl"
          style={{
            background: "url(/images/gradient-bg.png) no-repeat center",
            backgroundSize: "cover",
          }}
        >
          <div className="flex gap-12 p-8 bg-white rounded-3xl">
            <div className="grid grid-cols-3 gap-4 grid w-[300px] h-[300px] bg-gray-100 rounded-xl p-4">
              {[...Array(9)].map((_, i) => (
                <div
                  key={i}
                  className="w-full h-24 transition bg-gray-300 rounded-lg hover:scale-105"
                />
              ))}
            </div>
            <div className="chat w-[300px] flex flex-col justify-between">
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
                  onClick={handleSend}
                  className="w-full py-2 mt-2 text-white rounded-lg bg-cyan-400 hover:bg-cyan-500"
                >
                  전송
                </button>
              </div>
            </div>
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

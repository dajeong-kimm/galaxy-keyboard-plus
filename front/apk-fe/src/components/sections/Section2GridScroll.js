"use client";

import { useEffect, useRef } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

export default function Section2GridScroll() {
  const sectionRef = useRef(null);
  const highlightRef = useRef(null);

  useEffect(() => {
    const ctx = gsap.context(() => {
      // 하이라이트 박스 깜빡임
      gsap.fromTo(
        highlightRef.current,
        { opacity: 1 },
        {
          opacity: 0,
          repeat: -1,
          yoyo: true,
          duration: 0.4,
          ease: "power2.inOut",
          scrollTrigger: {
            trigger: highlightRef.current,
            start: "top 80%",
            end: "bottom center",
            toggleActions: "play none none reverse",
          },
        }
      );
    }, sectionRef);

    return () => ctx.revert();
  }, []);

  return (
    <section
      ref={sectionRef}
      id="experience"
      className="relative flex flex-col items-center justify-start bg-white overflow-hidden"
    >
      <div className="absolute top-0 left-0 z-0 w-8 h-full bg-black" />

      {/* 문구: sticky (스크롤로 최상단 고정 후, 자연스럽게 해제됨) */}
      <div className="sticky top-0 z-10 flex items-center justify-center w-full text-3xl font-bold text-center text-black bg-white min-h-48">
        <div className="absolute top-0 left-0 z-0 w-8 h-full bg-black" />
        아직도 수많은 이미지 속에서 찾고 계신가요?
      </div>

      {/* 자연스럽게 스크롤되는 그리드 */}
      <div className="grid grid-cols-5 gap-2 mt-10 px-4 pb-[30vh]">
        {Array.from({ length: 60 }).map((_, i) => (
          <div
            key={i}
            ref={i === 53 ? highlightRef : null}
            className={`w-24 h-24 rounded-sm ${i === 53 ? "bg-lime-400" : "bg-gray-300"}`}
          />
        ))}
      </div>
    </section>
  );
}

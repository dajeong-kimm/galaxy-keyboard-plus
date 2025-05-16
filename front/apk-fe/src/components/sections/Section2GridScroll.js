"use client";

import { useEffect, useRef } from "react";

import { ScrollTrigger } from "gsap/ScrollTrigger";
import gsap from "gsap";

gsap.registerPlugin(ScrollTrigger);

export default function Section2GridScroll() {
  const sectionRef = useRef(null);
  const gridRef = useRef(null);
  const highlightRef = useRef(null);

  useEffect(() => {
    const ctx = gsap.context(() => {
      // 스크롤 시 회색 네모들이 위로 올라가도록
      gsap.to(gridRef.current, {
        y: "-40%",
        scrollTrigger: {
          trigger: sectionRef.current,
          start: "top top",
          end: "bottom+=100% top",
          scrub: true,
        },
      });

      // 강조 네모 깜빡임 애니메이션
      gsap.fromTo(
        highlightRef.current,
        { opacity: 1 },
        {
          opacity: 0,
          repeat: -1,
          yoyo: true,
          duration: 0.4,
          ease: "power1.inOut",
          scrollTrigger: {
            trigger: sectionRef.current,
            start: "top center",
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
      className="relative flex flex-col items-center justify-start h-[200vh] bg-white overflow-hidden"
    >
      {/* 고정 문구 */}
      <div className="sticky z-10 flex items-center justify-center w-full text-3xl font-bold text-center text-black bg-red-100 min-h-48">
        아직도 수많은 이미지 속에서 찾고 계신가요?
      </div>

      {/* 네모 그리드 */}
      <div
        ref={gridRef}
        className="grid w-auto grid-cols-5 gap-2 mt-10"
      >
        {Array.from({ length: 60 }).map((_, i) => (
          <div
            key={i}
            ref={i === 53 ? highlightRef : null} // 19번째 네모 강조
            className={`w-24 h-24 rounded-sm ${i === 53 ? "bg-lime-400" : "bg-gray-300"}`}
          />
        ))}
      </div>
    </section>
  );
}

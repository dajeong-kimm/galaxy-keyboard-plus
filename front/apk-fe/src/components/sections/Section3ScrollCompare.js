"use client";

import { useEffect, useRef } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

export default function Section3ScrollCompare() {
  const sectionRef = useRef(null);
  const overlayRefs = useRef([]);
  const nailartLabelRef = useRef(null);
  const strikeRef = useRef(null);
  const konglishRef = useRef(null);

  useEffect(() => {
    const ctx = gsap.context(() => {
      const tl = gsap.timeline({
        scrollTrigger: {
          trigger: sectionRef.current,
          start: "top top",
          end: "+=2000",
          scrub: true,
          pin: true,
          anticipatePin: 1,
        },
      });

      tl.to(overlayRefs.current, {
        opacity: 1,
        stagger: 0.1,
        duration: 1,
      });

      tl.to(
        nailartLabelRef.current,
        {
          color: "#aaa",
          duration: 0.5,
        },
        "+=0.4"
      );

      tl.to(
        strikeRef.current,
        {
          width: "100%",
          duration: 0.5,
        },
        "<"
      );

      tl.fromTo(konglishRef.current, { opacity: 0, y: -20 }, { opacity: 1, y: 0, duration: 1 }, "+=0.8");
    }, sectionRef);

    return () => ctx.revert();
  }, []);

  return (
    <section ref={sectionRef} className="relative flex flex-col items-center justify-center py-12 bg-white h-[100vh]">
      <div className="absolute top-0 left-0 z-0 w-8 h-full bg-black" />

      <h2 className="text-3xl font-bold mb-32 text-center ">정확한 단어를 말하지 않으면 검색이 불가능했나요?</h2>

      <div className="flex gap-32 items-start">
        {/* 네일아트 전체 블럭 */}
        <div className="relative flex flex-col items-center">
          {/* 콩글리시 + 네일아트 텍스트 */}
          <div className="absolute -top-16 left-1/2 -translate-x-1/2 flex flex-col items-center text-center">
            <div ref={konglishRef} className="text-2xl font-semibold text-red-600 opacity-0">
              콩글리시
            </div>
            <div ref={nailartLabelRef} className="relative text-2xl font-bold text-black">
              네일아트
              <div
                ref={strikeRef}
                className="absolute top-1/2 left-0 h-[4px] bg-red-500"
                style={{ width: 0, transform: "translateY(-50%)" }}
              />
            </div>
          </div>

          {/* 네일아트 이미지 */}
          <div className="grid grid-cols-3 gap-2 mt-8">
            {Array.from({ length: 9 }).map((_, i) => (
              <div key={i} className="relative w-32 h-32 bg-gray-200">
                <img
                  src={`/images/nailart-${i + 1}.jpg`}
                  alt={`nailart-${i + 1}`}
                  className="w-full h-full object-cover"
                />
                {i !== 5 && (
                  <div
                    ref={(el) => {
                      if (el) overlayRefs.current[i] = el;
                    }}
                    className="absolute inset-0 pointer-events-none z-0"
                    style={{ backgroundColor: "rgba(0,0,0,0.5)", opacity: 0 }}
                  />
                )}
              </div>
            ))}
          </div>
        </div>

        {/* 매니큐어 블럭 */}
        <div className="relative flex flex-col items-center">
          <div className="absolute -top-16 left-1/2 -translate-x-1/2 flex flex-col items-center text-center">
            <div className="text-2xl font-semibold text-red-600 opacity-0">여백채우기</div>
            <div className="relative text-2xl font-bold text-black">
              매니큐어
              <div
                className="absolute top-1/2 left-0 h-[4px] bg-red-500"
                style={{ width: 0, transform: "translateY(-50%)" }}
              />
            </div>
          </div>

          {/* 매니큐어 이미지 */}
          <div className="grid grid-cols-3 gap-2 mt-8">
            {Array.from({ length: 9 }).map((_, i) => (
              <div key={i} className="relative w-32 h-32 bg-gray-200">
                <img
                  key={i}
                  src={`/images/mani-${i + 1}.jpg`}
                  alt={`mani-${i + 1}`}
                  className="w-32 h-32 object-cover"
                />
              </div>
            ))}
          </div>
        </div>
      </div>

      <p className="mt-8 text-xs text-gray-400">실제 갤러리에서 검색하여 나온 결과 이미지를 사용하였습니다</p>
    </section>
  );
}

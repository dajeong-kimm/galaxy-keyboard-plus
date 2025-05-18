"use client";

import { useEffect, useRef } from "react";
import gsap from "gsap";
import { ScrollTrigger } from "gsap/ScrollTrigger";

gsap.registerPlugin(ScrollTrigger);

export default function Section4Experience() {
  const sectionRef = useRef(null);
  const wrapperRef = useRef(null); // 🔥 전체 감싸는 wrapper
  const bgRef = useRef(null); // 배경만 따로 조작
  const phoneRef = useRef(null); // 목업만 따로 확대
  const textRef = useRef(null); // 텍스트

  useEffect(() => {
    const ctx = gsap.context(() => {
      const tl = gsap.timeline({
        scrollTrigger: {
          trigger: sectionRef.current,
          start: "top top",
          end: "+=2500",
          scrub: true,
          pin: true,
          anticipatePin: 1,
        },
      });

      // 1. 배경 확장
      tl.to(bgRef.current, {
        width: "100vw",
        height: "100vh",
        borderRadius: 0,
        duration: 1,
        ease: "power2.out",
      });

      // 2. 폰 확대 + 살짝 아래로
      tl.to(
        phoneRef.current,
        {
          scale: 3.0,
          y: -180,
          duration: 1,
          ease: "power2.out",
        },
        "<"
      );

      // 3. 텍스트 등장 (Phokey 타이핑)
      tl.fromTo(
        textRef.current?.children,
        { opacity: 0, y: 10 },
        {
          opacity: 1,
          y: 0,
          duration: 0.2,
          stagger: 0.15,
          ease: "power1.out",
        },
        "+=0.4"
      );

      // 4. 정지 시간
      tl.to({}, { duration: 0.5 });
    }, sectionRef);

    return () => ctx.revert();
  }, []);

  return (
    <section ref={sectionRef} className="relative h-[100vh] bg-white overflow-hidden">
      {/* wrapper: 배경 + 목업 포함 */}
      <div ref={wrapperRef} className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 z-10 origin-center">
        {/* 배경 */}
        <div
          ref={bgRef}
          className="relative w-[300px] h-[200px] rounded-2xl overflow-hidden"
          style={{
            backgroundImage: "url(/images/gradient-bg.png)",
            backgroundSize: "cover",
            backgroundPosition: "center",
          }}
        >
          {/* 목업 */}
          <div ref={phoneRef} className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 origin-center">
            <img src="/images/s4-mockup.png" alt="mockup" className="w-[220px] h-auto" />
          </div>
        </div>
      </div>

      {/* 텍스트 */}
      <div
        ref={textRef}
        className="absolute bottom-24 left-1/2 -translate-x-1/2 z-20 flex flex-row text-8xl font-bold text-black"
      >
        {"Phokey".split("").map((char, i) => (
          <span key={i}>{char}</span>
        ))}
      </div>
    </section>
  );
}

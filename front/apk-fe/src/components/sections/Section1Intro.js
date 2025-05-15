"use client";

import { useEffect, useRef, useState } from "react";

import { ArrowDown } from "lucide-react";
import Image from "next/image";
import { ScrollToPlugin } from "gsap/ScrollToPlugin";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import gsap from "gsap";

// ✅ ScrollTrigger, ScrollToPlugin 등록
gsap.registerPlugin(ScrollTrigger, ScrollToPlugin);

export default function Section1Intro() {
  const sectionRef = useRef(null);
  const mockup1Ref = useRef(null);
  const mockup2Ref = useRef(null);
  const [showQR, setShowQR] = useState(false);

  // ✅ 진입 애니메이션 + 스크롤 트리거
  useEffect(() => {
    const ctx = gsap.context(() => {
      // mockup 등장 애니메이션
      gsap.to(mockup1Ref.current, {
        y: 0,
        opacity: 1,
        duration: 1,
        ease: "power2.out",
        onComplete: () => {
          // ✅ 등장 이후 떠있는 효과 시작
          gsap.to(mockup1Ref.current, {
            y: "+=10",
            duration: 2,
            ease: "sine.inOut",
            yoyo: true,
            repeat: -1,
          });
        },
      });

      gsap.to(mockup2Ref.current, {
        y: 0,
        opacity: 1,
        duration: 1,
        delay: 0.2,
        ease: "power2.out",
        onComplete: () => {
          gsap.to(mockup2Ref.current, {
            y: "+=10",
            duration: 2,
            ease: "sine.inOut",
            yoyo: true,
            repeat: -1,
          });
        },
      });

      // 스크롤 트리거
      ScrollTrigger.create({
        trigger: sectionRef.current,
        start: "bottom 90%",
        onEnter: () => {
          gsap.to(window, {
            scrollTo: "#experience",
            duration: 1,
            ease: "power2.inOut",
          });
        },
      });
    }, sectionRef);

    return () => ctx.revert();
  }, []);

  const handleDownloadClick = () => {
    setShowQR(true);
    gsap.to(".title", { scale: 0.7, x: -30, y: 20 });
    gsap.to(".qr-container", { opacity: 1, scale: 1, duration: 0.8, delay: 0.2, y: -10 });
  };

  const scrollToExperience = () => {
    document.querySelector("#experience")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <section
      ref={sectionRef}
      className="relative flex flex-col items-center justify-center h-screen bg-white"
    >
      <div className="flex items-center justify-center gap-16">
        <div className="absolute top-0 left-0 z-0 w-8 h-full bg-black" />

        <div className="intro-mockup">
          <div className="relative w-[560px] h-[750px]">
            <Image
              ref={mockup1Ref}
              src="/images/intro-mockup-1.png"
              alt="1"
              width={300}
              height={400}
              className="absolute left-0 z-0 translate-y-16 opacity-0 top-12"
              loading="eager"
            />
            <Image
              ref={mockup2Ref}
              src="/images/intro-mockup-2.png"
              alt="2"
              width={300}
              height={400}
              className="absolute z-10 translate-y-16 opacity-0 top-40 left-40"
              loading="eager"
            />
          </div>
        </div>

        <div className="flex flex-col items-start justify-start ml-2 mr-32 gap-y-8">
          <div className="relative w-full h-[100px]">
            <h1 className="absolute top-0 left-0 text-6xl font-bold title">Phokey</h1>
            {!showQR && <p className="absolute text-xl font-semibold text-gray-600 top-16">서비스 한 줄 설명</p>}
          </div>

          <button
            onClick={handleDownloadClick}
            className="px-6 py-3 text-center transition rounded-lg min-w-[300px] hover:scale-105 qr-container"
            style={{
              backgroundImage: "linear-gradient(135deg, #67e8f9, #fff, #86efac)",
            }}
          >
            {showQR ? (
              <div className="flex flex-col items-center justify-center">
                <Image
                  src="/images/qr-code.png"
                  alt="qr"
                  width={240}
                  height={240}
                />
                <p className="mt-2 text-sm text-center text-gray-500">QR코드를 촬영하여 설치 페이지로 이동합니다</p>
              </div>
            ) : (
              <p className="font-semisbold">앱 다운로드</p>
            )}
          </button>
        </div>
      </div>

      <button
        onClick={scrollToExperience}
        className="absolute bottom-8"
      >
        <ArrowDown className="w-6 h-6 animate-bounce" />
      </button>
    </section>
  );
}

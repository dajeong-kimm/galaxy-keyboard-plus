"use client";

import { useEffect, useRef, useState } from "react";

import { ArrowDown } from "lucide-react";
import Image from "next/image";
import { ScrollToPlugin } from "gsap/ScrollToPlugin";
import { ScrollTrigger } from "gsap/ScrollTrigger";
import gsap from "gsap";

// gsap ScrollTrigger, ScrollToPlugin 등록
gsap.registerPlugin(ScrollTrigger, ScrollToPlugin);

export default function Section1Intro() {
  const sectionRef = useRef(null);
  const mockup1Ref = useRef(null);
  const mockup2Ref = useRef(null);
  const [showQR, setShowQR] = useState(false);

  useEffect(() => {
    if ("scrollRestoration" in window.history) {
      window.history.scrollRestoration = "manual";
    }

    let hasUserScrolled = false;

    const handleUserInteraction = () => {
      hasUserScrolled = true;
    };

    window.addEventListener("wheel", handleUserInteraction, { once: true, passive: true });
    window.addEventListener("touchstart", handleUserInteraction, { once: true, passive: true });

    const ctx = gsap.context(() => {
      // 모션
      gsap.to(mockup1Ref.current, {
        y: 0,
        opacity: 1,
        duration: 1,
        ease: "power2.out",
        onComplete: () => {
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

      // // 스크롤 트리거
      // const trigger = ScrollTrigger.create({
      //   trigger: sectionRef.current,
      //   start: "bottom 20%",
      //   // once: true,
      //   onEnter: () => {
      //     if (hasUserScrolled) {
      //       requestAnimationFrame(() => {
      //         gsap.to(window, {
      //           scrollTo: "#experience",
      //           duration: 1,
      //           ease: "power2.inOut",
      //         });
      //       });
      //     }
      //   },
      // });

      // return () => trigger.kill();
    }, sectionRef);

    return () => {
      ctx.revert();
      window.removeEventListener("wheel", handleUserInteraction);
      window.removeEventListener("touchstart", handleUserInteraction);
    };
  }, []);

  const handleDownloadClick = () => {
    // setShowQR(true);
    // gsap.to(".title", { scale: 0.7, x: -30, y: 20 });
    // gsap.to(".qr-container", { opacity: 1, scale: 1, duration: 0.8, delay: 0.2 });
  };

  const scrollToExperience = () => {
    document.querySelector("#experience")?.scrollIntoView({ behavior: "smooth" });
  };

  return (
    <section ref={sectionRef} className="relative flex flex-col items-center justify-center h-screen bg-white">
      <div className="flex items-center justify-center gap-16">
        <div className="absolute top-0 left-0 z-0 w-8 h-full bg-black" />

        <div className="intro-mockup">
          <div className="relative w-[560px] h-[850px]">
            <Image
              ref={mockup1Ref}
              src="/images/intro-mockup-1.png"
              alt="1"
              width={350}
              height={450}
              className="absolute left-0 z-0 translate-y-16 opacity-0 top-10"
              loading="eager"
            />
            <Image
              ref={mockup2Ref}
              src="/images/intro-mockup-2.png"
              alt="2"
              width={350}
              height={450}
              className="absolute z-10 translate-y-16 opacity-0 top-36 left-40"
              loading="eager"
            />
          </div>
        </div>

        <div className="flex flex-col items-start justify-start mr-32 gap-y-8">
          <div className="relative w-full h-[100px]">
            <h1 className="absolute top-0 left-0 text-6xl font-bold text-black title">Galaxy Keyboard+</h1>
            {/* {!showQR && <p className="absolute text-xl font-semibold text-gray-600 top-16">서비스 한 줄 설명</p>} */}
          </div>

          <button
            onClick={handleDownloadClick}
            className="px-6 py-3 text-center transition rounded-lg min-w-[350px] hover:scale-105 qr-container"
            style={{
              backgroundImage: "linear-gradient(135deg, #67e8f9, #fff, #86efac)",
            }}
          >
            {showQR ? (
              <div className="flex flex-col items-center justify-center">
                <Image src="/images/qr-code.jpg" alt="qr" width={240} height={240} />
                <p className="mt-2 text-sm text-center text-gray-500">QR코드를 촬영하여 설치 페이지로 이동합니다</p>
              </div>
            ) : (
              <p className="font-semibold text-black">앱 다운로드</p>
            )}
          </button>
        </div>
      </div>

      <button onClick={scrollToExperience} className="absolute bottom-12 right-8">
        <ArrowDown className="w-6 h-6 animate-bounce" />
      </button>
    </section>
  );
}

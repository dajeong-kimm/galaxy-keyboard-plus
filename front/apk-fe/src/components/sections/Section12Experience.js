"use client";

import { useEffect, useRef, useState } from "react";

import { ArrowUp } from "lucide-react";
import ChatBox from "@/components/common/ChatBox";
import ImageGrid from "@/components/common/ImageGrid";
import axiosInstance from "@/lib/axiosInstance"; // 🍧 임시
import gsap from "gsap";
import pLimit from "p-limit";

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

  //  🍧 임시
  // const compressImage = (fileBlob) => {
  //   return new Promise((resolve) => {
  //     const img = new Image();
  //     const canvas = document.createElement("canvas");
  //     const ctx = canvas.getContext("2d");

  //     img.onload = () => {
  //       const maxW = 1024;
  //       const scale = maxW / img.width;
  //       canvas.width = maxW;
  //       canvas.height = img.height * scale;
  //       ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
  //       canvas.toBlob((blob) => resolve(blob), "image/jpeg", 0.7); // 70% 퀄리티로 압축
  //     };

  //     img.src = URL.createObjectURL(fileBlob);
  //   });
  // };

  // const handleEmbedImages = async () => {
  //   const basePath = "/images/grid-images";
  //   const defaultMeta = {
  //     image_time: "2025:05:15 12:30:00",
  //     latitude: "35.093500",
  //     longitude: "128.856350",
  //   };

  //   const limit = pLimit(5); // 동시에 5개까지 처리

  //   const uploadTasks = Array.from({ length: 30 }, (_, idx) => {
  //     const i = idx + 1;
  //     const fileName = `${i}.jpg`;
  //     const accessId = String(i);

  //     return limit(async () => {
  //       try {
  //         const res = await fetch(`${basePath}/${fileName}`);
  //         const blob = await res.blob();
  //         const compressedBlob = await compressImage(blob);

  //         const formData = new FormData();
  //         formData.append("user_id", "adminadmin");
  //         formData.append("access_id", accessId);
  //         formData.append("image_time", defaultMeta.image_time);
  //         formData.append("latitude", defaultMeta.latitude);
  //         formData.append("longitude", defaultMeta.longitude);
  //         formData.append("file", compressedBlob, fileName);

  //         console.log(`📤 Uploading ${fileName}...`);

  //         const response = await axiosInstance.post("/rag/upload-image-keyword/", formData);
  //         console.log(`✅ ${fileName} uploaded:`, response.data);
  //       } catch (err) {
  //         console.error(`❌ Failed to upload ${fileName}:`, err);
  //       }
  //     });
  //   });

  //   await Promise.all(uploadTasks);
  //   console.log("🎉 모든 이미지 업로드 완료!");
  // };

  return (
    <section
      id="experience"
      ref={sectionRef}
      className="relative flex flex-col items-center w-full min-h-screen px-4 py-16 bg-white"
    >
      {/* 🍧 임시 버튼 */}
      {/* <button
        onClick={handleEmbedImages}
        className="px-4 py-2 mb-6 text-white bg-green-600 rounded-lg hover:bg-green-700"
      >
        📸 이미지 임베딩 실행
      </button> */}

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

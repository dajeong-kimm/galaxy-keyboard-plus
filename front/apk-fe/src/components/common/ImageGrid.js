"use client";

import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import Image from "next/image";

export default function ImageGrid() {
  const imageCount = 30;
  const imageList = Array.from({ length: imageCount }, (_, i) => `/images/grid-images/${i + 1}.jpg`);
  const [selectedIndex, setSelectedIndex] = useState(null);
  const modalRef = useRef(null);

  // 바깥 클릭 시 모달 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (modalRef.current && !modalRef.current.contains(e.target)) {
        setSelectedIndex(null);
      }
    };

    if (selectedIndex !== null) {
      document.addEventListener("mousedown", handleClickOutside);
    }

    return () => {
      document.removeEventListener("mousedown", handleClickOutside);
    };
  }, [selectedIndex]);

  const goPrev = () => {
    if (selectedIndex !== null && selectedIndex > 0) {
      setSelectedIndex(selectedIndex - 1);
    }
  };

  const goNext = () => {
    if (selectedIndex !== null && selectedIndex < imageCount - 1) {
      setSelectedIndex(selectedIndex + 1);
    }
  };

  return (
    <>
      <div className="grid w-auto overflow-x-hidden h-[448px] grid-cols-3 gap-4 p-4 overflow-y-scroll bg-gray-100 rounded-xl">
        {imageList.map((src, i) => (
          <div
            key={i}
            onClick={() => setSelectedIndex(i)}
            className="w-32 h-32 overflow-hidden transition bg-gray-300 rounded-lg cursor-pointer hover:scale-150"
          >
            <Image
              src={src}
              alt={`grid-${i + 1}`}
              width={96}
              height={96}
              className="object-cover w-full h-full"
            />
          </div>
        ))}
      </div>

      {/* 모달 */}
      {selectedIndex !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50">
          <div
            ref={modalRef}
            className="relative bg-white rounded-xl p-4 shadow-xl max-w-[90%] max-h-[90%] flex flex-col items-center"
          >
            <button
              onClick={() => setSelectedIndex(null)}
              className="absolute text-2xl font-bold text-gray-500 top-2 right-2 hover:text-black"
              aria-label="Close"
            >
              <X strokeWidth={0.8} />
            </button>
            <div className="relative flex items-center justify-center">
              {/* 이전 버튼 */}
              {selectedIndex > 0 && (
                <button
                  onClick={goPrev}
                  className="absolute left-[-80px] top-1/2 -translate-y-1/2 p-2 rounded-full bg-white shadow hover:bg-gray-100 disabled:opacity-30"
                  aria-label="Previous Image"
                >
                  <ChevronLeft
                    size={32}
                    strokeWidth={0.5}
                  />
                </button>
              )}

              <Image
                src={imageList[selectedIndex]}
                alt={`Selected ${selectedIndex + 1}`}
                width={600}
                height={600}
                className="object-contain max-w-full max-h-[80vh] rounded-md"
                priority
              />

              {/* 다음 버튼 */}
              {selectedIndex < imageCount - 1 && (
                <button
                  onClick={goNext}
                  className="absolute right-[-80px] top-1/2 -translate-y-1/2 p-2 rounded-full bg-white shadow hover:bg-gray-100 disabled:opacity-30"
                  aria-label="Next Image"
                >
                  <ChevronRight
                    size={32}
                    strokeWidth={0.5}
                  />
                </button>
              )}
            </div>

            {/* 이미지 인덱스 표시 */}
            <div className="px-4 py-1 mt-4 text-sm text-gray-700 bg-gray-300 font-base rounded-3xl">
              {selectedIndex + 1} / {imageCount}
            </div>
          </div>
        </div>
      )}
    </>
  );
}

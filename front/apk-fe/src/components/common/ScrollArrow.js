"use client";

import { ArrowDown, ArrowUp } from "lucide-react";

export default function ScrollArrow({ direction = "down", targetId }) {
  const handleClick = () => {
    const el = document.querySelector(`#${targetId}`);
    if (el) {
      el.scrollIntoView({ behavior: "smooth" });
    }
  };

  return (
    <button
      onClick={handleClick}
      className="absolute text-black bottom-8"
    >
      {direction === "down" ? <ArrowDown className="w-6 h-6 animate-bounce" /> : <ArrowUp className="w-6 h-6" />}
    </button>
  );
}

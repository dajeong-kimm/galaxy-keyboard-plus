"use client";

import Section12Experience from "@/components/sections/Section12Experience";
import Section1Intro from "@/components/sections/Section1Intro";
import Section2GridScroll from "@/components/sections/Section2GridScroll";
import Section3ScrollCompare from "@/components/sections/Section3ScrollCompare";

export default function HomePage() {
  return (
    <main>
      <Section1Intro />
      {/* <Section2GridScroll /> */}
      {/* <Section3ScrollCompare /> */}
      <Section12Experience />
    </main>
  );
}

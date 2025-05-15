import "./globals.css";

import localFont from "next/font/local";

const pretendard = localFont({
  src: "../fonts/PretendardVariable.woff2",
  variable: "--font-pretendard",
  display: "swap",
});

export const metadata = {
  title: "Phokey",
  description: "사진 기반 키보드 서비스",
};

export default function RootLayout({ children }) {
  return (
    <html lang="ko">
      <body className={`${pretendard.variable} font-sans`}>{children}</body>
    </html>
  );
}

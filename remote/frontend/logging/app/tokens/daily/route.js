import { NextResponse } from "next/server"

// 임시 데이터 (실제 API 연동 시 제거)
const mockDailyData = [
  { day: "월", value: 25 },
  { day: "화", value: 42 },
  { day: "수", value: 55 },
  { day: "목", value: 62 },
  { day: "금", value: 75 },
  { day: "토", value: 60 },
  { day: "일", value: 35 },
]

export async function GET(request, { params }) {
  try {
    // 실제 API 연동 시 아래 주석을 해제하고 mockDailyData 대신 사용
    // const response = await fetch(`http://your-backend-url/logging/tokens/daily/${params.date}`)
    // const data = await response.json()

    // 임시 데이터 반환
    return NextResponse.json(mockDailyData)
  } catch (error) {
    console.error("API 호출 오류:", error)
    return NextResponse.json({ error: "데이터를 가져오는데 실패했습니다" }, { status: 500 })
  }
}

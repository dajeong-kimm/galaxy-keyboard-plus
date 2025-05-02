import { NextResponse } from "next/server"

// 임시 데이터 (실제 API 연동 시 제거)
const mockWeeklyData = [
  { day: "월", value: 30 },
  { day: "화", value: 45 },
  { day: "수", value: 52 },
  { day: "목", value: 65 },
  { day: "금", value: 70 },
  { day: "토", value: 55 },
  { day: "일", value: 40 },
]

export async function GET(request, { params }) {
  try {
    // 실제 API 연동 시 아래 주석을 해제하고 mockWeeklyData 대신 사용
    // const response = await fetch(`http://your-backend-url/logging/tokens/weekly/${params.weekStart}`)
    // const data = await response.json()

    // 임시 데이터 반환
    return NextResponse.json(mockWeeklyData)
  } catch (error) {
    console.error("API 호출 오류:", error)
    return NextResponse.json({ error: "데이터를 가져오는데 실패했습니다" }, { status: 500 })
  }
}

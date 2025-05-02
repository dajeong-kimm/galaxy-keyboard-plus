// API 호출 함수

// 일별 토큰 통계 가져오기
export async function fetchDailyTokenStats(date) {
    try {
      const response = await fetch(`/api/logging/tokens/daily/${date}`)
      if (!response.ok) {
        throw new Error("일별 토큰 통계를 가져오는데 실패했습니다")
      }
      return await response.json()
    } catch (error) {
      console.error("API 호출 오류:", error)
      return []
    }
  }
  
  // 주별 토큰 통계 가져오기
  export async function fetchWeeklyTokenStats(weekStart) {
    try {
      const response = await fetch(`/api/logging/tokens/weekly/${weekStart}`)
      if (!response.ok) {
        throw new Error("주별 토큰 통계를 가져오는데 실패했습니다")
      }
      return await response.json()
    } catch (error) {
      console.error("API 호출 오류:", error)
      return []
    }
  }
  
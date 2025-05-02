"use client"

import { useState, useEffect } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, ResponsiveContainer } from "recharts"
import { fetchDailyTokenStats, fetchWeeklyTokenStats } from "@/lib/api"

// 요일 레이블
const dayLabels = ["월", "화", "수", "목", "금", "토", "일"]

export default function TokenUsageStats() {
  const [activeTab, setActiveTab] = useState("daily")
  const [dailyStats, setDailyStats] = useState([])
  const [weeklyStats, setWeeklyStats] = useState([])
  const [isLoading, setIsLoading] = useState(true)

  // 현재 날짜 포맷팅 (YYYY-MM-DD)
  const today = new Date()
  const formattedDate = today.toISOString().split("T")[0]

  // 이번 주 시작일 계산 (월요일 기준)
  const getWeekStart = () => {
    const day = today.getDay() || 7 // 일요일(0)을 7로 변환
    const diff = today.getDate() - day + 1 // 월요일로 조정
    const monday = new Date(today)
    monday.setDate(diff)
    return monday.toISOString().split("T")[0]
  }

  useEffect(() => {
    const loadData = async () => {
      setIsLoading(true)
      try {
        if (activeTab === "daily") {
          const data = await fetchDailyTokenStats(formattedDate)

          // 데이터 변환 (실제 API 응답에 맞게 조정 필요)
          const transformedData = [
            { day: "월", value: 25 },
            { day: "화", value: 42 },
            { day: "수", value: 55 },
            { day: "목", value: 62 },
            { day: "금", value: 75 },
            { day: "토", value: 60 },
            { day: "일", value: 35 },
          ]

          setDailyStats(transformedData)
        } else {
          const data = await fetchWeeklyTokenStats(getWeekStart())

          // 데이터 변환 (실제 API 응답에 맞게 조정 필요)
          const transformedData = [
            { day: "월", value: 25 },
            { day: "화", value: 42 },
            { day: "수", value: 55 },
            { day: "목", value: 62 },
            { day: "금", value: 75 },
            { day: "토", value: 60 },
            { day: "일", value: 35 },
          ]

          setWeeklyStats(transformedData)
        }
      } catch (error) {
        console.error("데이터 로딩 중 오류 발생:", error)
      } finally {
        setIsLoading(false)
      }
    }

    loadData()
  }, [activeTab, formattedDate])

  const currentData = activeTab === "daily" ? dailyStats : weeklyStats

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">토큰 사용 통계</h1>

      <Card className="bg-white rounded-xl shadow-sm">
        <Tabs defaultValue="daily" onValueChange={setActiveTab}>
          <TabsList className="grid grid-cols-2 w-[200px] ml-6 mt-6">
            <TabsTrigger value="daily" className="data-[state=active]:bg-[#B38B75] data-[state=active]:text-white">
              일별
            </TabsTrigger>
            <TabsTrigger value="weekly" className="data-[state=active]:bg-[#B38B75] data-[state=active]:text-white">
              주별
            </TabsTrigger>
          </TabsList>

          <CardContent className="p-6">
            <div className="h-[300px] w-full">
              {isLoading ? (
                <div className="flex items-center justify-center h-full">
                  <p>데이터 로딩 중...</p>
                </div>
              ) : (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={currentData} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                    <CartesianGrid vertical={false} strokeDasharray="3 3" />
                    <XAxis dataKey="day" axisLine={false} tickLine={false} />
                    <YAxis axisLine={false} tickLine={false} domain={[0, "dataMax + 20"]} />
                    <Bar dataKey="value" fill="#F8E7D8" radius={[4, 4, 0, 0]} barSize={40} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </div>
          </CardContent>
        </Tabs>
      </Card>
    </div>
  )
}

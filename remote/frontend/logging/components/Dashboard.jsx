import React from 'react';
import './Dashboard.css';

function Dashboard() {
  return (
    <div className="dashboard">
      <h2 className="title">토큰 사용 통계</h2>
      <div className="card">
        <div className="tabs">
          <div className="tab active">일별</div>
          <div className="tab">주별</div>
        </div>
        <div className="chart-placeholder">
          {/* 여기에는 실제 차트를 나중에 넣을 수 있음 */}
          <img src="/chart-sample.png" alt="Chart" />
        </div>
      </div>
    </div>
  );
}

export default Dashboard;

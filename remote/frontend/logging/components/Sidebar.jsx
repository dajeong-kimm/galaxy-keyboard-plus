import React from 'react';
import './Sidebar.css';

function Sidebar() {
  return (
    <div className="sidebar">
      <div className="logo">☕ Moca</div>
      <nav className="nav">
        <div className="nav-item active">대시보드</div>
        <div className="nav-item">사용자 관리</div>
        <div className="nav-item">MCP 서버 통계</div>
      </nav>
      <div className="footer">⚙ 설정</div>
    </div>
  );
}

export default Sidebar;

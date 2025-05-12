import os
import pytest
import time
import json
from unittest.mock import Mock, patch, MagicMock
from mcp_web_research import (
    MCPWebResearch,
    determine_query_type,
    perform_web_search,
    generate_answer_from_web_search,
    web_search,
    MCP_WEB_RESEARCH_ENABLED,
    mcp_web_research,
)


class TestMCPWebResearch:
    """MCPWebResearch 클래스 테스트"""

    def test_init(self):
        """초기화 테스트"""
        with patch("mcp_web_research.shutil.which") as mock_which:
            with patch("mcp_web_research.subprocess.Popen") as mock_popen:
                mock_which.return_value = "/usr/local/bin/npx"
                mock_process = MagicMock()
                mock_process.stdin = MagicMock()
                mock_process.stdout.readline.return_value = json.dumps(
                    {
                        "jsonrpc": "2.0",
                        "id": 1,
                        "result": {
                            "protocolVersion": "0.1.0",
                            "serverInfo": {
                                "name": "mcp-webresearch",
                                "version": "0.1.0",
                            },
                        },
                    }
                )
                mock_popen.return_value = mock_process

                mcp = MCPWebResearch()
                assert mcp.initialized == True
                mock_which.assert_called_once_with("npx")

    def test_search_google_success(self):
        """구글 검색 성공 테스트"""
        mcp = MCPWebResearch()
        mcp.initialized = True
        mcp.process = MagicMock()
        mcp.process.stdin = MagicMock()
        mcp.process.stdout.readline.return_value = json.dumps(
            {
                "jsonrpc": "2.0",
                "id": 2,
                "result": {
                    "content": [
                        {
                            "text": json.dumps(
                                [
                                    {
                                        "title": "테스트 제목",
                                        "url": "https://example.com",
                                        "snippet": "테스트 내용",
                                    }
                                ]
                            )
                        }
                    ]
                },
            }
        )

        result = mcp.search_google("테스트 쿼리")

        assert isinstance(result, list)
        assert len(result) > 0
        assert result[0]["title"] == "테스트 제목"
        assert result[0]["url"] == "https://example.com"

    def test_search_google_error(self):
        """구글 검색 실패 테스트"""
        mcp = MCPWebResearch()
        mcp.initialized = False

        result = mcp.search_google("테스트 쿼리")

        assert "error" in result
        assert result["error"] == "MCP 웹 검색 서버가 초기화되지 않았습니다."

    def test_visit_page_success(self):
        """웹페이지 방문 성공 테스트"""
        mcp = MCPWebResearch()
        mcp.initialized = True
        mcp.process = MagicMock()
        mcp.process.stdin = MagicMock()
        mcp.process.stdout.readline.return_value = json.dumps(
            {
                "jsonrpc": "2.0",
                "id": 3,
                "result": {
                    "content": [
                        {
                            "text": json.dumps(
                                {"content": "웹페이지 내용", "screenshotUrl": None}
                            )
                        }
                    ]
                },
            }
        )

        result = mcp.visit_page("https://example.com")

        assert "content" in result
        assert result["content"] == "웹페이지 내용"

    def test_cleanup(self):
        """정리 기능 테스트"""
        mcp = MCPWebResearch()
        mcp.process = MagicMock()
        mcp.temp_dir = MagicMock()
        mcp.initialized = True

        mcp.cleanup()

        mcp.process.terminate.assert_called_once()
        mcp.temp_dir.cleanup.assert_called_once()
        assert mcp.initialized == False


class TestDetermineQueryType:
    """쿼리 타입 판별 테스트"""

    @patch("mcp_web_research.client.chat.completions.create")
    def test_photo_query(self, mock_create):
        """사진 쿼리 판별 테스트"""
        mock_response = MagicMock()
        mock_response.choices[0].message.content = "photo"
        mock_create.return_value = mock_response

        result = determine_query_type("고양이 사진 보여줘")
        assert result == "photo"

    @patch("mcp_web_research.client.chat.completions.create")
    def test_info_query(self, mock_create):
        """정보 쿼리 판별 테스트"""
        mock_response = MagicMock()
        mock_response.choices[0].message.content = "info"
        mock_create.return_value = mock_response

        result = determine_query_type("파이썬의 역사를 알려줘")
        assert result == "info"

    @patch("mcp_web_research.client.chat.completions.create")
    def test_web_query(self, mock_create):
        """웹 검색 쿼리 판별 테스트"""
        mock_response = MagicMock()
        mock_response.choices[0].message.content = "web"
        mock_create.return_value = mock_response

        result = determine_query_type("지금 현재 날씨는?")
        assert result == "web"

    @patch("mcp_web_research.client.chat.completions.create")
    def test_ambiguous_query(self, mock_create):
        """애매한 쿼리 판별 테스트"""
        mock_response = MagicMock()
        mock_response.choices[0].message.content = "ambiguous"
        mock_create.return_value = mock_response

        result = determine_query_type("도와줘")
        assert result == "ambiguous"

    @patch("mcp_web_research.client.chat.completions.create")
    def test_invalid_response(self, mock_create):
        """잘못된 응답 처리 테스트"""
        mock_response = MagicMock()
        mock_response.choices[0].message.content = "invalid"
        mock_create.return_value = mock_response

        result = determine_query_type("테스트")
        assert result == "ambiguous"


class TestPerformWebSearch:
    """웹 검색 수행 테스트"""

    def test_web_search_disabled(self):
        """웹 검색 비활성화 상태 테스트"""
        with patch("mcp_web_research.MCP_WEB_RESEARCH_ENABLED", False):
            result = perform_web_search("테스트 쿼리")
            assert "error" in result
            assert "활성화되지 않았습니다" in result["error"]

    @patch("mcp_web_research.mcp_web_research")
    def test_web_search_success(self, mock_mcp):
        """웹 검색 성공 테스트"""
        mock_mcp.initialized = True
        mock_mcp.search_google.return_value = [
            {
                "title": "테스트 제목",
                "url": "https://example.com/1",
                "snippet": "테스트 스니펫",
            }
        ]
        mock_mcp.visit_page.return_value = {"content": "웹페이지 내용"}

        result = perform_web_search("테스트 쿼리")

        assert "search_results" in result
        assert "web_contents" in result
        assert len(result["web_contents"]) > 0
        assert result["web_contents"][0]["title"] == "테스트 제목"

    @patch("mcp_web_research.mcp_web_research")
    def test_web_search_with_error(self, mock_mcp):
        """웹 검색 오류 처리 테스트"""
        mock_mcp.initialized = True
        mock_mcp.search_google.return_value = {"error": "검색 실패"}

        result = perform_web_search("테스트 쿼리")

        assert "error" in result
        assert result["error"] == "검색 실패"


class TestGenerateAnswerFromWebSearch:
    """웹 검색 결과 기반 답변 생성 테스트"""

    def test_generate_with_error(self):
        """오류 결과로 답변 생성 테스트"""
        web_results = {"error": "검색 오류"}
        result = generate_answer_from_web_search("테스트 쿼리", web_results)

        assert "웹 검색 중 오류가 발생했습니다" in result
        assert "검색 오류" in result

    @patch("mcp_web_research.client.chat.completions.create")
    def test_generate_with_web_contents(self, mock_create):
        """웹 콘텐츠가 있는 경우 답변 생성 테스트"""
        web_results = {
            "web_contents": [
                {
                    "title": "테스트 제목",
                    "url": "https://example.com",
                    "content": "테스트 내용",
                    "snippet": "테스트 스니펫",
                }
            ]
        }

        mock_response = MagicMock()
        mock_response.choices[0].message.content = "생성된 답변"
        mock_create.return_value = mock_response

        result = generate_answer_from_web_search("테스트 쿼리", web_results)

        assert result == "생성된 답변"
        mock_create.assert_called_once()

    @patch("mcp_web_research.client.chat.completions.create")
    def test_generate_with_search_results_only(self, mock_create):
        """검색 결과만 있는 경우 답변 생성 테스트"""
        web_results = {
            "search_results": [
                {
                    "title": "테스트 제목",
                    "url": "https://example.com",
                    "snippet": "테스트 스니펫",
                }
            ]
        }

        mock_response = MagicMock()
        mock_response.choices[0].message.content = "생성된 답변"
        mock_create.return_value = mock_response

        result = generate_answer_from_web_search("테스트 쿼리", web_results)

        assert result == "생성된 답변"
        mock_create.assert_called_once()

    def test_generate_with_no_results(self):
        """결과가 없는 경우 답변 생성 테스트"""
        web_results = {}
        result = generate_answer_from_web_search("테스트 쿼리", web_results)

        assert result == "웹 검색 결과가 없습니다."


class TestWebSearch:
    """통합 웹 검색 기능 테스트"""

    @patch("mcp_web_research.perform_web_search")
    @patch("mcp_web_research.generate_answer_from_web_search")
    def test_web_search_integration(self, mock_generate, mock_perform):
        """웹 검색 통합 테스트"""
        mock_perform.return_value = {
            "search_results": [{"title": "테스트"}],
            "web_contents": [{"title": "테스트", "content": "내용"}],
        }
        mock_generate.return_value = "최종 답변"

        result = web_search("테스트 쿼리")

        assert result == "최종 답변"
        mock_perform.assert_called_once_with("테스트 쿼리")
        mock_generate.assert_called_once()


# 실제 MCP 서버와의 통합 테스트 (주석 처리)
# 실제 환경에서만 실행하시기 바랍니다.
"""
class TestMCPWebResearchIntegration:
    '''실제 MCP 서버와의 통합 테스트'''
    
    @pytest.mark.integration
    def test_real_google_search(self):
        '''실제 구글 검색 테스트'''
        mcp = MCPWebResearch()
        if mcp.initialized:
            result = mcp.search_google("OpenAI GPT-4")
            assert isinstance(result, (list, dict))
            if isinstance(result, list):
                assert len(result) > 0
        else:
            pytest.skip("MCP 서버가 초기화되지 않았습니다.")
    
    @pytest.mark.integration
    def test_real_page_visit(self):
        '''실제 웹페이지 방문 테스트'''
        mcp = MCPWebResearch()
        if mcp.initialized:
            result = mcp.visit_page("https://www.google.com")
            assert isinstance(result, dict)
            assert "content" in result or "error" in result
        else:
            pytest.skip("MCP 서버가 초기화되지 않았습니다.")
"""

if __name__ == "__main__":
    pytest.main([__file__, "-v"])

import openai
import os
from dotenv import load_dotenv
import sys # 추가
import io  # 추가
import math # 추가
from pydub import AudioSegment # pydub 추가 - 이 버전에서는 사용 안 함
from pydub.exceptions import CouldntDecodeError # pydub 예외 추가 - 이 버전에서는 사용 안 함
import glob # 파일 목록 가져오기 위해 추가
# 표준 출력 및 표준 에러를 UTF-8로 재설정
# MINGW64 환경 등에서 기본 인코딩 문제 방지
try:
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
except Exception as e:
    # 재설정 중 오류 발생 시 원본 스트림으로 메시지 출력 시도
    print(f"Warning: Failed to reconfigure stdout/stderr encoding: {e}", file=sys.__stderr__)

# .env 파일 로드 (API 키를 환경 변수로 관리하는 경우)
load_dotenv()

# OpenAI API 키 설정
API_KEY = os.getenv("OPENAI_API_KEY") # 환경 변수에서 가져오기
# 또는 직접 키 입력 (보안상 권장하지 않음)
# 주의: 실제 키는 코드에 직접 넣지 않는 것이 좋습니다.
# API_KEY = "sk-..."

if not API_KEY:
    print("오류: OpenAI API 키가 환경 변수(OPENAI_API_KEY)에 설정되지 않았습니다.", file=sys.stderr)
    sys.exit(1)

# OpenAI 클라이언트 생성
try:
    client = openai.OpenAI(api_key=API_KEY)
except Exception as e:
    # 클라이언트 생성 오류 출력 개선
    try:
        e_str = str(e)
    except Exception:
        e_str = repr(e)
    error_message = f"오류: OpenAI 클라이언트 생성 실패: {e_str}"
    try:
        print(error_message, file=sys.stderr)
    except UnicodeEncodeError:
        print(error_message.encode('utf-8', 'replace').decode('utf-8'), file=sys.stderr)
    sys.exit(1)

# --- 단일 오디오 파일 처리 함수 ---
def transcribe_single_audio(client, file_path, model="whisper-1"):
    """
    주어진 단일 오디오 파일을 처리하여
    전체 텍스트를 반환합니다.

    Args:
        client: OpenAI 클라이언트 객체
        file_path (str): 오디오 파일 경로
        model (str): 사용할 Whisper 모델 이름
        max_size_mb (int): 청크당 최대 크기 (MB)
    Returns:
        str or None: 변환된 전체 텍스트 또는 실패 시 None
    """
    if not os.path.exists(file_path):
        print(f"오류: 파일을 찾을 수 없습니다 - {file_path}", file=sys.stderr)
        return None
    
    print(f"파일 처리 중: {file_path}")
    try:
        with open(file_path, "rb") as audio_file:
            transcript = client.audio.transcriptions.create(
                model=model,
                file=audio_file
            )
        print(f"파일 처리 완료: {file_path}")
        return transcript.text
    except openai.APIError as e:
        # 오류 처리 (기존과 동일, 안전한 출력 방식 사용)
        try: e_str = str(e)
        except Exception: e_str = repr(e)
        error_message = f"OpenAI API 오류 ({os.path.basename(file_path)}): {e_str}"
        try: print(error_message, file=sys.stderr)
        except UnicodeEncodeError: print(error_message.encode('utf-8', 'replace').decode('utf-8'), file=sys.stderr)
        return None
    except Exception as e:
        # 오류 처리 (기존과 동일, 안전한 출력 방식 사용)
        try: e_str = str(e)
        except Exception: e_str = repr(e)
        error_message = f"오류 ({os.path.basename(file_path)} 처리 중): {e_str}"
        try: print(error_message, file=sys.stderr)
        except UnicodeEncodeError: print(error_message.encode('utf-8', 'replace').decode('utf-8'), file=sys.stderr)
        return None

# --- 메인 실행 부분 ---
# 처리할 오디오 파일들이 있는 폴더 경로 (스크립트와 같은 위치에 있다고 가정)
# 필요시 경로 수정 (예: "D:/audio_parts/")
audio_folder = "."
# 처리할 파일 패턴 (예: m4a, mp3 등) - 필요시 수정
file_pattern = "*.m4a"

# 폴더 내 오디오 파일 목록 가져오기 (이름 순으로 정렬)
audio_files = sorted(glob.glob(os.path.join(audio_folder, file_pattern)))

if not audio_files:
    print(f"오류: '{audio_folder}' 폴더에서 '{file_pattern}' 패턴의 오디오 파일을 찾을 수 없습니다.", file=sys.stderr)
    sys.exit(1)

print(f"총 {len(audio_files)}개의 오디오 파일 처리 시작...")

all_transcripts = []
has_error = False
for audio_file_path in audio_files:
    transcript_part = transcribe_single_audio(client, audio_file_path)
    if transcript_part is not None:
        all_transcripts.append(transcript_part)
    else:
        # 하나라도 실패하면 중단하거나 계속 진행할 수 있음 (여기서는 에러 플래그만 설정)
        has_error = True
        print(f"경고: {audio_file_path} 파일 처리 중 오류 발생.", file=sys.stderr)
        # break # 오류 발생 시 즉시 중단하려면 주석 해제

if not all_transcripts:
     print("\n오디오 파일 처리 결과가 없습니다.", file=sys.stderr)
     sys.exit(1)

# 모든 텍스트 합치기
final_transcript = " ".join(all_transcripts)

print("\n--- 최종 변환 결과 (합본) ---")
print(final_transcript)

if has_error:
    print("\n경고: 일부 파일 처리 중 오류가 발생했습니다.", file=sys.stderr)

# --- 요약 로직 (추가 필요) ---
print("\n--- 요약 요청 ---")
try:
    summary_response = client.chat.completions.create(
        model="gpt-4o-mini", # 또는 다른 모델
        messages=[
            {"role": "system", "content": "You are a helpful assistant that summarizes meeting transcripts."},
            {"role": "user", "content": f"다음 회의록을 요약해 주세요:\n\n{final_transcript}"}
        ]
    )
    summary = summary_response.choices[0].message.content
    print("\n--- 요약 결과 ---")
    print(summary)
except openai.APIError as e:
    print(f"OpenAI API 오류 (요약): {e}", file=sys.stderr)
except Exception as e:
    print(f"오류 (요약 처리 중): {e}", file=sys.stderr)


import openai
import os
from dotenv import load_dotenv
import sys # 추가
import io  # 추가
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

# --- 단일 텍스트 정제 함수 ---
def clean_transcript(client, raw_transcript, model="gpt-4o-mini"):
    """
    주어진 원본 텍스트를 LLM을 이용해 정제합니다.
    불필요한 내용 제거, 문맥 수정 등을 시도합니다.

    Args:
        client: OpenAI 클라이언트 객체
        raw_transcript (str): Whisper에서 변환된 원본 텍스트
        model (str): 사용할 LLM 모델 이름
    Returns:
        str or None: 정제된 텍스트 또는 실패 시 None
    """
    print("텍스트 정제 시도 중...")
    cleaning_prompt = f"""다음 회의록 텍스트에서 핵심 아이디어, 논의된 기능, 결정 사항, 주요 의견과 관련된 내용만 남겨주세요. 회의 시작/종료 시점의 잡담, 기술적 문제 해결 과정(예: 마이크 테스트), 주제와 직접 관련 없는 개인적인 이야기, 반복되는 필러(filler) 단어 등 불필요한 부분은 최대한 제거해 주세요. 문맥상 어색하거나 잘못 변환된 것으로 보이는 부분은 자연스럽게 수정해주세요. 결과는 정제된 회의록 텍스트만 출력해주세요.

[원본 회의록]
{raw_transcript}

[정제된 회의록]
"""
    try:
        response = client.chat.completions.create(
            model=model,
            messages=[{"role": "user", "content": cleaning_prompt}]
        )
        cleaned_text = response.choices[0].message.content.strip()
        print("텍스트 정제 완료.")
        return cleaned_text
    except Exception as e:
        print(f"[오류] 텍스트 정제 중 오류 발생: {e}", file=sys.stderr)
        return None # 실패 시 None 반환

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

all_cleaned_transcripts = []
has_error = False

# 정제된 텍스트 저장 폴더 생성 (없으면)
cleaned_output_dir = "cleaned_transcripts"
os.makedirs(cleaned_output_dir, exist_ok=True)

for audio_file_path in audio_files:
    # 1. Whisper로 변환
    raw_transcript = transcribe_single_audio(client, audio_file_path)

    if raw_transcript is not None:
        # 2. LLM으로 정제
        cleaned_transcript = clean_transcript(client, raw_transcript)

        # 3. 정제 결과 처리 및 저장
        if cleaned_transcript is None:
            # 정제 실패 시 원본 사용 및 경고
            print(f"경고: {os.path.basename(audio_file_path)} 텍스트 정제 실패. 원본 텍스트를 사용합니다.", file=sys.stderr)
            cleaned_transcript = raw_transcript # fallback
            has_error = True # 정제 실패도 오류로 간주

        # 개별 정제된 텍스트 파일 저장
        base_filename = os.path.splitext(os.path.basename(audio_file_path))[0]
        cleaned_filename = os.path.join(cleaned_output_dir, f"{base_filename}.cleaned.txt")
        try:
            with open(cleaned_filename, "w", encoding="utf-8") as f:
                f.write(cleaned_transcript)
            print(f"[정보] 정제된 텍스트를 '{cleaned_filename}' 파일로 저장했습니다.")
        except Exception as e:
            print(f"[오류] 정제된 텍스트 파일 저장 중 오류 발생 ({cleaned_filename}): {e}", file=sys.stderr)
            has_error = True

        # 최종 합본을 위해 리스트에 추가
        all_cleaned_transcripts.append(cleaned_transcript)

    else:
        # Whisper 변환 실패
        has_error = True
        print(f"경고: {os.path.basename(audio_file_path)} 파일 처리 중 오류 발생. 이 파일은 건너<0xEB><0><0x8F><0xBC>니다.", file=sys.stderr)
        # break # 오류 발생 시 즉시 중단하려면 주석 해제

if not all_cleaned_transcripts:
     print("\n처리된 텍스트 결과가 없습니다.", file=sys.stderr)
     sys.exit(1)

# 모든 정제된 텍스트 합치기
final_cleaned_transcript = "\n\n".join(all_cleaned_transcripts) # 각 파일 내용을 줄바꿈 두 번으로 구분

# --- 최종 합본 정제 텍스트 파일 저장 ---
final_output_filename = "final_cleaned_transcript.txt"
try:
    with open(final_output_filename, "w", encoding="utf-8") as f:
        f.write(final_cleaned_transcript)
    print(f"\n[정보] 최종 합본 정제 텍스트를 '{final_output_filename}' 파일로 저장했습니다.")
except Exception as e:
    print(f"\n[오류] 최종 변환 결과를 파일로 저장하는 중 오류 발생: {e}", file=sys.stderr)
if has_error:
    print("\n경고: 일부 파일 처리 중 오류가 발생했습니다.", file=sys.stderr)

# --- 요약 로직 (추가 필요) ---
print("\n--- 요약 요청 ---")
try:
    summary_response = client.chat.completions.create(
        model="gpt-4o-mini", # 요약에는 더 큰 모델 사용 고려
        messages=[
            {"role": "system", "content": "당신은 회의 내용을 요약해주는 AI입니다. 제공된 정제된 회의록 텍스트를 바탕으로, 핵심 아이디어, 논의된 기능, 결정 사항, 주요 의견들을 명확하고 구조적으로 요약해주세요. 각 항목이 잘 구분되도록 정리하고, 불필요한 내용은 제외해주세요."},
            # 원본 대신 정제된 최종 텍스트 사용
            {"role": "user", "content": f"다음 정제된 회의록을 요약해 주세요:\n\n{final_cleaned_transcript}"}
        ]
    )
    summary = summary_response.choices[0].message.content
    print("\n--- 요약 결과 ---")
    print(summary)
except openai.APIError as e:
    print(f"OpenAI API 오류 (요약): {e}", file=sys.stderr)
except Exception as e:
    print(f"오류 (요약 처리 중): {e}", file=sys.stderr)

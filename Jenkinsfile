pipeline {
  agent any

  environment {
    COMPOSE_FILE = 'docker-compose-prod.yml'
    ENV_FILE     = '.env.prod'
  }

  parameters {
    string(
      name: 'FORCE_SERVICES',
      defaultValue: '',
      description: '콤마(,)로 지정 시 해당 서비스만 빌드·배포 (예: gateway,auth,backend,rag,mcp,web-search,frontend)'
    )
  }

  stages {
    // 1) Checkout
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    // 2) Create .env.prod
    stage('Create .env.prod') {
      steps {
        withCredentials([
          string(credentialsId: 'POSTGRES_AUTH_USER',           variable: 'AUTH_USER'),
          string(credentialsId: 'POSTGRES_AUTH_PASSWORD',       variable: 'AUTH_PW'),
          string(credentialsId: 'POSTGRES_AUTH_DB_NAME',        variable: 'AUTH_DB'),
          string(credentialsId: 'POSTGRES_SCHED_USER',          variable: 'SCHED_USER'),
          string(credentialsId: 'POSTGRES_SCHED_PASSWORD',      variable: 'SCHED_PW'),
          string(credentialsId: 'POSTGRES_SCHED_DB_NAME',       variable: 'SCHED_DB'),
          string(credentialsId: 'POSTGRES_RAG_USER',            variable: 'RAG_USER'),
          string(credentialsId: 'POSTGRES_RAG_PASSWORD',        variable: 'RAG_PW'),
          string(credentialsId: 'POSTGRES_RAG_DB_NAME',         variable: 'RAG_DB'),
          string(credentialsId: 'PINECONE_API_KEY',             variable: 'PINECONE_API_KEY'),
          string(credentialsId: 'PINECONE_INDEX_NAME',          variable: 'PINECONE_INDEX_NAME'),
          file(  credentialsId: 'moca-457801-bfa12690864b.json', variable: 'GCP_KEY_FILE'),
          string(credentialsId: 'CLAUDE_API_KEY',               variable: 'CLAUDE_API_KEY'),
          string(credentialsId: 'OPENAI_API_KEY',               variable: 'OPENAI'),
          string(credentialsId: 'OPENAI_API_KEY_2',             variable: 'OPENAI2'),
          string(credentialsId: 'FIREBASE_CREDENTIALS_JSON_BASE64', variable: 'FIREBASE_CREDENTIALS_JSON_BASE64'),
          string(credentialsId: 'JWT_SECRET_KEY',               variable: 'JWT_SECRET_KEY'),
          string(credentialsId: 'KAKAO_CLIENT_ID',              variable: 'KAKAO_CLIENT_ID'),
          string(credentialsId: 'JWT_AT_VALIDITY',              variable: 'JWT_AT_VALIDITY'),
          string(credentialsId: 'JWT_RT_VALIDITY',              variable: 'JWT_RT_VALIDITY'),
          string(credentialsId: 'FRONTEND_API_URL',             variable: 'FRONTEND_API_URL'),
          string(credentialsId: 'GOOGLE_API_KEY',               variable: 'GOOGLE_API_KEY'),
          string(credentialsId: 'BRAVE_API_KEY',                variable: 'BRAVE_API_KEY'),
          string(credentialsId: 'GOOGLE_SEARCH_API_KEY',               variable: 'GOOGLE_SEARCH_API_KEY'),
          string(credentialsId: 'GOOGLE_SEARCH_ENGINE_ID',                variable: 'GOOGLE_SEARCH_ENGINE_ID')
        ]) {
          sh '''
            cp "$GCP_KEY_FILE" gcp-key.json
            chmod 644 gcp-key.json
            mkdir -p back/rag
            cp gcp-key.json back/rag/
            mkdir -p back/search
            cp gcp-key.json back/search/
          '''
          writeFile file: '.env.prod', text: """
POSTGRES_AUTH_USER=${AUTH_USER}
POSTGRES_AUTH_PASSWORD=${AUTH_PW}
POSTGRES_AUTH_DB_NAME=${AUTH_DB}

POSTGRES_SCHED_USER=${SCHED_USER}
POSTGRES_SCHED_PASSWORD=${SCHED_PW}
POSTGRES_SCHED_DB_NAME=${SCHED_DB}

POSTGRES_RAG_USER=${RAG_USER}
POSTGRES_RAG_PASSWORD=${RAG_PW}
POSTGRES_RAG_DB_NAME=${RAG_DB}

GOOGLE_API_KEY=${GOOGLE_API_KEY}
OPENAI_API_KEY=${OPENAI}
OPENAI_API_KEY_2=${OPENAI2}
PINECONE_API_KEY=${PINECONE_API_KEY}
PINECONE_INDEX_NAME=${PINECONE_INDEX_NAME}
CLAUDE_API_KEY=${CLAUDE_API_KEY}
FIREBASE_CREDENTIALS_JSON_BASE64=${FIREBASE_CREDENTIALS_JSON_BASE64}
JWT_SECRET_KEY=${JWT_SECRET_KEY}
KAKAO_CLIENT_ID=${KAKAO_CLIENT_ID}
JWT_AT_VALIDITY=${JWT_AT_VALIDITY}
JWT_RT_VALIDITY=${JWT_RT_VALIDITY}
FRONTEND_API_URL=${FRONTEND_API_URL}
BRAVE_API_KEY=${BRAVE_API_KEY}

GOOGLE_SEARCH_API_KEY=${GOOGLE_SEARCH_API_KEY}
GOOGLE_SEARCH_ENGINE_ID=${GOOGLE_SEARCH_ENGINE_ID}

ENV=prod
""".trim()
        }
      }
    }

    // 3) Detect Changed Services
    stage('Detect Changed Services') {
      steps {
        script {
          def diff = sh(
            script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'} ${env.GIT_COMMIT}",
            returnStdout: true
          ).trim()
          
          // 변경된 파일 경로에서 서비스 이름 매핑
          def changed = diff.split('\n')
                            .findAll{ it }
                            .findAll{ it.startsWith('back/') || it.startsWith('front/') }
                            .collect{ p ->
                              if (p.startsWith('front/apk-fe/'))        return 'frontend'
                              else if (p.startsWith('front/') )         return 'frontend'  // 모든 front/ 경로는 frontend 서비스로 매핑
                              else if (p.startsWith('back/search/'))    return 'search-service'  // search 디렉토리는 search-service로 매핑
                              else if (p.startsWith('back/mcp/'))       return 'mcp'  // mcp 디렉토리는 mcp 서비스로 매핑
                              else if (p.startsWith('back/brave-search/')) return 'web-search'  // brave-search 디렉토리는 web-search 서비스로 매핑
                              else if (p.startsWith('back/google-web-search/')) return 'google-web-search'  // web-search 디렉토리는 web-search 서비스로 매핑
                              else /* back/... */                       return p.tokenize('/')[1]
                            }
                            .unique()
          
          def forced = params.FORCE_SERVICES?.trim()
                        ? params.FORCE_SERVICES.split(',').collect{ it.trim() }
                        : []
          
          // 강제 서비스 이름 매핑 (사용자 입력을 docker-compose 서비스 이름과 일치시키기)
          def mappedForced = forced.collect { svc ->
            if (svc == 'search') return 'search-service'
            return svc
          }
          
          env.CHANGED_SERVICES = (mappedForced ?: changed).toSet().join(',')
          
          // Compose 파일에 정의된 서비스 목록 조회
          def available = sh(
            script: "docker compose -f ${COMPOSE_FILE} config --services",
            returnStdout: true
          ).trim().split('\n')
          
          // 변경된 서비스 중 실제로 compose에 존재하는 서비스 필터링
          def validServices = env.CHANGED_SERVICES.split(',')
                              .findAll { svc -> available.contains(svc) }
          
          // 없는 서비스 목록
          def invalidServices = env.CHANGED_SERVICES.split(',')
                                .findAll { svc -> !available.contains(svc) }
          
          if (invalidServices) {
            echo "⚠️ 다음 서비스는 docker-compose에 정의되어 있지 않아 무시됩니다: ${invalidServices.join(', ')}"
          }
          
          // 변경된 서비스가 있으나 유효한 서비스가 없는 경우 성공으로 처리
          if (env.CHANGED_SERVICES && !validServices) {
            echo '변경된 서비스가 있지만 모두 docker-compose에 정의되지 않은 서비스입니다. 빌드를 건너뜁니다.'
            env.SKIP_BUILD = 'true'
            // 이후 단계는 진행하되 실제 빌드는 수행하지 않음
          } else if (!validServices) {
            echo '변경된 서비스가 없습니다. 빌드를 건너뜁니다.'
            env.SKIP_BUILD = 'true'
          } else {
            env.VALID_SERVICES = validServices.join(',')
            echo "빌드 대상 서비스: ${env.VALID_SERVICES}"
          }
        }
      }
    }

    // 4) Build & Deploy Backend
    stage('Build & Deploy Backend') {
      when {
        allOf {
          expression { env.SKIP_BUILD != 'true' }
          expression { env.VALID_SERVICES.split(',').any { it != 'frontend' } }
        }
      }
      steps {
        script {
          // 백엔드 서비스 필터링
          def backendServices = env.VALID_SERVICES.split(',')
                                .findAll { svc -> svc != 'frontend' }

          // 실제 빌드·배포
          backendServices.each { svc ->
            echo "▶ Building & deploying ${svc}"
            // 서비스명에서 컨테이너 이름 추출
            def containerName
            if (svc == 'search-service') {
              containerName = 'search-service'
            } else if (svc == 'mcp') {
              containerName = 'mcp-api'
            } else if (svc == 'web-search') {
              containerName = 'web-search'
            } else if (svc != 'nginx' && svc != 'redis-ratelimiter' && !svc.startsWith('postgres_')) {
              containerName = "${svc}-service"
            } else {
              containerName = "${svc}"
            }
            
            sh """
              docker rm -f ${containerName} || true
              docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" build --no-cache ${svc}
              docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps --force-recreate ${svc}
            """
          }
        }
      }
    }

    // 5) Build & Deploy Frontend
    stage('Build & Deploy Frontend') {
      when {
        allOf {
          expression { env.SKIP_BUILD != 'true' }
          expression { env.VALID_SERVICES.split(',').contains('frontend') }
        }
      }
      steps {
        script {
          echo "▶ Building & deploying frontend"
          sh """
            docker rm -f frontend-service || true
            docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" build --no-cache frontend
            docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps --force-recreate frontend
          """
        }
      }
    }
    
    // 6) Check Services (새로 추가된 서비스 포함)
    stage('Check Services') {
      when {
        expression { env.SKIP_BUILD != 'true' }
      }
      steps {
        script {
          def validServices = env.VALID_SERVICES.split(',')
          
          // MCP 또는 Web Search 서비스가 배포되었는지 확인
          if (validServices.contains('mcp') || validServices.contains('web-search')) {
            echo "▶ Checking MCP and Web Search services..."
            sh '''
              # 서비스가 시작될 때까지 대기
              sleep 10
              
              # 서비스 실행 상태 확인
              if docker ps --filter "name=web-search" --format "{{.Names}}" | grep -q "web-search"; then
                echo "✅ Web Search service is running"
                docker logs web-search --tail 10
              fi
              
              if docker ps --filter "name=mcp-api" --format "{{.Names}}" | grep -q "mcp-api"; then
                echo "✅ MCP API service is running"
                docker logs mcp-api --tail 10
              fi
              
              # API 엔드포인트 테스트 (선택적)
              if docker ps --filter "name=web-search" --format "{{.Names}}" | grep -q "web-search"; then
                echo "Testing Web Search endpoint..."
                curl -s -o /dev/null -w "%{http_code}" http://localhost:8100 || echo "Web Search not responding"
              fi
              
              if docker ps --filter "name=mcp-api" --format "{{.Names}}" | grep -q "mcp-api"; then
                echo "Testing MCP API endpoint..."
                curl -s -o /dev/null -w "%{http_code}" http://localhost:8050/api/status/ || echo "MCP API not responding"
              fi
            '''
          }
        }
      }
    }
  }

  post {
    always {
      // 환경 파일 및 임시 키파일 정리
      sh 'rm -f .env.prod gcp-key.json back/rag/gcp-key.json back/search/gcp-key.json'
    }
    success {
      echo '빌드 및 배포 성공 🎉'
    }
    failure {
      echo '빌드 또는 배포 실패 ❗'
      sh '''
        # 오류 시 MCP 및 Web Search 컨테이너 로그 수집
        docker logs web-search --tail 50 > web_search_error.log || true
        docker logs mcp-api --tail 50 > mcp_api_error.log || true
      '''
    }
  }
}
pipeline {
  agent any

  environment {
    COMPOSE_FILE    = 'docker-compose-prod.yml'
    ENV_FILE        = '.env.prod'
  }

  parameters {
    string(
      name: 'FORCE_SERVICES',
      defaultValue: '',
      description: '콤마(,)로 지정 시 해당 서비스만 빌드·배포 (예: gateway,auth,backend,rag,frontend)'
    )
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Create .env.prod') {
      steps {
        withCredentials([
          string(credentialsId: 'POSTGRES_AUTH_USER',      variable: 'AUTH_USER'),
          string(credentialsId: 'POSTGRES_AUTH_PASSWORD',  variable: 'AUTH_PW'),
          string(credentialsId: 'POSTGRES_AUTH_DB_NAME',   variable: 'AUTH_DB'),
          string(credentialsId: 'POSTGRES_SCHED_USER',     variable: 'SCHED_USER'),
          string(credentialsId: 'POSTGRES_SCHED_PASSWORD', variable: 'SCHED_PW'),
          string(credentialsId: 'POSTGRES_SCHED_DB_NAME',  variable: 'SCHED_DB'),
          string(credentialsId: 'PINECONE_API_KEY',         variable: 'PINECONE_API_KEY'),
          string(credentialsId: 'PINECONE_INDEX_NAME',      variable: 'PINECONE_INDEX_NAME'),
          file(  credentialsId: 'moca-457801-bfa12690864b.json', variable: 'GCP_KEY_FILE'),
          string(credentialsId: 'CLAUDE_API_KEY',           variable: 'CLAUDE_API_KEY'),
          string(credentialsId: 'OPENAI_API_KEY',           variable: 'OPENAI'),
          string(credentialsId: 'OPENAI_API_KEY_2',           variable: 'OPENAI2'),
          string(credentialsId: 'FIREBASE_CREDENTIALS_JSON_BASE64', variable: 'FIREBASE_CREDENTIALS_JSON_BASE64'),
          string(credentialsId: 'JWT_SECRET_KEY',           variable: 'JWT_SECRET_KEY'),
          string(credentialsId: 'KAKAO_CLIENT_ID',          variable: 'KAKAO_CLIENT_ID'),
          string(credentialsId: 'JWT_AT_VALIDITY',          variable: 'JWT_AT_VALIDITY'),
          string(credentialsId: 'JWT_RT_VALIDITY',          variable: 'JWT_RT_VALIDITY'),
          string(credentialsId: 'POSTGRES_RAG_USER',          variable: 'RAG_USER'),
          string(credentialsId: 'POSTGRES_RAG_PASSWORD',          variable: 'RAG_PW'),
          string(credentialsId: 'POSTGRES_RAG_DB_NAME',          variable: 'RAG_DB'),
          string(credentialsId: 'FRONTEND_API_URL',         variable: 'FRONTEND_API_URL')
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

ENV=prod
""".trim()
        }
      }
    }

    stage('Detect Changed Services') {
      steps {
        script {
          def diff = sh(
            script: "git diff --name-only ${env.GIT_PREVIOUS_SUCCESSFUL_COMMIT ?: 'HEAD~1'} ${env.GIT_COMMIT}",
            returnStdout: true
          ).trim()
          def changed = diff.split('\n')
                            .findAll{ it }
                            .findAll{ it.startsWith('back/') || it.startsWith('front/') }
                            .collect{ p -> 
                              if (p.startsWith('front/apk-fe/')) return 'frontend'
                              else if (p.startsWith('front/') && !p.startsWith('front/apk-fe/')) return p.tokenize('/')[1]
                              else return p.tokenize('/')[1]
                            }
                            .unique()
          def forced = params.FORCE_SERVICES?.trim() ? params.FORCE_SERVICES.split(',').collect{ it.trim() } : []
          env.CHANGED_SERVICES = (forced ?: changed).toSet().join(',')
          if (!env.CHANGED_SERVICES) {
            echo 'No service changes.'
            currentBuild.result = 'SUCCESS'
          } else {
            echo "Target services: ${env.CHANGED_SERVICES}"
          }
        }
      }
    }

    stage('Build & Deploy Backend') {
      when {
        expression { env.CHANGED_SERVICES.split(',').any { it != 'frontend' } }
      }
      steps {
        script {
          env.CHANGED_SERVICES.split(',').each { svc ->
            if (svc != 'frontend') {
              echo "▶ Building & deploying ${svc}"
              sh """
                docker rm -f ${svc}-service || true
                docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" build --no-cache ${svc}
                docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps --force-recreate ${svc}
              """
            }
          }
        }
      }
    }

    stage('Build & Deploy Frontend') {
      when {
        expression { env.CHANGED_SERVICES.split(',').contains('frontend') }
      }
      steps {
        echo "▶ Building & deploying frontend"
        sh """
          docker rm -f frontend-service || true
          docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" build --no-cache frontend
          docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --no-deps --force-recreate frontend
        """
      }
    }
  }

  post {
    always {
      sh 'rm -f .env.prod gcp-key.json back/rag/gcp-key.json back/search/gcp-key.json'
    }
    success {
      echo '빌드 및 배포 성공 🎉'
    }
    failure {
      echo '빌드 또는 배포 실패 ❗'
    }
  }
}
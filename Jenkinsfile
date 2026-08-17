pipeline {
    agent { label 'docker-builder' }

    options {
        disableConcurrentBuilds()
    }

    environment {
        REGISTRY = 'kregistry.siwko.org:5000'
        SERVER_IMAGE = "${REGISTRY}/probe-server"
        APP_IMAGE = "${REGISTRY}/probe-app"
        IMAGE_TAG = "${env.BUILD_NUMBER}"
    }

    stages {
        stage('Build Base OpenLiberty Server') {
            steps {
                sh "docker build --provenance=false --sbom=false --build-arg BUILD_NUMBER=${IMAGE_TAG} -f app-server/Dockerfile -t ${SERVER_IMAGE}:${IMAGE_TAG} -t ${SERVER_IMAGE}:latest app-server"
            }
        }
        stage('Build Probe Application') {
            steps {
                sh "docker build --provenance=false --sbom=false --build-arg BUILD_NUMBER=${IMAGE_TAG} -f app/Dockerfile -t ${APP_IMAGE}:${IMAGE_TAG} -t ${APP_IMAGE}:latest app"
            }
        }
        stage('Push Docker Images') {
            steps {
                sh "docker push ${SERVER_IMAGE}:${IMAGE_TAG}"
                sh "docker push ${SERVER_IMAGE}:latest"
                sh "docker push ${APP_IMAGE}:${IMAGE_TAG}"
                sh "docker push ${APP_IMAGE}:latest"
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                sh "kubectl apply -f k8s/probe-service.yaml"
                sh "kubectl apply -f k8s/probe-ingress.yaml"
                sh "kubectl apply -f k8s/probe-deployment.yaml"
                sh "kubectl set image deployment/probe-server probe-server=${SERVER_IMAGE}:${IMAGE_TAG} probe-app=${APP_IMAGE}:${IMAGE_TAG} -n default"
                sh "kubectl rollout status deployment/probe-server -n default --timeout=4m"
            }
        }
    }
}

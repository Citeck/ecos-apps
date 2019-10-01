timestamps {
  node {
    stage('Checkout SCM') {
      checkout([
        $class: 'GitSCM',
        branches: [[name: 'master']],
        doGenerateSubmoduleConfigurations: false,
        extensions: [],
        submoduleCfg: [],
        userRemoteConfigs: [[credentialsId: 'bc074014-bab1-4fb0-b5a4-4cfa9ded5e66',url: 'git@bitbucket.org:citeck/ecos-apps.git']]
      ])
    }
    def project_version = readMavenPom().getVersion().toLowerCase()
    stage('Build project artifacts') {
      withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
        sh "mvn clean package -DskipTests=true -Djib.docker.image.tag=${project_version} jib:dockerBuild"
      }
    }
    stage('Psuh docker image') {
      docker.withRegistry('http://127.0.0.1:8082', '7d800357-2193-4474-b768-5c27b97a1030') {
        def microserviceImage = "ecos-apps"+":"+"${project_version}"
        def current_microserviceImage = docker.image("${microserviceImage}")
        current_microserviceImage.push()
      }
    }
  }
}
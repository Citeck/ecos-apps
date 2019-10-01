timestamps {
  node {
    def project_version = readMavenPom().getVersion().toLowerCase()
    stage('Build project artifacts') {
      withMaven(mavenLocalRepo: '/opt/jenkins/.m2/repository', tempBinDir: '') {
        sh "mvn clean package -DskipTests=true -Djib.docker.image.tag=${project_version} jib:dockerBuild"
      }
    }
  }
}
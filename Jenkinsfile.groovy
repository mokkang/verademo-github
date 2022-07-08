pipeline {
	stages {
	  pipeline {
		agent any
		environment {
			VERACODE_APP_NAME = 'sca-verademo'      // App Name in the Veracode Platform
		}
	
		// this is optional on Linux, if jenkins does not have access to your locally installed docker
		//tools {
			// these match up with 'Manage Jenkins -> Global Tool Config'
			//'org.jenkinsci.plugins.docker.commons.tools.DockerTool' 'docker-latest' 
		//}
	
		options {
			// only keep the last x build logs and artifacts (for space saving)
			buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '20'))
		}
	}
		stages{
	
			stage ('build') {
				steps {
					withMaven(maven:'latest') {
						script {
							dir('app') {
								sh 'mvn clean package'
							}
						}
					}
				}
			}
	
			stage ('Veracode scan') {
				steps {
					echo 'Veracode scanning'
					withCredentials([ usernamePassword (credentialsId: 'veracode_login', usernameVariable: 'VERACODE_API_ID', passwordVariable: 'VERACODE_API_KEY') ]) {
							// fire-and-forget 
							veracode applicationName: "${VERACODE_APP_NAME}", uploadIncludesPattern: "app/target/verademo.war", scanName: "${BUILD_TAG}-${env.HOST_OS}", vid: "${VERACODE_API_ID}", vkey: "${VERACODE_API_KEY}"
	
							// wait for scan to complete (timeout: x)
							//veracode applicationName: '${VERACODE_APP_NAME}'', criticality: 'VeryHigh', debug: true, timeout: 20, fileNamePattern: '', pHost: '', pPassword: '', pUser: '', replacementPattern: '', sandboxName: '', scanExcludesPattern: '', scanIncludesPattern: '', scanName: "${BUILD_TAG}", uploadExcludesPattern: '', uploadIncludesPattern: 'target/verademo.war', vid: '${VERACODE_API_ID}', vkey: '${VERACODE_API_KEY}'
						}      
				}
			}
	
			stage ('Veracode SCA') {
				steps {
					echo 'Veracode SCA'
					withCredentials([ string(credentialsId: 'SCA_Token, variable: 'SRCCLR_API_TOKEN')]) {
						withMaven(maven:'maven-3') {
							script: {
								sh '''
									curl -sSL https://download.sourceclear.com/ci.sh | bash -s scan ./app --update-advisor --allow-dirty
							}
						
						}                
					}            
				}
			}	
        stage ('Veracode pipeline scan') {
            steps {
                echo 'Veracode Pipeline scanning'
                withCredentials([ usernamePassword ( 
                    credentialsId: 'veracode_login', usernameVariable: 'VERACODE_API_ID', passwordVariable: 'VERACODE_API_KEY') ]) {
                        script {

                            // this try-catch block will show the flaws in the Jenkins log, and yet not
                            // fail the build due to any flaws reported in the pipeline scan
                            // alternately, you could add --fail_on_severity '', but that would not show the
                            // flaws in the Jenkins log

                            // issue_details true: add flaw details to the results.json file
                            try {
                                if(isUnix() == true) {
                                    sh """
                                        curl -sO https://downloads.veracode.com/securityscan/pipeline-scan-LATEST.zip
                                        unzip pipeline-scan-LATEST.zip pipeline-scan.jar
                                        java -jar veracode_scanner/pipeline-scan.jar --veracode_api_id $VERACODE_API_ID --veracode_api_key $VERACODE_API_KEY --file target/verademo.war --issue_details true -bf baseline_results.json
                                        """
                                }
                                else {
                                    powershell """
                                            curl  https://downloads.veracode.com/securityscan/pipeline-scan-LATEST.zip -o pipeline-scan.zip
                                            Expand-Archive -Force -Path pipeline-scan.zip -DestinationPath veracode_scanner 
                                            java -jar pipeline-scan.jar --veracode_api_id $VERACODE_API_ID --veracode_api_key $VERACODE_API_KEY --file target/verademo.war --issue_details true -bf powershell-baseline.json
                                            """
                                }
                            } catch (err) {
                                echo 'Pipeline err: ' + err
                            }
                        }    
                    } 
				}
			   }   echo "Pipeline scan done (failures ignored, results avialable in $WORKSPACE/results.json)"
			}
		}

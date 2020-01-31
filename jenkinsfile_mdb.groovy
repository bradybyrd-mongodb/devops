import groovy.json.*
import java.io.File
import java.text.SimpleDateFormat

// #-------------------------------------------------------#
// Atlas Devops Pipeline
// #-------------------------------------------------------#
// Set this variable to force it to pick the pipeline
// If it is set to job, then it will match the name of your jenkins job to the key in the branch map in the local_settings.json file.
def landscape = "job"
//def base_path = new File(getClass().protectionDomain.codeSource.location.path).parent
// Set this variable to point to the folder where your local_settings.json folder
def base_path = "/mnt/devops"
// Change this if you want to point to a different local settings file
def settings_file = "mdb_config.json"
// #------------------- Change anything below at your own risk -------------------#
// #---- (but please take the time to understand what is going on in the code here ;-> ) ---#

sep = "/" //FIXME Reset for windows if necessary

rootJobName = "$env.JOB_NAME";
if(landscape == "job"){ landscape = rootJobName.toLowerCase() }
//FIXME branchName = rootJobName.replaceFirst('.*/.*/','')
branchName = "master"
branchVersion = ""
// Outboard Local Settings
if(landscape == "job"){ landscape = rootJobName.toLowerCase() }
def config = [:]
// Settings
def git_message = ""
// message looks like this "Adding new tables [Version: V2.3.4] "
def reg = ~/.*\[Version: (.*)\].*/
def approver = ""
def cur_node = ""
def result = ""
def buildNumber = "$env.BUILD_NUMBER"

// Add a properties for Platform and Skip_Packaging
properties([
	parameters([
		//choice(name: 'Landscape', description: "Develop/Release to specify deployment target", choices: 'MP_Dev\nMP_Dev2,MP_Release'),
		choice(name: 'AtlasAction', description: "Choose Atlas action", choices: 'atlas_org_info\natlas_cluster_info\natlas_user_add\natlas_cluster_add\nconfig_test'),
    string(name: 'TemplateFile', description: "Enter path to json settings file (relative to repository root)", defaultValue: "templates/user_add.json"),
		string(name: 'RestParameters', description: "Enter overrides to json settings file (username=brady collection=movies)", defaultValue: "username=brady role=readAnyDatabase")
	])
])

config = get_settings("${base_path}/${settings_file}")
staging_path = config["staging_path"]
base_url = config["base_url"]
project_id = config["project_id"]
api_public_key = "" //config["api_public_key"]
api_private_key = "" //config["api_private_key"]
//user_add = config["templates"]["user_add"]
//cluster_add = config["templates"]["cluster_dev"]
echo "Working with: ${rootJobName}"

// note json is not serializable so unset the variable
config = null

/*
#-----------------------------------------------#
#  Stages
*/
stage('GitParams') {
  node (cur_node) {
    echo '#---------------------- Summary ----------------------#'
    echo "#  Validating Git Commit"
    echo "#------------------------------------------------------#"
    echo "# Update git repo..."
    echo "# Reset local path - original:"
    sh "echo %PATH%"
        echo "# Read latest commit..."
        sh "git --version"
        git_message = sh(
          script: "cd ${base_path} && git log -1 HEAD --pretty=format:%s",
          returnStdout: true
        ).trim()

    //git_message = "This is git message. [VERSION: 2.5.0]"
    echo "# From Git: ${git_message}"
  }
}

if(false){ //(git_message.length() == result.length()){
	echo "No VERSION found\nResult: ${result}\nGit: ${git_message}"
	currentBuild.result = "UNSTABLE"
	return
}

stage("Atlas Build") {
  node (cur_node) {
    echo "#------------------- Sending Atlas Command ${env.AtlasAction} ---------#"

    switch (env.AtlasAction){
      case "config_test":
        config_test()
        break
      case "atlas_org_info":
        atlas_org_info()
        break
      case "atlas_cluster_info":
        atlas_cluster_info()
        break
      case "atlas_user_add":
        atlas_user_add()
        break
      case "atlas_cluster_add":
        atlas_cluster_add()
        break
      default:
        not_found()
        break
        //bat "${staging_path}${sep}${item.trim()}*.sql\" \"${staging_dir}${sep}${version}\""
    }
  }
}

def not_found(){
    echo "${env.AtlasAction} action not found"
    currentBuild.result = "UNSTABLE"
}

//@NonCPS
def curl_get(url){
	def curl = ""
	withCredentials([usernameColonPassword(credentialsId: 'SA-NE', variable: 'SAcred')]) {
		curl = "curl -X GET -u \"${SAcred}\" --digest -i \"${url}\""
	}
	def result = shell_execute(curl)
  display_result(curl, result)
  def json = [:] //json_resolver(result)
  return json
}

def curl_post(url, file_path){
	def curl = ""
	withCredentials([usernameColonPassword(credentialsId: 'SA-NE', variable: 'SAcred')]) {
		curl = "curl -i -u \"${SAcred}\" --digest -H \"Content-Type: application/json\" -X POST \"${url}\" --data @${file_path}"
  }
	def result = shell_execute(curl)
  display_result(curl, result)
  def json = [:] //json_resolver(result)
  return json
}

def json_resolver(result){
  def jsonSlurper = new JsonSlurper()
  def blank = false
  def looksLikeJson = false
  def jsonstr = ""
	def cnt = 0
  result["stdout"].eachLine{ line ->
    println "|> ${line}"
    if( line == "\n" ) {
      blank = true
    }
    if( blank && looksLikeJson ) {
      looksLikeJson = false
    }
    if( !blank && looksLikeJson ) {
      looksLikeJson = true
      jsonstr += line
    }

    if( !blank && line.toString().startsWith("{" )) {
      blank = false
      looksLikeJson = true
      jsonstr += line
    }
		cnt += 1
  }
	if(jsonstr == ""){jsonstr = "{\"empty\" : true}"}
  //println "Deduced JSON:"
  //println jsonstr
  return(jsonSlurper.parseText(jsonstr))
}

def atlas_org_info(){
    def url = base_url + "?pretty=true"
    def result = curl_get(url)
}

def atlas_cluster_info(){
    def url = base_url + "/groups/${project_id}/clusters?pretty=true"
    def result = curl_get(url)
}

def atlas_user_add(){
    def obj = [:]
		def args = parse_args(env.RestParameters)
		obj["roles.0.roleName"] = args["role"]
		obj["username"] = args["username"]
		obj["password"] = args["password"]
		new_file = build_input_json(env.TemplateFile, obj)
		def url = base_url + "/groups/${project_id}/databaseUsers?pretty=true"
    def result = curl_post(url, new_file)
}

@NonCPS
def build_input_json(file_path, updaters = [:]) {
	def fname = "output_${new Date().format( 'yyyyMMddss' )}.json"
	def new_file = staging_path + sep + "results" + sep + fname
  def jsonSlurper = new JsonSlurper()
	def settings = [:]
	println "Input Template Document: ${staging_path + sep + file_path}"
	def json_file_obj = new File( staging_path + sep + file_path )
	if (json_file_obj.exists() ) {
	  settings = jsonSlurper.parseText(json_file_obj.text)
	}
	for k in updaters{
		jsn_path = k.split("\\.")
		switch (jsn_path.size()){
			case 1:
				settings[jsn_path[0]] = updaters[k]
			case 2:
				settings[jsn_path[0]][jsn_path[1]] = updaters[k]
			case 3:
				settings[jsn_path[0]][jsn_path[1]][jsn_path[2]] = updaters[k]
			case 4:
				settings[jsn_path[0]][jsn_path[1]][jsn_path[2]][jsn_path[3]] = updaters[k]
		}
	}
	def hnd = new File(new_file)
  def json_str = JsonOutput.toJson(settings)
  def json_beauty = JsonOutput.prettyPrint(json_str)
  hnd.write(json_beauty)
	return new_file
}

def config_test(){
    println "Config is OK"
}

def shell_execute(cmd, path = "none"){
  def pth = ""
  def command = sep == "/" ? ["/bin/bash", "-c"] : ["cmd", "/c"]
  if(path != "none") {
	pth = "cd ${path} && "
	command << pth
  }
	command << cmd
  def sout = new StringBuffer(), serr = new StringBuffer()
  //println "Running: ${command}"
	def proc = command.execute()
	proc.consumeProcessOutput(sout, serr)
	proc.waitForOrKill(1000)
  def outtxt = ["cmd" : cmd, "stdout" : sout, "stderr" : serr]
  return outtxt
}

def display_result(command, result){
	echo "#------------------------------------------------------#"
	echo "#------------------- Results --------------------------#"
	echo "cmd> ${command}"
	echo "#------------------- STDOUT ---------------------------#"
	echo result["stdout"].toString()
	echo "#------------------- STDERR ---------------------------#"
	echo result["stderr"].toString()
}

def ensure_dir(pth){
  folder = new File(pth)
  if ( !folder.exists() ){
    if( folder.mkdirs()){
        return true
    }else{
        return false
    }
  }else{
    return true
  }
}

def get_settings(file_path, project = "none") {
	def jsonSlurper = new JsonSlurper()
	def settings = [:]
	println "JSON Settings Document: ${file_path}"
	def json_file_obj = new File( file_path )
	if (json_file_obj.exists() ) {
	  settings = jsonSlurper.parseText(json_file_obj.text)
	}
	return settings
}

def parse_args(args){
	res = [:]
	items = args.split("\\s")
	for (arg in items) {
	  //logit arg
	  pair = arg.split("=")
	  if(pair.size() == 2) {
	    res[pair[0].trim()] = pair[1].trim()
	    //println "  ${pair[0]} - ${pair[1]}"
	  }else{
	    res[arg] = ""
	    //println "  ${arg}"
	  }
	}
	return res
}

def message_box(msg, def mtype = "sep") {
  def tot = 80
  def start = ""
  def res = ""
  msg = (msg.size() > 65) ? msg[0..64] : msg
  def ilen = tot - msg.size()
  if (mtype == "sep"){
    start = "#${"-" * (ilen/2).toInteger()} ${msg} "
    res = "${start}${"-" * (tot - start.size() + 1)}#"
  }else{
    res = "#${"-" * tot}#\n"
    start = "#${" " * (ilen/2).toInteger()} ${msg} "
    res += "${start}${" " * (tot - start.size() + 1)}#\n"
    res += "#${"-" * tot}#\n"
  }
  //println res
  return res
}

def separator( def ilength = 82){
  def dashy = "-" * (ilength - 2)
  //println "#${dashy}#"
}

//#---------------- GROOVY TESTER ---------------------------#
import groovy.json.*
import org.yaml.snakeyaml.*
import java.io.File
import java.text.SimpleDateFormat

sep = "/" //FIXME Reset for unix
base_path = new File(getClass().protectionDomain.codeSource.location.path).parent
arg_map = [:]
config = [:]
enforce_args = false
log_file = "${base_path}${sep}dbm_log.txt"
silent_log = false
config_file = "mdb_config.json"
jsonSlurper = new JsonSlurper()
staging_path = "/Users/brady.byrd/Documents/mongodb/dev/devops"

println "Arguments:"
for (arg in this.args) {
  //logit arg
  pair = arg.split("=")
  if(pair.size() == 2) {
    arg_map[pair[0].trim()] = pair[1].trim()
    println "  ${pair[0]} - ${pair[1]}"
  }else{
    arg_map[arg] = ""
    println "  ${arg}"
  }
}

def rest_get(url_frag, verbose = true) {
  separator()
  result = [:]
  //url = "${urlString}/${url_frag}".toURL()
  url = "${urlString}".toURL()
  result["url"] = url
  con = (HttpURLConnection) url.openConnection();
  String userpass = api_public_key + ":" + api_private_key;
  def encoded = userpass.bytes.encodeBase64().toString()
  String basicAuth = "Basic " + encoded;
  con.setRequestProperty ("Authorization", basicAuth);
  con.setUseCaches(true)
  con.setDoOutput(true)
  //con.setDoInput(true)
  con.setRequestProperty("Content-Type", "application/json")
  result["result"] = con.getResponseCode()
  result["response"] = con.getInputStream().getText()
  return result
}

def rest_post(url_frag, params, set_cookie = false) {
  separator()
  result = [:]
  url = "${urlString}/${url_frag}".toURL()
  result["url"] = url
  con = (HttpURLConnection) url.openConnection()
  if(!set_cookie){
  for (cookie in cookies){
  con.addRequestProperty("Cookie", cookie.split(";", 2)[0])
  }
  }
  con.setUseCaches(true)
  con.setDoOutput(true)
  con.setDoInput(true)
  con.setRequestProperty("Content-Type", "application/json")
  con.outputStream.withWriter { writer ->
    writer << params
  }
  if(set_cookie){
  cookies = con.getHeaderFields().get("Set-Cookie")
  }
  result["result"] = con.getResponseCode()
  result["response"] = con.getInputStream().getText()
  return result
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
  def outtxt = ["stdout" : sout, "stderr" : serr]
  return outtxt
}

def display_result(command, result){
  println "#------------------------------------------------------#"
  println "Running: ${command}"
  println "out> " + result["stdout"]
  println "err> " + result["stderr"]
}

def message_box(msg, def mtype = "sep") {
  def tot = 100
  def start = ""
  def res = ""
  msg = (msg.size() > 85) ? msg[0..84] : msg
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
  println res
  return res
}

def separator( def ilength = 82){
  def dashy = "-" * (ilength - 2)
  println "#${dashy}#"
}

def curl_get(url){
  def curl = "curl -X GET -u \"${api_public_key}:${api_private_key}\" --digest -i \"${url}\""
  //curl -X GET -u "yclukopd:b8c4f8ee-fada-4edb-8195-00f521974f79" --digest -i "https://cloud.mongodb.com/api/atlas/v1.0"
  result = shell_execute(curl)
  display_result(curl, result)
  def json = json_resolver(result)
  return json
}

def curl_post(url, details = [:]){
  def curl = "curl -i -u \"${api_public_key}:${api_private_key}\" --digest -H \"Content-Type: application/json\" -X POST \"${url}\" --data @json_out.txt"
  result = shell_execute(curl)
  display_result(curl, result)
  def json = json_resolver(result)
  return json
}

def json_resolver(result){
  def jsonSlurper = new JsonSlurper()
  def blank = false
  def looksLikeJson = false
  def jsonstr = ""
  result["stdout"].eachLine{ line ->
    //println "|> ${line}"
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
  }
  //println "Deduced JSON:"
  //println jsonstr
  return(jsonSlurper.parseText(jsonstr))
}

def print_rest(result){
  println "Rest Result:"
  result.each{ k,v ->
      if(k == "results"){
        println "results => "
        v.each{ item ->
          println "#----------------------------------------------------------------#"
          item.each{ i,j ->
            println "${i} => ${j}"
          }
        }
      }else{
        println "${k} => ${v}"
      }

    }
}

def atlas_org_info(){
    def url = base_url
    result = curl_get(url)
    print_rest(result)
}

def atlas_cluster_info(){
    def url = base_url + "/groups/${project_id}/clusters"
    result = curl_get(url)
    print_rest(result)
}

def atlas_user_add(){
    def obj = [
      "database_name" : "admin",
      "roles" : [
        ["databaseName" : "admin", "roleName" : "MyNewRole"]
      ],
      "username" : "MyNewUser",
      "password" : "doublesecret"
    ]
    def url = base_url + "/groups/${project_id}/databaseUsers?pretty=true"
    result = curl_post(url)
    print_rest(result)
}

def yaml_test(){
    base_path = "/Users/brady.byrd/Documents/mongodb/dev/DevOps"
    yaml_file = "deployment_template.yaml"
    def yaml_file_obj = new File( base_path, yaml_file )
    Yaml yaml = new Yaml()
    Map map = yaml.load(yaml_file_obj.text)
    println(map["region"])
}

def config_test(){
    logit "Config is OK"
    //logit "tst: ${config["templates"]["cluster_dev"]["name"]}"
    logit "date: ${new Date().format( 'yyyyMMddss' )}"
    poo = "bugsy.0.butt.3"
    def updaters = ["roles.0.roleName" : "admin", "username" : "brady", "password" : "bugsyb"]
    def fpath = "templates/user_add.json"
    //def res = build_input_json(fpath,updaters)
    def res = git_trigger()
    read_instructions(res)
}

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
  def json_str = JsonOutput.toJson(settings)
  def json_beauty = JsonOutput.prettyPrint(json_str)
  println "Settings\n${json_beauty}"
  for(k in updaters){
    jsn_path = k.key.split("\\.")
    jsn_path.eachWithIndex{ j,idx -> if( j.isInteger()){ jsn_path[idx] = j.toInteger() } }
    //println("jsnpath: ${jsn_path}, size: ${jsn_path.size()}")
    switch (jsn_path.size()){
    case 1:
    settings[jsn_path[0]] = k.value
      break
    case 2:
    settings[jsn_path[0]][jsn_path[1]] = k.value
      break
    case 3:
    settings[jsn_path[0]][0][jsn_path[2]] = k.value
      break
    case 4:
      settings[jsn_path[0]][jsn_path[1]][jsn_path[2]][jsn_path[3]] = k.value
      break
    }
  }
  def hnd = new File(new_file)
  json_str = JsonOutput.toJson(settings)
  json_beauty = JsonOutput.prettyPrint(json_str)
  hnd.write(json_beauty)
  return new_file
}

def stringify_json(json_obj, subval = false){
  seed = [:]
  json_obj.each{ k, v ->
    if( v instanceof Map){
      seed[k] = stringify_json(seed, true)
    }else if( v == null ){
      cname = "null"
    }else{
      cname = "var"
    }
    logit "${k} => ${v} => ${cname}"
    seed[k] = v
  }
  if( !subval ){
    logit "Final Result"
    logit seed.toMapString(80)
  }
  return seed
}

def init_log(){
  logit("#------------- New Run ---------------#")
  //logit("# ARGS:")
  //logit(arg_map.toString())
}

def logit(String message, log_type = "INFO", display_only = true){
  def sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss")
  def cur_date = new Date()
  if(!display_only){
  def hnd = new File(log_file)
  if( !hnd.exists() ){
  hnd.createNewFile()
  }
  }
  def stamp = "${sdf.format(cur_date)}|${log_type}> "
  message.eachLine { line->
  if(!silent_log){
  println "${stamp}${line.trim()}"
  }
  if(!display_only){
  hnd.append("\r\n${stamp}${line}")
  }
  }
}

def get_settings(file_path, project = "none") {
  def jsonSlurper = new JsonSlurper()
  def settings = [:]
  println "JSON Settings Document: ${file_path}"
  def json_file_obj = new File( file_path.toString() )
  if (json_file_obj.exists() ) {
    settings = jsonSlurper.parseText(json_file_obj.text)
  }
  return settings
}

def git_trigger() {
  def reg = ~/.*\[DBCR: (.*)\].*/
  def cmd = "git log -1 HEAD" // --pretty=format:%s"
  def res = shell_execute(cmd)
  def raw = ""
  def commit = "dont know yet"
  def target_path = ""
  message_box("Git Trigger")
  println "# Commit: ${res["stdout"]}"
  raw = res["stdout"].toString().trim()
  if(!raw.contains("#DBDEPLOY")){
    println "Commit did not contain #DBDEPLOY"
    System.exit(0)
  }
  def lines = raw.split("\n")
  //println "Git lines: ${lines}"
  lines.each{
    //println "Working: ${it}"
    if(it.startsWith("commit ")){
      commit = it.replaceAll("commit ","").trim()
    }
  }
  println "# Revision: ${commit}"
  //Pick new files in commit
  // git diff-tree --no-commit-id --name-only -r 32b0f0dd6e4bd810f3edc4bcd8a114f8f98a65ea
  cmd = "git diff-tree --no-commit-id --name-only -r ${commit}"
  res = shell_execute(cmd)
  display_result(cmd,res)
  raw = res["stdout"].toString().trim()

  def files = raw.split("\n")
  println "Git new files: ${files}"
  def cur_version = "2.3_fakeversion"
  def copy_files = []
  files.each{
    fil = new File("${staging_path}${sep}${it}")
    if(fil.getName().endsWith(".json")) {
      println "Match - ${fil.getName()}"
      copy_files << fil
    }
  }
  if(copy_files.size() > 0){
    copy_files.each{
      target_path = it
      println "Target Instructions: ${target_path}"
    }
  }else{
    println "#-------- No files for deployment - must have .json extension ----------#"
    System.exit(0)
  }
  return target_path
}

def read_instructions(instructions_json){
  def instructions = get_settings(instructions_json)
  def json_str = JsonOutput.toJson(instructions)
  def json_beauty = JsonOutput.prettyPrint(json_str)
  println "New Instructions: ${instructions_json}"
  println "Settings\n${json_beauty}"
  message_box(instructions["title"], "title")
  instructions["steps"].each{ step,action ->

  }

}

def process_action(instructions = [:]){

  if (arg_map.containsKey("action")) {
    switch (arg_map["action"].toLowerCase()) {
      case "atlas_clusters":
        atlas_cluster_info()
        break
      case "atlas_org_info":
        atlas_org_info()
        break
      case "atlas_create_cluster":
        atlas_create_cluster()
        break
      case "config_test":
        config_test()
        break
      default:
        println "Action does not exist"
      System.exit(1)
        break
    }
  }else{
    if(enforce_args){
       println "Error: specify action=<action> as argument"
       System.exit(1)
    }else{
      println "No args given"
    }
  }
}

/*
#---------------------------------------------------------#
#                         MAIN                            #
#---------------------------------------------------------#
*/
message_box("REST to ATLAS", "title")

// Open Templates and config
logit "JSON Config Document: ${base_path}${sep}${config_file}"
json_file_obj = new File( base_path, config_file )
if (json_file_obj.exists() ) {
  config = jsonSlurper.parseText(json_file_obj.text)
}else{
    logit "Cannot find config file"
}
logit "... done"

base_url = config["base_url"] //"https://cloud.mongodb.com/api/atlas/v1.0"
cookies = []
project_id = config["project_id"] //"5d4d7ed3f2a30b18f4f88946"
api_public_key = config["api_public_key"] //"yclukopd"
api_private_key = config["api_private_key"] //"b8c4f8ee-fada-4edb-8195-00f521974f79"
staging_path = "/Users/brady.byrd/Documents/mongodb/dev/DevOps" // config["staging_path"]
process_action()

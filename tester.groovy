import groovy.json.*
import java.io.File
import java.text.SimpleDateFormat

cur_node = ""

properties([
	parameters([
    string(name: 'RoleJson', description: "Enter json e.g. [{\"databaseName\" : \"admin\", \"roleName\" : \"MyNewRole\"}]", defaultValue: "[{\"databaseName\" : \"admin\", \"roleName\" : \"MyNewRole\"}]"),
    string(name: 'Username', description: "New database user name", defaultValue: 'my_new_user'),
    string(name: 'Password', description: "New password", defaultValue: 'not_secure')
	])
])


stage('Testing') {
  node (cur_node) {
    echo '#---------------------- Tester ----------------------#'
    echo "# text to json:"
    roleraw = input(
      id: 'userInput', message: 'Enter role definition', parameters: [
        [$class: 'StringParameterDefinition',
          name: "paramname",
          defaultValue: "mydefault",
          description: "put something here for roles"
        ]
      ]
      )
    def user_add = [
      "database_name" : "admin",
      "roles" : [["role" : "boop", "database" : "admin"]],
      "username" : env.Username,
      "password" : env.Password
    ]
    //def user_add2 = process_roles()
    echo "# Input: ${roleraw}"
    echo "# Translated: ${user_add["username"]}\n${user_add2}"
  }
}

@NonCPS
def process_roles(){
  def jsonSlurper = new JsonSlurper()
  def roles = jsonSlurper.parseText(env.RoleJson)
  def roleBlock = []
  roles.each{ role ->
    println "Role: ${role}"
    roleBlock << role
  }
  def user_add = [
    "database_name" : "admin",
    "roles" : roleBlock,
    "username" : env.Username,
    "password" : env.Password
  ]
  println "UserAdd: ${user_add}"
  roles = null
  jsonSlurper = null
  return(user_add)
}

def get_input_json(file_path) {
	def jsonSlurper = new JsonSlurper()
	def settings = [:]
	println "Input Settings Document: ${file_path}"
	def json_file_obj = new File( file_path )
	if (json_file_obj.exists() ) {
	  settings = jsonSlurper.parseText(json_file_obj.text)
	}
	return settings
}

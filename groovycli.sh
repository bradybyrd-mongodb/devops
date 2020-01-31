echo "#----------------------------#"
echo "# Groovy Processor           #"
echo "#----------------------------#"
#java -cp ".:/home/ec2-user/devops/groovy-all-2.4.12.jar" groovy.ui.GroovyMain /home/ec2-user/devops/mdb_test.groovy $@
java -cp "./lib/*" groovy.ui.GroovyMain /Users/brady.byrd/Documents/mongodb/dev/DevOps/mdb_test.groovy $@

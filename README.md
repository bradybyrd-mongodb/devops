### Jenkins Devops ###
Atlas provisioning routines

__Methodology__
The code here is built around Jenkins groovy pipeline operation.  Everything is orchestrated around the jenkinsfile_mdb.groovy file.  The job is meant to be a generic 10 step pipeline.  For each different deployment create a deploy folder in the instructions folder.  The macro or DSL logic is in the instructions.json.  The groovy script will read in the instructions file, then conduct each of the 'actions'.  Right now, there are four actions built:
- atlas_org_info : reports back the basic organization info
- atlas_cluster_info : reports details on all the existing clusters in a project
- atlas_user_add : adds a new user to the project
- atlas_cluster_add : creates a new atlas cluster

For each action there may be an accompanying json template to provide further details to the rest call.

Put your atlas account information in the mdb_config.json file.

__Operation__
#### Two modes of operation:
  Interactive: enter arguments right in the job
    __OR__
  Hands-Free: checkin an instructions.json file

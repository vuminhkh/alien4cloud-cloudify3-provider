import sys
from cloudify_rest_client import CloudifyClient
from influxdb.influxdb08 import InfluxDBClient
from influxdb.influxdb08.client import InfluxDBClientError

import json
from os import utime
from os import getpid
from os import path
import time
import datetime

# check against influxdb which nodes are available CPUtotal
# change status of missing nodes comparing to the node_instances that are taken from cloudify
# do it only for compute nodes


def check_liveness(nodes_to_monitor,depl_id):
  c = CloudifyClient('localhost')
  c_influx = InfluxDBClient(host='localhost', port=8086, database='cloudify')
  print ('in check_liveness\n')
  print ('nodes_to_monitor: {0}\n'.format(nodes_to_monitor))
  c = CloudifyClient('localhost')

  # compare influx data (monitoring) to cloudify desired state

  for node_name in nodes_to_monitor:
      instances=c.node_instances.list(depl_id,node_name)
      for instance in instances:
          q_string='SELECT MEAN(value) FROM /' + depl_id + '\.' + node_name + '\.' + instance.id + '\.cpu_total_system/ GROUP BY time(10s) '\
                   'WHERE  time > now() - 40s'
          print ('query string is {0}\n'.format(q_string))
          try:
             result=c_influx.query(q_string)
             print ('result is {0} \n'.format(result))
             if not result:
               executions=c.executions.list(depl_id)
               has_pending_execution = False
               if executions and len(executions)>0:
                 for execution in executions:
                #    print("Execution {0} : {1}".format(execution.id, execution.status))
                   if execution.status not in execution.END_STATES:
                     has_pending_execution = True

               if not has_pending_execution:
                 print ('Setting state to error for instance {0} and its children'.format(instance.id))
                 update_nodes_tree_state(c, depl_id, instance, 'error')
                 params = {'node_instance_id': instance.id}
                 print ('Calling Auto-healing workflow for container instance {0}'.format(instance.id))
                 c.executions.start(depl_id, 'a4c_heal', params)
               else:
                 print ('pendding executions on the deployment...waiting for their end before calling heal workfllow...')
          except InfluxDBClientError as ee:
             print ('DBClienterror {0}\n'.format(str(ee)))
             print ('instance id is {0}\n'.format(instance))
          except Exception as e:
             print (str(e))


def update_nodes_tree_state(client,depl_id,instance,state):
  print ('updating instance {0} state to {1}'.format(instance.id, state))
  client.node_instances.update(instance.id, state)
  dep_inst_list = client.node_instances.list(depl_id)
  for inst in dep_inst_list:
    try:
      if inst.relationships:
        for relationship in inst.relationships:
          target = relationship['target_name']
          type = relationship['type']
          if ('contained_in' in str(type)) and (target == instance.node_id):
            update_nodes_tree_state(client,depl_id,inst,state)
    except Exception as e:
      print(str(e))



def main(argv):
    for i in range(len(argv)):
       print ("argv={0}\n".format(argv[i]))
    depl_id=argv[2]
    monitoring_dir=argv[3]
    of = open(monitoring_dir+'/pid_file', 'w')
    of.write('%i' % getpid())
    of.close()

    nodes_to_monitor=json.loads(argv[1].replace("'", '"'))
    check_liveness(nodes_to_monitor, depl_id)

if __name__ == '__main__':
    main(sys.argv)

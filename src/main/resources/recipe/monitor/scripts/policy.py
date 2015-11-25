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
  f=open('/root/logfile_'+str(depl_id),'w')
  f.write('in check_liveness\n')
  f.write('nodes_to_monitor: {0}\n'.format(nodes_to_monitor))
  c = CloudifyClient('localhost')

  # compare influx data (monitoring) to cloudify desired state

  for node_name in nodes_to_monitor:
      instances=c.node_instances.list(depl_id,node_name)
      for instance in instances:
          q_string='SELECT MEAN(value) FROM /' + depl_id + '\.' + node_name + '\.' + instance.id + '\.cpu_total_system/ GROUP BY time(10s) '\
                   'WHERE  time > now() - 40s'
          f.write('query string is{0}\n'.format(q_string))
          try:
             result=c_influx.query(q_string)
             f.write('result is {0} \n'.format(result))
             if not result:
               c.node_instances.update(instance.id,'deleted')
               find_contained_nodes(c,depl_id,instance)
          except InfluxDBClientError as ee:
             f.write('DBClienterror {0}\n'.format(str(ee)))
             f.write('instance id is {0}\n'.format(instance))
          except Exception as e:
             f.write(str(e))


def find_contained_nodes(client,depl_id,instance):

  dep_inst_list = client.node_instances.list(depl_id)
  for inst in dep_inst_list:
    try:
      if inst.relationships:
        for relationship in inst.relationships:
          target = relationship['target_name']
          type = relationship['type']
          if ('contained_in' in str(type)) and (target == instance.node_id):
            client.node_instances.update(inst.id,'deleted')
            find_contained_nodes(client,depl_id,inst)
    except Exception as e:
      print(str(e))



def main(argv):
    of = open('/root/pid_file', 'w')
    of.write('%i' % getpid())
    of.close()

    for i in range(len(argv)):
       print ("argv={0}\n".format(argv[i]))
    nodes_to_monitor=json.loads(argv[1].replace("'", '"'))
    depl_id=argv[2]
    check_liveness(nodes_to_monitor, depl_id)

if __name__ == '__main__':
    main(sys.argv)

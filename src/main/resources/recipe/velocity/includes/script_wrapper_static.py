from cloudify import ctx
from cloudify.exceptions import NonRecoverableError
from cloudify.state import ctx_parameters as inputs
import subprocess
import os
import re
import sys
import time
import threading
from StringIO import StringIO
from cloudify_rest_client import CloudifyClient
from cloudify import utils

client = CloudifyClient(utils.get_manager_ip(), utils.get_manager_rest_service_port())

def get_instance_list(node_id):
    result = ''
    all_node_instances = client.node_instances.list(ctx.deployment.id, node_id)
    for node_instance in all_node_instances:
        if len(result) > 0:
            result += ','
        result += node_instance.id
    return result

def get_instance_data(entity, data_name, get_data_function):
    data = get_data_function(entity, data_name)
    if data is not None:
        ctx.logger.info(
            'Found the property/attribute {0} with value {1} on the node {2}'.format(data_name, data, entity.node.id))
        return data
    elif entity.instance.relationships:
        for relationship in entity.instance.relationships:
            if 'cloudify.relationships.contained_in' in relationship.type_hierarchy:
                ctx.logger.info(
                    'Attribute/Property not found {0} go up to the parent node {1} by following relationship {2}'.format(data_name,
                                                                                                                         relationship.target.node.id,
                                                                                                                         relationship.type))
                return get_instance_data(relationship.target, data_name, get_data_function)
        return ""
    else:
        return ""

def get_other_instances_data(entity, data_name, get_data_function):
    data = get_data_function(entity, data_name)
    if data is not None:
        result_map = {}
        # get all instances data using cfy rest client
        all_node_instances = client.node_instances.list(ctx.deployment.id, entity.node.id)
        for node_instance in all_node_instances:
            if node_instance.id != entity.instance.id:
                prop_value = node_instance.runtime_properties.get(data_name, None)
                ctx.logger.info('Found the property/attribute {0} with value {1} on the node {2} instance {3}'.format(data_name, prop_value, entity.node.id, entity.instance.id))
                result_map[entity.instance.id + '_'] = prop_value
        return result_map
    elif entity.instance.relationships:
        for relationship in entity.instance.relationships:
            if 'cloudify.relationships.contained_in' in relationship.type_hierarchy:
                ctx.logger.info(
                    'Attribute/Property not found {0} go up to the parent node {1} by following relationship {2}'.format(data_name,
                                                                                                                         relationship.target.node.id,
                                                                                                                         relationship.type))
                return get_other_instances_data(relationship.target, data_name, get_data_function)
        return None
    else:
        return None


def get_host(entity):
    if entity.instance.relationships:
        for relationship in entity.instance.relationships:
            if 'cloudify.relationships.contained_in' in relationship.type_hierarchy:
                return get_host(relationship.target)
    return entity


def get_attribute_data(entity, attribute_name):
    return entity.instance.runtime_properties.get(attribute_name, None)


def get_property_data(entity, property_name):
    return entity.node.properties.get(property_name, None)

def parse_output(output):
    # by convention, the last output is the result of the operation
    last_output = None
    outputs = {}
    pattern = re.compile('EXPECTED_OUTPUT_(\w+)=(.*)')
    for line in output.splitlines():
        match = pattern.match(line)
        if match is None:
            last_output = line
        else:
            output_name = match.group(1)
            output_value = match.group(2)
            outputs[output_name] = output_value
    return {'last_output':last_output, 'outputs':outputs}

def execute(script_path, process, outputNames):
    wrapper_path = ctx.download_resource("scriptWrapper.sh")
    os.chmod(wrapper_path, 0755)

    os.chmod(script_path, 0755)
    on_posix = 'posix' in sys.builtin_module_names

    env = os.environ.copy()
    process_env = process.get('env', {})
    env.update(process_env)

    if outputNames is not None:
        env['EXPECTED_OUTPUTS'] = outputNames

    command = '{0} {1}'.format(wrapper_path, script_path)

    ctx.logger.info('Executing: {0} in env {1}'.format(command, env))

    process = subprocess.Popen(command,
                               shell=True,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE,
                               env=env,
                               cwd=None,
                               bufsize=1,
                               close_fds=on_posix)

    return_code = None

    stdout_consumer = OutputConsumer(process.stdout)
    stderr_consumer = OutputConsumer(process.stderr)

    while True:
        return_code = process.poll()
        if return_code is not None:
            break
        time.sleep(0.1)

    stdout_consumer.join()
    stderr_consumer.join()

    parsed_output = parse_output(stdout_consumer.buffer.getvalue())
    if outputNames is not None:
        outputNameList = outputNames.split(';')
        for outputName in outputNameList:
            ctx.logger.info('Ouput name: {0} value : {1}'.format(outputName, parsed_output['outputs'][outputName]))

    ok_message = "Script {0} executed normally with standard output {1} and error output {2}".format(command, stdout_consumer.buffer.getvalue(),
                                                                                                     stderr_consumer.buffer.getvalue())
    error_message = "Script {0} encountered error with return code {1} and standard output {2}, error output {3}".format(command, return_code,
                                                                                                                         stdout_consumer.buffer.getvalue(),
                                                                                                                         stderr_consumer.buffer.getvalue())
    if return_code != 0:
        ctx.logger.error(error_message)
        raise NonRecoverableError(error_message)
    else:
        ctx.logger.info(ok_message)

    return parsed_output

class OutputConsumer(object):
    def __init__(self, out):
        self.out = out
        self.buffer = StringIO()
        self.consumer = threading.Thread(target=self.consume_output)
        self.consumer.daemon = True
        self.consumer.start()

    def consume_output(self):
        for line in iter(self.out.readline, b''):
            self.buffer.write(line)
        self.out.close()

    def join(self):
        self.consumer.join()


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


def get_attribute(entity, attribute_name):
    if attribute_name == 'floating_ip_address':
        return get_public_ip(entity)
    else:
        return get_instance_data(entity, attribute_name, get_attribute_data)


def get_property(entity, property_name):
    return get_instance_data(entity, property_name, get_property_data)


def get_public_ip(entity):
    host = get_host(entity)
    if host.instance.relationships:
        for relationship in host.instance.relationships:
            if 'cloudify.relationships.connected_to' in relationship.type_hierarchy:
                if relationship.target.node.id == '_a4c_floating_ip_' + host.node.id:
                    return relationship.target.instance.runtime_properties['floating_ip_address']
    return ""


def download(child_rel_path, child_abs_path, download_dir):
    artifact_downloaded_path = ctx.download_resource(child_abs_path)
    new_file = os.path.join(download_dir, child_rel_path)
    new_file_dir = os.path.dirname(new_file)
    if not os.path.exists(new_file_dir):
        os.makedirs(new_file_dir)
    os.rename(artifact_downloaded_path, new_file)
    ctx.logger.info('Downloaded artifact from path ' + child_abs_path + ', it\'s available now at ' + new_file)
    return new_file


def download_artifacts(artifacts, download_dir):
    downloaded_artifacts = {}
    os.makedirs(download_dir)
    for artifact_name, artifact_ref in artifacts.items():
        ctx.logger.info('Download artifact ' + artifact_name)
        if isinstance(artifact_ref, basestring):
            downloaded_artifacts[artifact_name] = download(os.path.basename(artifact_ref), artifact_ref, download_dir)
        else:
            child_download_dir = os.path.join(download_dir, artifact_name)
            for child_path in artifact_ref:
                download(child_path['relative_path'], child_path['absolute_path'], child_download_dir)
            downloaded_artifacts[artifact_name] = child_download_dir
    return downloaded_artifacts

new_script_process = {
    'env': {
    }
}

node_artifacts = {
    "war_file": "tomcat-war-types/warFiles/helloWorld.war"
}

relationship_artifacts = {
}

artifacts = node_artifacts.copy()
artifacts.update(relationship_artifacts)

download_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'downloads')
new_script_process['env'].update(download_artifacts(artifacts, download_dir))

ctx.logger.info('Operation is executed with inputs {0}'.format(inputs))
if inputs.get('process', None) is not None and inputs['process'].get('env', None) is not None:
    ctx.logger.info('Operation is executed with environment variable {0}'.format(inputs['process']['env']))
    new_script_process['env'].update(inputs['process']['env'])

operationOutputNames = None
parsed_output = execute(ctx.download_resource('artifact-test-types/scripts/create.sh'), new_script_process, operationOutputNames)
for k,v in parsed_output['outputs'].items():
    ctx.logger.info('Output name: {0} value: {1}'.format(k, v))
    ctx.instance.runtime_properties['_a4c_OO:tosca.interfaces.node.lifecycle.Standard:create:{0}'.format(k)] = v


ctx.instance.runtime_properties['application_url'] = r'http://' + get_attribute(ctx, 'floating_ip_address') + r':' + r'80' + r'/' + r'helloworld'
ctx.instance.runtime_properties['local_application_url'] = r'http://' + get_attribute(ctx, 'ip') + r':' + r'80' + r'/' + r'helloworld'
ctx.instance.update()

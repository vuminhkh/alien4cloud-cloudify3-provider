from cloudify import ctx
from cloudify.exceptions import NonRecoverableError
import subprocess
import os
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


def get_attribute_data(entity, attribute_name):
    return entity.instance.runtime_properties.get(attribute_name, None)


def get_property_data(entity, property_name):
    return entity.node.properties.get(property_name, None)


def get_attribute(entity, attribute_name):
    return get_instance_data(entity, attribute_name, get_attribute_data)


def get_property(entity, property_name):
    return get_instance_data(entity, property_name, get_property_data)


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


def execute(script_path, process):
    os.chmod(script_path, 0755)
    on_posix = 'posix' in sys.builtin_module_names

    env = os.environ.copy()
    process_env = process.get('env', {})
    env.update(process_env)

    cwd = process.get('cwd')

    command_prefix = process.get('command_prefix')
    if command_prefix:
        command = '{0} {1}'.format(command_prefix, script_path)
    else:
        command = script_path

    args = process.get('args')
    if args:
        command = ' '.join([command] + args)

    ctx.logger.info('Executing: {0}'.format(command))

    process = subprocess.Popen(command,
                               shell=True,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE,
                               env=env,
                               cwd=cwd,
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


ctx.logger.info('Just logging the web app host IP: _____{0}_____'.format(get_attribute(ctx.source, 'ip')))

ctx.logger.info('Just logging the web app test property: _____{0}_____'.format(get_property(ctx.source, 'a_simple_test')))

new_script_process = {
    "env": {
        "WEB_APP_HOST_IP": get_attribute(ctx.source, 'ip'),
        "WEB_APP_HOST_TEST_PROPERTY": get_property(ctx.source, 'a_simple_test')
    }
}

execute(ctx.download_resource("test.sh"), new_script_process)

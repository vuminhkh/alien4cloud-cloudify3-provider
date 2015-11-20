from cloudify.workflows import tasks as workflow_tasks
from workflow import PersistentResourceEvent
from workflow import WfEvent
from workflow import build_pre_event
from cloudify import logs
from cloudify.logs import _send_event
from cloudify.workflows.workflow_context import task_config

def _wait_for_host_to_start(ctx, host_node_instance):
    task = host_node_instance.execute_operation(
        'cloudify.interfaces.host.get_state')

    # handler returns True if if get_state returns False,
    # this means, that get_state will be re-executed until
    # get_state returns True
    def node_get_state_handler(tsk):
        host_started = tsk.async_result.get()
        if host_started:
            return workflow_tasks.HandlerResult.cont()
        else:
            return workflow_tasks.HandlerResult.retry(
                ignore_total_retries=True)
    if not task.is_nop():
        task.on_success = node_get_state_handler
    return task


def host_post_start(ctx, host_node_instance):

    plugins_to_install = filter(lambda plugin: plugin['install'],
                                host_node_instance.node.plugins_to_install)

    tasks = [_wait_for_host_to_start(ctx, host_node_instance)]
    if host_node_instance.node.properties['install_agent'] is True:
        tasks += [
            host_node_instance.send_event('Installing worker'),
            host_node_instance.execute_operation(
                'cloudify.interfaces.worker_installer.install'),
            host_node_instance.execute_operation(
                'cloudify.interfaces.worker_installer.start'),
        ]
        if plugins_to_install:
            tasks += [
                host_node_instance.send_event('Installing host plugins'),
                host_node_instance.execute_operation(
                    'cloudify.interfaces.plugin_installer.install',
                    kwargs={
                        'plugins': plugins_to_install}),
                host_node_instance.execute_operation(
                    'cloudify.interfaces.worker_installer.restart',
                    send_task_events=False)
            ]
    tasks += [
        host_node_instance.execute_operation(
            'cloudify.interfaces.monitoring_agent.install'),
        host_node_instance.execute_operation(
            'cloudify.interfaces.monitoring_agent.start'),
    ]
    return tasks


def host_pre_stop(host_node_instance):
    tasks = []
    tasks += [
        host_node_instance.execute_operation(
            'cloudify.interfaces.monitoring_agent.stop'),
        host_node_instance.execute_operation(
            'cloudify.interfaces.monitoring_agent.uninstall'),
    ]
    if host_node_instance.node.properties['install_agent'] is True:
        tasks += [
            host_node_instance.send_event('Uninstalling worker'),
            host_node_instance.execute_operation(
                'cloudify.interfaces.worker_installer.stop'),
            host_node_instance.execute_operation(
                'cloudify.interfaces.worker_installer.uninstall')
        ]
    for task in tasks:
        if task.is_remote():
            _set_send_node_event_on_error_handler(
                task, host_node_instance,
                'Error occurred while uninstalling worker - ignoring...')
    return tasks


def _set_send_node_event_on_error_handler(task, node_instance, error_message):
    def send_node_event_error_handler(tsk):
        node_instance.send_event(error_message)
        return workflow_tasks.HandlerResult.ignore()
    task.on_failure = send_node_event_error_handler


def _set_send_node_evt_on_failed_unlink_handlers(instance, tasks_with_targets):
    for unlink_task, target_id in tasks_with_targets:
        _set_send_node_event_on_error_handler(
            unlink_task,
            instance,
            "Error occurred while unlinking node from node {0} - "
            "ignoring...".format(target_id)
        )


def build_persistent_event_tasks(instance):
    persistent_property = instance.node.properties.get('_a4c_persistent_resources', None)
    if persistent_property != None:
        # send event to send resource id to alien
        tasks = []
        @task_config(send_task_events=False)
        def send_event_task(message):
            _send_event(instance, 'workflow_node', 'a4c_persistent_event', message, None, None, None)

        for key, value in persistent_property.iteritems():
            persistent_cloudify_attribute = key
            persistent_alien_attribute = value
            kwargs={'message':build_pre_event(PersistentResourceEvent(persistent_cloudify_attribute, persistent_alien_attribute))}
            tasks.append(instance.ctx.local_task(local_task=send_event_task, node=instance, info=kwargs.message, kwargs=kwargs))
        return tasks
    else:
        return None


def build_wf_event_task(instance, step_id, stage):
    event_msg = build_pre_event(WfEvent(stage, step_id))

    @task_config(send_task_events=False)
    def send_wf_event_task():
        _send_event(instance, 'workflow_node', 'a4c_workflow_event', event_msg, None, None, None)

    return instance.ctx.local_task(local_task=send_wf_event_task, node=instance, info=event_msg)

from cloudify.workflows import tasks as workflow_tasks


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

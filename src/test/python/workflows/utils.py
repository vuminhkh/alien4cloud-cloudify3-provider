from handlers import host_post_start
from handlers import host_pre_stop


def _get_nodes_instances(ctx, node_id):
    instances = []
    for node in ctx.nodes:
        for instance in node.instances:
            if instance.node_id == node_id:
                instances.append(instance)
    return instances


def _get_all_nodes_instances(ctx):
    node_instances = set()
    for node in ctx.nodes:
        for instance in node.instances:
            node_instances.add(instance)
    return node_instances


def set_state_task(ctx, graph, node_id, state_name, step_id, tasks):
    sequence = None
    instances = _get_nodes_instances(ctx, node_id)
    instance_count = len(instances)
    if instance_count == 1:
        instance = instances[0]
        sequence = set_state_task_for_instance(graph, node_id, instance, state_name)
    elif instance_count > 1:
        fork = ForkjoinWrapper(graph)
        for instance in instances:
            fork.add(set_state_task_for_instance(graph, node_id, instance, state_name))
        msg = "state {0} on all {1} node instances".format(state_name, node_id)
        sequence = forkjoin_sequence(graph, fork, instances[0], msg)
    if sequence is not None:
        sequence.name = step_id
        tasks[step_id] = sequence


def set_state_task_for_instance(graph, node_id, instance, state_name):
    task = TaskSequenceWrapper(graph)
    msg = "Setting state '{0}' on node '{1}' instance '{2}'".format(state_name, node_id, instance.id)
    task.add(instance.send_event(msg))
    task.add(instance.set_state(state_name))
    return task


def operation_task(ctx, graph, node_id, operation_fqname, step_id, tasks):
    sequence = None
    instances = _get_nodes_instances(ctx, node_id)
    instance_count = len(instances)
    if instance_count == 1:
        instance = instances[0]
        sequence = operation_task_for_instance(ctx, graph, node_id, instance, operation_fqname)
    elif instance_count > 1:
        fork = ForkjoinWrapper(graph)
        for instance in instances:
            instance_task = operation_task_for_instance(ctx, graph, node_id, instance, operation_fqname)
            fork.add(instance_task)
        msg = "operation {0} on all {1} node instances".format(operation_fqname, node_id)
        sequence = forkjoin_sequence(graph, fork, instances[0], msg)
    if sequence is not None:
        tasks[step_id] = sequence
        sequence.name = step_id


def operation_task_for_instance(ctx, graph, node_id, instance, operation_fqname):
    sequence = TaskSequenceWrapper(graph)
    msg = "Calling operation '{0}' on node '{1}' instance '{2}'".format(operation_fqname, node_id, instance.id)
    sequence.add(instance.send_event(msg))
    if operation_fqname == 'cloudify.interfaces.lifecycle.start' and _is_host_node(instance):
        sequence.add(instance.execute_operation(operation_fqname))
        sequence.add(*host_post_start(ctx, instance))
    elif operation_fqname == 'cloudify.interfaces.lifecycle.configure':
        preconf_tasks = _relationship_operations(instance, 'cloudify.interfaces.relationship_lifecycle.preconfigure')
        if len(preconf_tasks) > 0:
            preconfigure_tasks = ForkjoinWrapper(graph)
            preconfigure_tasks.add(*preconf_tasks)
            sequence.add(forkjoin_sequence(graph, preconfigure_tasks, instance, "preconf for {0}".format(instance.id)))
        sequence.add(instance.execute_operation(operation_fqname))
        postconf_tasks = _relationship_operations(instance, 'cloudify.interfaces.relationship_lifecycle.postconfigure')
        if len(postconf_tasks) > 0:
            postconfigure_tasks = ForkjoinWrapper(graph)
            postconfigure_tasks.add(*postconf_tasks)
            msg = "postconf for {0}".format(instance.id)
            sequence.add(forkjoin_sequence(graph, postconfigure_tasks, instance, msg))
    elif operation_fqname == 'cloudify.interfaces.lifecycle.stop':
        if _is_host_node(instance):
            sequence.add(*host_pre_stop(instance))
        sequence.add(instance.execute_operation(operation_fqname))
        # now call unlink onto relations' target
        unlink_tasks = _relationship_operations(instance, 'cloudify.interfaces.relationship_lifecycle.unlink')
        if len(unlink_tasks) > 0:
            fork = ForkjoinWrapper(graph)
            fork.add(*unlink_tasks)
            sequence.add(forkjoin_sequence(graph, fork, instance, "unlink"))
    else:
        # the default behavior : just do the job
        sequence.add(instance.execute_operation(operation_fqname))
    if operation_fqname == 'cloudify.interfaces.lifecycle.start':
        fork = ForkjoinWrapper(graph)
        fork.add(instance.execute_operation('cloudify.interfaces.monitoring.start'))
        establish_tasks = _relationship_operations(instance, 'cloudify.interfaces.relationship_lifecycle.establish')
        if len(establish_tasks) > 0:
            fork.add(*establish_tasks)
        sequence.add(
            instance.send_event("Start monitoring on node '{0}' instance '{1}'".format(node_id, instance.id)),
            forkjoin_sequence(graph, fork, instance, "establish")
        )
    return sequence


def forkjoin_sequence(graph, forkjoin_wrapper, instance, label):
    sequence = TaskSequenceWrapper(graph)
    sequence.add(instance.send_event("forking: {0} instance '{1}'".format(label, instance.id)))
    sequence.add(forkjoin_wrapper)
    sequence.add(instance.send_event("joining: {0} instance '{1}'".format(label, instance.id)))
    return sequence


def link_tasks(graph, source_id, target_id, tasks):
    sources = tasks.get(source_id, None)
    if sources is None:
        return
    if isinstance(sources, TaskSequenceWrapper) or isinstance(sources, ForkjoinWrapper):
        sources = sources.first_tasks
    else:
        sources = [sources]
    targets = tasks.get(target_id, None)
    if targets is None:
        return
    if isinstance(targets, TaskSequenceWrapper) or isinstance(targets, ForkjoinWrapper):
        targets = targets.last_tasks
    else:
        targets = [targets]
    for source in sources:
        for target in targets:
            graph.add_dependency(source, target)


def _is_host_node(node_instance):
    return 'cloudify.nodes.Compute' in node_instance.node.type_hierarchy


def _relationship_operations(node_instance, operation):
    tasks_with_targets = _relationship_operations_with_targets(
        node_instance, operation)
    return [task for task, _ in tasks_with_targets]


def _relationship_operations_with_targets(node_instance, operation):
    tasks = []
    for relationship in node_instance.relationships:
        tasks += _relationship_operations_with_target(relationship, operation)
    return tasks


def _relationship_operations_with_target(relationship, operation):
    return [
        (relationship.execute_source_operation(operation),
         relationship.target_id),
        (relationship.execute_target_operation(operation),
         relationship.target_id)
    ]


class ForkjoinWrapper(object):

    def __init__(self, graph, name=""):
        self.graph = graph
        self.first_tasks = []
        self.last_tasks = []
        self.name = name

    def add(self, *tasks):
        for element in tasks:
            if isinstance(element, ForkjoinWrapper):
                self.first_tasks.extend(element.first_tasks)
                self.last_tasks.extend(element.last_tasks)
            elif isinstance(element, TaskSequenceWrapper):
                self.first_tasks.extend(element.first_tasks)
                self.last_tasks.extend(element.last_tasks)
            else:
                self.first_tasks.append(element)
                self.last_tasks.append(element)
                self.graph.add_task(element)


class TaskSequenceWrapper(object):

    def __init__(self, graph, name=""):
        self.graph = graph
        self.first_tasks = None
        self.last_tasks = None
        self.name = name

    def add(self, *tasks):
        for element in tasks:
            tasks_head = None
            tasks_queue = None
            if isinstance(element, ForkjoinWrapper):
                tasks_head = element.first_tasks
                tasks_queue = element.last_tasks
            elif isinstance(element, TaskSequenceWrapper):
                tasks_head = element.first_tasks
                tasks_queue = element.last_tasks
            else:
                tasks_head = [element]
                tasks_queue = tasks_head
                self.graph.add_task(element)
            for task in tasks_head:
                if self.last_tasks is not None:
                    for last_task in self.last_tasks:
                        self.graph.add_dependency(task, last_task)
            if tasks_head is not None:
                if self.first_tasks is None:
                    self.first_tasks = tasks_head
            if tasks_queue is not None:
                self.last_tasks = tasks_queue

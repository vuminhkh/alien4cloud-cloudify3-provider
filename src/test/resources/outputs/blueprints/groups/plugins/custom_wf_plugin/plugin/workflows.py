from cloudify.decorators import workflow
from cloudify.workflows import ctx
from cloudify.workflows import tasks as workflow_tasks
from utils import set_state_task
from utils import operation_task
from utils import link_tasks
from utils import CustomContext


@workflow
def a4c_install(**kwargs):
    graph = ctx.graph_mode()
    custom_context = CustomContext(ctx)
    ctx.internal.send_workflow_event(
        event_type='workflow_started',
        message="Starting A4C generated '{0}' workflow execution".format(ctx.workflow_id))
    _a4c_install(ctx, graph, custom_context)
    return graph.execute()


@workflow
def a4c_uninstall(**kwargs):
    graph = ctx.graph_mode()
    custom_context = CustomContext(ctx)
    ctx.internal.send_workflow_event(
        event_type='workflow_started',
        message="Starting A4C generated '{0}' workflow execution".format(ctx.workflow_id))
    _a4c_uninstall(ctx, graph, custom_context)
    return graph.execute()


def _a4c_install(ctx, graph, custom_context):
    #  following code can be pasted in src/test/python/workflows/tasks.py for simulation
    set_state_task(ctx, graph, 'compute1', 'creating', 'compute1_creating', custom_context)
    operation_task(ctx, graph, 'compute2', 'cloudify.interfaces.lifecycle.start', 'start_compute2', custom_context)
    set_state_task(ctx, graph, 'compute2', 'configured', 'compute2_configured', custom_context)
    set_state_task(ctx, graph, 'compute1', 'starting', 'compute1_starting', custom_context)
    operation_task(ctx, graph, 'compute1', 'cloudify.interfaces.lifecycle.configure', 'configure_compute1', custom_context)
    operation_task(ctx, graph, 'compute1', 'cloudify.interfaces.lifecycle.start', 'start_compute1', custom_context)
    set_state_task(ctx, graph, 'compute2', 'starting', 'compute2_starting', custom_context)
    set_state_task(ctx, graph, 'compute1', 'initial', 'compute1_initial', custom_context)
    set_state_task(ctx, graph, 'compute2', 'creating', 'compute2_creating', custom_context)
    operation_task(ctx, graph, 'compute2', 'cloudify.interfaces.lifecycle.create', 'create_compute2', custom_context)
    set_state_task(ctx, graph, 'compute2', 'configuring', 'compute2_configuring', custom_context)
    operation_task(ctx, graph, 'compute1', 'cloudify.interfaces.lifecycle.create', 'create_compute1', custom_context)
    operation_task(ctx, graph, 'compute2', 'cloudify.interfaces.lifecycle.configure', 'configure_compute2', custom_context)
    set_state_task(ctx, graph, 'compute1', 'configured', 'compute1_configured', custom_context)
    set_state_task(ctx, graph, 'compute1', 'configuring', 'compute1_configuring', custom_context)
    set_state_task(ctx, graph, 'compute2', 'initial', 'compute2_initial', custom_context)
    set_state_task(ctx, graph, 'compute2', 'created', 'compute2_created', custom_context)
    set_state_task(ctx, graph, 'compute1', 'created', 'compute1_created', custom_context)
    set_state_task(ctx, graph, 'compute1', 'started', 'compute1_started', custom_context)
    set_state_task(ctx, graph, 'compute2', 'started', 'compute2_started', custom_context)
    link_tasks(graph, 'compute1_creating', 'compute1_initial', custom_context)
    link_tasks(graph, 'start_compute2', 'compute2_starting', custom_context)
    link_tasks(graph, 'compute2_configured', 'configure_compute2', custom_context)
    link_tasks(graph, 'compute1_starting', 'compute1_configured', custom_context)
    link_tasks(graph, 'configure_compute1', 'compute1_configuring', custom_context)
    link_tasks(graph, 'start_compute1', 'compute1_starting', custom_context)
    link_tasks(graph, 'compute2_starting', 'compute2_configured', custom_context)
    link_tasks(graph, 'compute2_creating', 'compute2_initial', custom_context)
    link_tasks(graph, 'create_compute2', 'compute2_creating', custom_context)
    link_tasks(graph, 'compute2_configuring', 'compute2_created', custom_context)
    link_tasks(graph, 'create_compute1', 'compute1_creating', custom_context)
    link_tasks(graph, 'configure_compute2', 'compute2_configuring', custom_context)
    link_tasks(graph, 'compute1_configured', 'configure_compute1', custom_context)
    link_tasks(graph, 'compute1_configuring', 'compute1_created', custom_context)
    link_tasks(graph, 'compute2_created', 'create_compute2', custom_context)
    link_tasks(graph, 'compute1_created', 'create_compute1', custom_context)
    link_tasks(graph, 'compute1_started', 'start_compute1', custom_context)
    link_tasks(graph, 'compute2_started', 'start_compute2', custom_context)


def _a4c_uninstall(ctx, graph, custom_context):
    #  following code can be pasted in src/test/python/workflows/tasks.py for simulation
    operation_task(ctx, graph, 'compute2', 'cloudify.interfaces.lifecycle.delete', 'delete_compute2', custom_context)
    set_state_task(ctx, graph, 'compute2', 'stopping', 'compute2_stopping', custom_context)
    operation_task(ctx, graph, 'compute1', 'cloudify.interfaces.lifecycle.delete', 'delete_compute1', custom_context)
    set_state_task(ctx, graph, 'compute2', 'deleting', 'compute2_deleting', custom_context)
    operation_task(ctx, graph, 'compute2', 'cloudify.interfaces.lifecycle.stop', 'stop_compute2', custom_context)
    set_state_task(ctx, graph, 'compute1', 'deleting', 'compute1_deleting', custom_context)
    set_state_task(ctx, graph, 'compute2', 'stopped', 'compute2_stopped', custom_context)
    set_state_task(ctx, graph, 'compute2', 'deleted', 'compute2_deleted', custom_context)
    operation_task(ctx, graph, 'compute1', 'cloudify.interfaces.lifecycle.stop', 'stop_compute1', custom_context)
    set_state_task(ctx, graph, 'compute1', 'stopping', 'compute1_stopping', custom_context)
    set_state_task(ctx, graph, 'compute1', 'stopped', 'compute1_stopped', custom_context)
    set_state_task(ctx, graph, 'compute1', 'deleted', 'compute1_deleted', custom_context)
    link_tasks(graph, 'delete_compute2', 'compute2_deleting', custom_context)
    link_tasks(graph, 'delete_compute1', 'compute1_deleting', custom_context)
    link_tasks(graph, 'compute2_deleting', 'compute2_stopped', custom_context)
    link_tasks(graph, 'stop_compute2', 'compute2_stopping', custom_context)
    link_tasks(graph, 'compute1_deleting', 'compute1_stopped', custom_context)
    link_tasks(graph, 'compute2_stopped', 'stop_compute2', custom_context)
    link_tasks(graph, 'compute2_deleted', 'delete_compute2', custom_context)
    link_tasks(graph, 'stop_compute1', 'compute1_stopping', custom_context)
    link_tasks(graph, 'compute1_stopped', 'stop_compute1', custom_context)
    link_tasks(graph, 'compute1_deleted', 'delete_compute1', custom_context)


#following code can be pasted in src/test/python/workflows/context.py for simulation
#def _build_nodes(ctx):
    #types = []
    #types.append('tosca.nodes.Compute')
    #types.append('tosca.nodes.Root')
    #node_compute1 = _build_node(ctx, 'compute1', types, 1)
    #types = []
    #types.append('tosca.nodes.Compute')
    #types.append('tosca.nodes.Root')
    #node_compute2 = _build_node(ctx, 'compute2', types, 1)

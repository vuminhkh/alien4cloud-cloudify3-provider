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
    set_state_task(ctx, graph, 'compute', 'configuring', 'compute_configuring', custom_context)
    set_state_task(ctx, graph, 'compute', 'initial', 'compute_initial', custom_context)
    operation_task(ctx, graph, 'compute', 'cloudify.interfaces.lifecycle.start', 'start_compute', custom_context)
    set_state_task(ctx, graph, 'compute', 'created', 'compute_created', custom_context)
    set_state_task(ctx, graph, 'compute', 'creating', 'compute_creating', custom_context)
    set_state_task(ctx, graph, 'compute', 'started', 'compute_started', custom_context)
    set_state_task(ctx, graph, 'compute', 'configured', 'compute_configured', custom_context)
    operation_task(ctx, graph, 'compute', 'cloudify.interfaces.lifecycle.configure', 'configure_compute', custom_context)
    operation_task(ctx, graph, 'compute', 'cloudify.interfaces.lifecycle.create', 'create_compute', custom_context)
    set_state_task(ctx, graph, 'compute', 'starting', 'compute_starting', custom_context)
    link_tasks(graph, 'compute_configuring', 'compute_created', custom_context)
    link_tasks(graph, 'start_compute', 'compute_starting', custom_context)
    link_tasks(graph, 'compute_created', 'create_compute', custom_context)
    link_tasks(graph, 'compute_creating', 'compute_initial', custom_context)
    link_tasks(graph, 'compute_started', 'start_compute', custom_context)
    link_tasks(graph, 'compute_configured', 'configure_compute', custom_context)
    link_tasks(graph, 'configure_compute', 'compute_configuring', custom_context)
    link_tasks(graph, 'create_compute', 'compute_creating', custom_context)
    link_tasks(graph, 'compute_starting', 'compute_configured', custom_context)


def _a4c_uninstall(ctx, graph, custom_context):
    #  following code can be pasted in src/test/python/workflows/tasks.py for simulation
    set_state_task(ctx, graph, 'compute', 'deleting', 'compute_deleting', custom_context)
    operation_task(ctx, graph, 'compute', 'cloudify.interfaces.lifecycle.delete', 'delete_compute', custom_context)
    set_state_task(ctx, graph, 'compute', 'stopped', 'compute_stopped', custom_context)
    set_state_task(ctx, graph, 'compute', 'stopping', 'compute_stopping', custom_context)
    set_state_task(ctx, graph, 'compute', 'deleted', 'compute_deleted', custom_context)
    operation_task(ctx, graph, 'compute', 'cloudify.interfaces.lifecycle.stop', 'stop_compute', custom_context)
    link_tasks(graph, 'compute_deleting', 'compute_stopped', custom_context)
    link_tasks(graph, 'delete_compute', 'compute_deleting', custom_context)
    link_tasks(graph, 'compute_stopped', 'stop_compute', custom_context)
    link_tasks(graph, 'compute_deleted', 'delete_compute', custom_context)
    link_tasks(graph, 'stop_compute', 'compute_stopping', custom_context)


#following code can be pasted in src/test/python/workflows/context.py for simulation
#def _build_nodes(ctx):
    #types = []
    #types.append('tosca.nodes.Compute')
    #types.append('tosca.nodes.Root')
    #node_compute = _build_node(ctx, 'compute', types, 1)

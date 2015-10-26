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
    operation_task(ctx, graph, 'Compute', 'cloudify.interfaces.lifecycle.configure', 'configure_Compute', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'starting', 'InternalNetwork_starting', custom_context)
    set_state_task(ctx, graph, 'Compute', 'starting', 'Compute_starting', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'starting', 'NetPub_starting', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'created', 'NetPub_created', custom_context)
    operation_task(ctx, graph, 'InternalNetwork', 'cloudify.interfaces.lifecycle.create', 'create_InternalNetwork', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'started', 'NetPub_started', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'configuring', 'InternalNetwork_configuring', custom_context)
    set_state_task(ctx, graph, 'Compute', 'configured', 'Compute_configured', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'started', 'InternalNetwork_started', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'initial', 'InternalNetwork_initial', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'created', 'InternalNetwork_created', custom_context)
    set_state_task(ctx, graph, 'Compute', 'started', 'Compute_started', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'creating', 'NetPub_creating', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'initial', 'NetPub_initial', custom_context)
    operation_task(ctx, graph, 'NetPub', 'cloudify.interfaces.lifecycle.start', 'start_NetPub', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'configuring', 'NetPub_configuring', custom_context)
    set_state_task(ctx, graph, 'Compute', 'created', 'Compute_created', custom_context)
    operation_task(ctx, graph, 'Compute', 'cloudify.interfaces.lifecycle.create', 'create_Compute', custom_context)
    operation_task(ctx, graph, 'InternalNetwork', 'cloudify.interfaces.lifecycle.start', 'start_InternalNetwork', custom_context)
    operation_task(ctx, graph, 'InternalNetwork', 'cloudify.interfaces.lifecycle.configure', 'configure_InternalNetwork', custom_context)
    operation_task(ctx, graph, 'Compute', 'cloudify.interfaces.lifecycle.start', 'start_Compute', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'configured', 'InternalNetwork_configured', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'creating', 'InternalNetwork_creating', custom_context)
    set_state_task(ctx, graph, 'Compute', 'initial', 'Compute_initial', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'configured', 'NetPub_configured', custom_context)
    operation_task(ctx, graph, 'NetPub', 'cloudify.interfaces.lifecycle.configure', 'configure_NetPub', custom_context)
    operation_task(ctx, graph, 'NetPub', 'cloudify.interfaces.lifecycle.create', 'create_NetPub', custom_context)
    set_state_task(ctx, graph, 'Compute', 'configuring', 'Compute_configuring', custom_context)
    set_state_task(ctx, graph, 'Compute', 'creating', 'Compute_creating', custom_context)
    link_tasks(graph, 'configure_Compute', 'Compute_configuring', custom_context)
    link_tasks(graph, 'InternalNetwork_starting', 'InternalNetwork_configured', custom_context)
    link_tasks(graph, 'Compute_starting', 'Compute_configured', custom_context)
    link_tasks(graph, 'NetPub_starting', 'NetPub_configured', custom_context)
    link_tasks(graph, 'NetPub_created', 'create_NetPub', custom_context)
    link_tasks(graph, 'create_InternalNetwork', 'InternalNetwork_creating', custom_context)
    link_tasks(graph, 'NetPub_started', 'start_NetPub', custom_context)
    link_tasks(graph, 'InternalNetwork_configuring', 'InternalNetwork_created', custom_context)
    link_tasks(graph, 'InternalNetwork_configuring', 'Compute_created', custom_context)
    link_tasks(graph, 'Compute_configured', 'configure_Compute', custom_context)
    link_tasks(graph, 'InternalNetwork_started', 'start_InternalNetwork', custom_context)
    link_tasks(graph, 'InternalNetwork_created', 'create_InternalNetwork', custom_context)
    link_tasks(graph, 'Compute_started', 'start_Compute', custom_context)
    link_tasks(graph, 'NetPub_creating', 'NetPub_initial', custom_context)
    link_tasks(graph, 'start_NetPub', 'NetPub_starting', custom_context)
    link_tasks(graph, 'NetPub_configuring', 'NetPub_created', custom_context)
    link_tasks(graph, 'NetPub_configuring', 'Compute_created', custom_context)
    link_tasks(graph, 'Compute_created', 'create_Compute', custom_context)
    link_tasks(graph, 'create_Compute', 'Compute_creating', custom_context)
    link_tasks(graph, 'start_InternalNetwork', 'InternalNetwork_starting', custom_context)
    link_tasks(graph, 'configure_InternalNetwork', 'InternalNetwork_configuring', custom_context)
    link_tasks(graph, 'start_Compute', 'Compute_starting', custom_context)
    link_tasks(graph, 'InternalNetwork_configured', 'configure_InternalNetwork', custom_context)
    link_tasks(graph, 'InternalNetwork_creating', 'InternalNetwork_initial', custom_context)
    link_tasks(graph, 'NetPub_configured', 'configure_NetPub', custom_context)
    link_tasks(graph, 'configure_NetPub', 'NetPub_configuring', custom_context)
    link_tasks(graph, 'create_NetPub', 'NetPub_creating', custom_context)
    link_tasks(graph, 'Compute_configuring', 'InternalNetwork_started', custom_context)
    link_tasks(graph, 'Compute_configuring', 'Compute_created', custom_context)
    link_tasks(graph, 'Compute_configuring', 'NetPub_started', custom_context)
    link_tasks(graph, 'Compute_creating', 'Compute_initial', custom_context)


def _a4c_uninstall(ctx, graph, custom_context):
    #  following code can be pasted in src/test/python/workflows/tasks.py for simulation
    operation_task(ctx, graph, 'NetPub', 'cloudify.interfaces.lifecycle.delete', 'delete_NetPub', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'deleted', 'NetPub_deleted', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'stopped', 'InternalNetwork_stopped', custom_context)
    operation_task(ctx, graph, 'Compute', 'cloudify.interfaces.lifecycle.delete', 'delete_Compute', custom_context)
    set_state_task(ctx, graph, 'Compute', 'stopped', 'Compute_stopped', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'deleting', 'NetPub_deleting', custom_context)
    set_state_task(ctx, graph, 'Compute', 'deleted', 'Compute_deleted', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'deleting', 'InternalNetwork_deleting', custom_context)
    set_state_task(ctx, graph, 'Compute', 'stopping', 'Compute_stopping', custom_context)
    operation_task(ctx, graph, 'InternalNetwork', 'cloudify.interfaces.lifecycle.delete', 'delete_InternalNetwork', custom_context)
    set_state_task(ctx, graph, 'Compute', 'deleting', 'Compute_deleting', custom_context)
    operation_task(ctx, graph, 'Compute', 'cloudify.interfaces.lifecycle.stop', 'stop_Compute', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'stopping', 'InternalNetwork_stopping', custom_context)
    set_state_task(ctx, graph, 'InternalNetwork', 'deleted', 'InternalNetwork_deleted', custom_context)
    operation_task(ctx, graph, 'NetPub', 'cloudify.interfaces.lifecycle.stop', 'stop_NetPub', custom_context)
    operation_task(ctx, graph, 'InternalNetwork', 'cloudify.interfaces.lifecycle.stop', 'stop_InternalNetwork', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'stopping', 'NetPub_stopping', custom_context)
    set_state_task(ctx, graph, 'NetPub', 'stopped', 'NetPub_stopped', custom_context)
    link_tasks(graph, 'delete_NetPub', 'NetPub_deleting', custom_context)
    link_tasks(graph, 'NetPub_deleted', 'delete_NetPub', custom_context)
    link_tasks(graph, 'InternalNetwork_stopped', 'stop_InternalNetwork', custom_context)
    link_tasks(graph, 'delete_Compute', 'Compute_deleting', custom_context)
    link_tasks(graph, 'Compute_stopped', 'stop_Compute', custom_context)
    link_tasks(graph, 'NetPub_deleting', 'NetPub_stopped', custom_context)
    link_tasks(graph, 'Compute_deleted', 'delete_Compute', custom_context)
    link_tasks(graph, 'InternalNetwork_deleting', 'InternalNetwork_stopped', custom_context)
    link_tasks(graph, 'delete_InternalNetwork', 'InternalNetwork_deleting', custom_context)
    link_tasks(graph, 'Compute_deleting', 'Compute_stopped', custom_context)
    link_tasks(graph, 'stop_Compute', 'Compute_stopping', custom_context)
    link_tasks(graph, 'InternalNetwork_deleted', 'delete_InternalNetwork', custom_context)
    link_tasks(graph, 'stop_NetPub', 'NetPub_stopping', custom_context)
    link_tasks(graph, 'stop_InternalNetwork', 'InternalNetwork_stopping', custom_context)
    link_tasks(graph, 'NetPub_stopped', 'stop_NetPub', custom_context)


#following code can be pasted in src/test/python/workflows/context.py for simulation
#def _build_nodes(ctx):
    #types = []
    #types.append('alien.nodes.openstack.PrivateNetwork')
    #types.append('alien.nodes.PrivateNetwork')
    #types.append('tosca.nodes.Network')
    #types.append('tosca.nodes.Root')
    #node_InternalNetwork = _build_node(ctx, 'InternalNetwork', types, 1)
    #types = []
    #types.append('alien.nodes.openstack.PublicNetwork')
    #types.append('alien.nodes.PublicNetwork')
    #types.append('tosca.nodes.Network')
    #types.append('tosca.nodes.Root')
    #node_NetPub = _build_node(ctx, 'NetPub', types, 1)
    #types = []
    #types.append('alien.nodes.openstack.Compute')
    #types.append('tosca.nodes.Compute')
    #types.append('tosca.nodes.Root')
    #node_Compute = _build_node(ctx, 'Compute', types, 1)
    #_add_relationship(node_Compute, node_NetPub)
    #_add_relationship(node_Compute, node_InternalNetwork)

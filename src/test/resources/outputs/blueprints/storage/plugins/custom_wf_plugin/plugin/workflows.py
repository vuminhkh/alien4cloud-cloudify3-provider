from cloudify.decorators import workflow
from cloudify.workflows import ctx
from cloudify.workflows import tasks as workflow_tasks
from utils import set_state_task
from utils import operation_task
from utils import link_tasks
from utils import CustomContext
from utils import generate_native_node_workflows


@workflow
def a4c_uninstall(**kwargs):
    graph = ctx.graph_mode()
    custom_context = CustomContext(ctx)
    ctx.internal.send_workflow_event(
        event_type='workflow_started',
        message="Starting A4C generated '{0}' workflow execution".format(ctx.workflow_id))
    _a4c_uninstall(ctx, graph, custom_context)
    return graph.execute()


@workflow
def a4c_install(**kwargs):
    graph = ctx.graph_mode()
    custom_context = CustomContext(ctx)
    ctx.internal.send_workflow_event(
        event_type='workflow_started',
        message="Starting A4C generated '{0}' workflow execution".format(ctx.workflow_id))
    _a4c_install(ctx, graph, custom_context)
    return graph.execute()


def _a4c_uninstall(ctx, graph, custom_context):
    #  following code can be pasted in src/test/python/workflows/tasks.py for simulation
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'deleted', 'FileSystem_deleted', custom_context)
    custom_context.register_native_delegate_wf_step('Compute', 'Compute_uninstall')
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'stopped', 'FileSystem_stopped', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'stopping', 'FileSystem_stopping', custom_context)
    operation_task(ctx, graph, 'FileSystem', 'cloudify.interfaces.lifecycle.stop', 'stop_FileSystem', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'deleting', 'FileSystem_deleting', custom_context)
    custom_context.register_native_delegate_wf_step('BlockStorage', 'BlockStorage_uninstall')
    custom_context.register_native_delegate_wf_step('DeletableBlockStorage', 'DeletableBlockStorage_uninstall')
    generate_native_node_workflows(ctx, graph, custom_context, 'uninstall')
    link_tasks(graph, 'FileSystem_deleted', 'FileSystem_deleting', custom_context)
    link_tasks(graph, 'Compute_uninstall', 'FileSystem_deleted', custom_context)
    link_tasks(graph, 'FileSystem_stopped', 'stop_FileSystem', custom_context)
    link_tasks(graph, 'stop_FileSystem', 'FileSystem_stopping', custom_context)
    link_tasks(graph, 'FileSystem_deleting', 'FileSystem_stopped', custom_context)
    link_tasks(graph, 'BlockStorage_uninstall', 'FileSystem_deleted', custom_context)


def _a4c_install(ctx, graph, custom_context):
    #  following code can be pasted in src/test/python/workflows/tasks.py for simulation
    custom_context.register_native_delegate_wf_step('Compute', 'Compute_install')
    custom_context.register_native_delegate_wf_step('DeletableBlockStorage', 'DeletableBlockStorage_install')
    operation_task(ctx, graph, 'FileSystem', 'cloudify.interfaces.lifecycle.configure', 'configure_FileSystem', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'configuring', 'FileSystem_configuring', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'starting', 'FileSystem_starting', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'creating', 'FileSystem_creating', custom_context)
    operation_task(ctx, graph, 'FileSystem', 'cloudify.interfaces.lifecycle.start', 'start_FileSystem', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'configured', 'FileSystem_configured', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'started', 'FileSystem_started', custom_context)
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'initial', 'FileSystem_initial', custom_context)
    custom_context.register_native_delegate_wf_step('BlockStorage', 'BlockStorage_install')
    custom_context.add_customized_wf_node('FileSystem')
    set_state_task(ctx, graph, 'FileSystem', 'created', 'FileSystem_created', custom_context)
    generate_native_node_workflows(ctx, graph, custom_context, 'install')
    link_tasks(graph, 'configure_FileSystem', 'FileSystem_configuring', custom_context)
    link_tasks(graph, 'FileSystem_configuring', 'FileSystem_created', custom_context)
    link_tasks(graph, 'FileSystem_starting', 'FileSystem_configured', custom_context)
    link_tasks(graph, 'FileSystem_creating', 'FileSystem_initial', custom_context)
    link_tasks(graph, 'start_FileSystem', 'FileSystem_starting', custom_context)
    link_tasks(graph, 'FileSystem_configured', 'configure_FileSystem', custom_context)
    link_tasks(graph, 'FileSystem_started', 'start_FileSystem', custom_context)
    link_tasks(graph, 'FileSystem_initial', 'Compute_install', custom_context)
    link_tasks(graph, 'FileSystem_initial', 'BlockStorage_install', custom_context)
    link_tasks(graph, 'FileSystem_created', 'FileSystem_creating', custom_context)


#following code can be pasted in src/test/python/workflows/context.py for simulation
#def _build_nodes(ctx):
    #types = []
    #types.append('alien.cloudify.openstack.nodes.Volume')
    #types.append('alien.cloudify.openstack.nodes.DeletableVolume')
    #types.append('tosca.nodes.BlockStorage')
    #types.append('tosca.nodes.Root')
    #node_BlockStorage = _build_node(ctx, 'BlockStorage', types, 1)
    #types = []
    #types.append('alien.cloudify.openstack.nodes.DeletableVolume')
    #types.append('tosca.nodes.BlockStorage')
    #types.append('tosca.nodes.Root')
    #node_DeletableBlockStorage = _build_node(ctx, 'DeletableBlockStorage', types, 1)
    #types = []
    #types.append('alien.nodes.openstack.Compute')
    #types.append('tosca.nodes.Compute')
    #types.append('tosca.nodes.Root')
    #node_Compute = _build_node(ctx, 'Compute', types, 1)
    #types = []
    #types.append('alien.nodes.LinuxFileSystem')
    #types.append('tosca.nodes.SoftwareComponent')
    #types.append('tosca.nodes.Root')
    #node_FileSystem = _build_node(ctx, 'FileSystem', types, 1)
    #_add_relationship(node_BlockStorage, node_Compute)
    #_add_relationship(node_DeletableBlockStorage, node_Compute)
    #_add_relationship(node_FileSystem, node_BlockStorage)
    #_add_relationship(node_FileSystem, node_Compute)

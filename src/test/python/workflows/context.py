from graph import Context
from graph import Node
from graph import Instance
from graph import Relationship


# content of this fn can be generated by workflow plugin (see workflows.py in generated blueprint)
def _build_nodes(ctx):
    # just put the generated sequence here :
    types = []
    types.append('fastconnect.nodes.MapRCore')
    types.append('tosca.nodes.SoftwareComponent')
    types.append('tosca.nodes.Root')
    node_MapRCore = _build_node(ctx, 'MapRCore', types, 1)
    types = []
    types.append('fastconnect.nodes.MapRZookeeper')
    types.append('tosca.nodes.Root')
    node_MapRZookeeper = _build_node(ctx, 'MapRZookeeper', types, 1)
    types = []
    types.append('tosca.nodes.Compute')
    types.append('tosca.nodes.Root')
    node_Compute = _build_node(ctx, 'Compute', types, 1)
    _add_relationship(node_MapRCore, node_Compute)
    _add_relationship(node_MapRZookeeper, node_MapRCore)


def build_context():
    ctx = Context()
    _build_nodes(ctx)
    return ctx


def _build_node(ctx, node_id, node_type, instance_count):
    node = Node(node_id, node_type)
    _build_intances(node, instance_count)
    ctx.nodes.append(node)
    return node


def _build_intances(node, instance_count):
    i = 0
    while i < instance_count:
        instance_id = node.id + str(i)
        instance = Instance(instance_id, node)
        node.instances.append(instance)
        i += 1


def _add_relationship(node, target_node):
    for instance in node.instances:
        instance.relationships.append(Relationship(instance, target_node))
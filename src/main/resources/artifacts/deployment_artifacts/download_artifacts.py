from cloudify import ctx

artifacts = ctx.node.properties['artifacts']

for artifact_name, artifact_path in artifacts.items():
    ctx.logger.info('Download artifact ' + artifact_name + ' from path ' + artifact_path)
    artifact_downloaded_path = ctx.download_resource(artifact_path)
    ctx.logger.info('Downloaded artifact ' + artifact_name + ' from path ' + artifact_path + ', it\'s available now at ' + artifact_downloaded_path)
    ctx.instance.runtime_properties[artifact_name] = artifact_downloaded_path
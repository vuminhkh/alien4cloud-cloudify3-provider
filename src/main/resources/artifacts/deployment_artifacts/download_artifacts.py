from cloudify import ctx
import os

download_dir = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'downloads')
os.makedirs(download_dir)
artifacts = ctx.node.properties['artifacts']


def download(path):
    artifact_downloaded_path = ctx.download_resource(path)
    new_file = os.path.join(download_dir, os.path.basename(path))
    os.rename(artifact_downloaded_path, new_file)
    ctx.logger.info('Downloaded artifact from path ' + path + ', it\'s available now at ' + new_file)
    return new_file


for artifact_name, artifact_ref in artifacts.items():
    ctx.logger.info('Download artifact ' + artifact_name)
    if isinstance(artifact_ref, basestring):
        ctx.instance.runtime_properties[artifact_name] = download(artifact_ref)
    else:
        for child_path in artifact_ref:
            download(child_path)
        ctx.instance.runtime_properties[artifact_name] = download_dir

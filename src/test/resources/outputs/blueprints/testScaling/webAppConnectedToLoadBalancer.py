from cloudify import ctx

import os

ctx.logger.info('Just logging the web app IP: _____{0}_____'.format(ctx.source.instance.runtime_properties['ip']))
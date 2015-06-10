from cloudify import ctx

import os

ctx.logger.info('Just logging the database IP: _____{0}_____'.format(os.environ.get('DATABASE_IP')))
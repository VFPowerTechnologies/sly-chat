import re
import itertools
from os import environ
from os.path import join
from configparser import ConfigParser, Interpolation, BasicInterpolation, InterpolationError


class EnvInterpolation(Interpolation):
    "Interpolates $\w in config files as env var references."

    _env_var_re = re.compile('\$([\w]+)')

    def __init__(self, base_interpolation=None):
        self._base_interpolation = base_interpolation if base_interpolation is not None else BasicInterpolation()

    def before_set(self, parser, section, option, value):
        return self._base_interpolation.before_set(parser, section, option, value)

    def before_get(self, parser, section, option, value, defaults):
        return self._base_interpolation.before_get(parser, section, option, value, defaults)

    def _get_env_var(self, section, option, m):
        k = m.group(1)
        try:
            return environ[k]
        except KeyError:
            raise InterpolationError(section, option, 'Unset enviroment variable: ' + k)

    def before_read(self, parser, section, option, value):
        new_value = self._env_var_re.sub(lambda m: self._get_env_var(section, option, m), value)
        return self._base_interpolation.before_read(parser, section, option, new_value)

    def before_write(self, parser, section, option, value):
        return self._base_interpolation.before_write(parser, section, option, value)


class ConfigError(Exception):
    def __init__(self, msg):
        super().__init__('Configuration file error: ' + msg)


def process_config_file(config):
    "Returns a dictionary using the given config file as a base."

    required_keys = [
        'root',
        'android-ndk-home',
        'android-sdk-home',
        'groovy-home',
        'libtool-home',
        'platforms',
        'linux-jdk-home',
        'osx-jdk-home',
        'win32-jdk-home',
        'gant-home',
    ]

    r = {}
    for key in required_keys:
        try:
            r[key] = config[key]
        except KeyError:
            raise ConfigError('Missing key: ' + key)

    r['platforms'] = [a.strip() for a in config['platforms'].split(',')]

    root_path = r['root']

    #derived values
    r['root-src-path'] = join(root_path, 'src')
    r['root-build-path'] = join(root_path, 'build')
    r['root-prefix-path'] = join(root_path, 'root')
    r['root-output-path'] = join(root_path, 'output')
    r['root-android-output-path'] = join(r['root-output-path'], 'android')

    return r


def read_context_from_config(path):
    "Returns a dictionary using the given config file."

    config = ConfigParser(interpolation=EnvInterpolation())

    with open(path, 'r', encoding='utf8') as fd:
        config.read_file(itertools.chain(['[default]'], fd))
        return process_config_file(config['default'])

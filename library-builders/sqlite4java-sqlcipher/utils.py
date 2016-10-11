import errno
import re
import tarfile
import subprocess
import shutil
import hashlib
from os import environ, makedirs, listdir, rmdir, rename, pathsep
from os.path import exists, join, basename, dirname, abspath


TEMPLATE_DIR = abspath(join(dirname(__file__), 'templates'))
PATCH_DIR = abspath(join(dirname(__file__), 'patches'))


def get_os_from_platform(platform):
    "Given os-platform, returns os"

    return platform.split('-', 1)[0]


def get_sha256_checksum(path):
    hasher = hashlib.sha256()
    with open(path, 'rb') as fd:
        while True:
            chunk = fd.read(10*1024)
            if len(chunk) <= 0:
                break
            hasher.update(chunk)

    return hasher.hexdigest()


def locate_command(cmd):
    "Locate a command by searching the patch"

    #XXX add .exe if on windows?
    for path in environ['PATH'].split(pathsep):
        cmd_path = join(path, cmd)
        if exists(cmd_path):
            return cmd_path

    return None


def apply_patch(cwd, patch_name, context):
    "Apply the given patch template by name in the given directly."
    patch_data = get_patch_template(patch_name).substitute(**context)
    p = subprocess.Popen(['patch', '-p1'], cwd=cwd, stdin=subprocess.PIPE)
    p.communicate(patch_data.encode('utf8'))
    if p.returncode != 0:
        raise RuntimeError('Failed to apply patch %s' % patch_name)


def get_android_configure_host_type(platform):
    "Used for ./configure --host=..."

    return {
        'android-x86': 'i686-linux-android',
        'android-armeabi-v7a': 'arm-linux-androideabi',
    }[platform]


def arch_to_setenv_info(platform):
    "Returns (platform, abi-compiler-prefix)"

    cpu = platform.split('-', 1)[1]
    #platforms/platform-* (arm/mips/x86)
    android_arch = {
        'x86': 'x86',
        'armeabi-v7a': 'arm',
    }[cpu]

    android_eabi = {
        'x86': 'x86-4.9',
        'armeabi-v7a': 'arm-linux-androideabi-4.9',
    }[cpu]

    return android_arch, android_eabi


def get_static_lib_name_for_platform(platform, lib):
    return 'lib%s.a' % lib


def get_dynamic_lib_name_for_platform(platform, lib):
    os = get_os_from_platform(platform)

    if os == 'linux':
        return 'lib%s.so' % lib
    elif os == 'osx':
        return 'lib%s.dylib' % lib
    elif os == 'win32':
        return '%s.dll' % lib
    else:
        raise ValueError('Unsupported os: ' + platform)


def platform_is_android(platform):
    return platform.startswith('android-')


def get_template(name):
    "Returns a Template for the given template under templates/."

    path = join(TEMPLATE_DIR, name)
    return Template.from_file(path)


def get_patch_template(name):
    "Returns a Template instance for the given patch name under patches/."

    path = join(PATCH_DIR, name + '.diff')
    return Template.from_file(path)


def write_to_file(path, text):
    "Writes the given text as UTF-8 to the given path"

    with open(path, 'w', encoding='utf-8') as fd:
        fd.write(text)


def list_files(path):
    "Returns a list of absolute paths for each child of a directory"

    return [join(path, child) for child in listdir(path)]


def unhoist_directory(path):
    """
    If the given directory contains only a single directory, move all the
    subdirectory's content to the parent directory and remove it.
    """

    children = list_files(path)
    if len(children) != 1:
        return

    container = children[0]
    children = list_files(container)
    for child in children:
        rename(child, join(path, basename(child)))

    rmdir(container)


def unpack_source(src_path, output_path):
    "Unpacks the src archive to the given path and unhoist it."

    if exists(output_path):
        shutil.rmtree(output_path)

    make_dirs(output_path)

    with tarfile.open(src_path) as tar:
        tar.extractall(output_path)

    unhoist_directory(output_path)


def make_dirs(path):
    "Creates the full given directory path"

    try:
        makedirs(path)
    except OSError as e:
        if e.errno != errno.EEXIST:
            raise


class Template(object):
    "Basic template which substitutes items within {{}} using a dictionary."

    _substitute_re = re.compile('{{([^}]+)}}')

    @classmethod
    def from_file(cls, path):
        with open(path, 'r', encoding='utf-8') as fd:
            return cls(fd.read())

    def __init__(self, template_text):
        self.template_text = template_text

    def substitute(self, **context):
        return self._substitute_re.sub(lambda m: context[m.group(1)], self.template_text)
